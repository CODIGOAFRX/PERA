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
$internalServiceKey = 'pera-local-internal-service-key-change-me'

# NIF del productor del software ante la AEAT. En desarrollo se usa el NIF de ejemplo que la propia
# AEAT emplea en su documentacion. En produccion es el NIF real de quien comercializa PERA y debe
# llegar por PERA_VERIFACTU_DEVELOPER_TAX_ID; si ya viene definido, este script lo respeta.
if (-not $env:PERA_VERIFACTU_DEVELOPER_TAX_ID) {
    $env:PERA_VERIFACTU_DEVELOPER_TAX_ID = '89890001K'
}

# Razon social de quien comercializa PERA. No tiene valor por defecto en la aplicacion a proposito:
# caer al nombre del programa producia un registro que parecia completo y no lo estaba.
if (-not $env:PERA_VERIFACTU_DEVELOPER_NAME) {
    $env:PERA_VERIFACTU_DEVELOPER_NAME = 'PERA ERP (desarrollo)'
}

# Identifica la INSTALACION del programa, no a la empresa que lo usa: varias empresas en la misma
# instalacion tienen que declarar todas el mismo numero.
if (-not $env:PERA_VERIFACTU_INSTALLATION_NUMBER) {
    $env:PERA_VERIFACTU_INSTALLATION_NUMBER = 'PERA-LOCAL-DEV'
}

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

        if ($candidate) {
            return $candidate
        }
    }

    throw 'No se ha encontrado PostgreSQL. Instala PostgreSQL 17+ o define PG_BIN.'
}

function Assert-PortAvailable([int]$Port) {
    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue

    if ($listener) {
        throw "El puerto $Port ya esta ocupado. Ejecuta scripts\stop-local.ps1 o revisa el proceso en escucha."
    }
}

# ---------------------------------------------------------------------------
# JAVA / JDK
#
# PERA necesita Java 17 o superior.
#
# No usamos directamente "java" porque Windows puede tener un Java antiguo
# (por ejemplo Java 8 de Oracle) antes que el JDK moderno en el PATH.
#
# El script busca un JDK compatible y prefiere Java 21 LTS.
# ---------------------------------------------------------------------------

function Get-JavaMajorVersion {
    param(
        [string]$JavaExe
    )

    try {
        $javaBin = Split-Path $JavaExe -Parent
        $javacExe = Join-Path $javaBin 'javac.exe'

        # PERA necesita un JDK, no solamente un JRE.
        if (-not (Test-Path $javacExe)) {
            return 0
        }

        # javac -version funciona correctamente tanto en
        # Windows PowerShell 5.1 como en PowerShell 7.
        $versionOutput = (& $javacExe -version 2>&1 | Select-Object -First 1).ToString()

        # Ejemplos:
        # javac 21.0.12
        # javac 17.0.10
        # javac 1.8.0_503
        if ($versionOutput -match 'javac\s+(?:1\.)?(\d+)') {
            return [int]$Matches[1]
        }
    }
    catch {
        return 0
    }

    return 0
}

