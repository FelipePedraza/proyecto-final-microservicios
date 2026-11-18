[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ScriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDirectory
$TemplateFile = Join-Path $ProjectRoot '.env.example'
$EnvFile = Join-Path $ProjectRoot '.env'
$SecretsDirectory = Join-Path $ProjectRoot 'secrets'
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$RunningOnWindows = [Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT
$TemporaryEnvFile = $null

function Fail {
    param([Parameter(Mandatory = $true)][string]$Message)
    throw "ERROR: $Message"
}

function Write-Utf8NoBom {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Content
    )

    [IO.File]::WriteAllText($Path, $Content, $script:Utf8NoBom)
}

function Protect-LocalPath {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [switch]$Directory
    )

    if (-not $script:RunningOnWindows) {
        return
    }

    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    $permission = if ($Directory) {
        '{0}:(OI)(CI)F' -f $identity
    }
    else {
        '{0}:(F)' -f $identity
    }

    & icacls.exe $Path '/inheritance:r' '/grant:r' $permission | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Fail "No se pudieron restringir los permisos de $Path."
    }
}

function New-RandomBytes {
    param([Parameter(Mandatory = $true)][int]$Count)

    [byte[]]$bytes = New-Object byte[] $Count
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    }
    finally {
        $generator.Dispose()
    }
    return $bytes
}

function New-RandomHex {
    param([Parameter(Mandatory = $true)][int]$ByteCount)

    [byte[]]$bytes = New-RandomBytes -Count $ByteCount
    return -join ($bytes | ForEach-Object { $_.ToString('x2') })
}

function New-RandomBase64 {
    param([Parameter(Mandatory = $true)][int]$ByteCount)

    [byte[]]$bytes = New-RandomBytes -Count $ByteCount
    return [Convert]::ToBase64String($bytes)
}

function Get-EnvValue {
    param([Parameter(Mandatory = $true)][string]$Key)

    $prefix = "$Key="
    foreach ($line in $script:EnvLines) {
        if ($line.StartsWith($prefix, [StringComparison]::Ordinal)) {
            return $line.Substring($prefix.Length)
        }
    }
    Fail "La plantilla no contiene la variable $Key."
}

function Get-ConfiguredOrGeneratedValue {
    param(
        [Parameter(Mandatory = $true)][string]$Key,
        [Parameter(Mandatory = $true)][scriptblock]$Generator
    )

    $current = Get-EnvValue -Key $Key
    if (-not [string]::IsNullOrWhiteSpace($current) -and
        -not $current.StartsWith('REEMPLAZAR', [StringComparison]::Ordinal) -and
        -not $current.StartsWith('GESTIONADO_EN_SECRETS', [StringComparison]::Ordinal)) {
        return $current
    }
    return & $Generator
}

function Ensure-Secret {
    param(
        [Parameter(Mandatory = $true)][string]$FileName,
        [Parameter(Mandatory = $true)][string]$Value
    )

    $path = Join-Path $script:SecretsDirectory $FileName
    if (Test-Path -LiteralPath $path -PathType Leaf) {
        $current = [IO.File]::ReadAllText($path).TrimEnd([char[]]"`r`n")
        if (-not [string]::IsNullOrWhiteSpace($current) -and
            -not $current.StartsWith('REEMPLAZAR', [StringComparison]::Ordinal)) {
            Protect-LocalPath -Path $path
            return
        }
    }

    if ([string]::IsNullOrWhiteSpace($Value)) {
        Fail "No se generó un valor para $FileName."
    }

    Write-Utf8NoBom -Path $path -Content "$Value`r`n"
    Protect-LocalPath -Path $path
}

