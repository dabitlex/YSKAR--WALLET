# startbilder.ps1 - den echten Start der App einfangen
#
# Der einzige Weg, den ersten Moment der App zu sehen. Bildschirmbilder
# aus dem Testrahmen zeigen fertige Bildschirme, nie den Start selbst:
# App beenden, starten, sofort mehrere Aufnahmen hintereinander.
# screencap braucht pro Bild rund 300 ms, die Aufnahmen liegen also ueber
# die erste Sekunde verteilt.
#
#   .\werkzeug\startbilder.ps1
#   .\werkzeug\startbilder.ps1 -Anzahl 10 -Ziel bilder\start

[CmdletBinding()]
param(
    [string] $Ziel   = 'bilder\start',
    [int]    $Anzahl = 6,
    [string] $Serial = $env:ANDROID_SERIAL
)

# adb schreibt auf stderr - mit 'Stop' wertet PowerShell schon die
# Daemon-Meldung als Abbruch.
$ErrorActionPreference = 'Continue'

$adb   = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$paket = 'com.yskar.wallet.debug'

$Wurzel = Split-Path -Parent $PSScriptRoot
Set-Location $Wurzel
New-Item -ItemType Directory -Force -Path $Ziel | Out-Null

& $adb -s $Serial shell am force-stop $paket | Out-Null
Start-Sleep -Milliseconds 800

Start-Process -FilePath $adb `
    -ArgumentList '-s', $Serial, 'shell', 'am', 'start', '-n', "$paket/com.yskar.wallet.MainActivity" `
    -NoNewWindow | Out-Null

for ($i = 0; $i -lt $Anzahl; $i++) {
    $datei = Join-Path (Resolve-Path $Ziel) ("start_{0}.png" -f $i)
    cmd /c "`"$adb`" -s $Serial exec-out screencap -p > `"$datei`""
}

Get-ChildItem (Join-Path $Ziel 'start_*.png') |
    ForEach-Object { "  {0,-14} {1,8} Bytes" -f $_.Name, $_.Length }
