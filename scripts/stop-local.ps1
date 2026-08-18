[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$postgresData = Join-Path $repoRoot '.runtime\postgres'

$expectedProcesses = @{
    8080 = 'api-gateway'
    8081 = 'identity-service'
    8082 = 'master-data-service'
    8083 = 'sales-service'
    8084 = 'finance-service'
    8085 = 'operations-service'
    8086 = 'activity-service'
    8087 = 'licensing-service'
    5173 = 'vite'
}

function Stop-ProcessTree([int]$ProcessId) {
    $children = Get-CimInstance Win32_Process -Filter "ParentProcessId=$ProcessId" -ErrorAction SilentlyContinue
    foreach ($child in @($children)) {
        Stop-ProcessTree ([int]$child.ProcessId)
    }
    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

foreach ($entry in $expectedProcesses.GetEnumerator()) {
    $listener = Get-NetTCPConnection -LocalPort $entry.Key -State Listen -ErrorAction SilentlyContinue
    foreach ($connection in @($listener)) {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($connection.OwningProcess)"
        if ($process.CommandLine -notlike "*$($entry.Value)*") {
            Write-Warning "No se detiene el PID $($connection.OwningProcess) del puerto $($entry.Key): no parece pertenecer a PERA ERP."
            continue
        }
        Write-Host "Deteniendo $($entry.Value) (PID $($connection.OwningProcess))..."
        Stop-ProcessTree ([int]$connection.OwningProcess)
    }
}

if (Test-Path (Join-Path $postgresData 'PG_VERSION')) {
    $postgresBin = if ($env:PG_BIN) { $env:PG_BIN } else {
        Get-ChildItem 'C:\Program Files\PostgreSQL' -Directory -ErrorAction SilentlyContinue |
            Sort-Object { [int]($_.Name -replace '\D.*$', '') } -Descending |
            ForEach-Object { Join-Path $_.FullName 'bin' } |
            Where-Object { Test-Path (Join-Path $_ 'pg_ctl.exe') } |
            Select-Object -First 1
    }
    if ($postgresBin) {
        $pgCtl = Join-Path $postgresBin 'pg_ctl.exe'
        & $pgCtl status -D $postgresData *> $null
        if ($LASTEXITCODE -eq 0) {
            Write-Host 'Deteniendo PostgreSQL local...'
            & $pgCtl stop -D $postgresData -m fast -w
        }
    }
}

Write-Host 'Entorno local detenido. Los datos se conservan en .runtime.' -ForegroundColor Green
