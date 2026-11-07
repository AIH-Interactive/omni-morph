param(
    [Parameter(Mandatory)] [string] $Config,
    [Parameter(Mandatory)] [string] $Name,
    [string] $OutputDirectory = 'build/preview/benchmark/render',
    [string] $ReportDirectory = '',
    [string] $Jdk = 'D:\Program Files\Java\25',
    [string] $NativeLibrary = 'build/windows-x64-Release/bin/Release/omm_core.dll',
    [int] $WarmupFrames = 120,
    [int] $SampleFrames = 600,
    [int] $Rounds = 5,
    [ValidateSet(1, 8, 16)] [int[]] $Threads = @(1),
    [switch] $SkipCompleted
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Set-Location -LiteralPath $repoRoot
$Config = (Resolve-Path -LiteralPath $Config).Path
$NativeLibrary = (Resolve-Path -LiteralPath $NativeLibrary).Path
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (-not $ReportDirectory) { $ReportDirectory = Join-Path $OutputDirectory 'reports' }
$ReportDirectory = [IO.Path]::GetFullPath($ReportDirectory)
$reportInput = Split-Path -Parent $OutputDirectory
$java = Join-Path $Jdk 'bin/java.exe'
$base = Get-Content -LiteralPath $Config -Raw | ConvertFrom-Json -AsHashtable
$caseDirectory = Join-Path $OutputDirectory 'cases'
New-Item -ItemType Directory -Force -Path $caseDirectory | Out-Null
if ($Name -notmatch '^[a-zA-Z0-9_-]+$') { throw 'Name must contain only letters, digits, underscores or hyphens' }

# Capture other build/test processes before starting our measured JVM; never stop them.
function Get-BenchmarkProcesses {
    return @(Get-Process | Where-Object {
        $_.ProcessName -match 'java|javac|msbuild|cmake|ninja|^cl$|ctest|vstest|gradle|cargo|rustc'
    } | Select-Object Id, ProcessName, CPU, WorkingSet64)
}

foreach ($path in @('cpu', 'gpu')) {
    foreach ($caseThreads in (@(0) + $Threads)) {
        $implementation = if ($caseThreads -eq 0) { 'java' } else { 'native' }
        $budget = if ($caseThreads -eq 0) { 1 } else { $caseThreads }
        $id = "$Name-$implementation-$path-$budget"
        $metadataFile = Join-Path $caseDirectory "$id.json"
        if (Test-Path -LiteralPath $metadataFile) {
            $existing = Get-Content -LiteralPath $metadataFile -Raw | ConvertFrom-Json
            if ($SkipCompleted -and $existing.status -eq 'complete') {
                Write-Output "Reusing completed case: $id"
                continue
            }
            throw "Case already exists: $metadataFile; choose another output/name"
        }
        $configData = $base | ConvertTo-Json -Depth 64 | ConvertFrom-Json -AsHashtable
        if (-not $configData.ContainsKey('scene')) { throw 'A model scene is required' }
        $resolvedOptions = @{}
        foreach ($model in $configData.scene.models) {
            $resolved = [IO.Path]::GetFullPath($model, (Split-Path -Parent $Config))
            if ($configData.scene.ContainsKey('modelOptions') -and $configData.scene.modelOptions.ContainsKey($model)) {
                $resolvedOptions[$resolved] = $configData.scene.modelOptions[$model]
            }
        }
        $configData.scene.models = @($configData.scene.models | ForEach-Object {
            if ($_ -eq 'builtin:cube') { throw 'Formal benchmark requires a real model' }
            [IO.Path]::GetFullPath($_, (Split-Path -Parent $Config))
        })
        $configData.scene.modelOptions = $resolvedOptions
        if (-not $configData.scene.ContainsKey('type')) { $configData.scene.type = 'single' }
        if (-not $configData.scene.ContainsKey('count')) { $configData.scene.count = 1 }
        if (-not $configData.scene.ContainsKey('modelOptions')) { $configData.scene.modelOptions = @{} }
        # Native currently parses YSM. Figura uses its original Java parser even with native rendering.
        foreach ($model in $configData.scene.models) {
            if (-not $configData.scene.modelOptions.ContainsKey($model)) { $configData.scene.modelOptions[$model] = @{} }
            $options = $configData.scene.modelOptions[$model]
            $mode = if ($options.ContainsKey('animation')) { $options.animation.mode } else { $configData.animation.mode }
            $options.parser = if ($implementation -eq 'java' -or $mode -eq 'figura') { 'java' } else { 'native' }
        }
        $parser = if ($implementation -eq 'java' -or $configData.animation.mode -eq 'figura') { 'java' } else { 'native' }
        $configData.execution = @{
            implementation = $implementation
            path = $path
            threads = $budget
            parser = $parser
            parserThreads = $budget
            nativeLibrary = $NativeLibrary
        }
        $configData.window = @{ width = 1280; height = 720; vsync = 0; title = "omm-core benchmark $id" }
        $configData.settings = @{ enabled = $false }
        $configData.interaction = @{ enabled = $false }
        $configData.recording = @{ enabled = $true; outputDirectory = (Join-Path $OutputDirectory 'runs') }
        $configData.benchmark = @{ enabled = $true; warmupFrames = $WarmupFrames; sampleFrames = $SampleFrames; rounds = $Rounds }
        $configFile = Join-Path $caseDirectory "$id-config.json"
        $configData | ConvertTo-Json -Depth 64 | Set-Content -LiteralPath $configFile -Encoding utf8
        $metadata = @{
            id = $id
            name = $Name
            implementation = $implementation
            path = $path
            threads = $budget
            status = 'running'
            config = $configFile
            startedUtc = [DateTime]::UtcNow.ToString('o')
            beforeProcesses = @(Get-BenchmarkProcesses)
        }
        $metadata | ConvertTo-Json -Depth 64 | Set-Content -LiteralPath $metadataFile -Encoding utf8
        $log = Join-Path $caseDirectory "$id.log"
        & $java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -jar build/preview/preview.jar --config $configFile 2>&1 | Tee-Object -FilePath build.log | Tee-Object -FilePath $log
        $buildExitCode = $LASTEXITCODE
        $metadata.afterProcesses = @(Get-BenchmarkProcesses)
        $metadata.finishedUtc = [DateTime]::UtcNow.ToString('o')
        $metadata.exitCode = $buildExitCode
        $metadata.status = if ($buildExitCode -eq 0) { 'validating' } else { 'failed' }
        $line = Get-Content -LiteralPath $log | Where-Object { $_ -like 'Preview output: *' } | Select-Object -Last 1
        if ($line) { $metadata.run = $line.Substring('Preview output: '.Length) }
        $metadata | ConvertTo-Json -Depth 64 | Set-Content -LiteralPath $metadataFile -Encoding utf8
        if ($buildExitCode -ne 0) { Get-Content build.log; exit $buildExitCode }
        if (-not $line) { throw "Missing measured output: $id" }
        $frames = @(Import-Csv -LiteralPath (Join-Path $metadata.run 'frames.csv'))
        if ($frames.Count -ne $WarmupFrames + $SampleFrames * $Rounds) { throw "Incomplete frame coverage: $id" }
        foreach ($frame in $frames) {
            if ([int]$frame.configured -ne [int]$configData.scene.count -or
                [int]$frame.visible -ne [int]$configData.scene.count -or
                [int]$frame.submitted -ne [int]$configData.scene.count) {
                throw "Models were outside the visible/submitted range: $id frame $($frame.frame_id)"
            }
        }
        $metadata.status = 'complete'
        $metadata | ConvertTo-Json -Depth 64 | Set-Content -LiteralPath $metadataFile -Encoding utf8
        Write-Output "Measured case: $id"
        & python tools/preview/Write-PreviewReport.py --input $reportInput --output $ReportDirectory --completed-only
        $reportExitCode = $LASTEXITCODE
        if ($reportExitCode -ne 0) { exit $reportExitCode }
    }
}
exit 0
