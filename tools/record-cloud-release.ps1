param(
    [Parameter(Mandatory = $true)]
    [string]$ClientArtifact,
    [Parameter(Mandatory = $true)]
    [string]$BackendArtifact,
    [string]$Output = 'build/reports/cloud/b8-release.json'
)

$ErrorActionPreference = 'Stop'
$clientRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $clientRoot 'omnm-cloud'

function Get-Checkpoint([string]$repository) {
    $checkpoint = & git -C $repository rev-parse HEAD
    if ($LASTEXITCODE -ne 0) {
        throw "Cannot read repository checkpoint: $repository"
    }
    $changes = & git -C $repository diff --name-only HEAD
    if ($LASTEXITCODE -ne 0) {
        throw "Cannot inspect repository changes: $repository"
    }
    $unexpected = @($changes | Where-Object { $_ -ne 'build.log' -and $_ -ne '' })
    if ($unexpected.Count -ne 0) {
        throw "Commit the milestone before recording its release: $($unexpected -join ', ')"
    }
    return $checkpoint.Trim()
}

function Get-Artifact([string]$path) {
    $resolved = (Resolve-Path -LiteralPath $path).Path
    $file = Get-Item -LiteralPath $resolved
    if ($file.PSIsContainer) {
        throw "Artifact must be a file: $resolved"
    }
    return [ordered]@{
        path = $resolved
        bytes = $file.Length
        sha256 = (Get-FileHash -LiteralPath $resolved -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}

$clientCommit = Get-Checkpoint $clientRoot
$backendCommit = Get-Checkpoint $backendRoot
$client = Get-Artifact $ClientArtifact
$backend = Get-Artifact $BackendArtifact
$archive = [IO.Compression.ZipFile]::OpenRead($client.path)
try {
    $entry = $archive.GetEntry('fabric.mod.json')
    if ($null -eq $entry) {
        throw 'Client artifact is not a Fabric mod JAR'
    }
    $reader = [IO.StreamReader]::new($entry.Open())
    try {
        $metadata = $reader.ReadToEnd() | ConvertFrom-Json
    } finally {
        $reader.Dispose()
    }
} finally {
    $archive.Dispose()
}
if ($metadata.version -match '-([0-9a-f]{7,40})$' -and -not $clientCommit.StartsWith($Matches[1])) {
    throw 'Client JAR embeds another commit; rebuild it after the milestone checkpoint'
}
$client.commit = $clientCommit
$client.modId = $metadata.id
$client.version = $metadata.version
$backend.commit = $backendCommit
$schemaPath = Join-Path $clientRoot 'protocol/omnimorph/cloud/v1/cloud.proto'
$schemaHash = (Get-FileHash -LiteralPath $schemaPath -Algorithm SHA256).Hash.ToLowerInvariant()
$manifest = [ordered]@{
    recordedUtc = [DateTime]::UtcNow.ToString('o')
    schemaSha256 = $schemaHash
    client = $client
    backend = $backend
    realGameValidation = 'SKIPPED/MANUAL_BY_USER'
    publicDeployment = 'SKIPPED/MANUAL_BY_USER'
}
$outputPath = [IO.Path]::GetFullPath($Output, $clientRoot)
[IO.Directory]::CreateDirectory((Split-Path -Parent $outputPath)) | Out-Null
$manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $outputPath -Encoding utf8
Write-Output "Recorded paired artifacts: $outputPath"
