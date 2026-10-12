param([string]$LogPath = (Join-Path $PSScriptRoot 'logs'))

$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath $LogPath -PathType Container) {
    $files = Get-ChildItem -LiteralPath $LogPath -Filter 'server-*.log' -File | Sort-Object LastWriteTime
} elseif (Test-Path -LiteralPath $LogPath -PathType Leaf) {
    $files = @(Get-Item -LiteralPath $LogPath)
} else {
    throw "No log found at '$LogPath'."
}

$rows = foreach ($file in $files) {
    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $file.FullName) {
        $lineNumber++
        if ($line -match '(?i)(?:NCP\s*>?\s*)?\[(MOVING_[A-Z_]+|PASSABLE|SURVIVALFLY)\].*?\bVL\s*=\s*([0-9]+(?:\.[0-9]+)?)(?:\(\+([0-9]+(?:\.[0-9]+)?)\))?.*?(?:\[([A-Za-z0-9_]{1,16})\])?') {
            [pscustomobject]@{
                LogFile = $file.Name
                Line = $lineNumber
                Timestamp = if ($line -match '^\[?([0-9]{2}:[0-9]{2}:[0-9]{2})') { $matches[1] } else { '' }
                Check = $matches[1]
                ViolationLevel = $matches[2]
                Delta = $matches[3]
                Player = $matches[4]
                Raw = $line
            }
        }
    }
}

$rows = @($rows)
if ($rows.Count -eq 0) {
    Write-Host 'No NCP movement-check lines matched. Raw logs remain available; check NCP logging/configuration.'
    exit 0
}
$csvPath = Join-Path $PSScriptRoot 'logs\ncp-movement-events.csv'
$rows | Export-Csv -LiteralPath $csvPath -NoTypeInformation -Encoding UTF8
$rows | Format-Table LogFile, Line, Timestamp, Check, ViolationLevel, Delta, Player -AutoSize
Write-Host "CSV export: $csvPath"