function Resolve-CompatibleJava {
    $candidates = @()

    # 1. JAVA_HOME
    if ($env:JAVA_HOME) {
        $javaFromHome = Join-Path $env:JAVA_HOME 'bin\java.exe'

        if (Test-Path $javaFromHome) {
            $candidates += $javaFromHome
        }
    }

    # 2. Instalaciones habituales de JDK en Windows
    $jdkRoots = @(
        'C:\Program Files\Eclipse Adoptium',
        'C:\Program Files\Java',
        'C:\Program Files\Microsoft',
        'C:\Program Files\Amazon Corretto',
        'C:\Program Files\Zulu'
    )

    foreach ($jdkRoot in $jdkRoots) {
        if (Test-Path $jdkRoot) {
            Get-ChildItem $jdkRoot -Directory -ErrorAction SilentlyContinue |
                ForEach-Object {
                    $candidate = Join-Path $_.FullName 'bin\java.exe'

                    if (Test-Path $candidate) {
                        $candidates += $candidate
                    }
                }
        }
    }

    # 3. Java disponibles en PATH
    $javaCommands = Get-Command java -All -ErrorAction SilentlyContinue

    if ($javaCommands) {
        foreach ($javaCommand in $javaCommands) {
            if ($javaCommand.Source) {
                $candidates += $javaCommand.Source
            }
        }
    }

    $compatible = @()

    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if (-not (Test-Path $candidate)) {
            continue
        }

        $javaBin = Split-Path $candidate -Parent
        $javacExe = Join-Path $javaBin 'javac.exe'

        # Necesitamos un JDK, no solamente un JRE,
        # porque start-local.ps1 compila el backend con Maven.
        if (-not (Test-Path $javacExe)) {
            continue
        }

        $major = Get-JavaMajorVersion $candidate

        if ($major -ge 17) {
            $compatible += [PSCustomObject]@{
                Path  = $candidate
                Major = $major
            }
        }
    }

    if ($compatible.Count -eq 0) {
        throw @"
No se ha encontrado un JDK Java 17 o superior.

PERA necesita JDK 17+ para compilar y ejecutar el backend.

Se recomienda instalar:

    Eclipse Temurin JDK 21 LTS

Despues vuelve a ejecutar:

    .\scripts\start-local.ps1
"@
    }

    # Preferencia:
    # 1. Java 21 LTS
    # 2. Java 17 LTS
    # 3. Cualquier otro Java >= 17
    $selected = $compatible |
        Sort-Object `
            @{ Expression = {
                    if ($_.Major -eq 21) {
                        0
                    }
                    elseif ($_.Major -eq 17) {
                        1
                    }
                    else {
                        2
                    }
                }
            },
            @{ Expression = { $_.Major }; Ascending = $true } |
        Select-Object -First 1

    return $selected
}

$selectedJava = Resolve-CompatibleJava

$script:JavaExe = $selectedJava.Path
$javaBin = Split-Path $script:JavaExe -Parent
$javaHome = Split-Path $javaBin -Parent

# Aseguramos que Maven y cualquier proceso hijo utilicen el mismo JDK.
$env:JAVA_HOME = $javaHome
$env:Path = "$javaBin;$env:Path"

Write-Host ''
Write-Host "Java $($selectedJava.Major) detectado correctamente." -ForegroundColor Green
Write-Host "  JAVA_HOME: $javaHome"
Write-Host "  java.exe:  $script:JavaExe"
Write-Host ''

function Start-JavaService {
    param(
        [string]$Name,
        [int]$Port,
        [string]$Jar,
        [string]$Database
    )

    $env:SERVER_PORT = [string]$Port
    $env:PERA_JWT_SECRET = $jwtSecret

    if ($Database) {
        $env:DB_URL = "jdbc:postgresql://localhost:$postgresPort/$Database"
        $env:DB_USER = 'pera'
        $env:DB_PASSWORD = 'pera_dev_password'
    }

    $arguments = @(
        '-jar',
        (Join-Path $repoRoot $Jar)
    )

    $process = Start-Process `
        -FilePath $script:JavaExe `
        -ArgumentList $arguments `
        -WorkingDirectory $repoRoot `
        -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logRoot "$Name.out.log") `
        -RedirectStandardError (Join-Path $logRoot "$Name.err.log") `
        -PassThru

    Write-Host ("  {0,-13} PID {1} - puerto {2}" -f $Name, $process.Id, $Port)
}

Set-Location $repoRoot

New-Item `
    -ItemType Directory `
    -Force `
    -Path $runtimeRoot, $logRoot |
    Out-Null

foreach ($port in @(8080, 8081, 8082, 8083, 8084, 8085, 8086, 8087, 5173)) {
    Assert-PortAvailable $port
}

$postgresBin = Resolve-PostgresBin

