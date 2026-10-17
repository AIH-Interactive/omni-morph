param(
    [string]$LibraryPath,
    [string]$JarPath,
    [string]$ExpectedCommit,
    [string]$ExpectedRun
)

$ErrorActionPreference = 'Stop'
if ([bool]$LibraryPath -eq [bool]$JarPath) {
    throw 'Select exactly one Android library or final JAR.'
}
$projectRoot = Split-Path -Parent $PSScriptRoot
$ndk = Join-Path $env:ANDROID_HOME 'ndk/28.2.13676358'
$llvm = Join-Path $ndk 'toolchains/llvm/prebuilt/linux-x86_64/bin'
$reportDirectory = Join-Path $projectRoot 'build/native/android-verification'
New-Item -ItemType Directory -Force -Path $reportDirectory | Out-Null
if ($JarPath) {
    $jar = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $JarPath))
    try {
        $reader = [IO.StreamReader]::new($jar.GetEntry('natives/manifest.json').Open())
        $manifest = $reader.ReadToEnd() | ConvertFrom-Json
        $reader.Dispose()
        if ($manifest.abi -ne 1 -or $manifest.libraries.Count -ne 6 -or
            $manifest.sourceCommit -ne $ExpectedCommit -or $manifest.runId -ne $ExpectedRun) {
            throw 'Final Android JAR platform set/ABI/source/run mismatch.'
        }
        $entries = @($manifest.libraries | Where-Object platform -eq 'android-aarch64')
        if ($entries.Count -ne 1) { throw 'Final JAR must have exactly one Android platform.' }
        $entry = $entries[0]
        if ($entry.path -ne 'natives/android-aarch64/libomnimorph_accel.so') { throw 'Unexpected Android library path.' }
        $LibraryPath = Join-Path $reportDirectory 'libomnimorph_accel.so'
        $source = $jar.GetEntry($entry.path).Open()
        $destination = [IO.File]::Create($LibraryPath)
        try {
            $source.CopyTo($destination)
        } finally {
            $source.Dispose()
            $destination.Dispose()
        }
        if ((Get-FileHash -LiteralPath $LibraryPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256) {
            throw 'Final Android library hash mismatch.'
        }
        if (-not $entry.licenses -or $entry.licenses.Count -lt 1) { throw 'Final Android NDK notices missing.' }
        foreach ($license in $entry.licenses) {
            $stream = $jar.GetEntry($license.path).Open()
            try {
                $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)).ToLowerInvariant()
            } finally {
                $stream.Dispose()
            }
            if ($hash -ne $license.sha256) { throw 'Final Android license hash mismatch.' }
        }
    } finally {
        $jar.Dispose()
    }
}
$header = & "$llvm/llvm-readelf" --file-header $LibraryPath 2>&1 | Tee-Object -FilePath "$reportDirectory/elf-header.txt"
if ($LASTEXITCODE -ne 0 -or ($header -join "`n") -notmatch 'AArch64' -or ($header -join "`n") -notmatch 'DYN') {
    throw 'Android native must be an AArch64 ELF shared library.'
}
$dynamic = & "$llvm/llvm-readelf" --dynamic $LibraryPath 2>&1 | Tee-Object -FilePath "$reportDirectory/dependencies.txt"
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect Android dependencies.' }
$dependencies = @([regex]::Matches(($dynamic -join "`n"), 'Shared library: \[([^\]]+)\]') | ForEach-Object { $_.Groups[1].Value })
foreach ($dependency in $dependencies) {
    if ($dependency -notin @('libc.so', 'libm.so', 'libdl.so', 'liblog.so')) {
        throw "Android library has a non-system or glibc dependency: $dependency"
    }
}
$symbols = & "$llvm/llvm-nm" --dynamic --defined-only $LibraryPath 2>&1 | Tee-Object -FilePath "$reportDirectory/jni-exports.txt"
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect Android JNI exports.' }
foreach ($symbol in @('JNI_OnLoad',
    'Java_top_aihmc_omnimorph_render_acceleration_NativeBindings_handshake',
    'Java_top_aihmc_omnimorph_render_acceleration_NativeBindings_capabilities',
    'Java_top_aihmc_omnimorph_render_acceleration_NativeBindings_transform')) {
    if (($symbols -join "`n") -notmatch "(?m)\b$symbol\s*$") { throw "Missing Android JNI ABI export: $symbol" }
}
$headers = & "$llvm/llvm-readelf" --program-headers --wide $LibraryPath 2>&1 | Tee-Object -FilePath "$reportDirectory/load-alignment.txt"
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect Android load alignment.' }
$loads = @($headers | Where-Object { $_ -match '^\s*LOAD\s' })
if ($loads.Count -eq 0) { throw 'Android ELF has no loadable segments.' }
foreach ($load in $loads) {
    $alignment = ($load.Trim() -split '\s+')[-1]
    if ([Convert]::ToInt64($alignment.Replace('0x', ''), 16) -lt 16384) {
        throw 'Android ELF must retain NDK r28 16 KiB segment alignment.'
    }
}
$dependencies | Write-Output
Write-Output 'PASS Android arm64-v8a ELF/Bionic dependencies/JNI exports/16 KiB alignment; Android device JNI/game MANUAL/PENDING'
