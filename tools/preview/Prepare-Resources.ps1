param(
    [string] $Config = (Join-Path $PSScriptRoot 'runtime.local.json'),
    [string] $MinecraftJar = ''
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $MinecraftJar) {
    $settings = Get-Content -LiteralPath $Config -Raw | ConvertFrom-Json
    $MinecraftJar = $settings.textureJar
    if (-not $MinecraftJar) {
        $versionDirectory = (Get-Item -LiteralPath $settings.fabricVersionDirectory).FullName
        $MinecraftJar = Join-Path $versionDirectory ((Split-Path $versionDirectory -Leaf) + '.jar')
    }
}
$source = (Get-Item -LiteralPath $MinecraftJar).FullName
$sourceHash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
$resourceRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot "build/preview/jar-host/resources/$sourceHash"))
New-Item -ItemType Directory -Force -Path $resourceRoot | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($source)
$resources = [Collections.Generic.List[object]]::new()
try {
    foreach ($entry in $archive.Entries) {
        if (-not $entry.FullName.StartsWith('assets/minecraft/textures/') -or $entry.Name -eq '') {
            continue
        }
        $target = [IO.Path]::GetFullPath((Join-Path $resourceRoot $entry.FullName))
        if (-not $target.StartsWith($resourceRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Resource entry escapes output directory: $($entry.FullName)"
        }
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
        $resources.Add([ordered]@{
            entry = $entry.FullName
            sha256 = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
        })
    }
} finally {
    $archive.Dispose()
}
if ($resources.Count -eq 0) {
    throw "No Minecraft textures found in $source"
}
[ordered]@{
    sourceJar = $source
    sourceSha256 = $sourceHash
    resources = $resources.ToArray()
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $resourceRoot 'texture-sources.json') -Encoding utf8
Set-Content -LiteralPath (Join-Path $repoRoot 'build/preview/jar-host/resource-root.txt') -Value $resourceRoot -Encoding utf8
Write-Host "Extracted $($resources.Count) texture resources to $resourceRoot, preserving assets/minecraft/textures paths."