$pgCtl = Join-Path $postgresBin 'pg_ctl.exe'
$initDb = Join-Path $postgresBin 'initdb.exe'
$createdb = Join-Path $postgresBin 'createdb.exe'
$psql = Join-Path $postgresBin 'psql.exe'

if (-not (Test-Path (Join-Path $postgresData 'PG_VERSION'))) {
    Write-Host 'Inicializando PostgreSQL local aislado en .runtime...'

    & $initDb `
        -D $postgresData `
        -U pera `
        --auth=trust `
        --encoding=UTF8 `
        --locale=C

    if ($LASTEXITCODE -ne 0) {
        throw 'No se pudo inicializar PostgreSQL.'
    }
}

& $pgCtl status -D $postgresData *> $null

if ($LASTEXITCODE -ne 0) {
    Write-Host "Arrancando PostgreSQL en el puerto $postgresPort..."

    & $pgCtl start `
        -D $postgresData `
        -l $postgresLog `
        -o "-p $postgresPort -h localhost" `
        -w

    if ($LASTEXITCODE -ne 0) {
        throw 'No se pudo arrancar PostgreSQL.'
    }
}

foreach ($database in @(
        'pera_identity',
        'pera_master_data',
        'pera_sales',
        'pera_finance',
        'pera_operations',
        'pera_activity',
        'pera_licensing'
    )) {

    $exists = & $psql `
        -h localhost `
        -p $postgresPort `
        -U pera `
        -d postgres `
        -tAc "SELECT 1 FROM pg_database WHERE datname='$database'"

    if ($exists -ne '1') {
        & $createdb `
            -h localhost `
            -p $postgresPort `
            -U pera `
            $database

        if ($LASTEXITCODE -ne 0) {
            throw "No se pudo crear la base $database."
        }
    }
}

if (-not $SkipBuild) {
    Write-Host 'Compilando backend y ejecutando pruebas...'

    & mvn -f backend/pom.xml package

    if ($LASTEXITCODE -ne 0) {
        throw 'El backend no ha superado la compilacion o las pruebas.'
    }

    Write-Host 'Instalando y verificando frontend...'

    Push-Location (Join-Path $repoRoot 'frontend')

    try {
        & npm.cmd ci

        if ($LASTEXITCODE -ne 0) {
            throw 'npm ci ha fallado.'
        }

        & npm.cmd run build

        if ($LASTEXITCODE -ne 0) {
            throw 'El frontend no ha compilado.'
        }
    }
    finally {
        Pop-Location
    }
}

Write-Host 'Arrancando servicios...'

$env:PERA_BOOTSTRAP_ADMIN_PASSWORD = 'ChangeMe123!'
$env:PERA_INTERNAL_SERVICE_KEY = $internalServiceKey
$env:PERA_COMPANY_LOGO_STORAGE_ROOT = Join-Path $runtimeRoot 'company-logos'
$env:MASTER_DATA_SERVICE_URL = 'http://localhost:8082'

Start-JavaService `
    'identity' `
    8081 `
    'backend\identity-service\target\identity-service-0.1.0-SNAPSHOT.jar' `
    'pera_identity'

Start-JavaService `
    'master-data' `
    8082 `
    'backend\master-data-service\target\master-data-service-0.1.0-SNAPSHOT.jar' `
    'pera_master_data'

Start-JavaService `
    'sales' `
    8083 `
    'backend\sales-service\target\sales-service-0.1.0-SNAPSHOT.jar' `
    'pera_sales'

Start-JavaService `
    'finance' `
    8084 `
    'backend\finance-service\target\finance-service-0.1.0-SNAPSHOT.jar' `
    'pera_finance'

$env:PERA_SHIPMENT_DOCUMENT_STORAGE_ROOT = Join-Path $runtimeRoot 'shipment-documents'
$env:PERA_SHIPMENT_DOCUMENT_MAX_BYTES = '10485760'

Start-JavaService `
    'operations' `
    8085 `
    'backend\operations-service\target\operations-service-0.1.0-SNAPSHOT.jar' `
    'pera_operations'

