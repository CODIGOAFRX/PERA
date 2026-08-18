[CmdletBinding()]
param(
    [string]$LicensingUrl = 'http://localhost:8087',
    [string]$InstallationId = "$env:COMPUTERNAME-pera-erp"
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($InstallationId) -or $InstallationId.Length -lt 8) {
    throw 'InstallationId debe tener al menos 8 caracteres y mantenerse estable en esta instalación.'
}

$secureCode = Read-Host 'Código de activación' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureCode)
try {
    $activationCode = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    $payload = @{ activationCode = $activationCode; installationId = $InstallationId } | ConvertTo-Json
    $endpoint = "$($LicensingUrl.TrimEnd('/'))/public/v1/licenses/activate"
    $response = Invoke-RestMethod -Method Post -Uri $endpoint -ContentType 'application/json' -Body $payload
} finally {
    if ($pointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
    $activationCode = $null
    $payload = $null
}

if (-not $response.valid -or [string]::IsNullOrWhiteSpace($response.installationToken)) {
    throw "La licencia no se pudo activar. Estado: $($response.status)"
}

Write-Host ''
Write-Host 'Licencia activada. Guarda estos valores en un gestor de secretos; el token no se puede recuperar después.' -ForegroundColor Green
Write-Host "PERA_LICENSE_ENFORCEMENT_ENABLED=true"
Write-Host "PERA_LICENSE_INSTALLATION_ID=$InstallationId"
Write-Host "PERA_LICENSE_INSTALLATION_TOKEN=$($response.installationToken)"
Write-Host "PERA_LICENSE_COMPANY_ID=$($response.companyId)"
Write-Host "Próxima comprobación: $($response.nextCheckAt)"
