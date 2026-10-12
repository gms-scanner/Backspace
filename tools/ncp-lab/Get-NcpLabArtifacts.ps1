param(
    [string]$ServerDirectory = (Join-Path $PSScriptRoot 'server'),
    [string]$PaperBuild = 'latest',
    [string]$NcpBuild = 'lastSuccessfulBuild'
)

$ErrorActionPreference = 'Stop'
$ServerDirectory = [IO.Path]::GetFullPath($ServerDirectory)
$PluginDirectory = Join-Path $ServerDirectory 'plugins'
New-Item -ItemType Directory -Force -Path $PluginDirectory | Out-Null

$headers = @{ 'User-Agent' = 'Backspace-NcpLab/1.0 (local reproducible Minecraft test lab)' }
$paperApi = 'https://fill.papermc.io/v3/projects/paper/versions/1.12.2/builds'
$ncpJob = 'https://ci.codemc.io/job/Updated-NoCheatPlus/job/Updated-NoCheatPlus'

Write-Host 'Resolving Paper 1.12.2 build metadata...'
$paperBuilds = Invoke-RestMethod -Uri $paperApi -Headers $headers
if ($PaperBuild -eq 'latest') {
    $stableBuild = $paperBuilds | Where-Object { $_.channel -eq 'STABLE' } | Sort-Object id -Descending | Select-Object -First 1
    if (-not $stableBuild) { throw 'Paper API returned no stable 1.12.2 build.' }
    $selectedPaperBuild = [int]$stableBuild.id
} else {
    $selectedPaperBuild = [int]$PaperBuild
}
if (-not ($paperBuilds.id -contains $selectedPaperBuild)) {
    throw "Paper build $selectedPaperBuild is not listed for Minecraft 1.12.2."
}
$paperMeta = $paperBuilds | Where-Object { [int]$_.id -eq $selectedPaperBuild } | Select-Object -First 1
$paperDownload = $paperMeta.downloads.'server:default'
$paperSha256 = $paperDownload.checksums.sha256
if (-not $paperDownload.name -or -not $paperSha256 -or -not $paperDownload.url) {
    throw 'Paper API did not provide an application jar name and SHA-256 checksum.'
}
$paperUri = $paperDownload.url
$paperPath = Join-Path $ServerDirectory 'server.jar'
Invoke-WebRequest -Uri $paperUri -Headers $headers -OutFile $paperPath -UseBasicParsing
$paperHash = (Get-FileHash -LiteralPath $paperPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($paperHash -ne $paperSha256.ToLowerInvariant()) {
    Remove-Item -LiteralPath $paperPath -Force
    throw "Paper checksum mismatch for build $selectedPaperBuild."
}

Write-Host 'Resolving Updated-NoCheatPlus build metadata...'
$ncpMeta = Invoke-RestMethod -Uri "$ncpJob/$NcpBuild/api/json?tree=number,artifacts[fileName,relativePath]" -Headers $headers
$ncpArtifacts = @($ncpMeta.artifacts | Where-Object { $_.fileName -match '(?i)nocheatplus.*\.jar$' })
if ($ncpArtifacts.Count -ne 1) {
    throw "Expected exactly one NoCheatPlus jar in $NcpBuild; found $($ncpArtifacts.Count). Inspect the Jenkins build artifacts manually."
}
$ncpArtifact = $ncpArtifacts[0]
$ncpUri = "$ncpJob/$($ncpMeta.number)/artifact/$($ncpArtifact.relativePath)"
$ncpPath = Join-Path $PluginDirectory 'NoCheatPlus.jar'
Invoke-WebRequest -Uri $ncpUri -Headers $headers -OutFile $ncpPath -UseBasicParsing

# ProtocolLib 4.4.0 is the upstream release that explicitly supports Bukkit 1.12.x.
$protocolLibPath = Join-Path $PluginDirectory 'ProtocolLib.jar'
Invoke-WebRequest -Uri 'https://dev.bukkit.org/projects/protocollib/files/2619793/download' -OutFile $protocolLibPath -UseBasicParsing
$protocolLibMd5 = (Get-FileHash -LiteralPath $protocolLibPath -Algorithm MD5).Hash.ToLowerInvariant()
if ($protocolLibMd5 -ne '96e66bf6bb1008fe706289e1cb72fbb0') {
    Remove-Item -LiteralPath $protocolLibPath -Force
    throw "ProtocolLib 4.4.0 checksum mismatch: $protocolLibMd5"
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($ncpPath)
try {
    $pluginEntry = $archive.GetEntry('plugin.yml')
    if (-not $pluginEntry) { throw 'Downloaded NCP jar has no plugin.yml.' }
    $reader = New-Object IO.StreamReader($pluginEntry.Open())
    try { $pluginYml = $reader.ReadToEnd() } finally { $reader.Dispose() }
    if ($pluginYml -notmatch '(?m)^name:\s*NoCheatPlus\s*$') { throw 'Downloaded jar is not identified as the NoCheatPlus plugin.' }
    $pluginVersion = [regex]::Match($pluginYml, '(?m)^version:\s*(.+)$').Groups[1].Value.Trim()
} finally { $archive.Dispose() }

@(
    "Paper Minecraft 1.12.2 build $selectedPaperBuild"
    "Paper SHA-256 $paperHash"
    "NoCheatPlus build $($ncpMeta.number), plugin version $pluginVersion"
    "NoCheatPlus artifact $($ncpArtifact.relativePath)"
    "ProtocolLib 4.4.0 MD5 $protocolLibMd5"
) | Set-Content -LiteralPath (Join-Path $ServerDirectory 'ARTIFACTS.txt') -Encoding UTF8

Write-Host "Installed Paper 1.12.2 build $selectedPaperBuild ($paperHash)"
Write-Host "Installed NoCheatPlus $pluginVersion from CI build $($ncpMeta.number)"
Write-Host 'Installed ProtocolLib 4.4.0 (MD5 verified)'
Write-Host "Artifacts directory: $ServerDirectory"