Start-JavaService `
    'activity' `
    8086 `
    'backend\activity-service\target\activity-service-0.1.0-SNAPSHOT.jar' `
    'pera_activity'

$env:PERA_LICENSE_HASH_PEPPER = 'pera-local-license-hash-pepper-2026-at-least-32-bytes'

Start-JavaService `
    'licensing' `
    8087 `
    'backend\licensing-service\target\licensing-service-0.1.0-SNAPSHOT.jar' `
    'pera_licensing'

$env:SERVER_PORT = '8080'
$env:PERA_JWT_SECRET = $jwtSecret
$env:PERA_INTERNAL_SERVICE_KEY = $internalServiceKey
$env:OPERATIONS_SERVICE_URL = 'http://localhost:8085'
$env:ACTIVITY_SERVICE_URL = 'http://localhost:8086'
$env:LICENSING_SERVICE_URL = 'http://localhost:8087'
$env:PERA_LICENSE_ENFORCEMENT_ENABLED = 'false'
$env:PERA_LICENSE_INSTALLATION_ID = ''
$env:PERA_LICENSE_INSTALLATION_TOKEN = ''
$env:PERA_LICENSE_COMPANY_ID = ''

$gateway = Start-Process `
    -FilePath $script:JavaExe `
    -ArgumentList @(
        '-jar',
        (Join-Path $repoRoot 'backend\api-gateway\target\api-gateway-0.1.0-SNAPSHOT.jar')
    ) `
    -WorkingDirectory $repoRoot `
    -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $logRoot 'gateway.out.log') `
    -RedirectStandardError (Join-Path $logRoot 'gateway.err.log') `
    -PassThru

Write-Host ("  {0,-13} PID {1} - puerto 8080" -f 'gateway', $gateway.Id)

$frontend = Start-Process `
    -FilePath 'npm.cmd' `
    -ArgumentList @(
        'run',
        'dev',
        '--',
        '--host',
        '127.0.0.1',
        '--port',
        '5173'
    ) `
    -WorkingDirectory (Join-Path $repoRoot 'frontend') `
    -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $logRoot 'frontend.out.log') `
    -RedirectStandardError (Join-Path $logRoot 'frontend.err.log') `
    -PassThru

Write-Host ("  {0,-13} PID {1} - puerto 5173" -f 'frontend', $frontend.Id)

$healthUrls = @(
    'http://localhost:8080/actuator/health',
    'http://localhost:8081/actuator/health',
    'http://localhost:8082/actuator/health',
    'http://localhost:8083/actuator/health',
    'http://localhost:8084/actuator/health',
    'http://localhost:8085/actuator/health',
    'http://localhost:8086/actuator/health',
    'http://localhost:8087/actuator/health',
    'http://127.0.0.1:5173'
)

$pending = @($healthUrls)
$deadline = (Get-Date).AddSeconds(60)

while ($pending.Count -gt 0 -and (Get-Date) -lt $deadline) {
    $nextPending = @()

    foreach ($url in $pending) {
        try {
            $response = Invoke-WebRequest `
                -UseBasicParsing `
                -Uri $url `
                -TimeoutSec 2

            if ($response.StatusCode -ne 200) {
                $nextPending += $url
            }
        }
        catch {
            $nextPending += $url
        }
    }

    $pending = $nextPending

    if ($pending.Count -gt 0) {
        Start-Sleep -Seconds 1
    }
}

if ($pending.Count -gt 0) {
    Write-Warning "No respondieron a tiempo: $($pending -join ', '). Revisa .runtime\logs."
    exit 1
}

Write-Host ''
Write-Host 'PERA ERP esta listo en http://localhost:5173' -ForegroundColor Green
Write-Host 'Usuario: admin'
Write-Host 'Contrasena: ChangeMe123!'
Write-Host 'Perfiles demo: admin, administracion, economia, logistica y catalogo'
Write-Host 'Para detenerlo: .\scripts\stop-local.ps1'