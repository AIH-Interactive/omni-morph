param(
    [Parameter(Mandatory)][ValidateSet('windows-x86_64', 'linux-x86_64', 'linux-aarch64', 'macos-x86_64', 'macos-aarch64')]
    [string]$Platform
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$interfaceSpec = Get-Content -LiteralPath 'build/native-interface/spec/acceleration.hpp' -Raw
$checkoutSpec = Get-Content -LiteralPath 'native/include/acceleration.hpp' -Raw
if ($interfaceSpec.Replace("`r`n", "`n") -ne $checkoutSpec.Replace("`r`n", "`n")) {
    throw 'Downloaded native ABI specification differs from the current checkout.'
}
if (-not $env:GITHUB_SHA -or -not $env:GITHUB_RUN_ID) {
    throw 'This script requires the current GitHub Actions run identity.'
}
$commit = (& git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $commit -ne $env:GITHUB_SHA) {
    throw 'Native checkout must match the current workflow commit.'
}
$processArchitecture = [Runtime.InteropServices.RuntimeInformation]::ProcessArchitecture.ToString().ToLowerInvariant()
$expectedArchitecture = if ($Platform.EndsWith('aarch64')) { 'arm64' } else { 'x64' }
if ($processArchitecture -ne $expectedArchitecture) {
    throw "Wrong native runner process architecture: $processArchitecture for $Platform"
}
$nativeRoot = Join-Path $projectRoot "build/native/$Platform"
New-Item -ItemType Directory -Force -Path $nativeRoot | Out-Null

function Invoke-BuildStep {
    param([string]$Name, [string]$Executable, [string[]]$Arguments)
    $stepDirectory = Join-Path $nativeRoot $Name
    New-Item -ItemType Directory -Force -Path $stepDirectory | Out-Null
    $stepLog = Join-Path $stepDirectory 'build.log'
    # Tee-Object receives no records for a successful quiet tool such as jar --create.
    # Create its log first so the mandatory post-command inspection remains valid.
    [IO.File]::WriteAllText($stepLog, '', [Text.UTF8Encoding]::new($false))
    & $Executable @Arguments 2>&1 | Tee-Object -FilePath $stepLog
    $buildExitCode = $LASTEXITCODE
    Get-Content -LiteralPath $stepLog -Tail 10
    if ($buildExitCode -ne 0) { exit $buildExitCode }
}

if ($Platform.StartsWith('windows-')) {
    if (-not $IsWindows) { throw 'Windows native requires a Windows runner.' }
    Invoke-BuildStep 'windows' 'pwsh' @('-NoProfile', '-File', './tools/build-native-windows.ps1',
        '-JniHeadersDirectory', (Join-Path $projectRoot 'build/native-interface/headers'))
    $localRoot = Join-Path $projectRoot 'build/native/windows-x64-release'
    $localManifest = Get-Content -LiteralPath "$localRoot/manifest.json" -Raw | ConvertFrom-Json
    $entry = $localManifest.libraries[0]
    $library = Join-Path $localRoot 'bin/omnimorph_accel.dll'
    $dependencyLog = Join-Path $nativeRoot 'dependencies.txt'
    $entry.dependencies | Set-Content -LiteralPath $dependencyLog -Encoding utf8NoBOM
} else {
    if (($Platform.StartsWith('macos-') -and -not $IsMacOS) -or ($Platform.StartsWith('linux-') -and -not $IsLinux)) {
        throw "Wrong operating system runner for $Platform"
    }
    $configure = @('-S', '.', '-B', $nativeRoot, '-DCMAKE_BUILD_TYPE=Release',
        "-DOMNIMORPH_JNI_HEADERS=$(Join-Path $projectRoot 'build/native-interface/headers')")
    if ($IsMacOS) {
        $configure += '-DCMAKE_OSX_DEPLOYMENT_TARGET=13.0'
    }
    Invoke-BuildStep 'configure' 'cmake' $configure
    Invoke-BuildStep 'compile' 'cmake' @('--build', $nativeRoot, '--config', 'Release', '--parallel', '2')
    Invoke-BuildStep 'test' 'ctest' @('--test-dir', $nativeRoot, '--output-on-failure')
    $libraryName = if ($IsMacOS) { 'libomnimorph_accel.dylib' } else { 'libomnimorph_accel.so' }
    $library = Join-Path $nativeRoot "bin/$libraryName"
    $dependencyLog = Join-Path $nativeRoot 'dependencies.txt'
    if ($IsMacOS) {
        Invoke-BuildStep 'sign' 'codesign' @('--force', '--sign', '-', $library)
        Invoke-BuildStep 'signature' 'codesign' @('--verify', '--strict', '--verbose=2', $library)
        $architecture = & lipo -archs $library
        if ($LASTEXITCODE -ne 0 -or $architecture.Trim() -ne $(if ($Platform.EndsWith('aarch64')) { 'arm64' } else { 'x86_64' })) {
            throw 'Mach-O architecture does not match manifest platform.'
        }
        $dependencyOutput = & otool -L $library 2>&1 | Tee-Object -FilePath $dependencyLog
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect Mach-O dependencies.' }
        $dependencies = @($dependencyOutput | Select-Object -Skip 1 | ForEach-Object { ($_ -split '\s+')[1] } |
            Where-Object { $_ -notmatch '(^|/)libomnimorph_accel\.dylib$' })
        foreach ($dependency in $dependencies) {
            if ($dependency -notmatch '^(/usr/lib/|/System/Library/)') { throw "Non-system Mach-O dependency: $dependency" }
        }
        $loadCommands = & otool -l $library 2>&1 | Tee-Object -FilePath (Join-Path $nativeRoot 'deployment.txt')
        if ($LASTEXITCODE -ne 0 -or ($loadCommands -join "`n") -notmatch '(minos|version)\s+13\.0') {
            throw 'Mach-O deployment target was not verified as 13.0.'
        }
        $minimumRuntime = 'macOS 13.0; system libc++; ad-hoc signed, not notarized'
    } else {
        $elfHeader = & readelf -h $library 2>&1
        $expectedMachine = if ($Platform.EndsWith('aarch64')) { 'AArch64' } else { 'Advanced Micro Devices X86-64' }
        if ($LASTEXITCODE -ne 0 -or ($elfHeader -join "`n") -notmatch $expectedMachine) {
            throw "ELF architecture does not match $Platform."
        }
        $dependencyOutput = & ldd $library 2>&1 | Tee-Object -FilePath $dependencyLog
        if ($LASTEXITCODE -ne 0 -or ($dependencyOutput -join "`n") -match 'not found') { throw 'Unresolved ELF dependency.' }
        $dynamic = & readelf -d $library 2>&1
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect ELF NEEDED entries.' }
        $dependencies = @([regex]::Matches(($dynamic -join "`n"), 'Shared library: \[([^\]]+)\]') | ForEach-Object { $_.Groups[1].Value })
        foreach ($dependency in $dependencies) {
            if ($dependency -notin @('libm.so.6', 'libc.so.6', 'ld-linux-x86-64.so.2', 'ld-linux-aarch64.so.1', 'libstdc++.so.6', 'libgcc_s.so.1')) {
                throw "Unexpected ELF dependency: $dependency"
            }
        }
        $versions = & readelf --version-info $library 2>&1 | Tee-Object -FilePath (Join-Path $nativeRoot 'symbol-versions.txt')
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect glibc symbol versions.' }
        $glibc = @([regex]::Matches(($versions -join "`n"), 'GLIBC_([0-9]+\.[0-9]+)') |
            ForEach-Object { [version]$_.Groups[1].Value } | Sort-Object -Unique)
        if ($glibc.Count -eq 0) { throw 'No measured glibc requirement found.' }
        $cxxVersions = @([regex]::Matches(($versions -join "`n"), 'GLIBCXX_([0-9]+\.[0-9]+(?:\.[0-9]+)?)') |
            ForEach-Object { [version]$_.Groups[1].Value } | Sort-Object -Unique)
        if ($cxxVersions.Count -eq 0) { throw 'No measured libstdc++ requirement found.' }
        $abiVersions = @([regex]::Matches(($versions -join "`n"), 'CXXABI_([0-9]+\.[0-9]+(?:\.[0-9]+)?)') |
            ForEach-Object { [version]$_.Groups[1].Value } | Sort-Object -Unique)
        if ($abiVersions.Count -eq 0) { throw 'No measured CXXABI requirement found.' }
        $minimumRuntime = "$Platform; glibc >= $($glibc[-1]); system libstdc++ GLIBCXX >= $($cxxVersions[-1]), CXXABI >= $($abiVersions[-1]); system libgcc_s"
    }
    $compiler = (& c++ --version | Select-Object -First 1)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot identify native compiler.' }
    $entry = [pscustomobject]@{
        platform = $Platform
        architecture = if ($Platform.EndsWith('aarch64')) { 'aarch64' } else { 'x86_64' }
        path = "natives/$Platform/$libraryName"
        abi = 1
        sourceCommit = $commit
        sourceTreeSha256 = (Get-FileHash -LiteralPath native/include/acceleration.hpp -Algorithm SHA256).Hash.ToLowerInvariant()
        sourceDirty = $false
        toolchain = "$compiler; C++17; no fast-math; fp-contract=off"
        minimumRuntime = $minimumRuntime
        sha256 = (Get-FileHash -LiteralPath $library -Algorithm SHA256).Hash.ToLowerInvariant()
        dependencies = $dependencies
    }
}
if (& git status --porcelain -- native CMakeLists.txt CMakePresets.json) {
    throw 'CI native sources must be clean.'
}
$sourceLines = foreach ($sourceFile in (& git ls-files -- native CMakeLists.txt CMakePresets.json)) {
    $sourceFile + ':' + (Get-FileHash -LiteralPath $sourceFile -Algorithm SHA256).Hash.ToLowerInvariant()
}
$sourceBytes = [Text.Encoding]::UTF8.GetBytes(($sourceLines -join "`n"))
$entry.sourceTreeSha256 = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($sourceBytes)).ToLowerInvariant()
$entry | Add-Member -NotePropertyName runId -NotePropertyValue $env:GITHUB_RUN_ID
$entry | Add-Member -NotePropertyName runAttempt -NotePropertyValue $env:GITHUB_RUN_ATTEMPT
$artifact = Join-Path $projectRoot "build/native-artifact/$Platform"
$resourceDirectory = Join-Path $artifact "natives/$Platform"
New-Item -ItemType Directory -Force -Path $resourceDirectory | Out-Null
Copy-Item -LiteralPath $library -Destination $resourceDirectory
Copy-Item -LiteralPath native/NOTICE.txt -Destination (Join-Path $artifact 'natives/NOTICE.txt')
@{ abi = 1; buildKind = 'ci'; libraries = @($entry) } | ConvertTo-Json -Depth 8 |
    Set-Content -LiteralPath (Join-Path $artifact 'natives/manifest.json') -Encoding utf8NoBOM
Invoke-BuildStep 'package-smoke' 'jar' @('--create', '--file', (Join-Path $artifact 'platform-smoke.jar'), '-C', $artifact, 'natives')
$classpath = @('build/native-interface/classes', 'build/native-interface/lib/*', (Join-Path $artifact 'platform-smoke.jar')) -join [IO.Path]::PathSeparator
Invoke-BuildStep 'jni-smoke' 'java' @('--enable-native-access=ALL-UNNAMED', '-Xcheck:jni', '-cp', $classpath,
    'top.aihmc.omnimorph.render.acceleration.NativePlatformSmokeTest')
Copy-Item -LiteralPath $dependencyLog -Destination $artifact -ErrorAction SilentlyContinue
Write-Output "Verified native platform artifact: $artifact"
