param([string] $Jdk = 'D:\Program Files\Java\25')

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Set-Location -LiteralPath $repoRoot
$java = Join-Path $Jdk 'bin/java.exe'
$jar = Join-Path $repoRoot 'build/preview/preview.jar'
$jvm = @('--enable-native-access=ALL-UNNAMED', '--sun-misc-unsafe-memory-access=allow')
$dependencies = (Get-Content -LiteralPath (Join-Path $repoRoot 'build/preview/classpath.txt') -Raw).Trim()
$compileClassPath = (Join-Path $repoRoot 'build/preview/classes') + [IO.Path]::PathSeparator + $dependencies
$testClassPath = (Join-Path $repoRoot 'build/preview/test-classes') + [IO.Path]::PathSeparator + $compileClassPath

& $java @jvm -jar $jar --help 2>&1 | Tee-Object -FilePath build/preview/test-help.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-help.log; exit $testExitCode }

$testSources = @(Get-ChildItem tools/preview/tests -Filter '*.java' | ForEach-Object FullName)
& (Join-Path $Jdk 'bin/javac.exe') --release 25 -cp $compileClassPath -d build/preview/test-classes @testSources 2>&1 | Tee-Object -FilePath build/preview/test-mesh-compile.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-mesh-compile.log; exit $testExitCode }
& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewMeshTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-mesh.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-mesh.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewModelAudioTest refs/YesSteveModel/src/verification/test/resources/audio-fixtures 2>&1 | Tee-Object -FilePath build/preview/test-model-audio.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-model-audio.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.FiguraLoaderTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-figura.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-figura.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewFiguraPartsTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-figura-parts.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-figura-parts.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewFiguraAnimationTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-figura-animation.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-figura-animation.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewVectorTest 2>&1 | Tee-Object -FilePath build/preview/test-vector.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-vector.log; exit $testExitCode }
& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewPlayerAnimationTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-player-animation.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-player-animation.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewKeybindTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-keybind.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-keybind.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewWorldClockTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-world-clock.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-world-clock.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewPartWorldMatrixTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-part-world.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-part-world.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewParticleTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-particles.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-particles.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewSoundTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-sound.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-sound.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewVanillaTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-vanilla.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-vanilla.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewMolangTest 2>&1 | Tee-Object -FilePath build/preview/test-molang.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-molang.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewAnimationTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-animation.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-animation.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewControllerTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-controller.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-controller.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewLuaTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-lua.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-lua.log; exit $testExitCode }
& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewLuaAnimationTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-lua-animation.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-lua-animation.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewWheelTest 2>&1 | Tee-Object -FilePath build/preview/test-wheel.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-wheel.log; exit $testExitCode }
& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewInteractionTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-interaction.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-interaction.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewYsmActionsTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-ysm-actions.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-ysm-actions.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewYsmSoundTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-ysm-sound.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-ysm-sound.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewYsmFunctionTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-ysm-functions.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-ysm-functions.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewControllerFunctionTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-controller-functions.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-controller-functions.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewYsmPivotTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-ysm-pivot.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-ysm-pivot.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewYsmInputTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-ysm-input.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-ysm-input.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewYsmParticleTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-ysm-particle.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-ysm-particle.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewDeclaredTransitionTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-declared-transition.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-declared-transition.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewYsmEquipmentTest 2>&1 | Tee-Object -FilePath build/preview/test-ysm-equipment.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-ysm-equipment.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewYsmSyncTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-ysm-sync.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-ysm-sync.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewBlockQueryTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-block-query.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-block-query.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewYsmBoneQueryTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-bone-query.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-bone-query.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewYsmSoundFunctionTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-ysm-sound-functions.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-ysm-sound-functions.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewYsmFilterTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-ysm-filters.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-ysm-filters.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PreviewSettingsTest build/preview/fixtures 2>&1 | Tee-Object -FilePath build/preview/test-settings.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-settings.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.YsmVisibilityDefaultsTest 2>&1 | Tee-Object -FilePath build/preview/test-visibility.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-visibility.log; exit $testExitCode }

