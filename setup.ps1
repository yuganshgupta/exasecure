#Requires -Version 5.1

<#
.SYNOPSIS
    Installs and verifies ExaSecure on a local Windows workstation without containers.

.DESCRIPTION
    Detects the Java and MySQL toolchains, restores hash-pinned Java dependencies,
    creates or safely upgrades the local MySQL schema, provisions a restricted
    runtime database account, writes an ignored db.properties file, creates
    BCrypt-backed demo users, and runs build/JDBC/authentication smoke tests.

    The normal path never drops, truncates, or deletes application data. -Reset is
    explicit, backed up, and protected by two interactive confirmations.
#>

[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param(
    [ValidatePattern('^[A-Za-z][A-Za-z0-9_]{0,63}$')]
    [string] $DatabaseName = 'secure_exam_db',

    [ValidateRange(1, 65535)]
    [int] $DatabasePort = 3306,

    [ValidatePattern('^[A-Za-z0-9_.-]{1,32}$')]
    [string] $DatabaseAdminUser = 'root',

    [ValidatePattern('^[A-Za-z][A-Za-z0-9_]{0,31}$')]
    [string] $AppDatabaseUser = 'exasecure_app',

    [System.Management.Automation.PSCredential] $AdminCredential,

    [string] $MySqlClientPath,

    [switch] $VerifyOnly,
    [switch] $SkipBuild,
    [switch] $SkipDemoAccounts,
    [switch] $InstallMissingPrerequisites,
    [switch] $RotateAppDbPassword,
    [switch] $RotateDemoPasswords,
    [switch] $Reset
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:ProjectRoot = [IO.Path]::GetFullPath($PSScriptRoot)
$script:DatabaseHost = 'localhost'
$script:AppDatabaseHost = 'localhost'
$script:SchemaFile = Join-Path $script:ProjectRoot 'setup\sql\schema-v3.sql'
$script:PropertiesFile = Join-Path $script:ProjectRoot 'db.properties'
$script:Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$script:TempRoot = $null
$script:LogPath = $null
$script:JavaExe = $null
$script:JavacExe = $null
$script:MySqlExe = $null
$script:BuildOutput = $null
$script:Rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$script:NewCredentials = New-Object 'System.Collections.Generic.List[object]'
$script:CredentialSummaryShown = $false
$script:CmdletContext = $PSCmdlet
$script:ResetPreviewOnly = $false

function Write-SetupMessage {
    param(
        [ValidateSet('STAGE', 'OK', 'INFO', 'WARN', 'ERROR')]
        [string] $Level,
        [string] $Message
    )

    $color = switch ($Level) {
        'STAGE' { 'Cyan' }
        'OK'    { 'Green' }
        'WARN'  { 'Yellow' }
        'ERROR' { 'Red' }
        default { 'Gray' }
    }

    Write-Host ("[{0}] {1}" -f $Level, $Message) -ForegroundColor $color
    if ($script:LogPath) {
        $line = "{0:u} [{1}] {2}{3}" -f (Get-Date), $Level, $Message, [Environment]::NewLine
        [IO.File]::AppendAllText($script:LogPath, $line, $script:Utf8NoBom)
    }
}

function Initialize-SetupLog {
    $localData = [Environment]::GetFolderPath('LocalApplicationData')
    if ([string]::IsNullOrWhiteSpace($localData)) {
        $localData = [IO.Path]::GetTempPath()
    }

    $logDirectory = Join-Path $localData 'ExaSecure\setup\logs'
    try {
        [void](New-Item -ItemType Directory -Path $logDirectory -Force)
    } catch {
        $logDirectory = Join-Path ([IO.Path]::GetTempPath()) 'ExaSecure-setup-logs'
        [void](New-Item -ItemType Directory -Path $logDirectory -Force)
    }
    $script:LogPath = Join-Path $logDirectory ("setup-{0}.log" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
    [IO.File]::WriteAllText($script:LogPath, '', $script:Utf8NoBom)
}

function Assert-ParameterCombinations {
    if ($env:OS -ne 'Windows_NT') {
        throw 'This setup script supports native Windows only.'
    }
    if ($VerifyOnly -and $Reset) {
        throw '-VerifyOnly and -Reset cannot be used together.'
    }
    if ($VerifyOnly -and $InstallMissingPrerequisites) {
        throw '-VerifyOnly is read-only and cannot be combined with -InstallMissingPrerequisites.'
    }
    if ($VerifyOnly -and ($RotateAppDbPassword -or $RotateDemoPasswords)) {
        throw '-VerifyOnly cannot rotate credentials.'
    }
    if ($SkipDemoAccounts -and $RotateDemoPasswords) {
        throw '-SkipDemoAccounts and -RotateDemoPasswords cannot be used together.'
    }
    if ($WhatIfPreference -and -not $Reset) {
        throw '-WhatIf is supported only with -Reset. Use -VerifyOnly for read-only validation.'
    }
    if ($WhatIfPreference -and $InstallMissingPrerequisites) {
        throw '-Reset -WhatIf cannot install prerequisites.'
    }
    if ($DatabaseName -in @('mysql', 'information_schema', 'performance_schema', 'sys')) {
        throw "'$DatabaseName' is a protected MySQL system schema name."
    }

    $effectiveAdminUser = if ($AdminCredential) {
        $AdminCredential.UserName
    } else {
        $DatabaseAdminUser
    }
    if ([string]::IsNullOrWhiteSpace($effectiveAdminUser)) {
        throw 'The MySQL administrator username cannot be empty.'
    }
    if ([string]::Equals($AppDatabaseUser, $effectiveAdminUser,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw "The runtime account '$AppDatabaseUser' must not be the MySQL administrator account. Choose a dedicated -AppDatabaseUser."
    }
    $protectedMySqlAccounts = @('root', 'mysql', 'mysql.sys', 'mysql.session', 'mysql.infoschema')
    if ($protectedMySqlAccounts -icontains $AppDatabaseUser) {
        throw "'$AppDatabaseUser' is a protected MySQL account name and cannot be used as the ExaSecure runtime account."
    }
    if (-not (Test-Path -LiteralPath $script:SchemaFile -PathType Leaf)) {
        throw "Required schema file is missing: $script:SchemaFile"
    }
}

function ConvertTo-NativeArgument {
    param([AllowEmptyString()][string] $Value)

    if ($Value.Contains('"')) {
        throw 'A native-process argument unexpectedly contains a double quote.'
    }
    if ($Value.Length -eq 0) {
        return '""'
    }
    if ($Value -notmatch '\s') {
        return $Value
    }
    return '"' + $Value + '"'
}

function Invoke-NativeProcess {
    param(
        [Parameter(Mandatory = $true)][string] $FilePath,
        [string[]] $ArgumentList = @(),
        [AllowNull()][string] $StandardInputText = $null,
        [string] $WorkingDirectory
    )

    $startInfo = New-Object Diagnostics.ProcessStartInfo
    $startInfo.FileName = $FilePath
    $startInfo.Arguments = (($ArgumentList | ForEach-Object { ConvertTo-NativeArgument $_ }) -join ' ')
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    if ($WorkingDirectory) {
        $startInfo.WorkingDirectory = $WorkingDirectory
    }
    try {
        $startInfo.StandardOutputEncoding = $script:Utf8NoBom
        $startInfo.StandardErrorEncoding = $script:Utf8NoBom
    } catch {
        # Encoding properties exist on supported .NET Framework versions, but
        # default process encoding is an acceptable fallback for tool diagnostics.
    }

    $process = New-Object Diagnostics.Process
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) {
            throw "Could not start $FilePath"
        }

        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        if ($null -ne $StandardInputText) {
            $process.StandardInput.Write($StandardInputText)
        }
        $process.StandardInput.Close()
        $process.WaitForExit()

        return [pscustomobject]@{
            ExitCode = $process.ExitCode
            StdOut   = $stdoutTask.Result
            StdErr   = $stderrTask.Result
        }
    } finally {
        $process.Dispose()
    }
}

function Get-ApplicationCommandPath {
    param([string] $Name)

    $command = Get-Command $Name -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($command) {
        return [IO.Path]::GetFullPath($command.Source)
    }
    return $null
}

function Refresh-ProcessPath {
    $machinePath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
    $env:Path = (($machinePath, $userPath) | Where-Object { $_ }) -join ';'
}

function Invoke-WinGetInstall {
    param(
        [string] $PackageId,
        [switch] $Interactive
    )

    $winget = Get-ApplicationCommandPath 'winget.exe'
    if (-not $winget) {
        throw "WinGet is unavailable. Install '$PackageId' manually and rerun setup."
    }

    $arguments = @(
        'install', '--id', $PackageId, '-e', '--source', 'winget',
        '--accept-source-agreements', '--accept-package-agreements'
    )
    if ($Interactive) {
        $arguments += '--interactive'
    } else {
        $arguments += '--silent'
    }

    Write-SetupMessage INFO "Invoking WinGet for $PackageId."
    $result = Invoke-NativeProcess -FilePath $winget -ArgumentList $arguments
    if ($result.ExitCode -ne 0) {
        throw "WinGet could not install $PackageId (exit code $($result.ExitCode))."
    }
    Refresh-ProcessPath
}

function Find-JavacPath {
    $pathResult = Get-ApplicationCommandPath 'javac.exe'
    if ($pathResult) {
        return $pathResult
    }

    foreach ($javaHome in @($env:EXASECURE_JAVA_HOME, $env:JAVA_HOME)) {
        if ([string]::IsNullOrWhiteSpace($javaHome)) {
            continue
        }
        $homeCandidate = Join-Path $javaHome 'bin\javac.exe'
        if (Test-Path -LiteralPath $homeCandidate -PathType Leaf) {
            return [IO.Path]::GetFullPath($homeCandidate)
        }
    }

    $roots = @(
        (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
        (Join-Path $env:ProgramFiles 'Java'),
        (Join-Path $env:ProgramFiles 'Microsoft')
    )
    foreach ($root in $roots) {
        if (-not (Test-Path -LiteralPath $root -PathType Container)) {
            continue
        }
        $candidates = Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending
        foreach ($candidate in $candidates) {
            $tool = Join-Path $candidate.FullName 'bin\javac.exe'
            if (Test-Path -LiteralPath $tool -PathType Leaf) {
                return [IO.Path]::GetFullPath($tool)
            }
        }
    }
    return $null
}

function Resolve-JavaToolchain {
    $script:JavacExe = Find-JavacPath
    if (-not $script:JavacExe -and $InstallMissingPrerequisites) {
        Invoke-WinGetInstall -PackageId 'EclipseAdoptium.Temurin.21.JDK'
        $script:JavacExe = Find-JavacPath
    }
    if (-not $script:JavacExe) {
        throw 'JDK not found. Install an LTS JDK (17 or 21 recommended), or rerun with -InstallMissingPrerequisites.'
    }

    $script:JavaExe = Join-Path (Split-Path -Parent $script:JavacExe) 'java.exe'
    if (-not (Test-Path -LiteralPath $script:JavaExe -PathType Leaf)) {
        throw "java.exe was not found beside javac.exe at $script:JavacExe"
    }

    $versionResult = Invoke-NativeProcess -FilePath $script:JavacExe -ArgumentList @('-version')
    $versionText = ($versionResult.StdOut + ' ' + $versionResult.StdErr).Trim()
    if ($versionResult.ExitCode -ne 0 -or $versionText -notmatch 'javac\s+(\d+)(?:\.(\d+))?') {
        throw "Could not determine the JDK version from $script:JavacExe"
    }

    $firstVersionPart = [int]$Matches[1]
    $javaMajor = if ($firstVersionPart -eq 1 -and $Matches[2]) { [int]$Matches[2] } else { $firstVersionPart }
    if ($javaMajor -lt 17) {
        throw "JDK $javaMajor is too old; ExaSecure 1.0 requires JDK 17 or newer."
    }

    Write-SetupMessage OK "Java toolchain: $versionText ($script:JavacExe)"
}

function Find-MySqlClientPath {
    if ($MySqlClientPath) {
        $explicitPath = [IO.Path]::GetFullPath($MySqlClientPath)
        if (-not (Test-Path -LiteralPath $explicitPath -PathType Leaf)) {
            throw "-MySqlClientPath does not exist: $explicitPath"
        }
        if ([IO.Path]::GetFileName($explicitPath) -ine 'mysql.exe') {
            throw '-MySqlClientPath must point to mysql.exe.'
        }
        return $explicitPath
    }

    $pathResult = Get-ApplicationCommandPath 'mysql.exe'
    if ($pathResult) {
        return $pathResult
    }

    $mysqlRoot = Join-Path $env:ProgramFiles 'MySQL'
    if (Test-Path -LiteralPath $mysqlRoot -PathType Container) {
        $servers = Get-ChildItem -LiteralPath $mysqlRoot -Directory -Filter 'MySQL Server *' -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending
        foreach ($server in $servers) {
            $candidate = Join-Path $server.FullName 'bin\mysql.exe'
            if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                return [IO.Path]::GetFullPath($candidate)
            }
        }
    }
    return $null
}

function Resolve-MySqlClient {
    $script:MySqlExe = Find-MySqlClientPath
    if (-not $script:MySqlExe -and $InstallMissingPrerequisites) {
        Write-SetupMessage INFO 'MySQL installation/configuration is interactive so you can set the root password and Windows service safely.'
        Invoke-WinGetInstall -PackageId 'Oracle.MySQL' -Interactive
        $script:MySqlExe = Find-MySqlClientPath
    }
    if (-not $script:MySqlExe) {
        throw 'mysql.exe was not found. Install and configure MySQL Server 8.x, or rerun with -InstallMissingPrerequisites.'
    }
    Write-SetupMessage OK "MySQL client: $script:MySqlExe"
}

function New-RestrictedTempDirectory {
    $tempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $path = Join-Path $tempBase ("exasecure-setup-{0}" -f [guid]::NewGuid().ToString('N'))
    [void](New-Item -ItemType Directory -Path $path)

    try {
        Set-RestrictedDirectoryAcl $path
    } catch {
        Remove-Item -LiteralPath $path -Force
        throw
    }
    return $path
}

function Remove-RestrictedTempDirectory {
    if (-not $script:TempRoot -or -not (Test-Path -LiteralPath $script:TempRoot)) {
        return
    }

    $tempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
    $resolved = [IO.Path]::GetFullPath($script:TempRoot)
    $leaf = [IO.Path]::GetFileName($resolved)
    if (-not $resolved.StartsWith($tempBase, [StringComparison]::OrdinalIgnoreCase) -or
        -not $leaf.StartsWith('exasecure-setup-', [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove an unexpected temporary path: $resolved"
    }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}

function Test-RepositorySecretSafety {
    $ignoreFile = Join-Path $script:ProjectRoot '.gitignore'
    if (-not (Test-Path -LiteralPath $ignoreFile -PathType Leaf)) {
        throw '.gitignore is missing; refusing to generate db.properties.'
    }

    $git = Get-ApplicationCommandPath 'git.exe'
    if ($git) {
        $tracked = Invoke-NativeProcess -FilePath $git -ArgumentList @(
            '-C', $script:ProjectRoot, 'ls-files', '--error-unmatch', '--', 'db.properties'
        )
        if ($tracked.ExitCode -eq 0) {
            throw 'db.properties is tracked by Git. Untrack it before running setup.'
        }

        $ignored = Invoke-NativeProcess -FilePath $git -ArgumentList @(
            '-C', $script:ProjectRoot, 'check-ignore', '--quiet', '--', 'db.properties'
        )
        if ($ignored.ExitCode -ne 0) {
            throw 'db.properties is not ignored by Git. Add it to .gitignore before setup.'
        }

        $trackedLocalSetup = Invoke-NativeProcess -FilePath $git -ArgumentList @(
            '-C', $script:ProjectRoot, 'ls-files', '--', '.setup-local'
        )
        if (-not [string]::IsNullOrWhiteSpace($trackedLocalSetup.StdOut)) {
            throw '.setup-local contains tracked files. Untrack them before setup can store backups or secure staging data.'
        }
        $ignoredLocalSetup = Invoke-NativeProcess -FilePath $git -ArgumentList @(
            '-C', $script:ProjectRoot, 'check-ignore', '--quiet', '--', '.setup-local/setup-safety-probe'
        )
        if ($ignoredLocalSetup.ExitCode -ne 0) {
            throw '.setup-local is not ignored by Git. Add /.setup-local/ before setup.'
        }
    } else {
        $hasIgnoreRule = Get-Content -LiteralPath $ignoreFile |
            Where-Object { $_.Trim() -match '^/?db\.properties$' } |
            Select-Object -First 1
        if (-not $hasIgnoreRule) {
            throw 'db.properties is not ignored by Git.'
        }
        $hasLocalSetupIgnoreRule = Get-Content -LiteralPath $ignoreFile |
            Where-Object { $_.Trim() -match '^/?\.setup-local/?$' } |
            Select-Object -First 1
        if (-not $hasLocalSetupIgnoreRule) {
            throw '.setup-local is not ignored by Git.'
        }
    }
    Write-SetupMessage OK 'Local secret safety: db.properties and .setup-local are ignored and untracked.'
}

function Ensure-JavaDependencies {
    $dependencySpecs = @(
        [pscustomobject]@{
            Name = 'MySQL Connector/J 9.5.0'
            File = 'mysql-connector-j-9.5.0.jar'
            Uri  = 'https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/9.5.0/mysql-connector-j-9.5.0.jar'
            Sha256 = 'F2CA3DFAF00D4AA311470DB7EA3051962944BA0CB60005A2F75467549C39F425'
        },
        [pscustomobject]@{
            Name = 'jBCrypt 0.4'
            File = 'jbcrypt-0.4.jar'
            Uri  = 'https://repo.maven.apache.org/maven2/org/mindrot/jbcrypt/0.4/jbcrypt-0.4.jar'
            Sha256 = 'E183F6F59404FC1E12073CFEA4ACE7EA103C900463CD21FB609A7C617ECDF624'
        }
    )

    $libDirectory = Join-Path $script:ProjectRoot 'lib'
    if (-not (Test-Path -LiteralPath $libDirectory -PathType Container)) {
        if ($VerifyOnly -or $WhatIfPreference) {
            throw 'lib directory is missing. Run normal setup to restore dependencies.'
        }
        [void](New-Item -ItemType Directory -Path $libDirectory)
    }

    foreach ($dependency in $dependencySpecs) {
        $target = Join-Path $libDirectory $dependency.File
        if (Test-Path -LiteralPath $target -PathType Leaf) {
            $actualHash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
            if ($actualHash -ine $dependency.Sha256) {
                throw "$($dependency.Name) failed SHA-256 verification at $target. Remove the unexpected file manually and rerun."
            }
            Write-SetupMessage OK "$($dependency.Name): verified existing artifact."
            continue
        }

        if ($VerifyOnly -or $WhatIfPreference) {
            throw "$($dependency.Name) is missing. Run normal setup to restore it."
        }

        $download = Join-Path $script:TempRoot ($dependency.File + '.download')
        Write-SetupMessage INFO "Downloading $($dependency.Name) from Maven Central."
        $oldProtocol = [Net.ServicePointManager]::SecurityProtocol
        try {
            [Net.ServicePointManager]::SecurityProtocol = $oldProtocol -bor [Net.SecurityProtocolType]::Tls12
            Invoke-WebRequest -UseBasicParsing -Uri $dependency.Uri -OutFile $download
        } catch {
            throw "Could not download $($dependency.Name). Manual URL: $($dependency.Uri); expected SHA-256: $($dependency.Sha256)"
        } finally {
            [Net.ServicePointManager]::SecurityProtocol = $oldProtocol
        }

        $downloadHash = (Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash
        if ($downloadHash -ine $dependency.Sha256) {
            throw "$($dependency.Name) download failed the pinned SHA-256 check."
        }
        Move-Item -LiteralPath $download -Destination $target
        Write-SetupMessage OK "$($dependency.Name): downloaded and SHA-256 verified."
    }

    $connectorCount = @(Get-ChildItem -LiteralPath $libDirectory -Filter 'mysql-connector-j-*.jar' -File).Count
    $bcryptCount = @(Get-ChildItem -LiteralPath $libDirectory -Filter 'jbcrypt-*.jar' -File).Count
    if ($connectorCount -ne 1 -or $bcryptCount -ne 1) {
        throw 'lib must contain exactly one Connector/J JAR and one jBCrypt JAR; remove duplicate versions manually.'
    }
}

function ConvertFrom-SecureStringBriefly {
    param([Parameter(Mandatory = $true)][Security.SecureString] $SecureValue)

    $pointer = [IntPtr]::Zero
    try {
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        if ($pointer -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
        }
    }
}

function ConvertTo-MySqlOptionValue {
    param([AllowEmptyString()][string] $Value)

    if ($Value.IndexOfAny([char[]]@([char]0, [char]10, [char]13)) -ge 0) {
        throw 'MySQL option values cannot contain NUL, CR, or LF.'
    }
    $escaped = $Value.Replace('\', '\\').Replace('"', '\"')
    return '"' + $escaped + '"'
}

function New-MySqlDefaultsFile {
    param(
        [Parameter(Mandatory = $true)][string] $Username,
        [Parameter(Mandatory = $true)][Security.SecureString] $Password
    )

    if ($Username.IndexOfAny([char[]]@([char]0, [char]10, [char]13)) -ge 0) {
        throw 'MySQL usernames cannot contain control characters.'
    }

    $plainPassword = $null
    try {
        $plainPassword = ConvertFrom-SecureStringBriefly $Password
        $content = @(
            '[client]'
            ('host=' + (ConvertTo-MySqlOptionValue $script:DatabaseHost))
            ('port=' + $DatabasePort)
            'protocol=tcp'
            ('user=' + (ConvertTo-MySqlOptionValue $Username))
            ('password=' + (ConvertTo-MySqlOptionValue $plainPassword))
            'default-character-set=utf8mb4'
            ''
        ) -join "`r`n"

        $path = Join-Path $script:TempRoot ("mysql-{0}.cnf" -f [guid]::NewGuid().ToString('N'))
        [IO.File]::WriteAllText($path, $content, $script:Utf8NoBom)
        return $path
    } finally {
        $plainPassword = $null
        $content = $null
    }
}

function Get-SafeMySqlErrorText {
    param([string] $Text)

    if ([string]::IsNullOrWhiteSpace($Text)) {
        return 'No diagnostic text was returned.'
    }
    $firstLine = ($Text -split '\r?\n' | Where-Object { $_.Trim() } | Select-Object -First 1)
    if ($firstLine.Length -gt 500) {
        $firstLine = $firstLine.Substring(0, 500) + '...'
    }
    return $firstLine
}

function Invoke-MySql {
    param(
        [Parameter(Mandatory = $true)][string] $DefaultsFile,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string] $Sql,
        [string] $SelectedDatabase,
        [string] $Operation = 'MySQL operation',
        [switch] $SafeToShowError
    )

    $arguments = @(
        "--defaults-file=$DefaultsFile",
        '--batch',
        '--raw',
        '--skip-column-names',
        '--connect-timeout=10'
    )
    if ($SelectedDatabase) {
        $arguments += "--database=$SelectedDatabase"
    }

    $result = Invoke-NativeProcess -FilePath $script:MySqlExe -ArgumentList $arguments -StandardInputText ($Sql + "`n")
    if ($result.ExitCode -ne 0) {
        if ($SafeToShowError) {
            $detail = Get-SafeMySqlErrorText $result.StdErr
            throw "$Operation failed (mysql exit $($result.ExitCode)): $detail"
        }
        throw "$Operation failed (mysql exit $($result.ExitCode)). Detailed output was suppressed because the input may contain credential material."
    }

    if ([string]::IsNullOrWhiteSpace($result.StdOut)) {
        return @()
    }
    return @($result.StdOut.TrimEnd() -split '\r?\n')
}

function ConvertTo-MySqlStringLiteral {
    param([AllowEmptyString()][string] $Value)

    if ($Value.IndexOfAny([char[]]@([char]0, [char]10, [char]13)) -ge 0) {
        throw 'SQL values cannot contain NUL, CR, or LF in setup.'
    }
    return "'" + $Value.Replace("'", "''") + "'"
}

function Quote-MySqlIdentifier {
    param([string] $Identifier)
    return '`' + $Identifier.Replace('`', '``') + '`'
}

function Quote-GrantDatabaseIdentifier {
    param([string] $Identifier)
    $escaped = $Identifier.Replace('\', '\\').Replace('_', '\_').Replace('%', '\%').Replace('`', '``')
    return '`' + $escaped + '`'
}

function Get-CryptoRandomIndex {
    param([ValidateRange(1, 255)][int] $UpperBound)

    $oneByte = New-Object byte[] 1
    $limit = 256 - (256 % $UpperBound)
    do {
        $script:Rng.GetBytes($oneByte)
    } while ([int]$oneByte[0] -ge $limit)
    return ([int]$oneByte[0] % $UpperBound)
}

function New-CryptoRandomPassword {
    param([ValidateRange(16, 64)][int] $Length = 24)

    $groups = @(
        'ABCDEFGHJKLMNPQRSTUVWXYZ',
        'abcdefghijkmnopqrstuvwxyz',
        '23456789',
        '!@#$%+-_'
    )
    $allCharacters = ($groups -join '')
    $characters = New-Object 'System.Collections.Generic.List[char]'
    foreach ($group in $groups) {
        $characters.Add($group[(Get-CryptoRandomIndex $group.Length)])
    }
    while ($characters.Count -lt $Length) {
        $characters.Add($allCharacters[(Get-CryptoRandomIndex $allCharacters.Length)])
    }

    for ($index = $characters.Count - 1; $index -gt 0; $index--) {
        $swapIndex = Get-CryptoRandomIndex ($index + 1)
        $temporary = $characters[$index]
        $characters[$index] = $characters[$swapIndex]
        $characters[$swapIndex] = $temporary
    }

    $plainPassword = -join $characters.ToArray()
    try {
        return ConvertTo-SecureString $plainPassword -AsPlainText -Force
    } finally {
        $plainPassword = $null
        for ($index = 0; $index -lt $characters.Count; $index++) {
            $characters[$index] = [char]0
        }
    }
}

function Build-Application {
    if ($SkipBuild) {
        Write-SetupMessage INFO 'Full build skipped; compiling only the BCrypt setup helper.'
        $helperOutput = Join-Path $script:TempRoot 'helper-classes'
        [void](New-Item -ItemType Directory -Path $helperOutput)
        $helperSource = Join-Path $script:ProjectRoot 'src\com\examsystem\setup\PasswordHasher.java'
        $classpath = Join-Path $script:ProjectRoot 'lib\*'
        $result = Invoke-NativeProcess -FilePath $script:JavacExe -ArgumentList @(
            '--release', '17', '-encoding', 'UTF-8', '-cp', $classpath, '-d', $helperOutput, $helperSource
        )
        if ($result.ExitCode -ne 0) {
            throw "Password helper compilation failed: $(Get-SafeMySqlErrorText ($result.StdErr + $result.StdOut))"
        }
        $script:BuildOutput = $helperOutput
        return
    }

    Write-SetupMessage STAGE 'Compiling all Java sources'
    $sources = @(Get-ChildItem -LiteralPath (Join-Path $script:ProjectRoot 'src') -Recurse -Filter '*.java' -File |
        Sort-Object FullName)
    if ($sources.Count -eq 0) {
        throw 'No Java source files were found under src.'
    }

    $sourceList = Join-Path $script:TempRoot 'javac-sources.txt'
    $sourceLines = @($sources | ForEach-Object {
        '"' + $_.FullName.Replace('\', '/') + '"'
    })
    [IO.File]::WriteAllLines($sourceList, $sourceLines, $script:Utf8NoBom)

    if ($VerifyOnly -or $WhatIfPreference) {
        $binDirectory = Join-Path $script:TempRoot 'verify-classes'
        [void](New-Item -ItemType Directory -Path $binDirectory)
    } else {
        $binDirectory = Join-Path $script:ProjectRoot 'bin'
        [void](New-Item -ItemType Directory -Path $binDirectory -Force)
    }
    $classpath = Join-Path $script:ProjectRoot 'lib\*'
    $result = Invoke-NativeProcess -FilePath $script:JavacExe -ArgumentList @(
        '--release', '17', '-encoding', 'UTF-8', '-cp', $classpath, '-d', $binDirectory, "@$sourceList"
    )
    if ($result.ExitCode -ne 0) {
        $diagnostic = Get-SafeMySqlErrorText ($result.StdErr + $result.StdOut)
        throw "Java compilation failed: $diagnostic"
    }
    $script:BuildOutput = $binDirectory
    Write-SetupMessage OK "Compiled $($sources.Count) source files."
}

function Invoke-PasswordHash {
    param([Parameter(Mandatory = $true)][Security.SecureString] $Password)

    $plainPassword = $null
    try {
        $plainPassword = ConvertFrom-SecureStringBriefly $Password
        $classpath = $script:BuildOutput + ';' + (Join-Path $script:ProjectRoot 'lib\*')
        $result = Invoke-NativeProcess -FilePath $script:JavaExe -ArgumentList @(
            '-cp', $classpath, 'com.examsystem.setup.PasswordHasher'
        ) -StandardInputText ($plainPassword + "`n")
        if ($result.ExitCode -ne 0) {
            throw 'The BCrypt password helper failed; diagnostic output was suppressed.'
        }
        $hash = $result.StdOut.Trim()
        if (-not (Test-JBcryptHashFormat $hash)) {
            throw 'The BCrypt helper returned an unexpected hash format.'
        }
        return $hash
    } finally {
        $plainPassword = $null
    }
}

function Read-DatabaseProperties {
    if (-not (Test-Path -LiteralPath $script:PropertiesFile -PathType Leaf)) {
        return $null
    }

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $script:PropertiesFile) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#') -or $trimmed.StartsWith('!')) {
            continue
        }
        $separator = $line.IndexOf('=')
        if ($separator -lt 1) {
            continue
        }
        $key = $line.Substring(0, $separator).Trim()
        $value = $line.Substring($separator + 1)
        $values[$key] = $value
    }

    if (-not $values.ContainsKey('db.url') -or -not $values.ContainsKey('db.user') -or
        -not $values.ContainsKey('db.password')) {
        return $null
    }

    $securePassword = ConvertTo-SecureString ([string]$values['db.password']) -AsPlainText -Force
    $values['db.password'] = $null
    return [pscustomobject]@{
        Url      = [string]$values['db.url']
        User     = [string]$values['db.user']
        Password = $securePassword
    }
}

function Initialize-DatabasePropertiesStagingDirectory {
    $localSetupDirectory = Join-Path $script:ProjectRoot '.setup-local'
    $stagingDirectory = Join-Path $localSetupDirectory 'secure-write'
    foreach ($directory in @($localSetupDirectory, $stagingDirectory)) {
        if (Test-Path -LiteralPath $directory -PathType Leaf) {
            throw "Secure setup directory path is occupied by a file: $directory"
        }
        if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
            [void](New-Item -ItemType Directory -Path $directory)
        }
        $directoryItem = Get-Item -LiteralPath $directory -Force
        if (($directoryItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Secure setup directory must not be a junction or symbolic link: $directory"
        }
        Set-RestrictedDirectoryAcl $directory
    }
    return $stagingDirectory
}

function Test-DatabasePropertiesWritePreflight {
    param([AllowNull()][object] $ExistingConfiguration)

    if (Test-Path -LiteralPath $script:PropertiesFile -PathType Leaf) {
        $semanticallyCanonical = $null -ne $ExistingConfiguration -and
            $ExistingConfiguration.User -ceq $AppDatabaseUser -and
            $ExistingConfiguration.Url -ceq (Get-ExpectedJdbcUrl)
        if (-not $semanticallyCanonical -and
            -not (Confirm-Choice 'Replace the existing ignored db.properties after the new runtime credential has been verified?' $true)) {
            throw 'db.properties replacement was declined before any database change.'
        }

        $stream = $null
        try {
            $stream = [IO.File]::Open($script:PropertiesFile, [IO.FileMode]::Open, [IO.FileAccess]::Write, [IO.FileShare]::Read)
        } catch {
            throw 'The existing db.properties is not writable by the current Windows user.'
        } finally {
            if ($stream) {
                $stream.Dispose()
            }
        }
    }

    $stagingDirectory = Initialize-DatabasePropertiesStagingDirectory
    $probeName = '.db.properties.preflight.' + [guid]::NewGuid().ToString('N') + '.tmp'
    $stagedProbe = Join-Path $stagingDirectory $probeName
    $destinationProbe = Join-Path $script:ProjectRoot $probeName
    try {
        [IO.File]::WriteAllText($stagedProbe, '# setup write/ACL preflight', $script:Utf8NoBom)
        Set-RestrictedFileAcl $stagedProbe
        Move-Item -LiteralPath $stagedProbe -Destination $destinationProbe
        Assert-RestrictedFileAcl $destinationProbe
    } finally {
        foreach ($probe in @($stagedProbe, $destinationProbe)) {
            if (Test-Path -LiteralPath $probe -PathType Leaf) {
                Remove-Item -LiteralPath $probe -Force
            }
        }
    }
    Write-SetupMessage OK 'Preflighted the exact secure staging, same-volume move, and db.properties ACL path.'
}

function Get-ExpectedJdbcUrl {
    return "jdbc:mysql://${script:DatabaseHost}:$DatabasePort/$DatabaseName" +
        '?sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=UTC'
}

function Set-RestrictedFileAcl {
    param([string] $Path)

    $currentSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $security = New-Object Security.AccessControl.FileSecurity
    $security.SetAccessRuleProtection($true, $false)
    $security.SetOwner($currentSid)
    $rule = New-Object Security.AccessControl.FileSystemAccessRule(
        $currentSid,
        [Security.AccessControl.FileSystemRights]::FullControl,
        [Security.AccessControl.AccessControlType]::Allow
    )
    [void]$security.AddAccessRule($rule)
    Set-Acl -LiteralPath $Path -AclObject $security

    Assert-RestrictedFileAcl $Path
}

function Assert-RestrictedFileAcl {
    param([string] $Path)

    $verified = Get-Acl -LiteralPath $Path
    $currentSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $unexpectedRules = @($verified.Access | Where-Object {
        $ruleSid = $_.IdentityReference.Translate([Security.Principal.SecurityIdentifier])
        $ruleSid -ne $currentSid -or $_.AccessControlType -ne [Security.AccessControl.AccessControlType]::Allow
    })
    $ownerSid = $verified.GetOwner([Security.Principal.SecurityIdentifier])
    if (-not $verified.AreAccessRulesProtected -or $verified.Access.Count -ne 1 -or
        $unexpectedRules.Count -gt 0 -or $ownerSid -ne $currentSid) {
        throw "File is not protected by the required current-user-only ACL: $Path"
    }
}

function Set-RestrictedDirectoryAcl {
    param([string] $Path)

    $currentSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $security = New-Object Security.AccessControl.DirectorySecurity
    $security.SetAccessRuleProtection($true, $false)
    $security.SetOwner($currentSid)
    $rule = New-Object Security.AccessControl.FileSystemAccessRule(
        $currentSid,
        [Security.AccessControl.FileSystemRights]::FullControl,
        ([Security.AccessControl.InheritanceFlags]::ContainerInherit -bor [Security.AccessControl.InheritanceFlags]::ObjectInherit),
        [Security.AccessControl.PropagationFlags]::None,
        [Security.AccessControl.AccessControlType]::Allow
    )
    [void]$security.AddAccessRule($rule)
    Set-Acl -LiteralPath $Path -AclObject $security

    Assert-RestrictedDirectoryAcl $Path
}

function Assert-RestrictedDirectoryAcl {
    param([string] $Path)

    $verified = Get-Acl -LiteralPath $Path
    $currentSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $unexpectedRules = @($verified.Access | Where-Object {
        $ruleSid = $_.IdentityReference.Translate([Security.Principal.SecurityIdentifier])
        $ruleSid -ne $currentSid -or $_.AccessControlType -ne [Security.AccessControl.AccessControlType]::Allow
    })
    $ownerSid = $verified.GetOwner([Security.Principal.SecurityIdentifier])
    if (-not $verified.AreAccessRulesProtected -or $verified.Access.Count -ne 1 -or
        $unexpectedRules.Count -gt 0 -or $ownerSid -ne $currentSid) {
        throw "Directory is not protected by the required current-user-only ACL: $Path"
    }
}

function Confirm-Choice {
    param(
        [string] $Prompt,
        [bool] $DefaultYes = $false
    )

    $suffix = if ($DefaultYes) { '[Y/n]' } else { '[y/N]' }
    $answer = Read-Host "$Prompt $suffix"
    if ([string]::IsNullOrWhiteSpace($answer)) {
        return $DefaultYes
    }
    return $answer.Trim() -match '^(?i:y|yes)$'
}

function Write-DatabaseProperties {
    param([Parameter(Mandatory = $true)][Security.SecureString] $Password)

    $plainPassword = $null
    $stagingDirectory = Initialize-DatabasePropertiesStagingDirectory
    $temporaryPath = Join-Path $stagingDirectory ('.db.properties.' + [guid]::NewGuid().ToString('N') + '.tmp')
    try {
        $plainPassword = ConvertFrom-SecureStringBriefly $Password
        if ($plainPassword.IndexOfAny([char[]]@([char]0, [char]10, [char]13)) -ge 0) {
            throw 'The generated database password contains an invalid control character.'
        }
        $content = @(
            '# Generated by setup.ps1 for local development. Do not commit.'
            ('db.url=' + (Get-ExpectedJdbcUrl))
            ('db.user=' + $AppDatabaseUser)
            ('db.password=' + $plainPassword)
            ''
        ) -join "`r`n"

        if (Test-Path -LiteralPath $script:PropertiesFile -PathType Leaf) {
            $current = [IO.File]::ReadAllText($script:PropertiesFile)
            if ($current -ceq $content) {
                Write-SetupMessage OK 'db.properties already matches the requested configuration.'
                Set-RestrictedFileAcl $script:PropertiesFile
                return
            }
        }

        # The secret is first written inside a protected directory on the same
        # volume as the destination. No plaintext file ever exists under the
        # repository's inherited ACL, and the same-volume move preserves ACLs.
        [IO.File]::WriteAllText($temporaryPath, $content, $script:Utf8NoBom)
        Set-RestrictedFileAcl $temporaryPath
        Move-Item -LiteralPath $temporaryPath -Destination $script:PropertiesFile -Force
        Assert-RestrictedFileAcl $script:PropertiesFile
        Write-SetupMessage OK 'Wrote ACL-restricted, ignored db.properties.'
    } finally {
        $plainPassword = $null
        $content = $null
        if (Test-Path -LiteralPath $temporaryPath -PathType Leaf) {
            Remove-Item -LiteralPath $temporaryPath -Force
        }
    }
}

function Get-ScalarResult {
    param(
        [string] $DefaultsFile,
        [string] $Sql,
        [string] $SelectedDatabase,
        [string] $Operation = 'query'
    )

    $rows = @(Invoke-MySql -DefaultsFile $DefaultsFile -Sql $Sql `
        -SelectedDatabase $SelectedDatabase -Operation $Operation -SafeToShowError)
    if ($rows.Count -eq 0) {
        return $null
    }
    return [string]$rows[0]
}

function Start-DetectedMySqlService {
    $services = @(Get-Service -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^MySQL' -or $_.DisplayName -match '^MySQL' })
    $running = @($services | Where-Object { $_.Status -eq 'Running' })
    if ($running.Count -gt 0) {
        Write-SetupMessage OK "MySQL Windows service is running: $($running[0].Name)"
        return
    }
    if ($services.Count -eq 1) {
        $service = $services[0]
        try {
            Write-SetupMessage INFO "Starting detected MySQL service '$($service.Name)'."
            Start-Service -Name $service.Name
            $service.WaitForStatus('Running', [TimeSpan]::FromSeconds(20))
            Write-SetupMessage OK "MySQL service is running: $($service.Name)"
        } catch {
            Write-SetupMessage WARN "Could not start '$($service.Name)'. Run 'Start-Service -Name $($service.Name)' in an elevated PowerShell window, then rerun setup."
        }
    } elseif ($services.Count -gt 1) {
        Write-SetupMessage WARN 'Multiple stopped MySQL services were detected; start the intended instance manually.'
    }
}

function Get-AdminDefaultsFile {
    if ($AdminCredential) {
        $credentialUser = $AdminCredential.UserName
        $defaults = New-MySqlDefaultsFile -Username $credentialUser -Password $AdminCredential.Password
        try {
            [void](Get-ScalarResult -DefaultsFile $defaults -Sql 'SELECT 1;' -Operation 'administrator connection')
            Write-SetupMessage OK "Authenticated to MySQL as '$credentialUser'."
            return $defaults
        } catch {
            Remove-Item -LiteralPath $defaults -Force -ErrorAction SilentlyContinue
            throw 'The supplied -AdminCredential could not authenticate to the local MySQL server.'
        }
    }

    for ($attempt = 1; $attempt -le 3; $attempt++) {
        Write-Host "MySQL administrator: $DatabaseAdminUser"
        $securePassword = Read-Host 'MySQL administrator password (never displayed or logged)' -AsSecureString
        $defaults = New-MySqlDefaultsFile -Username $DatabaseAdminUser -Password $securePassword
        try {
            [void](Get-ScalarResult -DefaultsFile $defaults -Sql 'SELECT 1;' -Operation 'administrator connection')
            Write-SetupMessage OK "Authenticated to MySQL as '$DatabaseAdminUser'."
            return $defaults
        } catch {
            Remove-Item -LiteralPath $defaults -Force -ErrorAction SilentlyContinue
            if ($attempt -eq 3) {
                throw 'MySQL administrator authentication failed three times.'
            }
            Write-SetupMessage WARN 'MySQL connection failed. Check the service, port, username, and password, then try again.'
        }
    }
    throw 'MySQL administrator authentication did not complete.'
}

function Assert-SupportedMySqlServer {
    param([string] $DefaultsFile)

    $versionLine = Get-ScalarResult -DefaultsFile $DefaultsFile `
        -Sql 'SELECT CONCAT(VERSION(), CHAR(9), @@version_comment);' `
        -Operation 'MySQL version check'
    if (-not $versionLine) {
        throw 'MySQL did not return a server version.'
    }
    if ($versionLine -match '(?i)MariaDB') {
        throw 'MariaDB was detected. This setup supports Oracle MySQL 8.x or newer.'
    }
    if ($versionLine -notmatch '^(\d+)\.') {
        throw "Could not parse the MySQL server version: $versionLine"
    }
    if ([int]$Matches[1] -lt 8) {
        throw "MySQL 8.x or newer is required; server reported $versionLine"
    }
    Write-SetupMessage OK "MySQL server: $versionLine"
}

function Test-DatabaseExists {
    param([string] $DefaultsFile)
    $nameLiteral = ConvertTo-MySqlStringLiteral $DatabaseName
    $value = Get-ScalarResult -DefaultsFile $DefaultsFile `
        -Sql "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name=$nameLiteral;" `
        -Operation 'database existence check'
    return ([int]$value -eq 1)
}

function Get-DatabaseTableNames {
    param([string] $DefaultsFile)
    $nameLiteral = ConvertTo-MySqlStringLiteral $DatabaseName
    return @(Invoke-MySql -DefaultsFile $DefaultsFile -Sql @"
SELECT table_name
FROM information_schema.tables
WHERE table_schema=$nameLiteral AND table_type='BASE TABLE'
ORDER BY table_name;
"@ -Operation 'table inventory' -SafeToShowError)
}

function Get-TableColumnNames {
    param(
        [string] $DefaultsFile,
        [string] $TableName
    )
    $databaseLiteral = ConvertTo-MySqlStringLiteral $DatabaseName
    $tableLiteral = ConvertTo-MySqlStringLiteral $TableName
    return @(Invoke-MySql -DefaultsFile $DefaultsFile -Sql @"
SELECT column_name
FROM information_schema.columns
WHERE table_schema=$databaseLiteral AND table_name=$tableLiteral
ORDER BY ordinal_position;
"@ -Operation "column inventory for $TableName" -SafeToShowError)
}

function Test-ColumnExists {
    param(
        [string] $DefaultsFile,
        [string] $TableName,
        [string] $ColumnName
    )
    $databaseLiteral = ConvertTo-MySqlStringLiteral $DatabaseName
    $tableLiteral = ConvertTo-MySqlStringLiteral $TableName
    $columnLiteral = ConvertTo-MySqlStringLiteral $ColumnName
    $count = Get-ScalarResult -DefaultsFile $DefaultsFile -Sql @"
SELECT COUNT(*) FROM information_schema.columns
WHERE table_schema=$databaseLiteral AND table_name=$tableLiteral AND column_name=$columnLiteral;
"@ -Operation "column check for $TableName.$ColumnName"
    return ([int]$count -eq 1)
}

function Get-ColumnMetadata {
    param(
        [string] $DefaultsFile,
        [string] $TableName,
        [string] $ColumnName
    )

    $databaseLiteral = ConvertTo-MySqlStringLiteral $DatabaseName
    $tableLiteral = ConvertTo-MySqlStringLiteral $TableName
    $columnLiteral = ConvertTo-MySqlStringLiteral $ColumnName
    $line = Get-ScalarResult -DefaultsFile $DefaultsFile -Sql @"
SELECT CONCAT(data_type, CHAR(9), COALESCE(character_maximum_length, 0), CHAR(9),
              is_nullable, CHAR(9), column_type, CHAR(9), COALESCE(extra, ''), CHAR(9),
              COALESCE(CAST(column_default AS CHAR), '<NULL>'))
FROM information_schema.columns
WHERE table_schema=$databaseLiteral AND table_name=$tableLiteral AND column_name=$columnLiteral;
"@ -Operation "column metadata for $TableName.$ColumnName"
    if ([string]::IsNullOrWhiteSpace($line)) {
        throw "Column metadata is missing for $TableName.$ColumnName."
    }
    $parts = $line -split "`t", 6
    if ($parts.Count -ne 6) {
        throw "Column metadata was malformed for $TableName.$ColumnName."
    }
    return [pscustomobject]@{
        DataType   = $parts[0]
        MaxLength  = [int64]$parts[1]
        IsNullable = $parts[2]
        ColumnType = $parts[3]
        Extra      = $parts[4]
        Default    = $parts[5]
    }
}

function Backup-And-ResetDatabase {
    param([string] $AdminDefaultsFile)

    if (-not (Test-DatabaseExists $AdminDefaultsFile)) {
        Write-SetupMessage INFO "Database '$DatabaseName' does not exist; reset has nothing to remove."
        return
    }

    $tables = @(Get-DatabaseTableNames $AdminDefaultsFile)
    $knownTables = @('schema_versions', 'users', 'exams', 'questions', 'options',
        'exam_attempts', 'student_answers', 'proctor_logs')
    $fingerprintDefinitions = [ordered]@{
        schema_versions = @('version', 'applied_at')
        users = @('username', 'password', 'full_name', 'role')
        exams = @('title', 'duration_minutes', 'created_by')
        questions = @('exam_id', 'question_text', 'correct_option_number')
        options = @('question_id', 'option_number', 'option_text')
        exam_attempts = @('exam_id', 'student_id', 'start_time', 'focus_lost_count')
        student_answers = @('attempt_id', 'question_id', 'is_correct')
        proctor_logs = @('attempt_id', 'violation_type', 'screenshot_data')
    }
    $matchingFingerprints = New-Object 'System.Collections.Generic.List[string]'
    foreach ($table in $fingerprintDefinitions.Keys) {
        if ($table -notin $tables) {
            continue
        }
        $actualColumns = @(Get-TableColumnNames $AdminDefaultsFile $table)
        $missingFingerprintColumns = @($fingerprintDefinitions[$table] | Where-Object { $_ -notin $actualColumns })
        if ($missingFingerprintColumns.Count -eq 0) {
            [void]$matchingFingerprints.Add($table)
        }
    }
    $domainFingerprintCount = @($matchingFingerprints | Where-Object { $_ -ne 'schema_versions' }).Count
    $hasVersionFingerprint = 'schema_versions' -in $matchingFingerprints
    $hasStrongFingerprint = $domainFingerprintCount -ge 4 -or
        ($hasVersionFingerprint -and $domainFingerprintCount -ge 2)
    if ($tables.Count -gt 0 -and -not $hasStrongFingerprint) {
        throw "Refusing to reset '$DatabaseName': multiple ExaSecure table/column signatures were not found. No database was removed."
    }

    Write-Host ''
    Write-Host 'RESET TARGET' -ForegroundColor Red
    Write-Host "  Server   : ${script:DatabaseHost}:$DatabasePort"
    Write-Host "  Database : $DatabaseName"
    Write-Host "  Tables   : $($tables.Count)"
    foreach ($table in $tables | Where-Object { $_ -in $knownTables }) {
        $quotedTable = Quote-MySqlIdentifier $table
        $rowCount = Get-ScalarResult -DefaultsFile $AdminDefaultsFile `
            -SelectedDatabase $DatabaseName -Sql "SELECT COUNT(*) FROM $quotedTable;" `
            -Operation "row count for $table"
        Write-Host ("  {0,-20} {1,10} rows" -f $table, $rowCount)
    }

    $unknownTables = @($tables | Where-Object { $_ -notin $knownTables })
    if ($unknownTables.Count -gt 0) {
        Write-Host ('  Extra tables: ' + ($unknownTables -join ', ')) -ForegroundColor Yellow
    }

    if ($WhatIfPreference) {
        [void]$script:CmdletContext.ShouldProcess(
            "${script:DatabaseHost}:$DatabasePort/$DatabaseName",
            'Back up and drop the database'
        )
        $script:ResetPreviewOnly = $true
        Write-SetupMessage INFO 'Reset WhatIf preview complete; no confirmation, backup, drop, or later setup mutation was performed.'
        return
    }

    $requiredPhrase = "RESET $DatabaseName"
    $typed = Read-Host "Type exactly '$requiredPhrase' to continue"
    if ($typed -cne $requiredPhrase) {
        throw 'Reset confirmation did not match; no data was removed.'
    }
    if ($unknownTables.Count -gt 0) {
        $extraPhrase = 'RESET WITH EXTRA TABLES'
        $typedExtra = Read-Host "Type exactly '$extraPhrase' to acknowledge the extra tables"
        if ($typedExtra -cne $extraPhrase) {
            throw 'Extra-table confirmation did not match; no data was removed.'
        }
    }
    if (-not $script:CmdletContext.ShouldProcess("${script:DatabaseHost}:$DatabasePort/$DatabaseName", 'Back up and drop the database')) {
        throw 'Reset was not performed.'
    }

    $dumpExe = Join-Path (Split-Path -Parent $script:MySqlExe) 'mysqldump.exe'
    if (-not (Test-Path -LiteralPath $dumpExe -PathType Leaf)) {
        $dumpExe = Get-ApplicationCommandPath 'mysqldump.exe'
    }
    if (-not $dumpExe) {
        throw 'mysqldump.exe is required for -Reset; no database was removed.'
    }

    $backupDirectory = Join-Path $script:ProjectRoot '.setup-local\backups'
    [void](New-Item -ItemType Directory -Path $backupDirectory -Force)
    Set-RestrictedDirectoryAcl $backupDirectory

    $backupPath = Join-Path $backupDirectory ("{0}-{1}.sql" -f $DatabaseName, (Get-Date -Format 'yyyyMMdd-HHmmss'))
    $dumpResult = Invoke-NativeProcess -FilePath $dumpExe -ArgumentList @(
        "--defaults-file=$AdminDefaultsFile", '--single-transaction',
        '--routines', '--events', '--triggers', '--hex-blob', '--set-gtid-purged=OFF',
        "--result-file=$backupPath", $DatabaseName
    )
    if ($dumpResult.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $backupPath -PathType Leaf)) {
        Remove-Item -LiteralPath $backupPath -Force -ErrorAction SilentlyContinue
        throw 'The pre-reset mysqldump backup failed; no database was removed.'
    }
    Set-RestrictedFileAcl $backupPath
    Write-SetupMessage OK "Reset backup created: $backupPath"

    $quotedDatabase = Quote-MySqlIdentifier $DatabaseName
    [void](Invoke-MySql -DefaultsFile $AdminDefaultsFile `
        -Sql "DROP DATABASE $quotedDatabase;" -Operation 'explicit database reset' -SafeToShowError)
    Write-SetupMessage WARN "Removed database '$DatabaseName' after backup and explicit confirmation."
}

function Ensure-Database {
    param([string] $AdminDefaultsFile)

    $quotedDatabase = Quote-MySqlIdentifier $DatabaseName
    if (-not (Test-DatabaseExists $AdminDefaultsFile)) {
        [void](Invoke-MySql -DefaultsFile $AdminDefaultsFile -Sql @"
CREATE DATABASE $quotedDatabase CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
"@ -Operation 'database creation' -SafeToShowError)
        Write-SetupMessage OK "Created database '$DatabaseName'."
        return
    }

    $nameLiteral = ConvertTo-MySqlStringLiteral $DatabaseName
    $metadata = Get-ScalarResult -DefaultsFile $AdminDefaultsFile -Sql @"
SELECT CONCAT(default_character_set_name, CHAR(9), default_collation_name)
FROM information_schema.schemata WHERE schema_name=$nameLiteral;
"@ -Operation 'database charset check'
    $metadataParts = $metadata -split "`t"
    if ($metadataParts[0] -ine 'utf8mb4') {
        throw "Existing database '$DatabaseName' uses charset '$($metadataParts[0])'. Convert it manually after a backup; setup will not rewrite populated text columns."
    }
    if ($metadataParts[1] -ine 'utf8mb4_unicode_ci') {
        Write-SetupMessage WARN "Existing database collation is '$($metadataParts[1])'; utf8mb4_unicode_ci is recommended. Existing data was not altered."
    }
    Write-SetupMessage OK "Using existing database '$DatabaseName' without deleting data."
}

function Assert-RecognizedExistingTables {
    param([string] $AdminDefaultsFile)

    $baseColumns = @{
        users = @('id', 'username', 'password', 'full_name', 'enrollment_number', 'section', 'role', 'created_at')
        exams = @('id', 'title', 'duration_minutes', 'created_by', 'created_at')
        questions = @('id', 'exam_id', 'question_text', 'correct_option_number')
        options = @('id', 'question_id', 'option_number', 'option_text')
        exam_attempts = @('id', 'exam_id', 'student_id', 'start_time', 'end_time', 'score', 'focus_lost_count')
        student_answers = @('id', 'attempt_id', 'question_id', 'is_correct', 'answer_timestamp')
        proctor_logs = @('id', 'attempt_id', 'violation_type', 'screenshot_data', 'violation_time')
        schema_versions = @('version', 'applied_at')
    }

    $tables = @(Get-DatabaseTableNames $AdminDefaultsFile)
    $recognized = @($tables | Where-Object { $baseColumns.ContainsKey($_) })
    $unknown = @($tables | Where-Object { -not $baseColumns.ContainsKey($_) })
    if ($tables.Count -gt 0 -and $recognized.Count -eq 0) {
        throw "Database '$DatabaseName' contains tables but none match ExaSecure. Choose another database name; no changes were made."
    }
    if ($unknown.Count -gt 0) {
        Write-SetupMessage WARN ('Preserving unrecognized extra tables: ' + ($unknown -join ', '))
    }

    foreach ($table in $recognized) {
        $actualColumns = @(Get-TableColumnNames $AdminDefaultsFile $table)
        $missing = @($baseColumns[$table] | Where-Object { $_ -notin $actualColumns })
        if ($missing.Count -gt 0) {
            throw "Existing table '$table' is not a recognized ExaSecure shape; missing base columns: $($missing -join ', ')."
        }
    }

    if ('schema_versions' -in $tables) {
        $versionShape = Get-ScalarResult -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName `
            -Sql @'
SELECT CONCAT(COUNT(*), CHAR(9), COUNT(DISTINCT version), CHAR(9),
              COALESCE(SUM(CASE WHEN version IS NULL OR version NOT IN (1,2,3) THEN 1 ELSE 0 END), 0))
FROM schema_versions;
'@ -Operation 'schema version preflight'
        $versionParts = $versionShape -split "`t"
        $isSupportedSubset = $versionParts.Count -eq 3 -and
            [int]$versionParts[0] -eq [int]$versionParts[1] -and
            [int]$versionParts[2] -eq 0 -and [int]$versionParts[0] -le 3
        if (-not $isSupportedSubset) {
            throw "Existing schema_versions must be a unique subset of {1,2,3} before setup can run; found '$versionShape'. No schema changes were made."
        }
    }
}

function Add-ColumnWhenMissing {
    param(
        [string] $AdminDefaultsFile,
        [string[]] $ExistingTables,
        [string] $TableName,
        [string] $ColumnName,
        [string] $Definition
    )

    if ($TableName -notin $ExistingTables -or (Test-ColumnExists $AdminDefaultsFile $TableName $ColumnName)) {
        return
    }
    $quotedTable = Quote-MySqlIdentifier $TableName
    $quotedColumn = Quote-MySqlIdentifier $ColumnName
    [void](Invoke-MySql -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName `
        -Sql "ALTER TABLE $quotedTable ADD COLUMN $quotedColumn $Definition;" `
        -Operation "adding $TableName.$ColumnName" -SafeToShowError)
    Write-SetupMessage INFO "Added legacy migration column $TableName.$ColumnName."
}

function Update-RecognizedLegacySchema {
    param([string] $AdminDefaultsFile)

    $tables = @(Get-DatabaseTableNames $AdminDefaultsFile)
    Add-ColumnWhenMissing $AdminDefaultsFile $tables 'users' 'is_active' 'TINYINT(1) NOT NULL DEFAULT 1'
    Add-ColumnWhenMissing $AdminDefaultsFile $tables 'exams' 'is_active' 'TINYINT(1) NOT NULL DEFAULT 1'
    Add-ColumnWhenMissing $AdminDefaultsFile $tables 'questions' 'is_active' 'TINYINT(1) NOT NULL DEFAULT 1'
    Add-ColumnWhenMissing $AdminDefaultsFile $tables 'questions' 'question_type' "VARCHAR(20) NOT NULL DEFAULT 'SINGLE'"
    Add-ColumnWhenMissing $AdminDefaultsFile $tables 'options' 'is_active' 'TINYINT(1) NOT NULL DEFAULT 1'
    Add-ColumnWhenMissing $AdminDefaultsFile $tables 'options' 'is_correct' 'TINYINT(1) NOT NULL DEFAULT 0'

    if ('student_answers' -in $tables) {
        $hasOldAnswerColumn = Test-ColumnExists $AdminDefaultsFile 'student_answers' 'selected_option_number'
        $hasNewAnswerColumn = Test-ColumnExists $AdminDefaultsFile 'student_answers' 'selected_options'

        $oldAnswerMetadata = $null
        if ($hasOldAnswerColumn) {
            $oldAnswerMetadata = Get-ColumnMetadata $AdminDefaultsFile 'student_answers' 'selected_option_number'
            $allowedLegacyNumberTypes = @('tinyint', 'smallint', 'mediumint', 'int', 'bigint')
            if ($oldAnswerMetadata.DataType -notin $allowedLegacyNumberTypes -or
                $oldAnswerMetadata.ColumnType -notmatch '^(?i)(tinyint|smallint|mediumint|int|bigint)(\(\d+\))?( unsigned)?$' -or
                -not [string]::IsNullOrWhiteSpace($oldAnswerMetadata.Extra)) {
                throw "selected_option_number has unsupported metadata '$($oldAnswerMetadata.ColumnType) $($oldAnswerMetadata.Extra)'; setup will not coerce it."
            }
        }

        $newAnswerMetadata = $null
        $selectedTargetLength = 255
        if ($hasNewAnswerColumn) {
            $newAnswerMetadata = Get-ColumnMetadata $AdminDefaultsFile 'student_answers' 'selected_options'
            $allowedSelectedTypes = @('varchar', 'char', 'text', 'tinytext', 'int', 'tinyint', 'smallint', 'mediumint', 'bigint')
            if ($newAnswerMetadata.DataType -notin $allowedSelectedTypes) {
                throw "selected_options uses unsupported legacy type '$($newAnswerMetadata.DataType)'; setup will not coerce arbitrary binary or structured data."
            }
            if ($newAnswerMetadata.DataType -ieq 'varchar' -and $newAnswerMetadata.MaxLength -gt 255) {
                $selectedTargetLength = [int]$newAnswerMetadata.MaxLength
            }
            $maximumExistingLength = Get-ScalarResult -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName `
                -Sql 'SELECT COALESCE(MAX(CHAR_LENGTH(CAST(selected_options AS CHAR))), 0) FROM student_answers;' `
                -Operation 'selected_options length preflight'
            if ([int64]$maximumExistingLength -gt $selectedTargetLength) {
                throw "selected_options contains a value $maximumExistingLength characters long; setup will not truncate it to VARCHAR($selectedTargetLength)."
            }
        }

        if ($hasOldAnswerColumn -and -not $hasNewAnswerColumn) {
            $nullLegacyAnswers = Get-ScalarResult -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName `
                -Sql 'SELECT COUNT(*) FROM student_answers WHERE selected_option_number IS NULL;' `
                -Operation 'legacy answer null preflight'
            if ([int]$nullLegacyAnswers -gt 0) {
                throw 'selected_option_number contains NULL data; resolve it manually before converting to selected_options.'
            }
            $maximumLegacyLength = Get-ScalarResult -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName `
                -Sql 'SELECT COALESCE(MAX(CHAR_LENGTH(CAST(selected_option_number AS CHAR))), 0) FROM student_answers;' `
                -Operation 'legacy answer length preflight'
            if ([int64]$maximumLegacyLength -gt 255) {
                throw "selected_option_number contains a value $maximumLegacyLength characters long; setup will not truncate it."
            }
            [void](Invoke-MySql -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName -Sql @'
ALTER TABLE `student_answers`
CHANGE COLUMN `selected_option_number` `selected_options` VARCHAR(255) NOT NULL;
'@ -Operation 'renaming legacy answer column' -SafeToShowError)
            Write-SetupMessage INFO 'Migrated student_answers.selected_option_number to selected_options.'
        } elseif ($hasOldAnswerColumn -and $hasNewAnswerColumn) {
            $unrecoverableNullAnswers = Get-ScalarResult -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName `
                -Sql @'
SELECT COUNT(*) FROM student_answers
WHERE selected_options IS NULL AND selected_option_number IS NULL;
'@ -Operation 'duplicate answer null preflight'
            if ([int]$unrecoverableNullAnswers -gt 0) {
                throw 'Some answer rows have NULL in both legacy and current selection columns; resolve them manually before migration.'
            }
            $maximumCopyLength = Get-ScalarResult -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName `
                -Sql @'
SELECT COALESCE(MAX(CHAR_LENGTH(CAST(selected_option_number AS CHAR))), 0)
FROM student_answers
WHERE (selected_options IS NULL OR CAST(selected_options AS CHAR) = '')
  AND selected_option_number IS NOT NULL;
'@ -Operation 'duplicate answer copy-length preflight'
            if ([int64]$maximumCopyLength -gt $selectedTargetLength) {
                throw "Legacy answer copying requires $maximumCopyLength characters; setup will not truncate selected_options."
            }

            $nullableSelectedDefinition = "VARCHAR($selectedTargetLength) NULL"
            [void](Invoke-MySql -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName `
                -Sql "ALTER TABLE ``student_answers`` MODIFY COLUMN ``selected_options`` $nullableSelectedDefinition;" `
                -Operation 'safely normalizing duplicate selected_options' -SafeToShowError)
            [void](Invoke-MySql -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName -Sql @'
UPDATE `student_answers`
SET `selected_options` = CAST(`selected_option_number` AS CHAR)
WHERE (`selected_options` IS NULL OR `selected_options` = '')
  AND `selected_option_number` IS NOT NULL;
'@ -Operation 'reconciling duplicate answer columns' -SafeToShowError)

            $remainingNullAnswers = Get-ScalarResult -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName `
                -Sql 'SELECT COUNT(*) FROM student_answers WHERE selected_options IS NULL;' `
                -Operation 'reconciled answer null verification'
            if ([int]$remainingNullAnswers -gt 0) {
                throw 'selected_options still contains NULL after safe reconciliation; no truncating conversion was attempted.'
            }
            $safeLegacyNumberType = $oldAnswerMetadata.ColumnType
            [void](Invoke-MySql -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName `
                -Sql "ALTER TABLE ``student_answers`` MODIFY COLUMN ``selected_option_number`` $safeLegacyNumberType NULL DEFAULT NULL;" `
                -Operation 'making duplicate legacy answer column nullable' -SafeToShowError)
            [void](Invoke-MySql -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName `
                -Sql "ALTER TABLE ``student_answers`` MODIFY COLUMN ``selected_options`` VARCHAR($selectedTargetLength) NOT NULL;" `
                -Operation 'finalizing selected_options contract' -SafeToShowError)
            Write-SetupMessage WARN 'Preserved both legacy answer columns; selected_option_number was made nullable for runtime compatibility.'
        } elseif (-not $hasNewAnswerColumn) {
            throw 'student_answers has neither selected_option_number nor selected_options; setup will not guess how to repair it.'
        } elseif ($newAnswerMetadata.DataType -ine 'varchar' -or
            $newAnswerMetadata.MaxLength -lt 255 -or $newAnswerMetadata.IsNullable -ine 'NO') {
            $nullAnswers = Get-ScalarResult -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName `
                -Sql 'SELECT COUNT(*) FROM student_answers WHERE selected_options IS NULL;' `
                -Operation 'selected_options null check'
            if ([int]$nullAnswers -gt 0) {
                throw 'selected_options contains NULL data; resolve it manually before widening the column.'
            }
            [void](Invoke-MySql -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName `
                -Sql "ALTER TABLE ``student_answers`` MODIFY COLUMN ``selected_options`` VARCHAR($selectedTargetLength) NOT NULL;" `
                -Operation 'widening selected_options' -SafeToShowError)
        }
    }

    if ('questions' -in $tables -and (Test-ColumnExists $AdminDefaultsFile 'questions' 'question_type')) {
        $questionTypeMetadata = Get-ColumnMetadata $AdminDefaultsFile 'questions' 'question_type'
        $allowedQuestionTypeStorage = @('varchar', 'char', 'text', 'tinytext', 'enum')
        if ($questionTypeMetadata.DataType -notin $allowedQuestionTypeStorage -or
            -not [string]::IsNullOrWhiteSpace($questionTypeMetadata.Extra)) {
            throw "questions.question_type uses unsupported metadata '$($questionTypeMetadata.ColumnType) $($questionTypeMetadata.Extra)'; setup will not coerce it."
        }
        $badTypes = Get-ScalarResult -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName -Sql @'
SELECT COUNT(*) FROM questions
WHERE question_type IS NOT NULL AND TRIM(CAST(question_type AS CHAR)) <> ''
  AND UPPER(TRIM(CAST(question_type AS CHAR))) NOT IN ('SINGLE', 'MULTI');
'@ -Operation 'question type validation'
        if ([int]$badTypes -gt 0) {
            throw "$badTypes questions have unsupported question_type values; only SINGLE and MULTI are accepted."
        }
        $maximumQuestionTypeLength = Get-ScalarResult -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName `
            -Sql 'SELECT COALESCE(MAX(CHAR_LENGTH(CAST(question_type AS CHAR))), 0) FROM questions;' `
            -Operation 'question type length preflight'
        if ([int64]$maximumQuestionTypeLength -gt 20) {
            throw "questions.question_type contains a value $maximumQuestionTypeLength characters long; setup will not truncate it to the v3 contract."
        }
        $questionTypeTargetLength = 20
        if ($questionTypeMetadata.DataType -ieq 'varchar' -and $questionTypeMetadata.MaxLength -gt 20) {
            $questionTypeTargetLength = [int]$questionTypeMetadata.MaxLength
        }
        $needsQuestionTypeStorageNormalization = $questionTypeMetadata.DataType -ine 'varchar' -or
            $questionTypeMetadata.MaxLength -lt 20 -or $questionTypeMetadata.IsNullable -ine 'NO' -or
            $questionTypeMetadata.Default -ine 'SINGLE'
        if ($needsQuestionTypeStorageNormalization) {
            [void](Invoke-MySql -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName `
                -Sql "ALTER TABLE ``questions`` MODIFY COLUMN ``question_type`` VARCHAR($questionTypeTargetLength) NULL;" `
                -Operation 'safely normalizing question type storage' -SafeToShowError)
        }
        [void](Invoke-MySql -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName -Sql @'
UPDATE questions SET question_type='SINGLE'
WHERE question_type IS NULL OR TRIM(question_type)='';
UPDATE questions SET question_type=UPPER(TRIM(question_type))
WHERE UPPER(TRIM(question_type)) IN ('SINGLE','MULTI');
'@ -Operation 'question type normalization' -SafeToShowError)
        if ($needsQuestionTypeStorageNormalization) {
            [void](Invoke-MySql -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName `
                -Sql "ALTER TABLE ``questions`` MODIFY COLUMN ``question_type`` VARCHAR($questionTypeTargetLength) NOT NULL DEFAULT 'SINGLE';" `
                -Operation 'finalizing question type contract' -SafeToShowError)
        }
    }

    if ('options' -in $tables -and 'questions' -in $tables -and
        (Test-ColumnExists $AdminDefaultsFile 'options' 'is_correct')) {
        [void](Invoke-MySql -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName -Sql @'
UPDATE `options` AS o
JOIN `questions` AS q
  ON q.`id` = o.`question_id`
 AND q.`correct_option_number` = o.`option_number`
LEFT JOIN (
  SELECT `question_id` FROM `options` WHERE `is_correct` = 1 GROUP BY `question_id`
) AS marked ON marked.`question_id` = q.`id`
SET o.`is_correct` = 1
WHERE q.`question_type` = 'SINGLE' AND marked.`question_id` IS NULL;
'@ -Operation 'legacy correct-option backfill' -SafeToShowError)
    }
}

function Assert-SchemaContract {
    param(
        [string] $DefaultsFile,
        [switch] $RequireVersion
    )

    $expectedColumns = @{
        schema_versions = @('version', 'applied_at')
        users = @('id', 'username', 'password', 'full_name', 'enrollment_number', 'section', 'role', 'is_active', 'created_at')
        exams = @('id', 'title', 'duration_minutes', 'created_by', 'is_active', 'created_at')
        questions = @('id', 'exam_id', 'question_text', 'correct_option_number', 'question_type', 'is_active')
        options = @('id', 'question_id', 'option_number', 'option_text', 'is_correct', 'is_active')
        exam_attempts = @('id', 'exam_id', 'student_id', 'start_time', 'end_time', 'score', 'focus_lost_count')
        student_answers = @('id', 'attempt_id', 'question_id', 'selected_options', 'is_correct', 'answer_timestamp')
        proctor_logs = @('id', 'attempt_id', 'violation_type', 'screenshot_data', 'violation_time')
    }

    $tables = @(Get-DatabaseTableNames $DefaultsFile)
    $missingTables = @($expectedColumns.Keys | Where-Object { $_ -notin $tables })
    if ($missingTables.Count -gt 0) {
        throw 'Schema validation failed; missing tables: ' + ($missingTables -join ', ')
    }

    foreach ($table in $expectedColumns.Keys) {
        $columns = @(Get-TableColumnNames $DefaultsFile $table)
        $missingColumns = @($expectedColumns[$table] | Where-Object { $_ -notin $columns })
        if ($missingColumns.Count -gt 0) {
            throw "Schema validation failed; $table is missing: $($missingColumns -join ', ')."
        }
    }

    $databaseLiteral = ConvertTo-MySqlStringLiteral $DatabaseName
    $columnMetadataLines = @(Invoke-MySql -DefaultsFile $DefaultsFile -Sql @"
SELECT CONCAT(table_name, CHAR(9), column_name, CHAR(9), data_type, CHAR(9),
              COALESCE(character_maximum_length, 0), CHAR(9), is_nullable, CHAR(9),
              column_type, CHAR(9), COALESCE(extra, ''), CHAR(9),
              COALESCE(CAST(column_default AS CHAR), '<NULL>'))
FROM information_schema.columns
WHERE table_schema=$databaseLiteral
  AND table_name IN ('schema_versions','users','exams','questions','options',
                     'exam_attempts','student_answers','proctor_logs')
ORDER BY table_name, ordinal_position;
"@ -Operation 'column contract inventory' -SafeToShowError)
    $columnMetadataByKey = @{}
    foreach ($line in $columnMetadataLines) {
        $parts = $line -split "`t", 8
        if ($parts.Count -ne 8) {
            throw 'Schema column metadata output was malformed.'
        }
        $columnMetadataByKey[($parts[0] + '.' + $parts[1])] = [pscustomobject]@{
            DataType   = $parts[2]
            MaxLength  = [int64]$parts[3]
            IsNullable = $parts[4]
            ColumnType = $parts[5]
            Extra      = $parts[6]
            Default    = $parts[7]
        }
    }

    # Format: table|column|data_type|nullability|minimum character length|
    #         AUTO/NONE|required default or ANY. A few nullable legacy defaults
    # are intentionally accepted where runtime semantics remain compatible.
    $columnContractText = @'
schema_versions|version|int|NO|0|NONE|ANY
schema_versions|applied_at|timestamp|ANY|0|NONE|CURRENT_TIMESTAMP
users|id|int|NO|0|AUTO|ANY
users|username|varchar|NO|50|NONE|ANY
users|password|varchar|NO|100|NONE|ANY
users|full_name|varchar|NO|100|NONE|ANY
users|enrollment_number|varchar|YES|50|NONE|ANY
users|section|varchar|YES|10|NONE|ANY
users|role|enum|NO|0|NONE|ANY
users|is_active|tinyint|ANY|0|NONE|1
users|created_at|timestamp|ANY|0|NONE|CURRENT_TIMESTAMP
exams|id|int|NO|0|AUTO|ANY
exams|title|varchar|NO|255|NONE|ANY
exams|duration_minutes|int|NO|0|NONE|ANY
exams|created_by|int|NO|0|NONE|ANY
exams|is_active|tinyint|ANY|0|NONE|1
exams|created_at|timestamp|ANY|0|NONE|CURRENT_TIMESTAMP
questions|id|int|NO|0|AUTO|ANY
questions|exam_id|int|NO|0|NONE|ANY
questions|question_text|text|NO|0|NONE|ANY
questions|correct_option_number|int|NO|0|NONE|ANY
questions|question_type|varchar|NO|20|NONE|SINGLE
questions|is_active|tinyint|NO|0|NONE|1
options|id|int|NO|0|AUTO|ANY
options|question_id|int|NO|0|NONE|ANY
options|option_number|int|NO|0|NONE|ANY
options|option_text|varchar|NO|500|NONE|ANY
options|is_correct|tinyint|NO|0|NONE|0
options|is_active|tinyint|NO|0|NONE|1
exam_attempts|id|int|NO|0|AUTO|ANY
exam_attempts|exam_id|int|NO|0|NONE|ANY
exam_attempts|student_id|int|NO|0|NONE|ANY
exam_attempts|start_time|datetime|NO|0|NONE|ANY
exam_attempts|end_time|datetime|YES|0|NONE|ANY
exam_attempts|score|int|YES|0|NONE|0
exam_attempts|focus_lost_count|int|ANY|0|NONE|0
student_answers|id|int|NO|0|AUTO|ANY
student_answers|attempt_id|int|NO|0|NONE|ANY
student_answers|question_id|int|NO|0|NONE|ANY
student_answers|selected_options|varchar|NO|255|NONE|ANY
student_answers|is_correct|tinyint|NO|0|NONE|ANY
student_answers|answer_timestamp|datetime|NO|0|NONE|ANY
proctor_logs|id|int|NO|0|AUTO|ANY
proctor_logs|attempt_id|int|NO|0|NONE|ANY
proctor_logs|violation_type|varchar|NO|100|NONE|ANY
proctor_logs|screenshot_data|longblob|YES|0|NONE|ANY
proctor_logs|violation_time|timestamp|ANY|0|NONE|CURRENT_TIMESTAMP
'@
    $contractRows = @($columnContractText -split '\r?\n' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    foreach ($row in $contractRows) {
        $contract = $row -split '\|', 7
        if ($contract.Count -ne 7) {
            throw "Internal schema contract row is malformed: $row"
        }
        $key = $contract[0] + '.' + $contract[1]
        if (-not $columnMetadataByKey.ContainsKey($key)) {
            throw "Schema validation failed; column metadata is missing for $key."
        }
        $metadata = $columnMetadataByKey[$key]
        if ($metadata.DataType -ine $contract[2]) {
            throw "Schema validation failed; $key must use $($contract[2]), found $($metadata.DataType)."
        }
        if ($contract[3] -ine 'ANY' -and $metadata.IsNullable -ine $contract[3]) {
            throw "Schema validation failed; $key nullability must be $($contract[3]), found $($metadata.IsNullable)."
        }
        if ([int64]$contract[4] -gt 0 -and $metadata.MaxLength -lt [int64]$contract[4]) {
            throw "Schema validation failed; $key must be at least $($contract[4]) characters wide, found $($metadata.MaxLength)."
        }
        $hasAutoIncrement = $metadata.Extra -match '(?i)(^|\s)auto_increment($|\s)'
        if (($contract[5] -ieq 'AUTO' -and -not $hasAutoIncrement) -or
            ($contract[5] -ieq 'NONE' -and $hasAutoIncrement)) {
            throw "Schema validation failed; $key auto-increment metadata is incompatible."
        }
        if ($contract[6] -ieq 'CURRENT_TIMESTAMP') {
            if ($metadata.Default -notmatch '^(?i:current_timestamp(?:\(\))?)$') {
                throw "Schema validation failed; $key must default to CURRENT_TIMESTAMP."
            }
        } elseif ($contract[6] -ine 'ANY' -and $metadata.Default -ine $contract[6]) {
            throw "Schema validation failed; $key must default to '$($contract[6])', found '$($metadata.Default)'."
        }
    }

    $tableMetadata = @(Invoke-MySql -DefaultsFile $DefaultsFile -Sql @"
SELECT CONCAT(table_name, CHAR(9), engine, CHAR(9), table_collation)
FROM information_schema.tables
WHERE table_schema=$databaseLiteral AND table_type='BASE TABLE'
  AND table_name IN ('schema_versions','users','exams','questions','options',
                     'exam_attempts','student_answers','proctor_logs')
ORDER BY table_name;
"@ -Operation 'table engine validation' -SafeToShowError)
    foreach ($line in $tableMetadata) {
        $parts = $line -split "`t"
        if ($parts.Count -lt 3 -or $parts[1] -ine 'InnoDB' -or $parts[2] -notmatch '^(?i)utf8mb4_') {
            throw "Table '$($parts[0])' must use InnoDB and an utf8mb4 collation."
        }
    }

    $roleType = $columnMetadataByKey['users.role'].ColumnType
    if ($roleType -notmatch "(?i)^enum\('admin','student'\)$") {
        throw "users.role must be ENUM('admin','student'); found '$roleType'."
    }

    $primaryKeys = @(Invoke-MySql -DefaultsFile $DefaultsFile -Sql @"
SELECT CONCAT(table_name, CHAR(9), GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ','))
FROM information_schema.statistics
WHERE table_schema=$databaseLiteral AND index_name='PRIMARY'
GROUP BY table_name, index_name;
"@ -Operation 'primary key validation' -SafeToShowError)
    $requiredPrimaryKeys = @(
        "schema_versions`tversion", "users`tid", "exams`tid", "questions`tid",
        "options`tid", "exam_attempts`tid", "student_answers`tid", "proctor_logs`tid"
    )
    foreach ($signature in $requiredPrimaryKeys) {
        if ($signature -notin $primaryKeys) {
            $displaySignature = $signature.Replace("`t", '(') + ')'
            throw "Schema validation failed; missing primary key on $displaySignature."
        }
    }

    $uniqueIndexes = @(Invoke-MySql -DefaultsFile $DefaultsFile -Sql @"
SELECT CONCAT(table_name, CHAR(9), GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ','))
FROM information_schema.statistics
WHERE table_schema=$databaseLiteral AND non_unique=0
GROUP BY table_name, index_name;
"@ -Operation 'unique index validation' -SafeToShowError)
    $requiredUniqueIndexes = @(
        "users`tusername",
        "options`tquestion_id,option_number",
        "student_answers`tattempt_id,question_id"
    )
    foreach ($signature in $requiredUniqueIndexes) {
        if ($signature -notin $uniqueIndexes) {
            $displaySignature = $signature.Replace("`t", '(') + ')'
            throw "Schema validation failed; missing unique index on $displaySignature."
        }
    }

    $foreignKeys = @(Invoke-MySql -DefaultsFile $DefaultsFile -Sql @"
SELECT CONCAT(kcu.table_name, '.', kcu.column_name, '->',
              kcu.referenced_table_name, '.', kcu.referenced_column_name, ':', rc.delete_rule)
FROM information_schema.key_column_usage AS kcu
JOIN information_schema.referential_constraints AS rc
  ON rc.constraint_schema=kcu.constraint_schema
 AND rc.table_name=kcu.table_name
 AND rc.constraint_name=kcu.constraint_name
WHERE kcu.table_schema=$databaseLiteral AND kcu.referenced_table_name IS NOT NULL;
"@ -Operation 'foreign key validation' -SafeToShowError)
    $requiredForeignKeys = @(
        'exams.created_by->users.id:CASCADE',
        'questions.exam_id->exams.id:CASCADE',
        'options.question_id->questions.id:CASCADE',
        'exam_attempts.exam_id->exams.id:CASCADE',
        'exam_attempts.student_id->users.id:CASCADE',
        'student_answers.attempt_id->exam_attempts.id:CASCADE',
        'student_answers.question_id->questions.id:CASCADE',
        'proctor_logs.attempt_id->exam_attempts.id:CASCADE'
    )
    foreach ($relationship in $requiredForeignKeys) {
        if ($relationship -notin $foreignKeys) {
            throw "Schema validation failed; missing or incompatible foreign key: $relationship"
        }
    }

    $badQuestionTypes = Get-ScalarResult -DefaultsFile $DefaultsFile -SelectedDatabase $DatabaseName -Sql @'
SELECT COUNT(*) FROM questions
WHERE question_type IS NULL OR question_type NOT IN ('SINGLE','MULTI');
'@ -Operation 'question type contract validation'
    if ([int]$badQuestionTypes -gt 0) {
        throw "$badQuestionTypes questions have unsupported type values."
    }

    if ($RequireVersion) {
        $versionShape = Get-ScalarResult -DefaultsFile $DefaultsFile -SelectedDatabase $DatabaseName `
            -Sql @'
SELECT CONCAT(COUNT(*), CHAR(9), COALESCE(MIN(version), 0), CHAR(9),
              COALESCE(MAX(version), 0), CHAR(9), COALESCE(SUM(version), 0))
FROM schema_versions;
'@ `
            -Operation 'schema version validation'
        $versionParts = $versionShape -split "`t"
        $hasExactVersions = $versionParts.Count -eq 4 -and
            [int]$versionParts[0] -eq 3 -and [int]$versionParts[1] -eq 1 -and
            [int]$versionParts[2] -eq 3 -and [int]$versionParts[3] -eq 6
        if (-not $hasExactVersions) {
            throw "Schema version validation failed: expected exactly the set {1,2,3}; found '$versionShape'."
        }
    }

    Write-SetupMessage OK 'Schema tables, columns, indexes, foreign keys, and data contract validated.'
}

function Install-Or-UpgradeSchema {
    param([string] $AdminDefaultsFile)

    Assert-RecognizedExistingTables $AdminDefaultsFile
    Update-RecognizedLegacySchema $AdminDefaultsFile

    $schemaSql = [IO.File]::ReadAllText($script:SchemaFile)
    [void](Invoke-MySql -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName `
        -Sql $schemaSql -Operation 'schema installation' -SafeToShowError)

    Assert-SchemaContract -DefaultsFile $AdminDefaultsFile
    [void](Invoke-MySql -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName `
        -Sql 'INSERT IGNORE INTO schema_versions (version) VALUES (1),(2),(3);' `
        -Operation 'schema version recording' -SafeToShowError)
    Assert-SchemaContract -DefaultsFile $AdminDefaultsFile -RequireVersion
}

function Get-ApplicationAccountReference {
    $userLiteral = ConvertTo-MySqlStringLiteral $AppDatabaseUser
    $hostLiteral = ConvertTo-MySqlStringLiteral $script:AppDatabaseHost
    return "$userLiteral@$hostLiteral"
}

function Get-ExpectedApplicationTablePrivileges {
    return @(
        "$DatabaseName`tusers`tSELECT", "$DatabaseName`tusers`tINSERT", "$DatabaseName`tusers`tUPDATE",
        "$DatabaseName`texams`tSELECT", "$DatabaseName`texams`tINSERT", "$DatabaseName`texams`tUPDATE",
        "$DatabaseName`tquestions`tSELECT", "$DatabaseName`tquestions`tINSERT", "$DatabaseName`tquestions`tUPDATE",
        "$DatabaseName`toptions`tSELECT", "$DatabaseName`toptions`tINSERT", "$DatabaseName`toptions`tUPDATE",
        "$DatabaseName`texam_attempts`tSELECT", "$DatabaseName`texam_attempts`tINSERT",
        "$DatabaseName`texam_attempts`tUPDATE", "$DatabaseName`texam_attempts`tDELETE",
        "$DatabaseName`tstudent_answers`tSELECT", "$DatabaseName`tstudent_answers`tINSERT",
        "$DatabaseName`tstudent_answers`tUPDATE", "$DatabaseName`tstudent_answers`tDELETE",
        "$DatabaseName`tproctor_logs`tSELECT", "$DatabaseName`tproctor_logs`tINSERT",
        "$DatabaseName`tproctor_logs`tDELETE", "$DatabaseName`tschema_versions`tSELECT"
    )
}

function Get-ApplicationProxyGrantCount {
    param([string] $AdminDefaultsFile)

    $userLiteral = ConvertTo-MySqlStringLiteral $AppDatabaseUser
    $hostLiteral = ConvertTo-MySqlStringLiteral $script:AppDatabaseHost
    return Get-ScalarResult -DefaultsFile $AdminDefaultsFile -Sql @"
SELECT COUNT(*) FROM mysql.proxies_priv
WHERE (User=$userLiteral AND Host=$hostLiteral)
   OR (Proxied_user=$userLiteral AND Proxied_host=$hostLiteral);
"@ -Operation 'runtime proxy privilege audit'
}

function Set-ApplicationDatabaseGrants {
    param(
        [string] $AdminDefaultsFile,
        [switch] $AllowPrivilegeCleanupPrompt
    )

    $databaseForGrant = Quote-GrantDatabaseIdentifier $DatabaseName
    $account = Get-ApplicationAccountReference
    $granteeName = "'$AppDatabaseUser'@'$($script:AppDatabaseHost)'"
    $granteeLiteral = ConvertTo-MySqlStringLiteral $granteeName
    $expectedPrivileges = @(Get-ExpectedApplicationTablePrivileges)

    $proxyGrantCount = Get-ApplicationProxyGrantCount $AdminDefaultsFile
    if ([int]$proxyGrantCount -gt 0) {
        throw "The dedicated runtime account participates in a MySQL PROXY grant. Revoke that mapping manually before setup can enforce its exact allowlist."
    }

    $roleCount = Get-ScalarResult -DefaultsFile $AdminDefaultsFile -Sql @"
SELECT COUNT(*) FROM mysql.role_edges
WHERE TO_USER=$(ConvertTo-MySqlStringLiteral $AppDatabaseUser)
  AND TO_HOST=$(ConvertTo-MySqlStringLiteral $script:AppDatabaseHost);
"@ -Operation 'runtime role audit'
    if ([int]$roleCount -gt 0) {
        throw "The dedicated runtime account has an assigned MySQL role. Revoke that role manually before setup can enforce its exact privilege allowlist."
    }

    $elevatedPrivilegeCount = Get-ScalarResult -DefaultsFile $AdminDefaultsFile -Sql @"
SELECT
  (SELECT COUNT(*) FROM information_schema.user_privileges WHERE grantee=$granteeLiteral) +
  (SELECT COUNT(*) FROM information_schema.schema_privileges WHERE grantee=$granteeLiteral) +
  (SELECT COUNT(*) FROM information_schema.column_privileges WHERE grantee=$granteeLiteral) +
  (SELECT COUNT(*) FROM information_schema.routine_privileges WHERE grantee=$granteeLiteral) +
  (SELECT COUNT(*) FROM information_schema.table_privileges
   WHERE grantee=$granteeLiteral AND is_grantable='YES');
"@ -Operation 'runtime elevated privilege audit'
    $currentTablePrivileges = @(Invoke-MySql -DefaultsFile $AdminDefaultsFile -Sql @"
SELECT CONCAT(table_schema, CHAR(9), table_name, CHAR(9), privilege_type)
FROM information_schema.table_privileges
WHERE grantee=$granteeLiteral
ORDER BY table_schema, table_name, privilege_type;
"@ -Operation 'runtime table privilege audit' -SafeToShowError)
    $unexpectedTablePrivileges = @($currentTablePrivileges | Where-Object { $_ -notin $expectedPrivileges })

    if ([int]$elevatedPrivilegeCount -gt 0 -or $unexpectedTablePrivileges.Count -gt 0) {
        Write-SetupMessage WARN "The dedicated '$AppDatabaseUser' account has privileges outside ExaSecure's exact table allowlist."
        if (-not $AllowPrivilegeCleanupPrompt) {
            throw "The newly created runtime account unexpectedly acquired privileges before setup could apply its allowlist. No automatic cleanup was attempted."
        }
        if (-not (Confirm-Choice 'Remove all privileges from that dedicated MySQL account, then apply only the documented ExaSecure grants?' $false)) {
            throw 'Privilege cleanup was declined. Setup will not report a broadly privileged runtime account as ready.'
        }
        [void](Invoke-MySql -DefaultsFile $AdminDefaultsFile `
            -Sql "REVOKE ALL PRIVILEGES, GRANT OPTION FROM $account;" `
            -Operation 'confirmed runtime privilege cleanup' -SafeToShowError)
        Write-SetupMessage INFO "Removed pre-existing privileges from '$AppDatabaseUser' after explicit confirmation."
    }

    $statements = @(
        "GRANT SELECT, INSERT, UPDATE ON ${databaseForGrant}.``users`` TO $account;",
        "GRANT SELECT, INSERT, UPDATE ON ${databaseForGrant}.``exams`` TO $account;",
        "GRANT SELECT, INSERT, UPDATE ON ${databaseForGrant}.``questions`` TO $account;",
        "GRANT SELECT, INSERT, UPDATE ON ${databaseForGrant}.``options`` TO $account;",
        "GRANT SELECT, INSERT, UPDATE, DELETE ON ${databaseForGrant}.``exam_attempts`` TO $account;",
        "GRANT SELECT, INSERT, UPDATE, DELETE ON ${databaseForGrant}.``student_answers`` TO $account;",
        "GRANT SELECT, INSERT, DELETE ON ${databaseForGrant}.``proctor_logs`` TO $account;",
        "GRANT SELECT ON ${databaseForGrant}.``schema_versions`` TO $account;"
    )
    [void](Invoke-MySql -DefaultsFile $AdminDefaultsFile -Sql ($statements -join "`n") `
        -Operation 'runtime privilege grants' -SafeToShowError)

    $verifiedElevatedCount = Get-ScalarResult -DefaultsFile $AdminDefaultsFile -Sql @"
SELECT
  (SELECT COUNT(*) FROM information_schema.user_privileges WHERE grantee=$granteeLiteral) +
  (SELECT COUNT(*) FROM information_schema.schema_privileges WHERE grantee=$granteeLiteral) +
  (SELECT COUNT(*) FROM information_schema.column_privileges WHERE grantee=$granteeLiteral) +
  (SELECT COUNT(*) FROM information_schema.routine_privileges WHERE grantee=$granteeLiteral) +
  (SELECT COUNT(*) FROM information_schema.table_privileges
   WHERE grantee=$granteeLiteral AND is_grantable='YES');
"@ -Operation 'runtime elevated privilege verification'
    $verifiedTablePrivileges = @(Invoke-MySql -DefaultsFile $AdminDefaultsFile -Sql @"
SELECT CONCAT(table_schema, CHAR(9), table_name, CHAR(9), privilege_type)
FROM information_schema.table_privileges
WHERE grantee=$granteeLiteral
ORDER BY table_schema, table_name, privilege_type;
"@ -Operation 'runtime table privilege verification' -SafeToShowError)
    $missingPrivileges = @($expectedPrivileges | Where-Object { $_ -notin $verifiedTablePrivileges })
    $extraPrivileges = @($verifiedTablePrivileges | Where-Object { $_ -notin $expectedPrivileges })
    $verifiedProxyGrantCount = Get-ApplicationProxyGrantCount $AdminDefaultsFile
    if ([int]$verifiedElevatedCount -ne 0 -or [int]$verifiedProxyGrantCount -ne 0 -or
        $missingPrivileges.Count -gt 0 -or $extraPrivileges.Count -gt 0) {
        throw 'The runtime database privilege set did not match the exact table-level allowlist after GRANT.'
    }
    Write-SetupMessage OK 'Verified the exact table-level runtime privilege allowlist (no global, schema, column, routine, or extra-table grants).'
}

function Assert-CurrentApplicationDatabaseGrants {
    param([string] $AppDefaultsFile)

    $granteeName = "'$AppDatabaseUser'@'$($script:AppDatabaseHost)'"
    $granteeLiteral = ConvertTo-MySqlStringLiteral $granteeName
    $expectedPrivileges = @(Get-ExpectedApplicationTablePrivileges)
    $identityState = Get-ScalarResult -DefaultsFile $AppDefaultsFile -Sql `
        'SELECT CONCAT(CURRENT_USER(), CHAR(9), CURRENT_ROLE());' `
        -Operation 'read-only runtime identity audit'
    $identityParts = $identityState -split "`t", 2
    $expectedCurrentUser = "$AppDatabaseUser@$($script:AppDatabaseHost)"
    if ($identityParts.Count -ne 2 -or $identityParts[0] -cne $expectedCurrentUser) {
        throw "Read-only verification authenticated as an unexpected MySQL identity; expected '$expectedCurrentUser'."
    }
    if ($identityParts[1] -ine 'NONE') {
        throw 'Read-only verification found one or more active or mandatory MySQL roles on the runtime identity.'
    }
    $elevatedPrivilegeCount = Get-ScalarResult -DefaultsFile $AppDefaultsFile -Sql @"
SELECT
  (SELECT COUNT(*) FROM information_schema.user_privileges WHERE grantee=$granteeLiteral) +
  (SELECT COUNT(*) FROM information_schema.schema_privileges WHERE grantee=$granteeLiteral) +
  (SELECT COUNT(*) FROM information_schema.column_privileges WHERE grantee=$granteeLiteral) +
  (SELECT COUNT(*) FROM information_schema.routine_privileges WHERE grantee=$granteeLiteral) +
  (SELECT COUNT(*) FROM information_schema.table_privileges
   WHERE grantee=$granteeLiteral AND is_grantable='YES');
"@ -Operation 'read-only runtime elevated privilege audit'
    $tablePrivileges = @(Invoke-MySql -DefaultsFile $AppDefaultsFile -Sql @"
SELECT CONCAT(table_schema, CHAR(9), table_name, CHAR(9), privilege_type)
FROM information_schema.table_privileges
WHERE grantee=$granteeLiteral
ORDER BY table_schema, table_name, privilege_type;
"@ -Operation 'read-only runtime table privilege audit' -SafeToShowError)
    # SHOW GRANTS without FOR includes the current identity and mandatory roles.
    # Allow only USAGE or a direct grant on one of the documented tables; every
    # role, PROXY, global, schema, routine, and unrelated-object line is rejected.
    $grantLines = @(Invoke-MySql -DefaultsFile $AppDefaultsFile `
        -Sql 'SHOW GRANTS;' -Operation 'read-only runtime grant audit' -SafeToShowError)
    $allowedTables = @($expectedPrivileges | ForEach-Object { ($_ -split "`t")[1] } | Select-Object -Unique)
    $databasePattern = '`?' + [regex]::Escape($DatabaseName) + '`?'
    $tablePattern = '`?(?:' + (($allowedTables | ForEach-Object { [regex]::Escape($_) }) -join '|') + ')`?'
    $directTableGrantPattern = '(?i)^GRANT\s+[A-Z_, ]+\s+ON\s+' + $databasePattern + '\.' + $tablePattern + '\s+TO\s+'
    $usageGrantPattern = '(?i)^GRANT\s+USAGE\s+ON\s+\*\.\*\s+TO\s+'
    $unexpectedGrantLines = @($grantLines | Where-Object {
        $_ -notmatch $usageGrantPattern -and $_ -notmatch $directTableGrantPattern
    })
    $missingPrivileges = @($expectedPrivileges | Where-Object { $_ -notin $tablePrivileges })
    $extraPrivileges = @($tablePrivileges | Where-Object { $_ -notin $expectedPrivileges })
    if ([int]$elevatedPrivilegeCount -ne 0 -or $unexpectedGrantLines.Count -gt 0 -or
        $missingPrivileges.Count -gt 0 -or $extraPrivileges.Count -gt 0) {
        throw 'Read-only verification failed: the runtime account does not have the exact role-free table-level privilege allowlist. Run normal setup to reconcile direct grants; revoke assigned roles manually.'
    }
    Write-SetupMessage OK 'Read-only audit confirmed the exact role-free runtime privilege allowlist.'
}

function Ensure-ApplicationDatabaseAccount {
    param(
        [string] $AdminDefaultsFile,
        [AllowNull()][object] $ExistingConfiguration
    )

    $account = Get-ApplicationAccountReference
    $userLiteral = ConvertTo-MySqlStringLiteral $AppDatabaseUser
    $hostLiteral = ConvertTo-MySqlStringLiteral $script:AppDatabaseHost
    $exists = Get-ScalarResult -DefaultsFile $AdminDefaultsFile -Sql @"
SELECT COUNT(*) FROM mysql.user WHERE User=$userLiteral AND Host=$hostLiteral;
"@ -Operation 'runtime account existence check'

    $appPassword = $null
    $isNew = ([int]$exists -eq 0)
    $hasReusableConfig = $null -ne $ExistingConfiguration -and
        $ExistingConfiguration.User -ceq $AppDatabaseUser

    # Any consent-gated privilege cleanup for an existing account must happen
    # before its password can be changed. A declined cleanup therefore cannot
    # strand a freshly rotated secret that has not yet reached db.properties.
    if (-not $isNew) {
        Set-ApplicationDatabaseGrants $AdminDefaultsFile -AllowPrivilegeCleanupPrompt
    }

    if ($isNew) {
        $appPassword = New-CryptoRandomPassword -Length 32
        $plainPassword = $null
        try {
            $plainPassword = ConvertFrom-SecureStringBriefly $appPassword
            $passwordLiteral = ConvertTo-MySqlStringLiteral $plainPassword
            [void](Invoke-MySql -DefaultsFile $AdminDefaultsFile `
                -Sql "CREATE USER $account IDENTIFIED BY $passwordLiteral;" `
                -Operation 'runtime account creation')
        } finally {
            $plainPassword = $null
            $passwordLiteral = $null
        }
        Write-SetupMessage OK "Created local-only MySQL account '$AppDatabaseUser'."
    } elseif ($RotateAppDbPassword) {
        $appPassword = New-CryptoRandomPassword -Length 32
        $plainPassword = $null
        try {
            $plainPassword = ConvertFrom-SecureStringBriefly $appPassword
            $passwordLiteral = ConvertTo-MySqlStringLiteral $plainPassword
            [void](Invoke-MySql -DefaultsFile $AdminDefaultsFile `
                -Sql "ALTER USER $account IDENTIFIED BY $passwordLiteral;" `
                -Operation 'explicit runtime password rotation')
        } finally {
            $plainPassword = $null
            $passwordLiteral = $null
        }
        Write-SetupMessage OK "Rotated '$AppDatabaseUser' because -RotateAppDbPassword was supplied."
    } elseif ($hasReusableConfig) {
        $appPassword = $ExistingConfiguration.Password
        Write-SetupMessage INFO 'Reusing the runtime password from ignored db.properties; the JDBC URL will be normalized separately.'
    } else {
        if (Confirm-Choice "The '$AppDatabaseUser' account exists but no local password is available. Rotate it now?" $false) {
            $appPassword = New-CryptoRandomPassword -Length 32
            $plainPassword = $null
            try {
                $plainPassword = ConvertFrom-SecureStringBriefly $appPassword
                $passwordLiteral = ConvertTo-MySqlStringLiteral $plainPassword
                [void](Invoke-MySql -DefaultsFile $AdminDefaultsFile `
                    -Sql "ALTER USER $account IDENTIFIED BY $passwordLiteral;" `
                    -Operation 'confirmed runtime password recovery')
            } finally {
                $plainPassword = $null
                $passwordLiteral = $null
            }
            Write-SetupMessage OK "Rotated '$AppDatabaseUser' after interactive confirmation."
        } else {
            $appPassword = Read-Host "Existing password for MySQL account '$AppDatabaseUser'" -AsSecureString
        }
    }

    try {
        if ($isNew) {
            Set-ApplicationDatabaseGrants $AdminDefaultsFile
        }
        $appDefaults = New-MySqlDefaultsFile -Username $AppDatabaseUser -Password $appPassword
        [void](Get-ScalarResult -DefaultsFile $appDefaults -Sql 'SELECT 1;' -Operation 'runtime account connection')
    } catch {
        $provisioningError = $_.Exception.Message
        if ($isNew) {
            try {
                [void](Invoke-MySql -DefaultsFile $AdminDefaultsFile `
                    -Sql "DROP USER IF EXISTS $account;" -Operation 'new runtime account rollback')
                Write-SetupMessage WARN "Removed newly created '$AppDatabaseUser' after provisioning failed; no unknown password was stranded."
            } catch {
                throw "New runtime account provisioning failed, and automatic rollback also failed. Remove $account manually before rerunning. Original error: $provisioningError"
            }
            throw "New runtime account provisioning failed and the newly created account was removed safely: $provisioningError"
        }
        throw "The '$AppDatabaseUser' connection failed. Rerun with -RotateAppDbPassword if its stored password is unknown."
    }
    Write-SetupMessage OK "Restricted runtime account '$AppDatabaseUser' connected successfully."

    return [pscustomobject]@{
        Password     = $appPassword
        DefaultsFile = $appDefaults
        IsNew        = $isNew
    }
}

function Get-DemoAccountRecord {
    param(
        [string] $AdminDefaultsFile,
        [string] $Username
    )

    $usernameLiteral = ConvertTo-MySqlStringLiteral $Username
    $rows = @(Invoke-MySql -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName -Sql @"
SELECT CONCAT(id, CHAR(9), role, CHAR(9), is_active, CHAR(9), password)
FROM users WHERE username=$usernameLiteral LIMIT 1;
"@ -Operation "demo account lookup for $Username" -SafeToShowError)
    if ($rows.Count -eq 0) {
        return $null
    }
    $parts = $rows[0] -split "`t"
    return [pscustomobject]@{
        Id     = [int]$parts[0]
        Role   = $parts[1]
        Active = ([int]$parts[2] -eq 1)
        Hash   = $parts[3]
    }
}

function Test-JBcryptHashFormat {
    param([AllowNull()][string] $Hash)

    # jBCrypt 0.4 accepts the 2a revision and work factors 04 through 30.
    # The remaining 53 characters are the BCrypt salt/hash base64 alphabet.
    return $null -ne $Hash -and
        $Hash -cmatch '^\$2a\$(0[4-9]|[12][0-9]|30)\$[./A-Za-z0-9]{53}$'
}

function Ensure-DemoAccount {
    param(
        [string] $AdminDefaultsFile,
        [string] $Username,
        [string] $Role,
        [string] $FullName,
        [AllowNull()][string] $Enrollment,
        [AllowNull()][string] $Section
    )

    $record = Get-DemoAccountRecord $AdminDefaultsFile $Username
    if ($record) {
        if ($record.Role -cne $Role -or -not $record.Active) {
            throw "Existing user '$Username' conflicts with the required active '$Role' demo account."
        }
        $validHash = Test-JBcryptHashFormat $record.Hash
        if (-not $RotateDemoPasswords) {
            if (-not $validHash) {
                throw "Existing demo user '$Username' does not have a BCrypt password. Rerun with -RotateDemoPasswords."
            }
            Write-SetupMessage INFO "Demo user '$Username' already exists as ID $($record.Id); password unchanged and not displayed."
            return
        }
    }

    $password = New-CryptoRandomPassword -Length 20
    $hash = Invoke-PasswordHash $password
    $usernameLiteral = ConvertTo-MySqlStringLiteral $Username
    $hashLiteral = ConvertTo-MySqlStringLiteral $hash

    if ($record) {
        [void](Invoke-MySql -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName `
            -Sql "UPDATE users SET password=$hashLiteral WHERE id=$($record.Id);" `
            -Operation "demo password rotation for $Username")
        $state = 'ROTATED'
        $userId = $record.Id
    } else {
        $fullNameLiteral = ConvertTo-MySqlStringLiteral $FullName
        $roleLiteral = ConvertTo-MySqlStringLiteral $Role
        $enrollmentLiteral = if ($null -eq $Enrollment) { 'NULL' } else { ConvertTo-MySqlStringLiteral $Enrollment }
        $sectionLiteral = if ($null -eq $Section) { 'NULL' } else { ConvertTo-MySqlStringLiteral $Section }
        [void](Invoke-MySql -DefaultsFile $AdminDefaultsFile -SelectedDatabase $DatabaseName -Sql @"
INSERT INTO users (username, password, full_name, enrollment_number, section, role, is_active)
VALUES ($usernameLiteral, $hashLiteral, $fullNameLiteral, $enrollmentLiteral, $sectionLiteral, $roleLiteral, 1);
"@ -Operation "demo account creation for $Username")
        $created = Get-DemoAccountRecord $AdminDefaultsFile $Username
        if (-not $created) {
            throw "Demo account '$Username' was not found after insertion."
        }
        $userId = $created.Id
        $state = 'CREATED'
    }

    $script:NewCredentials.Add([pscustomobject]@{
        Username = $Username
        Role     = $Role
        Id       = $userId
        Password = $password
        State    = $state
    })
    Write-SetupMessage OK "$state demo user '$Username' (ID $userId, role $Role)."
}

function Ensure-DemoAccounts {
    param([string] $AdminDefaultsFile)

    if ($SkipDemoAccounts) {
        Write-SetupMessage INFO 'Demo account creation skipped.'
        return
    }

    Ensure-DemoAccount $AdminDefaultsFile 'demo_admin' 'admin' 'Demo Administrator' $null $null
    Ensure-DemoAccount $AdminDefaultsFile 'demo_student' 'student' 'Demo Student' 'DEMO001' 'A'
}

function Test-JdbcConnection {
    if ($SkipBuild) {
        Write-SetupMessage INFO 'JDBC and AuthService smoke tests skipped with -SkipBuild.'
        return
    }

    $classpath = $script:BuildOutput + ';' + (Join-Path $script:ProjectRoot 'lib\*')
    $result = Invoke-NativeProcess -FilePath $script:JavaExe -ArgumentList @(
        '-cp', $classpath, 'com.examsystem.db.TestDBConnection'
    ) -WorkingDirectory $script:ProjectRoot
    if ($result.ExitCode -ne 0 -or $result.StdOut -notmatch 'SUCCESS: JDBC connection OK') {
        throw 'The application JDBC smoke test failed. Review db.properties, Connector/J, and MySQL service state.'
    }
    Write-SetupMessage OK 'Application JDBC connection smoke test passed.'
}

function Test-NewDemoAuthentications {
    if ($SkipBuild -or $script:NewCredentials.Count -eq 0) {
        return
    }

    $classpath = $script:BuildOutput + ';' + (Join-Path $script:ProjectRoot 'lib\*')
    foreach ($credential in $script:NewCredentials) {
        $plainPassword = $null
        try {
            $plainPassword = ConvertFrom-SecureStringBriefly $credential.Password
            $result = Invoke-NativeProcess -FilePath $script:JavaExe -ArgumentList @(
                '-cp', $classpath, 'com.examsystem.setup.AuthenticationVerifier'
            ) -StandardInputText ($credential.Username + "`n" + $plainPassword + "`n") `
              -WorkingDirectory $script:ProjectRoot
            if ($result.ExitCode -ne 0 -or $result.StdOut -notmatch '^AUTHENTICATION_OK') {
                throw "AuthService rejected newly $($credential.State.ToLowerInvariant()) account '$($credential.Username)'."
            }
            Write-SetupMessage OK "AuthService login passed for '$($credential.Username)'."
        } finally {
            $plainPassword = $null
        }
    }
}

function Write-DataQualityWarnings {
    param([string] $DefaultsFile)

    $legacyPasswords = Get-ScalarResult -DefaultsFile $DefaultsFile -SelectedDatabase $DatabaseName -Sql @'
SELECT COUNT(*) FROM users
WHERE password NOT REGEXP '^[$]2a[$](0[4-9]|[12][0-9]|30)[$][./A-Za-z0-9]{53}$';
'@ -Operation 'password-format audit'
    if ([int]$legacyPasswords -gt 0) {
        Write-SetupMessage WARN "$legacyPasswords legacy user account(s) have non-BCrypt passwords and cannot use strict login until an admin resets them."
    }

    $invalidSingleQuestions = Get-ScalarResult -DefaultsFile $DefaultsFile -SelectedDatabase $DatabaseName -Sql @'
SELECT COUNT(*) FROM (
  SELECT q.id
  FROM questions q
  LEFT JOIN options o ON o.question_id=q.id AND o.is_active=1
  WHERE q.question_type='SINGLE'
  GROUP BY q.id
  HAVING SUM(CASE WHEN o.is_correct=1 THEN 1 ELSE 0 END) <> 1
) AS invalid_questions;
'@ -Operation 'question correctness audit'
    if ([int]$invalidSingleQuestions -gt 0) {
        Write-SetupMessage WARN "$invalidSingleQuestions SINGLE question(s) do not have exactly one active correct option; review them in the admin UI."
    }
}

function Show-CredentialSummary {
    if ($script:CredentialSummaryShown -or $script:NewCredentials.Count -eq 0) {
        return
    }
    $script:CredentialSummaryShown = $true

    Write-Host ''
    Write-Host 'APPLICATION LOGIN CREDENTIALS - SHOWN ONCE' -ForegroundColor Yellow
    Write-Host 'Save these now. They are not written to a credential-summary file.' -ForegroundColor Yellow
    foreach ($credential in $script:NewCredentials) {
        $plainPassword = $null
        try {
            $plainPassword = ConvertFrom-SecureStringBriefly $credential.Password
            Write-Host ("  {0,-12} username={1,-16} id={2,-5} role={3,-7} password={4}" -f `
                $credential.State, $credential.Username, $credential.Id, $credential.Role, $plainPassword)
        } finally {
            $plainPassword = $null
        }
    }
    Write-Host ''
}

function Show-CompletionBanner {
    Write-Host ''
    Write-Host '============================================================' -ForegroundColor Green
    Write-Host '  PROJECT SETUP COMPLETE - STATUS: READY' -ForegroundColor Green
    Write-Host '============================================================' -ForegroundColor Green
}

function Invoke-VerificationOnly {
    if (-not (Test-Path -LiteralPath $script:PropertiesFile -PathType Leaf)) {
        throw 'db.properties is missing. Run .\setup.ps1 first.'
    }
    Assert-RestrictedFileAcl $script:PropertiesFile
    $configuration = Read-DatabaseProperties
    if (-not $configuration) {
        throw 'db.properties is missing or incomplete. Run .\setup.ps1 first.'
    }
    if ($configuration.User -cne $AppDatabaseUser -or
        $configuration.Url -cne (Get-ExpectedJdbcUrl)) {
        throw 'db.properties does not match the requested local database/runtime account. Run normal setup to reconcile it.'
    }

    $appDefaults = New-MySqlDefaultsFile -Username $configuration.User -Password $configuration.Password
    [void](Get-ScalarResult -DefaultsFile $appDefaults -Sql 'SELECT 1;' -Operation 'runtime account verification')
    Assert-SupportedMySqlServer $appDefaults
    Assert-CurrentApplicationDatabaseGrants $appDefaults
    Assert-SchemaContract -DefaultsFile $appDefaults -RequireVersion
    Test-JdbcConnection
    Write-DataQualityWarnings $appDefaults

    Write-SetupMessage OK 'Read-only verification completed successfully.'
    Show-CompletionBanner
    Write-Host ''
    Write-Host 'Launch the application from the project root:' -ForegroundColor Green
    Write-Host '  .\run_onwindows.bat'
}

function Invoke-NormalSetup {
    $existingConfiguration = Read-DatabaseProperties
    if (-not $WhatIfPreference) {
        Test-DatabasePropertiesWritePreflight $existingConfiguration
    }
    $adminDefaults = Get-AdminDefaultsFile
    Assert-SupportedMySqlServer $adminDefaults

    if ($Reset) {
        Backup-And-ResetDatabase $adminDefaults
        if ($script:ResetPreviewOnly) {
            return
        }
    }

    Write-SetupMessage STAGE 'Creating or safely upgrading the database'
    Ensure-Database $adminDefaults
    Install-Or-UpgradeSchema $adminDefaults

    Write-SetupMessage STAGE 'Provisioning the restricted runtime database account'
    $appAccount = Ensure-ApplicationDatabaseAccount $adminDefaults $existingConfiguration
    try {
        Write-DatabaseProperties $appAccount.Password
    } catch {
        $configurationError = $_.Exception.Message
        if ($appAccount.IsNew) {
            $newAccount = Get-ApplicationAccountReference
            try {
                [void](Invoke-MySql -DefaultsFile $adminDefaults `
                    -Sql "DROP USER IF EXISTS $newAccount;" -Operation 'unpersisted runtime account rollback')
                Write-SetupMessage WARN "Removed newly created '$AppDatabaseUser' because its credential could not be persisted safely."
            } catch {
                throw "db.properties could not be written, and the new runtime account could not be rolled back. Remove $newAccount manually. Original error: $configurationError"
            }
            throw "db.properties could not be written; the new runtime account was removed so no credential was stranded. Original error: $configurationError"
        }
        throw
    }

    # Revalidate with the restricted account, not only the bootstrap administrator.
    Assert-SchemaContract -DefaultsFile $appAccount.DefaultsFile -RequireVersion
    Test-JdbcConnection

    Write-SetupMessage STAGE 'Creating idempotent BCrypt demo accounts'
    Ensure-DemoAccounts $adminDefaults
    Test-NewDemoAuthentications
    Write-DataQualityWarnings $appAccount.DefaultsFile

    Write-SetupMessage OK 'ExaSecure setup and verification completed successfully.'
    Show-CompletionBanner
    Write-Host ''
    Write-Host 'Runtime database account:' -ForegroundColor Green
    Write-Host "  $AppDatabaseUser@$($script:AppDatabaseHost) (table-level privileges only)"
    Write-Host '  Password stored only in ignored, ACL-restricted db.properties.'
    Write-Host ''
    Write-Host 'Launch the application from the project root:' -ForegroundColor Green
    Write-Host '  .\run_onwindows.bat'
}

$exitCode = 0
try {
    Initialize-SetupLog
    Write-SetupMessage STAGE 'ExaSecure Windows-native setup'
    Write-SetupMessage INFO "Project root: $script:ProjectRoot"
    Write-SetupMessage INFO "Sanitized log: $script:LogPath"

    Assert-ParameterCombinations
    $script:TempRoot = New-RestrictedTempDirectory
    Test-RepositorySecretSafety

    Write-SetupMessage STAGE 'Checking prerequisites and pinned dependencies'
    Resolve-JavaToolchain
    Resolve-MySqlClient
    Ensure-JavaDependencies
    Build-Application

    if ($VerifyOnly) {
        Invoke-VerificationOnly
    } else {
        if (-not $WhatIfPreference) {
            Start-DetectedMySqlService
        }
        Invoke-NormalSetup
    }
} catch {
    $exitCode = 1
    Write-SetupMessage ERROR $_.Exception.Message
    Write-Host ''
    Write-Host 'SETUP INCOMPLETE - correct the issue above and rerun the same command.' -ForegroundColor Red
    Write-Host "Safe diagnostic log: $script:LogPath" -ForegroundColor Yellow
    Write-Host 'No automatic rollback was attempted; MySQL DDL commits per statement. Rerun setup after correcting the reported issue.' -ForegroundColor Yellow
} finally {
    Show-CredentialSummary
    try {
        Remove-RestrictedTempDirectory
    } catch {
        Write-Host "[WARN] Temporary setup cleanup failed: $($_.Exception.Message)" -ForegroundColor Yellow
        if ($exitCode -eq 0) {
            $exitCode = 1
        }
    }
    if ($script:Rng) {
        $script:Rng.Dispose()
    }
}

exit $exitCode
