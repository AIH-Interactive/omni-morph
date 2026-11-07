param(
    [string] $Jdk = 'D:\Program Files\Java\25',
    [string] $NativeLibrary = 'build/windows-x64-Release/bin/Release/omm_core.dll'
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Set-Location -LiteralPath $repoRoot
$java = Join-Path $Jdk 'bin/java.exe'
$NativeLibrary = (Resolve-Path -LiteralPath $NativeLibrary).Path
$jvm = @('--enable-native-access=ALL-UNNAMED', '--sun-misc-unsafe-memory-access=allow')
$dependencies = (Get-Content -LiteralPath build/preview/classpath.txt -Raw).Trim()
$classPath = (Join-Path $repoRoot 'build/preview/classes') + [IO.Path]::PathSeparator + $dependencies
$testClassPath = (Join-Path $repoRoot 'build/preview/test-classes') + [IO.Path]::PathSeparator + $classPath

& (Join-Path $Jdk 'bin/javac.exe') --release 25 -cp $classPath -d build/preview/test-classes tools/preview/tests/PreviewCpuVerticesTest.java 2>&1 | Tee-Object -FilePath build.log | Tee-Object -FilePath build/preview/test-cpu-vertices-compile.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build.log; exit $testExitCode }
& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewCpuVerticesTest build/preview/fixtures $NativeLibrary 2>&1 | Tee-Object -FilePath build.log | Tee-Object -FilePath build/preview/test-cpu-vertices.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build.log; exit $testExitCode }

# Fixtures come from Test-Preview.ps1; this is bounded path correctness, not the formal performance matrix.
$cases = @(
    @{ implementation = 'java'; path = 'cpu'; threads = 1 },
    @{ implementation = 'java'; path = 'gpu'; threads = 1 },
    @{ implementation = 'native'; path = 'cpu'; threads = 1 },
    @{ implementation = 'native'; path = 'cpu'; threads = 8 },
    @{ implementation = 'native'; path = 'cpu'; threads = 16 },
    @{ implementation = 'native'; path = 'cpu'; threads = 0 },
    @{ implementation = 'native'; path = 'gpu'; threads = 1 },
    @{ implementation = 'native'; path = 'gpu'; threads = 8 },
    @{ implementation = 'native'; path = 'gpu'; threads = 16 },
    @{ implementation = 'native'; path = 'gpu'; threads = 0 }
)
foreach ($scene in @('animated-ysm', 'figura-keyframes')) {
    foreach ($case in $cases) {
        $path = $case.path
        $implementation = $case.implementation
        $threads = $case.threads
        $caseId = "$scene-$implementation-$path-$threads"
        $config = Get-Content -LiteralPath "build/preview/fixtures/$scene.json" -Raw | ConvertFrom-Json
        $config.execution.path = $path
        $config.execution.implementation = $implementation
        $config.execution.threads = $threads
        $config.execution | Add-Member -NotePropertyName nativeLibrary -NotePropertyValue $NativeLibrary -Force
        $config.animation | Add-Member -NotePropertyName fixedTimeSeconds -NotePropertyValue 0.25 -Force
        $config.window.width = 640
        $config.window.height = 480
        $config.window.vsync = 0
        $config.settings.enabled = $false
        $config.benchmark.enabled = $false
        $config.recording.enabled = $true
        $config.recording.outputDirectory = 'cpu-path-runs'
        $configFile = "build/preview/fixtures/$caseId-path.json"
        $config | ConvertTo-Json -Depth 64 | Set-Content -LiteralPath $configFile -Encoding utf8
        $log = "build/preview/test-$caseId-path.log"
        & $java @jvm -jar build/preview/preview.jar --config $configFile --frames 60 2>&1 | Tee-Object -FilePath build.log | Tee-Object -FilePath $log
        $testExitCode = $LASTEXITCODE
        if ($testExitCode -ne 0) { Get-Content build.log; exit $testExitCode }
        $outputLine = Get-Content -LiteralPath $log | Where-Object { $_ -like 'Preview output: *' } | Select-Object -Last 1
        if (-not $outputLine) { throw "Missing output path: $scene/$path" }
        $run = $outputLine.Substring('Preview output: '.Length)
        $frames = @(Import-Csv -LiteralPath (Join-Path $run 'frames.csv'))
        if ($frames.Count -ne 60) { throw "Expected 60 frames: $scene/$path" }
        foreach ($frame in $frames) {
            if ($frame.implementation -ne $implementation -or $frame.path -ne $path -or
                ($threads -gt 0 -and [int]$frame.threads -ne $threads) -or [int]$frame.threads -lt 1) {
                throw "Incorrect execution metadata: $scene/$path"
            }
            if ([int]$frame.draw_calls -lt 1 -or $frame.bones_ms -eq 'NA' -or [double]$frame.animation_time -ne 0.25) {
                throw "Actual animated model submission is missing: $scene/$path"
            }
            $expectedNativeDraws = if ($implementation -eq 'native' -and $path -eq 'gpu') { [int]$frame.draw_calls } else { 0 }
            if ([int]$frame.native_draw_calls -ne $expectedNativeDraws) {
                throw "Native draw count does not match actual submission: $caseId"
            }
            if (($path -eq 'cpu' -and ($frame.vertices_ms -eq 'NA' -or [double]$frame.vertices_ms -lt 0)) -or
                ($path -eq 'gpu' -and $frame.vertices_ms -ne 'NA')) {
                throw "Incorrect CPU vertex timing applicability: $scene/$path"
            }
        }
        $summary = Join-Path $run "$path-summary.md"
        if (-not (Test-Path -LiteralPath $summary)) { throw "Missing path-specific summary: $scene/$path" }
        $audio = Get-Content -LiteralPath (Join-Path $run 'audio.json') -Raw | ConvertFrom-Json
        if (-not $audio.closed -or $audio.activeSources -ne 0) { throw "Model audio did not close: $scene/$path" }
    }
}
Write-Output 'CPU path PASS: shader parity including native 1/8/16, twenty 60-frame visible runs for YSM/Figura; explicit/automatic thread budgets, labels, timing, summaries and cleanup verified.'
exit 0
