param(
    [string] $Jdk = 'D:\Program Files\Java\25',
    [string] $NativeLibrary = 'build/windows-x64-Release/bin/Release/omm_core.dll'
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Set-Location -LiteralPath $repoRoot
$java = Join-Path $Jdk 'bin/java.exe'
$NativeLibrary = (Resolve-Path -LiteralPath $NativeLibrary).Path
$root = Join-Path $repoRoot 'build/preview/fixtures'
$ysmFolder = Join-Path $root 'multi-ysm-clips'
New-Item -ItemType Directory -Force -Path $ysmFolder | Out-Null
foreach ($file in Get-ChildItem -LiteralPath (Join-Path $root 'animated-ysm') -File) {
    Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $ysmFolder $file.Name) -Force
}
$animationFile = Join-Path $ysmFolder 'main.animation.json'
$animations = Get-Content -LiteralPath $animationFile -Raw | ConvertFrom-Json -AsHashtable
$other = $animations.animations['preview.wave'] | ConvertTo-Json -Depth 64 | ConvertFrom-Json -AsHashtable
$bone = @($other.bones.Keys)[0]
$other.bones[$bone]['rotation'] = @(0, 0, 45)
$animations.animations['preview.other'] = $other
$offscreen = $animations.animations['preview.wave'] | ConvertTo-Json -Depth 64 | ConvertFrom-Json -AsHashtable
$offscreen.bones[$bone]['position'] = @(1600000, 0, 0)
$animations.animations['preview.offscreen'] = $offscreen
$animations | ConvertTo-Json -Depth 64 | Set-Content -LiteralPath $animationFile -Encoding utf8
$figura = Get-Content -LiteralPath (Join-Path $root 'figura-keyframes.json') -Raw | ConvertFrom-Json -AsHashtable
$cases = @(
    @{ implementation = 'java'; path = 'gpu'; threads = 1 },
    @{ implementation = 'java'; path = 'cpu'; threads = 1 },
    @{ implementation = 'native'; path = 'cpu'; threads = 8 },
    @{ implementation = 'native'; path = 'gpu'; threads = 8 }
)
$references = @{}
foreach ($sceneType in @('same-model', 'mixed')) {
    foreach ($case in $cases) {
        $counts = if ($case.implementation -eq 'java' -and $case.path -eq 'gpu') { @(1, 5, 20, 50, 100) } else { @(100) }
        foreach ($count in $counts) {
            $config = Get-Content -LiteralPath (Join-Path $root 'animated-ysm.json') -Raw | ConvertFrom-Json -AsHashtable
            $config.scene.type = $sceneType
            $config.scene.count = $count
            $config.scene.models = @($ysmFolder)
            $config.scene.clips = @('preview.wave', 'preview.other')
            $config.scene.timeOffsetStepSeconds = 0.01
            $config.scene.modelOptions = @{}
            if ($sceneType -eq 'mixed') {
                $figuraModel = $figura.scene.models[0]
                $config.scene.models += $figuraModel
                $config.scene.modelOptions[$figuraModel] = @{
                    animation = $figura.animation; scripts = $figura.scripts; states = $figura.states; clips = @('')
                }
            }
            $config.execution.implementation = $case.implementation
            $config.execution.path = $case.path
            $config.execution.threads = $case.threads
            $config.execution.nativeLibrary = $NativeLibrary
            $config.window.width = 640
            $config.window.height = 480
            $config.window.vsync = 0
            $config.camera.pitchDegrees = 25
            $config.animation.fixedTimeSeconds = 0.25
            $config.settings.enabled = $false
            $config.interaction.enabled = $false
            $config.recording.outputDirectory = 'multi-runs'
            $id = "$sceneType-$count-$($case.implementation)-$($case.path)"
            $configFile = Join-Path $root "multi-$id.json"
            $config | ConvertTo-Json -Depth 64 | Set-Content -LiteralPath $configFile -Encoding utf8
            $log = "build/preview/test-multi-$id.log"
            & $java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -jar build/preview/preview.jar --config $configFile --frames 12 2>&1 | Tee-Object -FilePath build.log | Tee-Object -FilePath $log
            $testExitCode = $LASTEXITCODE
            if ($testExitCode -ne 0) { Get-Content build.log; exit $testExitCode }
            $line = Get-Content -LiteralPath $log | Where-Object { $_ -like 'Preview output: *' } | Select-Object -Last 1
            if (-not $line) { throw "Missing run output: $id" }
            $run = $line.Substring('Preview output: '.Length)
            $rows = @(Import-Csv -LiteralPath (Join-Path $run 'frames.csv'))
            if ($rows.Count -ne 12) { throw "Incorrect frame count: $id" }
            foreach ($row in $rows) {
                if ([int]$row.configured -ne $count -or [int]$row.visible -ne $count -or [int]$row.submitted -ne $count) {
                    throw "Configured/actual visible/submitted count mismatch: $id"
                }
                if ($row.gpu_ms -eq 'NA' -or [long]$row.peak_rss_bytes -le 0 -or [double]$row.animation_time -ne 0.25) {
                    throw "Missing GPU, process memory or fixed-time evidence: $id"
                }
                $nativeDraws = if ($case.implementation -eq 'native' -and $case.path -eq 'gpu') { [int]$row.draw_calls } else { 0 }
                if ([int]$row.native_draw_calls -ne $nativeDraws) { throw "Native draw mismatch: $id" }
            }
            $scene = Get-Content -LiteralPath (Join-Path $run 'scene.json') -Raw | ConvertFrom-Json
            $expectedSources = if ($sceneType -eq 'same-model') { 1 } else { 2 }
            if ($scene.uniqueModelResources -ne $expectedSources -or $scene.geometrySets -ne $expectedSources) {
                throw "Immutable geometry was not shared per model: $id"
            }
            $expectedRuntimes = if ($case.implementation -eq 'native') { 1 } else { 0 }
            if ($scene.nativeRuntimes -ne $expectedRuntimes) { throw "Native CPU budget was duplicated per source: $id" }
            if (@($scene.placements | ForEach-Object stateIdentity | Sort-Object -Unique).Count -ne $count) {
                throw "Playback states are not independent instances: $id"
            }
            $palettes = Get-Content -LiteralPath (Join-Path $run 'instance-palettes.json') -Raw | ConvertFrom-Json -AsHashtable -NoEnumerate
            if ($palettes.Count -ne $count) { throw "Missing palette snapshots: $id" }
            if ($count -eq 100) {
                if ($case.implementation -eq 'java' -and $case.path -eq 'gpu') {
                    $references[$sceneType] = $palettes
                    $variants = @($palettes | ForEach-Object { $_.palette | ConvertTo-Json -Depth 20 -Compress } | Sort-Object -Unique)
                    if ($variants.Count -lt 2) { throw "Configured clips/offsets did not change actual poses: $id" }
                } else {
                    $expected = $references[$sceneType]
                    for ($instance = 0; $instance -lt $count; $instance++) {
                        for ($bone = 0; $bone -lt $expected[$instance].palette.Count; $bone++) {
                            for ($value = 0; $value -lt 16; $value++) {
                                if ([Math]::Abs($palettes[$instance].palette[$bone][$value] - $expected[$instance].palette[$bone][$value]) -gt 0.0005) {
                                    throw "CPU/GPU/Java/native palette differs: $id instance=$instance bone=$bone"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
# One deliberately culled correctness case; formal workload cases above must remain fully visible.
$config.scene.type = 'same-model'
$config.scene.count = 5
$config.scene.models = @($ysmFolder)
$config.scene.modelOptions = @{}
$config.scene.clips = @('preview.wave', 'preview.offscreen')
$config.execution.implementation = 'java'
$config.execution.path = 'gpu'
$config.execution.threads = 1
$configFile = Join-Path $root 'multi-culling-negative.json'
$config | ConvertTo-Json -Depth 64 | Set-Content -LiteralPath $configFile -Encoding utf8
& $java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -jar build/preview/preview.jar --config $configFile --frames 12 2>&1 | Tee-Object -FilePath build.log | Tee-Object -FilePath build/preview/test-multi-culling.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build.log; exit $testExitCode }
$line = Get-Content build/preview/test-multi-culling.log | Where-Object { $_ -like 'Preview output: *' } | Select-Object -Last 1
$run = $line.Substring('Preview output: '.Length)
foreach ($row in Import-Csv -LiteralPath (Join-Path $run 'frames.csv')) {
    if ([int]$row.configured -ne 5 -or [int]$row.visible -ne 3 -or [int]$row.submitted -ne 3) {
        throw 'Deliberately out-of-frustum poses were counted as visible/submitted models.'
    }
}
Write-Output 'Multi-model PASS: both scene types, 1/5/20/50/100, four CPU/GPU paths at 100, shared geometry/runtime, distinct clips/phases, palette parity, visibility/submission counts and peak RSS.'
exit 0
