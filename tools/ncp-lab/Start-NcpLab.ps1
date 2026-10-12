param(
    [string]$ServerJar = (Join-Path $PSScriptRoot 'server\server.jar'),
    [string]$NcpPluginJar = (Join-Path $PSScriptRoot 'server\plugins\NoCheatPlus.jar'),
    [string]$JavaPath,
    [int]$Port = 25566
)

$ErrorActionPreference = 'Stop'
$ServerJar = [IO.Path]::GetFullPath($ServerJar)
$serverDirectory = Split-Path -Parent $ServerJar
$pluginDirectory = Join-Path $serverDirectory 'plugins'

if (-not (Test-Path -LiteralPath $ServerJar -PathType Leaf)) {
    throw "Server jar missing: $ServerJar. Place a Java 8-compatible 1.12.2 Paper/Spigot server jar there."
}
if (-not (Test-Path -LiteralPath $NcpPluginJar -PathType Leaf) -and -not (Get-ChildItem -LiteralPath $pluginDirectory -Filter '*NoCheatPlus*.jar' -File -ErrorAction SilentlyContinue)) {
    throw "NoCheatPlus plugin missing. Add a compatible NCP jar to '$pluginDirectory' or pass -NcpPluginJar."
}

$eulaPath = Join-Path $serverDirectory 'eula.txt'
if (-not (Test-Path -LiteralPath $eulaPath) -or -not (Select-String -LiteralPath $eulaPath -Pattern '^eula=true\s*$' -Quiet)) {
    throw "Minecraft EULA has not been accepted for this server directory. Review the EULA and set eula=true in '$eulaPath' yourself, then rerun."
}

if (-not (Test-Path -LiteralPath $pluginDirectory)) { New-Item -ItemType Directory -Path $pluginDirectory | Out-Null }
if (Test-Path -LiteralPath $NcpPluginJar -PathType Leaf) {
    $pluginTarget = Join-Path $pluginDirectory (Split-Path -Leaf $NcpPluginJar)
    if ([IO.Path]::GetFullPath($NcpPluginJar) -ne [IO.Path]::GetFullPath($pluginTarget)) {
        Copy-Item -LiteralPath $NcpPluginJar -Destination $pluginTarget -Force
    }
}

$propertiesPath = Join-Path $serverDirectory 'server.properties'
$properties = @{}
if (Test-Path -LiteralPath $propertiesPath) {
    foreach ($line in Get-Content -LiteralPath $propertiesPath) {
        if ($line -match '^([^#=]+)=(.*)$') { $properties[$matches[1]] = $matches[2] }
    }
}
$properties['server-ip'] = '127.0.0.1'
$properties['server-port'] = [string]$Port
$properties['online-mode'] = 'false'
$properties['motd'] = 'Backspace local NCP diagnostics'
$properties['enable-rcon'] = 'false'
$properties['enable-query'] = 'false'
$properties['white-list'] = 'false'
$properties['spawn-protection'] = '0'
$properties.GetEnumerator() | Sort-Object Name | ForEach-Object { '{0}={1}' -f $_.Name, $_.Value } | Set-Content -LiteralPath $propertiesPath -Encoding ASCII

$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$bundledJava = Join-Path $projectRoot 'work\jdk-dist\jdk8u504-b01\bin\java.exe'
$javaCommand = Get-Command 'java.exe' -ErrorAction SilentlyContinue
$java = if ($JavaPath) { [IO.Path]::GetFullPath($JavaPath) }
    elseif (Test-Path -LiteralPath $bundledJava) { $bundledJava }
    elseif ($javaCommand) { $javaCommand.Source }
    else { $null }
$probeOut = [IO.Path]::GetTempFileName()
$probeErr = [IO.Path]::GetTempFileName()
if (-not $java -or -not (Test-Path -LiteralPath $java)) { throw 'Java was not found on PATH or in the bundled JDK directory.' }
try {
    $javaProbe = Start-Process -FilePath $java -ArgumentList '-XshowSettings:properties -version' -RedirectStandardOutput $probeOut -RedirectStandardError $probeErr -Wait -PassThru -NoNewWindow
    $javaProperties = (Get-Content -LiteralPath $probeOut -Raw) + (Get-Content -LiteralPath $probeErr -Raw)
} finally {
    Remove-Item -LiteralPath $probeOut, $probeErr -Force -ErrorAction SilentlyContinue
}
if ($javaProbe.ExitCode -ne 0 -or $javaProperties -notmatch 'sun\.arch\.data\.model\s*=\s*64') {
    throw "A 64-bit Java runtime is required for this lab's 2 GiB heap. Selected: $java"
}
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$logDirectory = Join-Path $PSScriptRoot 'logs'
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
$stdout = Join-Path $logDirectory "server-$stamp.log"
$stderr = Join-Path $logDirectory "server-$stamp.err.log"
$arguments = @('-Xms512m', '-Xmx2G', '-jar', ('"{0}"' -f $ServerJar), 'nogui')

Write-Host "Starting local NCP lab at 127.0.0.1:$Port"
Write-Host "Console log: $stdout"
Write-Host 'The listener is loopback-only; Ctrl+C stops log following (the server process remains available).'
$process = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $serverDirectory -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru -WindowStyle Hidden
Set-Content -LiteralPath (Join-Path $logDirectory 'server.pid') -Value $process.Id -Encoding ASCII
Write-Host "Server PID: $($process.Id)"
Write-Host 'Use Inspect-NcpLabLogs.ps1 after or during a run to export NCP movement flags.'

 $shownLines = 0
while (-not $process.HasExited) {
    if (Test-Path -LiteralPath $stdout) {
        $currentLines = @(Get-Content -LiteralPath $stdout -ErrorAction SilentlyContinue)
        if ($currentLines.Count -gt $shownLines) {
            $currentLines[$shownLines..($currentLines.Count - 1)] | ForEach-Object { Write-Host $_ }
            $shownLines = $currentLines.Count
        }
    }
    Start-Sleep -Seconds 2
    $process.Refresh()
}
Write-Host "Server exited with code $($process.ExitCode). Logs are in $logDirectory"
