param(
    [Parameter(Mandatory)][string] $JarPath,
    [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string] $ExpectedCommit,
    [Parameter(Mandatory)][ValidatePattern('^[0-9]+$')][string] $ExpectedRun
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))

function Read-ArtifactText {
    param([IO.Compression.ZipArchive] $Archive, [string] $EntryPath)
    $entry = $Archive.GetEntry($EntryPath)
    if ($null -eq $entry) {
        throw "Missing artifact entry: $EntryPath"
    }
    $reader = [IO.StreamReader]::new($entry.Open())
    try {
        return $reader.ReadToEnd()
    } finally {
        $reader.Dispose()
    }
}

function Assert-ArtifactHash {
    param([IO.Compression.ZipArchive] $Archive, [string] $EntryPath, [string] $ExpectedHash)
    if ($ExpectedHash -cnotmatch '^[0-9a-f]{64}$') {
        throw "Invalid artifact hash: $EntryPath"
    }
    $entry = $Archive.GetEntry($EntryPath)
    if ($null -eq $entry) {
        throw "Missing hashed artifact entry: $EntryPath"
    }
    $stream = $entry.Open()
    try {
        $actual = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)).ToLowerInvariant()
        if ($actual -cne $ExpectedHash) {
            throw "Artifact hash mismatch: $EntryPath"
        }
    } finally {
        $stream.Dispose()
    }
}

function Assert-ProjectDocument {
    param([IO.Compression.ZipArchive] $Archive, [string] $EntryPath, [string] $SourceName)
    $actual = (Read-ArtifactText $Archive $EntryPath).Replace("`r`n", "`n")
    $expected = [IO.File]::ReadAllText((Join-Path $projectRoot $SourceName)).Replace("`r`n", "`n")
    if ($actual -cne $expected) {
        throw "Project document differs from the checked-out source: $EntryPath"
    }
}

function Read-BuildProperty {
    param([string] $Name)
    $values = @([IO.File]::ReadAllLines((Join-Path $projectRoot 'gradle.properties')) | Where-Object {
        $_ -match ('^' + [regex]::Escape($Name) + '\s*=')
    })
    if ($values.Count -ne 1) {
        throw "Expected one build property: $Name"
    }
    return ($values[0] -split '=', 2)[1].Trim()
}

$platforms = @{
    'windows-x86_64' = 'omnimorph_accel.dll'
    'linux-x86_64' = 'libomnimorph_accel.so'
    'linux-aarch64' = 'libomnimorph_accel.so'
    'android-aarch64' = 'libomnimorph_accel.so'
    'macos-x86_64' = 'libomnimorph_accel.dylib'
    'macos-aarch64' = 'libomnimorph_accel.dylib'
}
$resolvedJar = (Resolve-Path -LiteralPath $JarPath).Path
$archive = [IO.Compression.ZipFile]::OpenRead($resolvedJar)
try {
    $names = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($entry in $archive.Entries) {
        if (-not $names.Add($entry.FullName)) {
            throw "Duplicate JAR entry: $($entry.FullName)"
        }
    }
    Assert-ProjectDocument $archive 'licenses/OmniMorph-PolyForm-Noncommercial-1.0.0.txt' 'LICENSE.md'
    Assert-ProjectDocument $archive 'credits/OmniMorph.txt' 'CREDITS'
    foreach ($required in @(
        'natives/NOTICE.txt', 'licenses/YSM-Sparkle-Morpher.txt',
        'top/aihmc/omnimorph/render/acceleration/NativeBindings.class',
        'top/aihmc/omnimorph/render/acceleration/GpuRigidData.class',
        'top/aihmc/omnimorph/model/ysm/YsmRenderer.class'
    )) {
        if (-not $names.Contains($required)) {
            throw "Missing required JAR entry: $required"
        }
    }
    $manifest = Read-ArtifactText $archive 'natives/manifest.json' | ConvertFrom-Json
    if ($manifest.abi -ne 2 -or $manifest.buildKind -cne 'ci' -or
            $manifest.sourceCommit -cne $ExpectedCommit -or [string] $manifest.runId -cne $ExpectedRun) {
        throw 'Final native manifest ABI/build kind/commit/run mismatch.'
    }
    $seenPlatforms = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $seenPaths = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($library in $manifest.libraries) {
        if (-not $platforms.ContainsKey($library.platform) -or -not $seenPlatforms.Add($library.platform) -or
                $library.abi -ne 2 -or $library.sourceCommit -cne $ExpectedCommit -or
                [string] $library.runId -cne $ExpectedRun -or $library.runAttempt -ne $manifest.runAttempt -or
                $library.sourceDirty -isnot [bool] -or $library.sourceDirty) {
            throw 'Native platform set or source identity mismatch.'
        }
        $expectedPath = "natives/$($library.platform)/$($platforms[$library.platform])"
        $expectedArchitecture = if ($library.platform.EndsWith('aarch64')) { 'aarch64' } else { 'x86_64' }
        if ($library.path -cne $expectedPath -or $library.architecture -cne $expectedArchitecture -or
                $library.sourceTreeSha256 -cnotmatch '^[0-9a-f]{64}$' -or
                [string]::IsNullOrWhiteSpace($library.toolchain) -or
                [string]::IsNullOrWhiteSpace($library.minimumRuntime)) {
            throw "Invalid native metadata: $($library.platform)"
        }
        [void] $seenPaths.Add($expectedPath)
        Assert-ArtifactHash $archive $library.path $library.sha256
        if ($library.platform -ceq 'android-aarch64') {
            if ($library.licenses.Count -lt 1) {
                throw 'Missing Android runtime notices.'
            }
            foreach ($license in $library.licenses) {
                if (-not $license.path.StartsWith('natives/android-aarch64/licenses/', [StringComparison]::Ordinal) -or
                        $license.path.Contains('..') -or $license.path.Contains([char] 92)) {
                    throw 'Unsafe Android notice path.'
                }
                Assert-ArtifactHash $archive $license.path $license.sha256
            }
        }
    }
    if (-not $seenPlatforms.SetEquals([string[]] $platforms.Keys)) {
        throw 'The final JAR must contain exactly six native platforms.'
    }
    $nativePaths = @($archive.Entries | Where-Object { $_.FullName -cmatch '^natives/[^/]+/[^/]+\.(dll|so|dylib)$' } |
        ForEach-Object { $_.FullName })
    if (-not $seenPaths.SetEquals([string[]] $nativePaths)) {
        throw 'Unlisted native library in final JAR.'
    }
    $mod = Read-ArtifactText $archive 'fabric.mod.json' | ConvertFrom-Json
    $expectedVersion = "$(Read-BuildProperty 'mod_version')+$(Read-BuildProperty 'minecraft_version')-$($ExpectedCommit.Substring(0, 7))"
    if ($mod.id -cne 'omni-morph' -or $mod.version -cne $expectedVersion) {
        throw 'Final mod ID/version/source commit mismatch.'
    }
    foreach ($dependency in $mod.jars) {
        if (-not $names.Contains($dependency.file)) {
            throw "Missing declared nested dependency: $($dependency.file)"
        }
    }
    Write-Output 'PASS final artifact metadata: six platforms, source/run/ABI, native and Android notice hashes, project license/credits, mod version and nested entries'
    Write-Output 'Actual JNI and Android ELF execution checks are separate CI gates; real GPU/device/game remain MANUAL/PENDING.'
} finally {
    $archive.Dispose()
}
Write-Output "JAR SHA-256: $((Get-FileHash -LiteralPath $resolvedJar -Algorithm SHA256).Hash.ToLowerInvariant())"
