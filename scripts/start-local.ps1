[CmdletBinding()]
param(
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = Join-Path $repoRoot '.runtime'
$logRoot = Join-Path $runtimeRoot 'logs'
$postgresData = Join-Path $runtimeRoot 'postgres'
$postgresLog = Join-Path $runtimeRoot 'postgres.log'
$postgresPort = 55432
$jwtSecret = 'pera-local-development-secret-2026-minimum-32-bytes'

function Resolve-PostgresBin {
    if ($env:PG_BIN -and (Test-Path (Join-Path $env:PG_BIN 'pg_ctl.exe'))) {
        return (Resolve-Path $env:PG_BIN).Path
    }

    $installRoot = 'C:\Program Files\PostgreSQL'
    if (Test-Path $installRoot) {
        $candidate = Get-ChildItem $installRoot -Directory |
            Sort-Object { [int]($_.Name -replace '\D.*$', '') } -Descending |
            ForEach-Object { Join-Path $_.FullName 'bin' } |
            Where-Object { Test-Path (Join-Path $_ 'pg_ctl.exe') } |
            Select-Object -First 1
        if ($candidate) { return $candidate }
    }

    throw 'No se ha encontrado PostgreSQL. Instala PostgreSQL 17+ o define PG_BIN.'
}

function Assert-PortAvailable([int]$Port) {
    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($listener) {
        throw "El puerto $Port ya esta ocupado. Ejecuta scripts\stop-local.ps1 o revisa el proceso en escucha."
    }
}

function Start-JavaService {
    param([string]$Name, [int]$Port, [string]$Jar, [string]$Database)

    $env:SERVER_PORT = [string]$Port
    $env:PERA_JWT_SECRET = $jwtSecret
    if ($Database) {
        $env:DB_URL = "jdbc:postgresql://localhost:$postgresPort/$Database"
        $env:DB_USER = 'pera'
        $env:DB_PASSWORD = 'pera_dev_password'
    }

    $arguments = @('-jar', (Join-Path $repoRoot $Jar))
    $process = Start-Process -FilePath 'java' -ArgumentList $arguments -WorkingDirectory $repoRoot `
        -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logRoot "$Name.out.log") `
        -RedirectStandardError (Join-Path $logRoot "$Name.err.log") -PassThru
    Write-Host ("  {0,-13} PID {1} - puerto {2}" -f $Name, $process.Id, $Port)
}

Set-Location $repoRoot
New-Item -ItemType Directory -Force -Path $runtimeRoot, $logRoot | Out-Null

foreach ($port in @(8080, 8081, 8082, 8083, 8084, 5173)) {
    Assert-PortAvailable $port
}

$postgresBin = Resolve-PostgresBin
$pgCtl = Join-Path $postgresBin 'pg_ctl.exe'
$initDb = Join-Path $postgresBin 'initdb.exe'
$createdb = Join-Path $postgresBin 'createdb.exe'
$psql = Join-Path $postgresBin 'psql.exe'

if (-not (Test-Path (Join-Path $postgresData 'PG_VERSION'))) {
    Write-Host 'Inicializando PostgreSQL local aislado en .runtime...'
    & $initDb -D $postgresData -U pera --auth=trust --encoding=UTF8 --locale=C
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo inicializar PostgreSQL.' }
}

& $pgCtl status -D $postgresData *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Arrancando PostgreSQL en el puerto $postgresPort..."
    & $pgCtl start -D $postgresData -l $postgresLog -o "-p $postgresPort -h localhost" -w
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo arrancar PostgreSQL.' }
}

foreach ($database in @('pera_identity', 'pera_master_data', 'pera_sales', 'pera_finance')) {
    $exists = & $psql -h localhost -p $postgresPort -U pera -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$database'"
    if ($exists -ne '1') {
        & $createdb -h localhost -p $postgresPort -U pera $database
        if ($LASTEXITCODE -ne 0) { throw "No se pudo crear la base $database." }
    }
}

if (-not $SkipBuild) {
    Write-Host 'Compilando backend y ejecutando pruebas...'
    & mvn -f backend/pom.xml package
    if ($LASTEXITCODE -ne 0) { throw 'El backend no ha superado la compilacion o las pruebas.' }

    Write-Host 'Instalando y verificando frontend...'
    Push-Location (Join-Path $repoRoot 'frontend')
    try {
        & npm.cmd ci
        if ($LASTEXITCODE -ne 0) { throw 'npm ci ha fallado.' }
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw 'El frontend no ha compilado.' }
    } finally {
        Pop-Location
    }
}

Write-Host 'Arrancando servicios...'
$env:PERA_BOOTSTRAP_ADMIN_PASSWORD = 'ChangeMe123!'
Start-JavaService 'identity' 8081 'backend\identity-service\target\identity-service-0.1.0-SNAPSHOT.jar' 'pera_identity'
Start-JavaService 'master-data' 8082 'backend\master-data-service\target\master-data-service-0.1.0-SNAPSHOT.jar' 'pera_master_data'
Start-JavaService 'sales' 8083 'backend\sales-service\target\sales-service-0.1.0-SNAPSHOT.jar' 'pera_sales'
Start-JavaService 'finance' 8084 'backend\finance-service\target\finance-service-0.1.0-SNAPSHOT.jar' 'pera_finance'

$env:SERVER_PORT = '8080'
$env:PERA_JWT_SECRET = $jwtSecret
$gateway = Start-Process -FilePath 'java' -ArgumentList @('-jar', (Join-Path $repoRoot 'backend\api-gateway\target\api-gateway-0.1.0-SNAPSHOT.jar')) `
    -WorkingDirectory $repoRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logRoot 'gateway.out.log') `
    -RedirectStandardError (Join-Path $logRoot 'gateway.err.log') -PassThru
Write-Host ("  {0,-13} PID {1} - puerto 8080" -f 'gateway', $gateway.Id)

$frontend = Start-Process -FilePath 'npm.cmd' -ArgumentList @('run', 'dev', '--', '--host', '127.0.0.1', '--port', '5173') `
    -WorkingDirectory (Join-Path $repoRoot 'frontend') -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $logRoot 'frontend.out.log') `
    -RedirectStandardError (Join-Path $logRoot 'frontend.err.log') -PassThru
Write-Host ("  {0,-13} PID {1} - puerto 5173" -f 'frontend', $frontend.Id)

$healthUrls = @(
    'http://localhost:8080/actuator/health',
    'http://localhost:8081/actuator/health',
    'http://localhost:8082/actuator/health',
    'http://localhost:8083/actuator/health',
    'http://localhost:8084/actuator/health',
    'http://127.0.0.1:5173'
)
$pending = @($healthUrls)
$deadline = (Get-Date).AddSeconds(60)
while ($pending.Count -gt 0 -and (Get-Date) -lt $deadline) {
    $nextPending = @()
    foreach ($url in $pending) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 2
            if ($response.StatusCode -ne 200) { $nextPending += $url }
        } catch {
            $nextPending += $url
        }
    }
    $pending = $nextPending
    if ($pending.Count -gt 0) { Start-Sleep -Seconds 1 }
}

if ($pending.Count -gt 0) {
    Write-Warning "No respondieron a tiempo: $($pending -join ', '). Revisa .runtime\logs."
    exit 1
}

Write-Host ''
Write-Host 'PERA ERP esta listo en http://localhost:5173' -ForegroundColor Green
Write-Host 'Usuario: admin'
Write-Host 'Contrasena: ChangeMe123!'
Write-Host 'Para detenerlo: .\scripts\stop-local.ps1'