function Set-EnvMarkers {
    param([Parameter(Mandatory = $true)][hashtable]$Markers)

    $found = @{}
    $updated = foreach ($line in $script:EnvLines) {
        $replacement = $null
        foreach ($key in $Markers.Keys) {
            $prefix = "$key="
            if ($line.StartsWith($prefix, [StringComparison]::Ordinal)) {
                $replacement = "$key=$($Markers[$key])"
                $found[$key] = $true
                break
            }
        }
        if ($null -ne $replacement) { $replacement } else { $line }
    }

    foreach ($key in $Markers.Keys) {
        if (-not $found.ContainsKey($key)) {
            Fail "La plantilla no contiene la variable $key."
        }
    }

    $script:TemporaryEnvFile = Join-Path $script:ProjectRoot ".env.tmp.$([Guid]::NewGuid().ToString('N'))"
    [IO.File]::WriteAllLines($script:TemporaryEnvFile, [string[]]$updated, $script:Utf8NoBom)
    Move-Item -LiteralPath $script:TemporaryEnvFile -Destination $script:EnvFile -Force
    $script:TemporaryEnvFile = $null
    Protect-LocalPath -Path $script:EnvFile
}

try {
    if (-not (Test-Path -LiteralPath $TemplateFile -PathType Leaf)) {
        Fail "No se encontró $TemplateFile."
    }

    if (-not (Test-Path -LiteralPath $EnvFile -PathType Leaf)) {
        Copy-Item -LiteralPath $TemplateFile -Destination $EnvFile
    }

    if (-not (Test-Path -LiteralPath $SecretsDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $SecretsDirectory | Out-Null
    }

    Protect-LocalPath -Path $EnvFile
    Protect-LocalPath -Path $SecretsDirectory -Directory
    $script:EnvLines = [IO.File]::ReadAllLines($EnvFile)

    $secretDefinitions = @(
        @{ File = 'db_empleado_password.txt'; Key = 'EMPLEADO_DB_PASSWORD'; Generate = { New-RandomHex -ByteCount 20 } },
        @{ File = 'db_departamento_password.txt'; Key = 'DEPARTAMENTO_DB_PASSWORD'; Generate = { New-RandomHex -ByteCount 20 } },
        @{ File = 'db_notificaciones_password.txt'; Key = 'NOTIFICACIONES_DB_PASSWORD'; Generate = { New-RandomHex -ByteCount 20 } },
        @{ File = 'db_perfiles_password.txt'; Key = 'PERFILES_DB_PASSWORD'; Generate = { New-RandomHex -ByteCount 20 } },
        @{ File = 'db_vacaciones_password.txt'; Key = 'VACACIONES_DB_PASSWORD'; Generate = { New-RandomHex -ByteCount 20 } },
        @{ File = 'db_auth_password.txt'; Key = 'AUTH_DB_PASSWORD'; Generate = { New-RandomHex -ByteCount 20 } },
        @{ File = 'broker_password.txt'; Key = 'RABBITMQ_PASSWORD'; Generate = { New-RandomHex -ByteCount 20 } },
        @{ File = 'rabbitmq_erlang_cookie.txt'; Key = 'RABBITMQ_ERLANG_COOKIE'; Generate = { New-RandomHex -ByteCount 32 } },
        @{ File = 'jwt_secret.txt'; Key = 'JWT_SECRET_KEY'; Generate = { New-RandomBase64 -ByteCount 32 } },
        @{ File = 'auth_admin_password.txt'; Key = 'ADMIN_PASSWORD'; Generate = { New-RandomHex -ByteCount 20 } },
        @{ File = 'jenkins_admin_password.txt'; Key = 'JENKINS_ADMIN_PASSWORD'; Generate = { New-RandomHex -ByteCount 20 } },
        @{ File = 'sonar_db_password.txt'; Key = 'SONAR_DB_PASSWORD'; Generate = { New-RandomHex -ByteCount 20 } },
        @{ File = 'sonar_token.txt'; Key = 'SONAR_TOKEN'; Generate = { "PENDIENTE_CONFIGURAR_EN_SONARQUBE_$(New-RandomHex -ByteCount 8)" } },
        @{ File = 'grafana_admin_password.txt'; Key = 'GRAFANA_ADMIN_PASSWORD'; Generate = { New-RandomHex -ByteCount 20 } },
        @{ File = 'discord_webhook_url.txt'; Key = 'DISCORD_WEBHOOK_URL'; Generate = { 'https://discord.com/api/webhooks/0/placeholder' } }
    )

    foreach ($definition in $secretDefinitions) {
        $value = Get-ConfiguredOrGeneratedValue -Key $definition.Key -Generator $definition.Generate
        Ensure-Secret -FileName $definition.File -Value $value
    }
    Ensure-Secret -FileName 'vault_root_token.txt' -Value (New-RandomHex -ByteCount 32)

    $markers = @{
        EMPLEADO_DB_PASSWORD = 'GESTIONADO_EN_SECRETS_DB_EMPLEADO'
        DEPARTAMENTO_DB_PASSWORD = 'GESTIONADO_EN_SECRETS_DB_DEPARTAMENTO'
        NOTIFICACIONES_DB_PASSWORD = 'GESTIONADO_EN_SECRETS_DB_NOTIFICACIONES'
        PERFILES_DB_PASSWORD = 'GESTIONADO_EN_SECRETS_DB_PERFILES'
        VACACIONES_DB_PASSWORD = 'GESTIONADO_EN_SECRETS_DB_VACACIONES'
        AUTH_DB_PASSWORD = 'GESTIONADO_EN_SECRETS_DB_AUTH'
        RABBITMQ_PASSWORD = 'GESTIONADO_EN_SECRETS_BROKER'
        RABBITMQ_ERLANG_COOKIE = 'GESTIONADO_EN_SECRETS_RABBITMQ_COOKIE'
        JWT_SECRET_KEY = 'GESTIONADO_EN_SECRETS_JWT'
        ADMIN_PASSWORD = 'GESTIONADO_EN_SECRETS_AUTH_ADMIN'
        JENKINS_ADMIN_PASSWORD = 'GESTIONADO_EN_SECRETS_JENKINS_ADMIN'
        SONAR_DB_PASSWORD = 'GESTIONADO_EN_SECRETS_SONAR_DB'
        SONAR_TOKEN = 'GESTIONADO_EN_SECRETS_SONAR_TOKEN'
        E2E_ADMIN_PASSWORD = 'GESTIONADO_EN_SECRETS_AUTH_ADMIN'
        E2E_JWT_SECRET = 'GESTIONADO_EN_SECRETS_JWT'
        GRAFANA_ADMIN_PASSWORD = 'GESTIONADO_EN_SECRETS_GRAFANA_ADMIN'
        DISCORD_WEBHOOK_URL = 'GESTIONADO_EN_SECRETS_DISCORD_WEBHOOK'
    }
    Set-EnvMarkers -Markers $markers

    $expectedSecretFiles = @($secretDefinitions.File) + 'vault_root_token.txt'
    foreach ($fileName in $expectedSecretFiles) {
        $path = Join-Path $SecretsDirectory $fileName
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            Fail "No se creó el archivo requerido $path."
        }
    }
    foreach ($secretFile in (Get-ChildItem -LiteralPath $SecretsDirectory -Filter '*.txt' -File)) {
        $content = [IO.File]::ReadAllText($secretFile.FullName).Trim()
        if ([string]::IsNullOrWhiteSpace($content)) {
            Fail "El archivo $($secretFile.FullName) está vacío."
        }
        if ($content.StartsWith('REEMPLAZAR', [StringComparison]::Ordinal)) {
            Fail "El archivo $($secretFile.FullName) conserva un marcador de plantilla."
        }
        Protect-LocalPath -Path $secretFile.FullName
    }

    Write-Host 'Configuración local preparada.'
    Write-Host '  .env: variables no sensibles y referencias; acceso restringido al usuario actual.'
    Write-Host '  secrets/: 16 archivos locales; acceso restringido al usuario actual.'
    Write-Host 'Los valores no se mostraron en la terminal.'
    Write-Host 'Reemplaza secrets\sonar_token.txt con un token real antes de ejecutar análisis de Jenkins.'
}
catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 1
}
finally {
    if ($null -ne $TemporaryEnvFile -and (Test-Path -LiteralPath $TemporaryEnvFile)) {
        Remove-Item -LiteralPath $TemporaryEnvFile -Force
    }
}
