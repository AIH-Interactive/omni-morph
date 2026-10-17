param([string]$VisualStudioPath)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$clients = Get-CimInstance Win32_Process -Filter "name = 'java.exe' or name = 'javaw.exe'" |
    Where-Object { $_.CommandLine -match 'KnotClient|net.minecraft.client.main.Main' }
if ($clients) {
    throw 'Close the Minecraft client before compiling native libraries.'
}

if (-not $VisualStudioPath) {
    $cmakeCommand = Get-Command cmake -ErrorAction SilentlyContinue
    if ($cmakeCommand -and $cmakeCommand.Source -match '^(.*)[\\/]Common7[\\/]') {
        $VisualStudioPath = $Matches[1]
    } else {
        $vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio/Installer/vswhere.exe'
        if (Test-Path -LiteralPath $vswhere) {
            $VisualStudioPath = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
        }
    }
}
if (-not $VisualStudioPath) {
    throw 'MSVC x64 not found. Pass -VisualStudioPath with the installed Visual Studio directory.'
}
$developerCommand = Join-Path $VisualStudioPath 'Common7/Tools/VsDevCmd.bat'
if (-not (Test-Path -LiteralPath $developerCommand)) {
    throw "Missing MSVC developer environment: $developerCommand"
}
# Import only the developer environment; build commands below stay in foreground PowerShell.
$developerEnvironment = & $env:ComSpec /d /c "call `"$developerCommand`" -no_logo -arch=x64 -host_arch=x64 && set"
$environmentExitCode = $LASTEXITCODE
if ($environmentExitCode -ne 0) {
    throw 'MSVC x64 environment initialization failed.'
}
foreach ($line in $developerEnvironment) {
    if ($line -match '^([^=]+)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath "$env:JAVA_HOME/include/jni.h")) {
    throw 'Set JAVA_HOME to JDK 25 with JNI headers (usejdk 25).'
}
$nativeRoot = Join-Path $projectRoot 'build/native/windows-x64-release'
New-Item -ItemType Directory -Force -Path $nativeRoot | Out-Null

function Invoke-NativeStep {
    param([string]$Name, [string]$Executable, [string[]]$Arguments)
    $logDirectory = Join-Path $nativeRoot $Name
    New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
    $stepLog = Join-Path $logDirectory 'build.log'
    & $Executable @Arguments 2>&1 | Tee-Object -FilePath $stepLog
    $buildExitCode = $LASTEXITCODE
    Get-Content -LiteralPath $stepLog -Tail 10
    if ($buildExitCode -ne 0) {
        exit $buildExitCode
    }
}

Invoke-NativeStep 'configure' 'cmake' @('--preset', 'windows-x64-release')
Invoke-NativeStep 'compile' 'cmake' @('--build', '--preset', 'windows-x64-release')
Invoke-NativeStep 'test' 'ctest' @('--preset', 'windows-x64-release')

$library = Join-Path $nativeRoot 'bin/omnimorph_accel.dll'
$dependencyOutput = & dumpbin /DEPENDENTS $library
$dependencyExitCode = $LASTEXITCODE
$dependencyOutput | Write-Output
if ($dependencyExitCode -ne 0) {
    throw 'Cannot inspect native runtime dependencies.'
}
$dependencies = @($dependencyOutput | ForEach-Object {
    if ($_ -match '^\s+([A-Za-z0-9_.-]+\.dll)\s*$') {
        $Matches[1]
    }
} | Sort-Object -Unique)
if ($dependencies.Count -eq 0) {
    throw 'Native runtime dependency inspection returned no DLLs.'
}
$sourceCommit = (& git rev-parse HEAD).Trim()
$sourceFiles = @(Get-Item -LiteralPath (Join-Path $projectRoot 'CMakeLists.txt'), (Join-Path $projectRoot 'CMakePresets.json')) +
    @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'native') -File -Recurse | Sort-Object FullName)
$sourceDigest = ($sourceFiles | ForEach-Object {
    $_.FullName.Substring($projectRoot.Length + 1).Replace('\', '/') + ':' + (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
}) -join "`n"
$digestBytes = [Text.Encoding]::UTF8.GetBytes($sourceDigest)
$sha = [Security.Cryptography.SHA256]::Create()
try {
    $sourceHash = ([BitConverter]::ToString($sha.ComputeHash($digestBytes))).Replace('-', '').ToLowerInvariant()
} finally {
    $sha.Dispose()
}
$manifest = [ordered]@{
    abi = 1
    buildKind = 'local'
    libraries = @([ordered]@{
        platform = 'windows-x86_64'
        architecture = 'x86_64'
        path = 'natives/windows-x86_64/omnimorph_accel.dll'
        abi = 1
        sourceCommit = $sourceCommit
        sourceTreeSha256 = $sourceHash
        sourceDirty = [bool](& git status --porcelain -- native CMakeLists.txt CMakePresets.json)
        toolchain = "MSVC $env:VCToolsVersion; C++17; /fp:strict; static CRT"
        minimumRuntime = 'Windows 10 x64; system libraries only; static MSVC CRT'
        sha256 = (Get-FileHash -LiteralPath $library -Algorithm SHA256).Hash.ToLowerInvariant()
        dependencies = $dependencies
    })
}
$manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $nativeRoot 'manifest.json') -Encoding UTF8
Write-Output "Native build and tests passed: $library"
exit 0