& $java @jvm -cp $testClassPath top.aihmc.omm.preview.PackagedTexturesTest $jar 2>&1 | Tee-Object -FilePath build/preview/test-packaged-textures.log
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) { Get-Content build/preview/test-packaged-textures.log; exit $testExitCode }

& $java @jvm -jar $jar --config tools/preview/configs/demo.json --state running=123 2>&1 | Tee-Object -FilePath build/preview/test-invalid.log
$testExitCode = $LASTEXITCODE
$invalidLog = Get-Content build/preview/test-invalid.log -Raw
if ($testExitCode -eq 0 -or $invalidLog -notmatch 'states.running must be boolean') {
    throw 'Invalid simulated-state configuration was not rejected before window startup.'
}

function Invoke-WindowTest {
    param([string] $Config, [string] $Log, [string[]] $ExtraArguments)
    & $java @jvm -jar $jar --config $Config @ExtraArguments 2>&1 | Tee-Object -FilePath $Log | Out-Host
    $windowExitCode = $LASTEXITCODE
    if ($windowExitCode -ne 0) { Get-Content -LiteralPath $Log -Tail 30; exit $windowExitCode }
    $result = Get-Content -LiteralPath $Log | Where-Object { $_.StartsWith('Preview output: ') } | Select-Object -Last 1
    if (-not $result) { throw 'Visible-window run did not report its output directory.' }
    return $result.Substring('Preview output: '.Length)
}

$run = Invoke-WindowTest -Config tools/preview/configs/smoke.json -Log build/preview/test-window.log -ExtraArguments @('--state', 'running=false')
$rows = @(Import-Csv -LiteralPath (Join-Path $run 'frames.csv'))
if ($rows.Count -ne 70 -or @($rows | Where-Object phase -eq 'sample').Count -ne 60) {
    throw 'Visible-window sample/warmup counts differ from the configured run.'
}
for ($index = 0; $index -lt $rows.Count; $index++) {
    if ([int] $rows[$index].frame_id -ne $index -or $rows[$index].gpu_ms -eq 'NA' -or
        [double]::Parse($rows[$index].animation_time, [Globalization.CultureInfo]::InvariantCulture) -ne 0) {
        throw 'Frame IDs, GPU query results or simulated running=false state are invalid.'
    }
}
$resolved = Get-Content -LiteralPath (Join-Path $run 'resolved-config.json') -Raw | ConvertFrom-Json
if ($resolved.states.running -ne $false) { throw 'Resolved configuration lost the command-line override.' }
if (-not (Test-Path -LiteralPath (Join-Path $run 'gpu-summary.md'))) { throw 'Frame summary was not generated.' }

