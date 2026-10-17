$ErrorActionPreference = 'Stop'
$releaseRoot = $PSScriptRoot
$serverRelease = Join-Path $releaseRoot '../omnm-cloud/protocol'
$manifest = Get-Content -LiteralPath (Join-Path $releaseRoot 'release.sha256')
foreach ($line in $manifest) {
    $parts = $line -split '  ', 2
    $expected = $parts[0]
    $relative = $parts[1]
    foreach ($directory in @($releaseRoot, $serverRelease)) {
        $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $directory $relative)).Hash.ToLowerInvariant()
        if ($actual -ne $expected) {
            throw "Protocol release mismatch: $directory/$relative"
        }
    }
}
Write-Host 'PASS frozen protocol release: both repositories match the manifest'
exit 0
