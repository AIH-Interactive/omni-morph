param(
    [string] $Jdk = 'D:\Program Files\Java\25',
    [string] $GradleCache = (Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1'),
    [string] $MinecraftJar = '',
    [string] $MinecraftAssets = (Join-Path $env:USERPROFILE '.gradle/caches/fabric-loom/assets'),
    [string] $AssetIndex = '',
    [string[]] $SoundEvents = @('entity.squid.hurt', 'entity.squid.death', 'entity.squid.squirt')
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Set-Location -LiteralPath $repoRoot
$output = Join-Path $repoRoot 'build/preview'
$classes = Join-Path $output 'classes'
$lib = Join-Path $output 'lib'
New-Item -ItemType Directory -Force -Path $classes, $lib | Out-Null
# Read a local game resource only; no Minecraft class or process is started.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$assets = Join-Path $output 'resources'
New-Item -ItemType Directory -Force -Path $assets | Out-Null
$textureEntries = @(
    'assets/minecraft/textures/block/grass_block_top.png',
    'assets/minecraft/textures/environment/clouds.png',
    'assets/minecraft/textures/environment/celestial/sun.png',
    'assets/minecraft/textures/environment/celestial/moon/full_moon.png',
    'assets/minecraft/textures/entity/player/wide/steve.png',
    'assets/minecraft/textures/entity/player/slim/alex.png'
)
$particleTypes = @('cherry_leaves', 'firework', 'campfire_signal_smoke', 'bubble_column_up', 'dragon_breath', 'squid_ink')
$particleDefinitions = @($particleTypes | ForEach-Object { "assets/minecraft/particles/$_.json" })
$textureEntries += @(0..11 | ForEach-Object { "assets/minecraft/textures/particle/cherry_$_.png" })
$textureEntries += @(0..11 | ForEach-Object { "assets/minecraft/textures/particle/big_smoke_$_.png" })
$textureEntries += @(0..7 | ForEach-Object { "assets/minecraft/textures/particle/spark_$_.png" })
$textureEntries += @(0..7 | ForEach-Object { "assets/minecraft/textures/particle/generic_$_.png" })
$textureEntries += 'assets/minecraft/textures/particle/bubble.png'
$resourceEntries = $textureEntries + $particleDefinitions
$candidates = if ($MinecraftJar) {
    @((Get-Item -LiteralPath $MinecraftJar).FullName)
} else {
    @(Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.gradle/caches/fabric-loom') -Recurse -File -Filter '*26.3*.jar' |
        Where-Object Name -notmatch 'sources|javadoc' | Sort-Object FullName | ForEach-Object FullName)
}
$textureSource = $null
foreach ($candidate in $candidates) {
    $archive = [IO.Compression.ZipFile]::OpenRead($candidate)
    try {
        $entries = @($resourceEntries | ForEach-Object { $archive.GetEntry($_) })
        if ($entries.Count -eq $resourceEntries.Count -and @($entries | Where-Object { $null -eq $_ }).Count -eq 0) {
            foreach ($entry in $entries) {
                $target = Join-Path $assets $entry.FullName
                New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
            }
            $textureSource = $candidate
            break
        }
    } finally {
        $archive.Dispose()
    }
}
if (-not $textureSource) {
    throw 'Environment textures not found. Supply -MinecraftJar with an installed Minecraft 26.3 game JAR.'
}
$sourceMetadata = Join-Path $assets 'assets/omm_preview/texture-sources.json'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $sourceMetadata) | Out-Null
@{
    sourceJar = $textureSource
    sourceSha256 = (Get-FileHash -LiteralPath $textureSource -Algorithm SHA256).Hash.ToLowerInvariant()
    textures = @($textureEntries | ForEach-Object {
        @{entry = $_; sha256 = (Get-FileHash -LiteralPath (Join-Path $assets $_) -Algorithm SHA256).Hash.ToLowerInvariant()}
    })
    particleDefinitions = @($particleDefinitions | ForEach-Object {
        @{entry = $_; sha256 = (Get-FileHash -LiteralPath (Join-Path $assets $_) -Algorithm SHA256).Hash.ToLowerInvariant()}
    })
} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $sourceMetadata -Encoding utf8
# Minecraft audio is stored in the installed asset object store rather than the game JAR.
if (-not $AssetIndex) {
    $AssetIndex = (Get-ChildItem -LiteralPath (Join-Path $MinecraftAssets 'indexes') -Filter '*.json' |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}
$assetObjects = (Get-Content -LiteralPath $AssetIndex -Raw | ConvertFrom-Json -AsHashtable).objects
function Get-AssetObject([string] $name) {
    $assetObject = $assetObjects[$name]
    if (-not $assetObject) { throw "Missing installed Minecraft asset index entry: $name" }
    $assetPath = Join-Path $MinecraftAssets "objects/$($assetObject.hash.Substring(0, 2))/$($assetObject.hash)"
    if (-not (Test-Path -LiteralPath $assetPath)) { throw "Missing installed Minecraft audio object: $name" }
    if ((Get-FileHash -LiteralPath $assetPath -Algorithm SHA1).Hash.ToLowerInvariant() -ne $assetObject.hash) {
        throw "Minecraft audio object hash mismatch: $name"
    }
    return $assetPath
}
$soundCatalogPath = Get-AssetObject 'minecraft/sounds.json'
$soundCatalog = Get-Content -LiteralPath $soundCatalogPath -Raw | ConvertFrom-Json -AsHashtable
$selectedSounds = [ordered]@{}
$selectedAudio = [Collections.Generic.HashSet[string]]::new()
function Add-SoundEvent([string] $eventId) {
    $eventId = $eventId -replace '^minecraft:', ''
    if ($selectedSounds.Contains($eventId)) { return }
    $definition = $soundCatalog[$eventId]
    if (-not $definition) { throw "Missing installed Minecraft sound event: $eventId" }
    $selectedSounds[$eventId] = $definition
    foreach ($sound in $definition.sounds) {
        if ($sound -is [string]) {
            $soundName = $sound
        } else {
            $soundName = $sound.name
            if ($sound.type -eq 'event') { Add-SoundEvent $soundName; continue }
        }
        $soundName = $soundName -replace '^minecraft:', ''
        [void] $selectedAudio.Add("minecraft/sounds/$soundName.ogg")
    }
}
foreach ($soundEvent in $SoundEvents) { Add-SoundEvent $soundEvent }
$soundCatalogOutput = Join-Path $assets 'assets/minecraft/sounds.json'
$selectedSounds | ConvertTo-Json -Depth 32 | Set-Content -LiteralPath $soundCatalogOutput -Encoding utf8
$audioSources = @()
foreach ($soundName in ($selectedAudio | Sort-Object)) {
    $assetPath = Get-AssetObject $soundName
    $target = Join-Path $assets "assets/$soundName"
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    Copy-Item -LiteralPath $assetPath -Destination $target -Force
    $audioSources += @{entry = "assets/$soundName"; sha1 = $assetObjects[$soundName].hash;
        sha256 = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()}
}
@{
    assetIndex = (Get-Item -LiteralPath $AssetIndex).FullName
    assetIndexSha256 = (Get-FileHash -LiteralPath $AssetIndex -Algorithm SHA256).Hash.ToLowerInvariant()
    originalCatalogSha256 = (Get-FileHash -LiteralPath $soundCatalogPath -Algorithm SHA256).Hash.ToLowerInvariant()
    selectedEvents = $SoundEvents
    audio = $audioSources
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $assets 'assets/omm_preview/audio-sources.json') -Encoding utf8
Copy-Item -LiteralPath (Join-Path $assets 'assets') -Destination $classes -Recurse -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'resources/assets') -Destination $classes -Recurse -Force
$dependencies = @(
    @('org.lwjgl', 'lwjgl', '3.4.1', 'lwjgl-3.4.1.jar'),
    @('org.lwjgl', 'lwjgl', '3.4.1', 'lwjgl-3.4.1-natives-windows.jar'),
    @('org.lwjgl', 'lwjgl-glfw', '3.4.1', 'lwjgl-glfw-3.4.1.jar'),
    @('org.lwjgl', 'lwjgl-glfw', '3.4.1', 'lwjgl-glfw-3.4.1-natives-windows.jar'),
    @('org.lwjgl', 'lwjgl-opengl', '3.4.1', 'lwjgl-opengl-3.4.1.jar'),
    @('org.lwjgl', 'lwjgl-opengl', '3.4.1', 'lwjgl-opengl-3.4.1-natives-windows.jar'),
    @('org.lwjgl', 'lwjgl-stb', '3.4.1', 'lwjgl-stb-3.4.1.jar'),
    @('org.lwjgl', 'lwjgl-stb', '3.4.1', 'lwjgl-stb-3.4.1-natives-windows.jar'),
    @('org.lwjgl', 'lwjgl-openal', '3.4.1', 'lwjgl-openal-3.4.1.jar'),
    @('org.lwjgl', 'lwjgl-openal', '3.4.1', 'lwjgl-openal-3.4.1-natives-windows.jar'),
    @('org.gagravarr', 'vorbis-java-core', '0.8', 'vorbis-java-core-0.8.jar'),
    @('io.github.jaredmdobson', 'concentus', '1.0.2', 'concentus-1.0.2.jar'),
    @('com.google.code.gson', 'gson', '2.11.0', 'gson-2.11.0.jar'),
    @('org.joml', 'joml', '1.10.8', 'joml-1.10.8.jar'),
    @('org.jetbrains', 'annotations', '26.0.2', 'annotations-26.0.2.jar'),
    @('com.github.FiguraMC.luaj', 'luaj-core', '3.0.8-figura', 'luaj-core-3.0.8-figura.jar'),
    @('com.github.FiguraMC.luaj', 'luaj-jse', '3.0.8-figura', 'luaj-jse-3.0.8-figura.jar'),
    @('com.google.guava', 'guava', '33.6.0-jre', 'guava-33.6.0-jre.jar'),
    @('io.airlift', 'aircompressor-v3', '3.10', 'aircompressor-v3-3.10.jar'),
    @('com.twelvemonkeys.imageio', 'imageio-webp', '3.15.0', 'imageio-webp-3.15.0.jar'),
    @('com.twelvemonkeys.imageio', 'imageio-core', '3.15.0', 'imageio-core-3.15.0.jar'),
    @('com.twelvemonkeys.imageio', 'imageio-metadata', '3.15.0', 'imageio-metadata-3.15.0.jar'),
    @('com.twelvemonkeys.common', 'common-lang', '3.15.0', 'common-lang-3.15.0.jar'),
    @('com.twelvemonkeys.common', 'common-io', '3.15.0', 'common-io-3.15.0.jar'),
    @('com.twelvemonkeys.common', 'common-image', '3.15.0', 'common-image-3.15.0.jar'),
    @('com.github.ustc-zzzz', 'avif-imageio-native-reader', 'master-6af2f277a8-1', 'avif-imageio-native-reader-master-6af2f277a8-1.jar')
)
$jars = @()
foreach ($dependency in $dependencies) {
    $directory = Join-Path $GradleCache "$($dependency[0])/$($dependency[1])/$($dependency[2])"
    $jar = Get-ChildItem -LiteralPath $directory -Recurse -File | Where-Object Name -eq $dependency[3] | Select-Object -First 1
    if (-not $jar) {
        throw "Missing cached preview dependency: $($dependency[3])"
    }
    $target = Join-Path $lib $jar.Name
    Copy-Item -LiteralPath $jar.FullName -Destination $target -Force
    $jars += $target
}
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
$sources += @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'vendor/omp') -Recurse -Filter '*.java' | ForEach-Object FullName)
$sources += @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'vendor/omm-core/java') -Filter '*.java' -Recurse | ForEach-Object FullName)
$classPath = $jars -join [System.IO.Path]::PathSeparator
[System.IO.File]::WriteAllText((Join-Path $output 'classpath.txt'), $classPath, [System.Text.UTF8Encoding]::new($false))
& (Join-Path $Jdk 'bin/javac.exe') --release 25 -encoding UTF-8 -cp $classPath -d $classes @sources 2>&1 | Tee-Object -FilePath (Join-Path $output 'javac.log')
$buildExitCode = $LASTEXITCODE
if ($buildExitCode -ne 0) { Get-Content (Join-Path $output 'javac.log') -Tail 35; exit $buildExitCode }
$notices = Join-Path $classes 'META-INF/omm-preview'
New-Item -ItemType Directory -Force -Path $notices | Out-Null
Copy-Item (Join-Path $PSScriptRoot 'vendor/omp/NOTICE.md') $notices -Force
Copy-Item (Join-Path $PSScriptRoot 'vendor/omp/top/aihmc/omnimorph/avatar/ysm/crypto/LICENSE-Sparkle-Morpher.txt') $notices -Force
$manifest = "Manifest-Version: 1.0`nMain-Class: top.aihmc.omm.preview.PreviewMain`n"
$classPathLine = 'Class-Path: ' + (($jars | ForEach-Object { 'lib/' + [System.IO.Path]::GetFileName($_) }) -join ' ')
while ($classPathLine.Length -gt 70) {
    $manifest += $classPathLine.Substring(0, 70) + "`n"
    $classPathLine = ' ' + $classPathLine.Substring(70)
}
$manifest += $classPathLine + "`n`n"
$manifestFile = Join-Path $output 'MANIFEST.MF'
[System.IO.File]::WriteAllText($manifestFile, $manifest, [System.Text.UTF8Encoding]::new($false))
& (Join-Path $Jdk 'bin/jar.exe') --create --file (Join-Path $output 'preview.jar') --manifest $manifestFile -C $classes . -C (Join-Path $PSScriptRoot 'vendor/omp') LICENSE.md 2>&1 | Tee-Object -FilePath (Join-Path $output 'jar.log')
$buildExitCode = $LASTEXITCODE
if ($buildExitCode -ne 0) { Get-Content (Join-Path $output 'jar.log') -Tail 35; exit $buildExitCode }
Write-Host "Preview JAR: $(Join-Path $output 'preview.jar')"
