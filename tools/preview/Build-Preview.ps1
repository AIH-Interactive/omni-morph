param(
    [string] $Jdk = $env:JAVA_HOME,
    [string] $RuntimeConfig = (Join-Path $PSScriptRoot 'runtime.local.json')
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$output = Join-Path $repoRoot 'build/preview/jar-host'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$previousJavaHome = $env:JAVA_HOME
try {
    if ($Jdk) {
        $env:JAVA_HOME = $Jdk
    }
    & (Join-Path $repoRoot 'gradlew.bat') -p $PSScriptRoot --no-daemon --console=plain assemble "-PruntimeConfig=$RuntimeConfig" 2>&1 |
        Tee-Object -FilePath (Join-Path $output 'build.log')
    $buildExitCode = $LASTEXITCODE
    if ($buildExitCode -ne 0) {
        Get-Content (Join-Path $output 'build.log') -Tail 40
        exit $buildExitCode
    }
} finally {
    $env:JAVA_HOME = $previousJavaHome
}
