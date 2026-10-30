$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
if (-not $IsLinux -or [Runtime.InteropServices.RuntimeInformation]::ProcessArchitecture -ne 'X64') {
    throw 'The pinned Android NDK build runs on the Linux x64 CI host only.'
}
$commit = (& git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $commit -ne $env:GITHUB_SHA -or -not $env:GITHUB_RUN_ID) {
    throw 'Android checkout must match the current workflow commit/run.'
}
$interfaceSpec = Get-Content -LiteralPath 'build/native-interface/spec/acceleration.hpp' -Raw
$checkoutSpec = Get-Content -LiteralPath native/include/acceleration.hpp -Raw
if ($interfaceSpec.Replace("`r`n", "`n") -ne $checkoutSpec.Replace("`r`n", "`n")) {
    throw 'Downloaded Android ABI specification differs from checkout.'
}
$ndk = Join-Path $env:ANDROID_HOME 'ndk/28.2.13676358'
$revision = Get-Content -LiteralPath (Join-Path $ndk 'source.properties') -Raw
if ($revision -notmatch 'Pkg.Revision\s*=\s*28\.2\.13676358') { throw 'Unexpected Android NDK revision.' }
$nativeRoot = Join-Path $projectRoot 'build/native/android-aarch64'
New-Item -ItemType Directory -Force -Path $nativeRoot | Out-Null

function Invoke-AndroidBuildStep {
    param([string]$Name, [string]$Executable, [string[]]$Arguments)
    $directory = Join-Path $nativeRoot $Name
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    $stepLog = Join-Path $directory 'build.log'
    [IO.File]::WriteAllText($stepLog, '', [Text.UTF8Encoding]::new($false))
    & $Executable @Arguments 2>&1 | Tee-Object -FilePath $stepLog
    $buildExitCode = $LASTEXITCODE
    Get-Content -LiteralPath $stepLog -Tail 10
    if ($buildExitCode -ne 0) { exit $buildExitCode }
}

$configure = @('-S', '.', '-B', $nativeRoot, '-DCMAKE_BUILD_TYPE=Release',
    "-DCMAKE_TOOLCHAIN_FILE=$ndk/build/cmake/android.toolchain.cmake", '-DANDROID_ABI=arm64-v8a',
    '-DANDROID_PLATFORM=android-26', '-DANDROID_STL=c++_static',
    '-DCMAKE_CROSSCOMPILING_EMULATOR=/usr/bin/qemu-aarch64',
    "-DOMNIMORPH_JNI_HEADERS=$(Join-Path $projectRoot 'build/native-interface/headers')")
Invoke-AndroidBuildStep 'configure' 'cmake' $configure
Invoke-AndroidBuildStep 'compile' 'cmake' @('--build', $nativeRoot, '--parallel', '2')
Invoke-AndroidBuildStep 'bionic-core-test' 'ctest' @('--test-dir', $nativeRoot, '--output-on-failure', '--verbose')
$library = Join-Path $nativeRoot 'bin/libomnimorph_accel.so'
$llvm = Join-Path $ndk 'toolchains/llvm/prebuilt/linux-x86_64/bin'
Invoke-AndroidBuildStep 'strip-debug' "$llvm/llvm-strip" @('--strip-debug', $library)
Invoke-AndroidBuildStep 'elf-jni-verification' 'pwsh' @('-NoProfile', '-File', './tools/verify-android-native.ps1', '-LibraryPath', $library)
$dynamic = Get-Content -LiteralPath (Join-Path $projectRoot 'build/native/android-verification/dependencies.txt') -Raw
$dependencies = @([regex]::Matches($dynamic, 'Shared library: \[([^\]]+)\]') | ForEach-Object { $_.Groups[1].Value })

$artifact = Join-Path $projectRoot 'build/native-artifact/android-aarch64'
$resourceDirectory = Join-Path $artifact 'natives/android-aarch64'
$licenseDirectory = Join-Path $resourceDirectory 'licenses'
New-Item -ItemType Directory -Force -Path $licenseDirectory | Out-Null
Copy-Item -LiteralPath $library -Destination $resourceDirectory
Copy-Item -LiteralPath native/NOTICE.txt -Destination (Join-Path $artifact 'natives/NOTICE.txt')
$licenses = @()
foreach ($name in @('NOTICE', 'NOTICE.toolchain')) {
    $file = Join-Path $ndk $name
    if (Test-Path -LiteralPath $file) {
        Copy-Item -LiteralPath $file -Destination $licenseDirectory
        $licenses += @{ path = "natives/android-aarch64/licenses/$name"; sha256 = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() }
    }
}
if ($licenses.Count -eq 0) { throw 'NDK redistribution notices were not found.' }
if (& git status --porcelain -- native CMakeLists.txt CMakePresets.json) { throw 'Android native sources must be clean.' }
$sourceLines = foreach ($file in (& git ls-files -- native CMakeLists.txt CMakePresets.json)) {
    $file + ':' + (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
}
$sourceBytes = [Text.Encoding]::UTF8.GetBytes(($sourceLines -join "`n"))
$entry = [ordered]@{
    platform = 'android-aarch64'
    architecture = 'aarch64'
    androidAbi = 'arm64-v8a'
    path = 'natives/android-aarch64/libomnimorph_accel.so'
    abi = 2
    sourceCommit = $commit
    sourceTreeSha256 = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($sourceBytes)).ToLowerInvariant()
    sourceDirty = $false
    runId = $env:GITHUB_RUN_ID
    runAttempt = $env:GITHUB_RUN_ATTEMPT
    toolchain = 'Android NDK r28c 28.2.13676358; Clang; C++17; c++_static; fp-contract=off'
    minimumRuntime = 'Android API 26 arm64-v8a/Bionic; NEON; 16 KiB ELF alignment; compatible launcher Java runtime required'
    sha256 = (Get-FileHash -LiteralPath $library -Algorithm SHA256).Hash.ToLowerInvariant()
    dependencies = $dependencies
    licenses = $licenses
    validation = 'NDK compile; Bionic/NEON core tests under qemu-user; ELF/JNI-export verification; device JNI/game MANUAL/PENDING'
}
@{ abi = 2; buildKind = 'ci'; libraries = @($entry) } | ConvertTo-Json -Depth 8 |
    Set-Content -LiteralPath (Join-Path $artifact 'natives/manifest.json') -Encoding utf8NoBOM
Write-Output "Android native compiled and core-tested: $artifact; Android device JNI/game MANUAL/PENDING"
