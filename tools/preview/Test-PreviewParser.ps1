param(
    [string] $Jdk = 'D:\Program Files\Java\25',
    [string] $NativeLibrary = 'build/windows-x64-Release/bin/Release/omm_core.dll'
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Set-Location -LiteralPath $repoRoot
$java = Join-Path $Jdk 'bin/java.exe'
$NativeLibrary = (Resolve-Path -LiteralPath $NativeLibrary).Path
$dependencies = (Get-Content -LiteralPath build/preview/classpath.txt -Raw).Trim()
$classPath = (Join-Path $repoRoot 'build/preview/classes') + [IO.Path]::PathSeparator + $dependencies
$testClassPath = (Join-Path $repoRoot 'build/preview/test-classes') + [IO.Path]::PathSeparator + $classPath
& (Join-Path $Jdk 'bin/javac.exe') --release 25 -cp $classPath -d build/preview/test-classes tools/preview/tests/PreviewNativeAssetTest.java tools/preview/tests/YsmVisibilityDefaultsTest.java 2>&1 | Tee-Object -FilePath build.log | Tee-Object -FilePath build/preview/test-native-asset-compile.log
$buildExitCode = $LASTEXITCODE
if ($buildExitCode -ne 0) { Get-Content build.log; exit $buildExitCode }
foreach ($test in @('YsmVisibilityDefaultsTest', 'PreviewNativeAssetTest')) {
    & $java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -cp $testClassPath "top.aihmc.omm.preview.$test" build/preview/fixtures $NativeLibrary 2>&1 | Tee-Object -FilePath build.log | Tee-Object -FilePath "build/preview/test-parser-$test.log"
    $buildExitCode = $LASTEXITCODE
    if ($buildExitCode -ne 0) { Get-Content build.log; exit $buildExitCode }
}

# Real encrypted input, actual named clip, all three requested native parser budgets.
foreach ($threads in @(1, 8, 16)) {
    $config = Get-Content -LiteralPath build/preview/fixtures/encrypted-new.json -Raw | ConvertFrom-Json -AsHashtable
    $config.execution = @{ implementation = 'native'; path = 'gpu'; threads = $threads; parser = 'native'; parserThreads = $threads; nativeLibrary = $NativeLibrary }
    $config.animation = @{ enabled = $true; mode = 'clip'; clip = 'attacked'; fixedTimeSeconds = 0.125 }
    $config.settings = @{ enabled = $false }
    $config.window.width = 640
    $config.window.height = 480
    $config.window.vsync = 0
    $config.recording.outputDirectory = 'parser-runs'
    $configFile = "build/preview/fixtures/native-parser-$threads.json"
    $config | ConvertTo-Json -Depth 64 | Set-Content -LiteralPath $configFile -Encoding utf8
    $log = "build/preview/test-parser-window-$threads.log"
    & $java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -jar build/preview/preview.jar --config $configFile --frames 30 2>&1 | Tee-Object -FilePath build.log | Tee-Object -FilePath $log
    $buildExitCode = $LASTEXITCODE
    if ($buildExitCode -ne 0) { Get-Content build.log; exit $buildExitCode }
    $line = Get-Content -LiteralPath $log | Where-Object { $_ -like 'Preview output: *' } | Select-Object -Last 1
    if (-not $line) { throw 'Missing native parser window output' }
    $run = $line.Substring('Preview output: '.Length)
    $parse = Get-Content -LiteralPath (Join-Path $run 'parse.json') -Raw | ConvertFrom-Json
    if ($parse.implementation -ne 'native' -or $parse.threads -ne $threads -or $parse.nativeParseMs -le 0 -or $parse.loadMs -lt $parse.nativeParseMs) {
        throw 'Native parser timing/budget evidence differs'
    }
    $frames = @(Import-Csv -LiteralPath (Join-Path $run 'frames.csv'))
    if ($frames.Count -ne 30) { throw 'Expected thirty real-window frames' }
    foreach ($frame in $frames) {
        if ([int]$frame.submitted -ne 1 -or [int]$frame.draw_calls -lt 1 -or [int]$frame.native_draw_calls -ne [int]$frame.draw_calls -or [double]$frame.animation_time -ne 0.125) {
            throw 'Encrypted native parser model did not reach animated drawing'
        }
    }
}
Write-Output 'Native parser PASS: ten inputs at 1/8/16, geometry/pixels/visibility/resources/poses and three real encrypted-model windows.'
exit 0