$disabled = Invoke-WindowTest -Config tools/preview/configs/demo.json -Log build/preview/test-record-disabled.log -ExtraArguments @('--frames', '30', '--no-record')
if (@(Import-Csv -LiteralPath (Join-Path $disabled 'frames.csv')).Count -ne 0) {
    throw 'Recording-disabled interactive run unexpectedly wrote frame rows.'
}
$static = Invoke-WindowTest -Config build/preview/fixtures/zip.json -Log build/preview/test-static-window.log -ExtraArguments @('--frames', '40')
$staticRows = @(Import-Csv -LiteralPath (Join-Path $static 'frames.csv'))
if ($staticRows.Count -ne 40 -or @($staticRows | Where-Object animation_time -ne '0.000000').Count -ne 0) {
    throw 'Static YSM window did not record its actual fixed bind pose.'
}
$figura = Invoke-WindowTest -Config build/preview/fixtures/figura-v5-zip.json -Log build/preview/test-figura-window.log -ExtraArguments @('--frames', '40')
$figuraRows = @(Import-Csv -LiteralPath (Join-Path $figura 'frames.csv'))
if ($figuraRows.Count -ne 40 -or @($figuraRows | Where-Object {
    $_.draw_calls -ne '2' -or $_.ground_draw_calls -ne '1' -or $_.sky_draw_calls -ne '1' -or
        $_.total_draw_calls -ne '4' -or $_.ground_gpu_ms -eq 'NA' -or $_.sky_gpu_ms -eq 'NA'
}).Count -ne 0) {
    throw 'Figura real-window draw counts differ from the two material ranges.'
}
$noGroundFile = Join-Path $repoRoot 'build/preview/fixtures/no-ground.json'
@{
    window = @{title = 'omm-core ground-disabled smoke'; width = 640; height = 480}
    ground = @{enabled = $false; texture = 'missing-texture-is-not-read.png'}
    sky = @{enabled = $false}
    recording = @{outputDirectory = 'runs'}
} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $noGroundFile -Encoding utf8
$noGround = Invoke-WindowTest -Config $noGroundFile -Log build/preview/test-no-ground.log -ExtraArguments @('--frames', '20')
$noGroundRows = @(Import-Csv -LiteralPath (Join-Path $noGround 'frames.csv'))
if ($noGroundRows.Count -ne 20 -or @($noGroundRows | Where-Object {
    $_.ground_draw_calls -ne '0' -or $_.ground_gpu_ms -ne 'NA' -or $_.sky_draw_calls -ne '0' -or
        $_.sky_gpu_ms -ne 'NA' -or $_.total_draw_calls -ne $_.draw_calls
}).Count -ne 0) {
    throw 'Ground-disabled run still submitted or measured ground work.'
}
$animated = Invoke-WindowTest -Config build/preview/fixtures/animated-ysm-fixed.json -Log build/preview/test-animation-window.log -ExtraArguments @('--frames', '40')
$animatedRows = @(Import-Csv -LiteralPath (Join-Path $animated 'frames.csv'))
if ($animatedRows.Count -ne 40 -or @($animatedRows | Where-Object {
    $_.bones_ms -eq 'NA' -or $_.animation_time -ne '1.000000' -or $_.gpu_ms -eq 'NA'
}).Count -ne 0) {
    throw 'Animated window did not measure the configured real animation/bone stages.'
}
$controlled = Invoke-WindowTest -Config build/preview/fixtures/controlled-ysm.json -Log build/preview/test-controller-window.log -ExtraArguments @('--frames', '40')
$controlledRows = @(Import-Csv -LiteralPath (Join-Path $controlled 'frames.csv'))
if ($controlledRows.Count -ne 40 -or @($controlledRows | Where-Object {
    $_.bones_ms -eq 'NA' -or $_.animation_time -ne '0.300000' -or $_.gpu_ms -eq 'NA'
}).Count -ne 0 -or (Get-Content (Join-Path $controlled 'model.txt') -Raw) -notmatch 'YSM controllers') {
    throw 'Controller window did not submit and record the requested actual controller pose.'
}
$scripted = Invoke-WindowTest -Config build/preview/fixtures/lua-animated-ysm.json -Log build/preview/test-lua-window.log -ExtraArguments @('--frames', '40')
$scriptedRows = @(Import-Csv -LiteralPath (Join-Path $scripted 'frames.csv'))
if ($scriptedRows.Count -ne 40 -or @($scriptedRows | Where-Object {
    $_.bones_ms -eq 'NA' -or $_.animation_time -ne '0.125000' -or $_.gpu_ms -eq 'NA'
}).Count -ne 0 -or (Get-Content (Join-Path $scripted 'model.txt') -Raw) -notmatch 'Figura LuaJ scripts=1') {
    throw 'Lua-driven window did not submit and record the requested actual script/model pose.'
}
$interactive = Invoke-WindowTest -Config build/preview/fixtures/interactive-ysm.json -Log build/preview/test-interactive-window.log -ExtraArguments @('--frames', '180')
if (@(Import-Csv -LiteralPath (Join-Path $interactive 'frames.csv')).Count -ne 180 -or
    -not (Test-Path -LiteralPath (Join-Path $interactive 'interaction.jsonl'))) {
    throw 'Interactive preview did not complete its visible-window run and input log lifecycle.'
}
$declared = Invoke-WindowTest -Config build/preview/fixtures/ysm-declared-actions.json -Log build/preview/test-declared-actions-window.log -ExtraArguments @('--frames', '120')
$declaredState = Get-Content (Join-Path $declared 'interaction-state.json') -Raw | ConvertFrom-Json
if (@(Import-Csv -LiteralPath (Join-Path $declared 'frames.csv')).Count -ne 120 -or
    $declaredState.wheelPage -ne 'YSM' -or -not $declaredState.actions.'extra_animation.wave') {
    throw 'Declared YSM window did not record its real schema/action host and final model state.'
}
$figuraScripted = Invoke-WindowTest -Config build/preview/fixtures/figura-scripted-parts.json -Log build/preview/test-figura-scripted-window.log -ExtraArguments @('--frames', '60')
if (@(Import-Csv -LiteralPath (Join-Path $figuraScripted 'frames.csv')).Count -ne 60 -or
    (Get-Content (Join-Path $figuraScripted 'model.txt') -Raw) -notmatch 'Figura scripted parts=') {
    throw 'Figura scripted window did not record its actual model-part host.'
}
$figuraKeyframes = Invoke-WindowTest -Config build/preview/fixtures/figura-keyframes.json -Log build/preview/test-figura-keyframes-window.log -ExtraArguments @('--frames', '90')
if (@(Import-Csv -LiteralPath (Join-Path $figuraKeyframes 'frames.csv')).Count -ne 90 -or
    (Get-Content (Join-Path $figuraKeyframes 'model.txt') -Raw) -notmatch 'clips=5') {
    throw 'Figura keyframe window did not record its real clips and Lua host.'
}
$playerSimulation = Invoke-WindowTest -Config build/preview/fixtures/figura-player-simulation.json -Log build/preview/test-player-window.log -ExtraArguments @('--frames', '60')
$playerSimulationState = Get-Content (Join-Path $playerSimulation 'interaction-state.json') -Raw | ConvertFrom-Json
if (@(Import-Csv -LiteralPath (Join-Path $playerSimulation 'frames.csv')).Count -ne 60 -or
    -not @($playerSimulationState.controls | Where-Object { $_.id -eq 'running' -and $_.value -eq $true }).Count) {
    throw 'Configured player-state window did not record its actual model setting.'
}
$keybinds = Invoke-WindowTest -Config build/preview/fixtures/figura-keybinds.json -Log build/preview/test-keybind-window.log -ExtraArguments @('--frames', '60')
$keyState = Get-Content (Join-Path $keybinds 'interaction-state.json') -Raw | ConvertFrom-Json
if (@(Import-Csv -LiteralPath (Join-Path $keybinds 'frames.csv')).Count -ne 60 -or
    @($keyState.keybinds).Count -ne 3 -or @($keyState.keybinds | Where-Object pressed).Count -ne 0) {
    throw 'Keybind real-window replay did not record final released binding states.'
}
$worldClock = Invoke-WindowTest -Config build/preview/fixtures/figura-world-clock.json -Log build/preview/test-world-clock-window.log -ExtraArguments @('--frames', '60')
$worldClockRows = @(Import-Csv -LiteralPath (Join-Path $worldClock 'frames.csv'))
if ($worldClockRows.Count -ne 60 -or @($worldClockRows | Where-Object { $_.animation_time -ne '0.125000' -or $_.bones_ms -eq 'NA' }).Count -ne 0) {
    throw 'World-clock window did not submit the configured real animation pose.'
}
$vanilla = Invoke-WindowTest -Config build/preview/fixtures/figura-vanilla.json -Log build/preview/test-vanilla-window.log -ExtraArguments @('--frames', '90')
$vanillaRows = @(Import-Csv -LiteralPath (Join-Path $vanilla 'frames.csv'))
if ($vanillaRows.Count -ne 90) { throw 'Vanilla visibility window did not record all frames.' }
for ($frame = 0; $frame -lt 90; $frame++) {
    $expectedDraws = if ($frame -ge 30 -and $frame -lt 60) { '2' } else { '3' }
    if ($vanillaRows[$frame].draw_calls -ne $expectedDraws) {
        throw "Vanilla player was not actually removed/restored from drawing at frame $frame."
    }
}
$partWorld = Invoke-WindowTest -Config build/preview/fixtures/figura-part-world.json -Log build/preview/test-part-world-window.log -ExtraArguments @('--frames', '60')
if (@(Import-Csv -LiteralPath (Join-Path $partWorld 'frames.csv')).Count -ne 60) {
    throw 'Part world matrix callback window did not record all frames.'
}
$particles = Invoke-WindowTest -Config build/preview/fixtures/figura-particles.json -Log build/preview/test-particles-window.log -ExtraArguments @('--frames', '60')
$particleRows = @(Import-Csv -LiteralPath (Join-Path $particles 'frames.csv'))
if ($particleRows.Count -ne 60 -or @($particleRows | Where-Object { $_.particles -ne '6' -or $_.particle_draw_calls -ne '1' -or $_.particle_gpu_ms -eq 'NA' }).Count -ne 0) {
    throw 'Six particle sprites were not actually submitted and GPU-timed on every frame.'
}
$sound = Invoke-WindowTest -Config build/preview/fixtures/figura-sound-zip.json -Log build/preview/test-sound-window.log -ExtraArguments @('--frames', '60')
$audio = Get-Content (Join-Path $sound 'audio.json') -Raw | ConvertFrom-Json
if (@(Import-Csv -LiteralPath (Join-Path $sound 'frames.csv')).Count -ne 60 -or
    $audio.playCalls -lt 1 -or $audio.decodedBufferLoads -ne 1 -or $audio.activeSources -ne 0 -or -not $audio.closed) {
    throw 'Avatar ZIP sound did not actually play or release its native sources/device on window exit.'
}
foreach ($settingsCase in @(@('figura', 'offset', 40), @('ysm', 'ysm.width_scale', 2.5))) {
    $engine = $settingsCase[0]
    $write = Invoke-WindowTest -Config "build/preview/fixtures/settings-$engine-write.json" -Log "build/preview/test-settings-$engine-write.log" -ExtraArguments @('--frames', '60')
    $read = Invoke-WindowTest -Config "build/preview/fixtures/settings-$engine-read.json" -Log "build/preview/test-settings-$engine-read.log" -ExtraArguments @('--frames', '60')
    $restored = Get-Content (Join-Path $read 'interaction-state.json') -Raw | ConvertFrom-Json
    $writeSettings = Get-Content (Join-Path $write 'settings.json') -Raw | ConvertFrom-Json
    $readSettings = Get-Content (Join-Path $read 'settings.json') -Raw | ConvertFrom-Json
    if (@($restored.controls | Where-Object { $_.id -eq $settingsCase[1] -and $_.value -eq $settingsCase[2] }).Count -ne 1 -or
        $writeSettings.writes -lt 1 -or $readSettings.writes -ne 0 -or $readSettings.mode -ne 'enabled' -or
        $writeSettings.file -ne $readSettings.file -or @(Import-Csv (Join-Path $read 'frames.csv')).Count -ne 60) {
        throw "$engine real-window restart did not restore the accepted persistent model setting."
    }
}
$release = Invoke-WindowTest -Config build/preview/fixtures/settings-release.json -Log build/preview/test-settings-release-window.log -ExtraArguments @('--frames', '60')
$releaseState = Get-Content (Join-Path $release 'interaction-state.json') -Raw | ConvertFrom-Json
$releaseSettings = Get-Content (Join-Path $release 'settings.json') -Raw | ConvertFrom-Json
$releaseAudio = Get-Content (Join-Path $release 'audio.json') -Raw | ConvertFrom-Json
if ($releaseSettings.writes -ne 1 -or $releaseAudio.playCalls -ne 1 -or -not $releaseAudio.closed -or
    $releaseAudio.activeSources -ne 0 -or @($releaseState.keybinds | Where-Object pressed).Count -ne 0 -or
    @($releaseState.controls | Where-Object { $_.id -eq 'offset' -and $_.value -eq 24 }).Count -ne 1) {
    throw 'Window-close input release did not save and play before final runtime cleanup/recording.'
}
$ysmSound = Invoke-WindowTest -Config build/preview/fixtures/ysm-sound-events-zip.json -Log build/preview/test-ysm-sound-window.log -ExtraArguments @('--frames', '60')
$ysmAudio = Get-Content (Join-Path $ysmSound 'audio.json') -Raw | ConvertFrom-Json
if (@(Import-Csv -LiteralPath (Join-Path $ysmSound 'frames.csv')).Count -ne 60 -or $ysmAudio.playCalls -ne 2 -or
    $ysmAudio.decodedBufferLoads -ne 1 -or $ysmAudio.activeSources -ne 0 -or -not $ysmAudio.closed) {
    throw 'YSM ZIP state/clip event sounds were not actually played and released without model Lua.'
}
$ysmFunctions = Invoke-WindowTest -Config build/preview/fixtures/ysm-functions-zip.json -Log build/preview/test-ysm-functions-window.log -ExtraArguments @('--frames', '60')
if (@(Import-Csv -LiteralPath (Join-Path $ysmFunctions 'frames.csv')).Count -ne 60) {
    throw 'YSM function stage window did not complete its recorded model draws.'
}
$ysmFunctionSounds = Invoke-WindowTest -Config build/preview/fixtures/ysm-function-sounds.json -Log build/preview/test-ysm-function-sounds-window.log -ExtraArguments @('--frames', '60')
$ysmFunctionAudio = Get-Content (Join-Path $ysmFunctionSounds 'audio.json') -Raw | ConvertFrom-Json
if (@(Import-Csv -LiteralPath (Join-Path $ysmFunctionSounds 'frames.csv')).Count -ne 60 -or
    $ysmFunctionAudio.playCalls -ne 2 -or $ysmFunctionAudio.activeSources -ne 0 -or -not $ysmFunctionAudio.closed) {
    throw 'YSM function stage/entry sounds did not actually play and release their native sources.'
}
$ysmFilters = Invoke-WindowTest -Config build/preview/fixtures/ysm-filters.json -Log build/preview/test-ysm-filters-window.log -ExtraArguments @('--frames', '60')
if (@(Import-Csv -LiteralPath (Join-Path $ysmFilters 'frames.csv')).Count -ne 60) {
    throw 'YSM filtered bone window did not complete its actual pose submissions.'
}
$ysmEquipmentSync = Invoke-WindowTest -Config build/preview/fixtures/ysm-equipment-sync.json -Log build/preview/test-ysm-equipment-sync-window.log -ExtraArguments @('--frames', '60')
if (@(Import-Csv -LiteralPath (Join-Path $ysmEquipmentSync 'frames.csv')).Count -ne 60) {
    throw 'Configured equipment/local sync window did not submit all actual model frames.'
}
$controllerFunctions = Invoke-WindowTest -Config build/preview/fixtures/ysm-controller-functions.json -Log build/preview/test-controller-functions-window.log -ExtraArguments @('--frames', '60')
$controllerFunctionAudio = Get-Content (Join-Path $controllerFunctions 'audio.json') -Raw | ConvertFrom-Json
if (@(Import-Csv -LiteralPath (Join-Path $controllerFunctions 'frames.csv')).Count -ne 60 -or
    $controllerFunctionAudio.playCalls -ne 1 -or $controllerFunctionAudio.activeSources -ne 0 -or -not $controllerFunctionAudio.closed) {
    throw 'Controller function window did not execute its delayed timeline sound and release its native source.'
}
$blockQuery = Invoke-WindowTest -Config build/preview/fixtures/ysm-block-query.json -Log build/preview/test-block-query-window.log -ExtraArguments @('--frames', '60')
if (@(Import-Csv -LiteralPath (Join-Path $blockQuery 'frames.csv')).Count -ne 60) {
    throw 'Relative block query window did not submit its actual controller-driven model frames.'
}
$boneQuery = Invoke-WindowTest -Config build/preview/fixtures/ysm-bone-queries.json -Log build/preview/test-bone-query-window.log -ExtraArguments @('--frames', '60')
if (@(Import-Csv -LiteralPath (Join-Path $boneQuery 'frames.csv')).Count -ne 60) {
    throw 'Bone query window did not submit its actual queried pose frames.'
}
$ysmParticles = Invoke-WindowTest -Config build/preview/fixtures/ysm-particles.json -Log build/preview/test-ysm-particle-window.log -ExtraArguments @('--frames', '60')
$ysmParticleFrames = @(Import-Csv -LiteralPath (Join-Path $ysmParticles 'frames.csv'))
if ($ysmParticleFrames.Count -ne 60 -or @($ysmParticleFrames | Where-Object { [int]$_.particles -gt 0 -and [int]$_.particle_draw_calls -gt 0 }).Count -ne 60) {
    throw 'No-Lua YSM particle window did not submit actual particle draws in every frame.'
}
$ysmInput = Invoke-WindowTest -Config build/preview/fixtures/ysm-input.json -Log build/preview/test-ysm-input-window.log -ExtraArguments @('--frames', '60')
$ysmInputEvents = @(Get-Content -LiteralPath (Join-Path $ysmInput 'interaction.jsonl') | ForEach-Object { $_ | ConvertFrom-Json })
$ysmFinalInput = (Get-Content -LiteralPath (Join-Path $ysmInput 'interaction-state.json') -Raw | ConvertFrom-Json).ysmInput
if (@(Import-Csv -LiteralPath (Join-Path $ysmInput 'frames.csv')).Count -ne 60 -or
    @($ysmInputEvents | Where-Object { $_.ysmInput.keys -contains 75 }).Count -eq 0 -or
    @($ysmInputEvents | Where-Object { $_.ysmInput.mouse -contains 0 }).Count -eq 0 -or
    @($ysmInputEvents | Where-Object { $_.type -eq 'input-ready' -and -not $_.ysmInput.ready }).Count -eq 0 -or
    @($ysmFinalInput.keys).Count -ne 0 -or @($ysmFinalInput.mouse).Count -ne 0) {
    throw 'No-Lua YSM input window did not record actual held states, readiness and final cleanup.'
}
$ysmPivot = Invoke-WindowTest -Config build/preview/fixtures/ysm-pivot-query.json -Log build/preview/test-ysm-pivot-window.log -ExtraArguments @('--frames', '60')
if (@(Import-Csv -LiteralPath (Join-Path $ysmPivot 'frames.csv')).Count -ne 60) {
    throw 'Tracked YSM pivot window did not submit its actual model frames.'
}
$failureFolder = Join-Path $repoRoot 'build/preview/fixtures/expected-render-failure'
New-Item -ItemType Directory -Force -Path $failureFolder | Out-Null
foreach ($resource in @('avatar.json', 'blue.png', 'orange.png', 'main.bbmodel')) {
    Copy-Item -LiteralPath (Join-Path $repoRoot "build/preview/fixtures/figura-scripted-parts/$resource") -Destination $failureFolder -Force
}
"function events.render() error('EXPECTED_RENDER_FAILURE') end" | Set-Content -LiteralPath (Join-Path $failureFolder 'script.lua') -Encoding utf8
$failureConfig = Get-Content build/preview/fixtures/figura-scripted-parts.json -Raw | ConvertFrom-Json
$failureConfig.scene.models = @($failureFolder)
$failureConfig.scripts.controls = [pscustomobject]@{}
$failureConfig.interaction.enabled = $true
$failureConfig.interaction.actions = @()
$failureConfig.interaction.replayFile = ''
$failureConfig.vanilla.enabled = $false
$failureOutput = Join-Path $repoRoot 'build/preview/fixtures/runs/expected-render-failure'
$failureConfig.recording.outputDirectory = $failureOutput
$failureConfigPath = Join-Path $repoRoot 'build/preview/fixtures/expected-render-failure.json'
$failureConfig | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $failureConfigPath -Encoding utf8
& $java @jvm -jar $jar --config $failureConfigPath --frames 60 2>&1 | Tee-Object -FilePath build/preview/test-window-failure.log
$failureExitCode = $LASTEXITCODE
$failureLog = Get-Content build/preview/test-window-failure.log -Raw
if ($failureExitCode -ne 1 -or $failureLog -notmatch 'EXPECTED_RENDER_FAILURE' -or $failureLog -notmatch 'OpenGL:') {
    throw 'Real-window script failure did not exit with status 1 and its actual error.'
}
$failedRun = Get-ChildItem -LiteralPath $failureOutput -Directory | Sort-Object Name | Select-Object -Last 1
$failedAudio = Get-Content (Join-Path $failedRun.FullName 'audio.json') -Raw | ConvertFrom-Json
if (-not $failedAudio.closed -or $failedAudio.activeSources -ne 0 -or
    -not (Test-Path (Join-Path $failedRun.FullName 'interaction-state.json'))) {
    throw 'Failed preview exited before its model/interaction cleanup and final recording.'
}
Write-Output 'Preview PASS: real schema/action/animation/Lua/vector/player/keybind/clock/skin/world-matrix/particle/sound/settings/YSM-effects/functions/filters/equipment/sync/controller-commands/block-query/bone-query/wheel/replay checks, thirty-five successful visible runs plus one expected render-failure window; cleanup and exit codes verified.'
exit 0
