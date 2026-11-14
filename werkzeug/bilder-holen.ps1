# bilder-holen.ps1 - Bildschirmbilder vom Geraet holen
#
# Baut die beiden Debug-APKs, laesst den Instrumentierungstest
# "Bildschirmbilder" jeden Bildschirm einmal zeichnen und holt die PNGs
# nach YskarWallet\bilder. Das ist der einzige Weg, die Oberflaeche zu
# beurteilen: HyperOS verweigert "adb shell input" mit INJECT_EVENTS,
# von aussen ist in der App nichts zu bedienen.
#
# Zwei Bedingungen, sonst scheitert der Lauf:
#   1. Das Telefon muss WACH und ENTSPERRT sein. Schlaeft es, bricht jede
#      Aufnahme mit "No compose hierarchies found" ab.
#   2. Fuer das Scannerbild muss die Kameraerlaubnis schon erteilt sein.
#      "adb shell pm grant" scheitert auf HyperOS mit
#      GRANT_RUNTIME_PERMISSIONS - einmal von Hand am Geraet erlauben.
#
# Anders als OberflaecheTest fasst Bildschirmbilder den Tresor NICHT an.
# Es ist damit auch dann unbedenklich, wenn eine echte Wallet auf dem
# Geraet liegt.
#
#   .\werkzeug\bilder-holen.ps1                 # nach .\bilder
#   .\werkzeug\bilder-holen.ps1 -Ziel bilder\r7 # woanders hin
#   .\werkzeug\bilder-holen.ps1 -NurHolen       # ohne neu zu bauen

[CmdletBinding()]
param(
    [string] $Ziel    = 'bilder',
    [string] $Serial  = $env:ANDROID_SERIAL,
    [switch] $NurHolen
)

$ErrorActionPreference = 'Stop'

$JdkRoot = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$SdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'

$env:JAVA_HOME        = $JdkRoot
$env:ANDROID_HOME     = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
foreach ($p in @((Join-Path $SdkRoot 'platform-tools'), (Join-Path $JdkRoot 'bin'))) {
    if ($env:Path -notlike "*$p*") { $env:Path = "$p;$env:Path" }
}

# Projektwurzel = Elternverzeichnis dieses Scripts
$Wurzel = Split-Path -Parent $PSScriptRoot
Set-Location $Wurzel

$Paket    = 'com.yskar.wallet.debug'
$AmQuelle = "/sdcard/Android/data/$Paket/files/bilder"
$Klasse   = 'com.yskar.wallet.Bildschirmbilder'
$Laeufer  = "$Paket.test/androidx.test.runner.AndroidJUnitRunner"

# adb mit vollem Pfad, und die Hilfsfunktionen unten heissen NICHT "Adb":
# PowerShell unterscheidet keine Gross- und Kleinschreibung und zieht eine
# Funktion dem gleichnamigen Programm vor. Eine Funktion "Adb", die "adb"
# aufruft, ruft sich selbst auf.
$AdbExe = Join-Path $SdkRoot 'platform-tools/adb.exe'

function Schritt($text) { Write-Host ''; Write-Host "=== $text ===" -ForegroundColor Cyan }
function Gut($text)     { Write-Host "  [OK]   $text" -ForegroundColor Green }
function Schlecht($t)   { Write-Host "  [FEHL] $t"    -ForegroundColor Red }
function Hinweis($t)    { Write-Host "  [i]    $t"    -ForegroundColor Yellow }

# Gradle und adb schreiben auf stderr. Mit $ErrorActionPreference='Stop'
# wertet PowerShell das als Abbruch - darum vor nativen Aufrufen auf
# 'Continue' und stattdessen $LASTEXITCODE pruefen.
function Rufe([string] $datei, [string[]] $werte) {
    $alt = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    # Out-Host, damit die Ausgabe am Bildschirm landet und NICHT im
    # Rueckgabewert: Sonst kaeme ein Feld aus Textzeilen und der Zahl zurueck,
    # und "-ne 0" waere darauf immer wahr.
    try { & $datei @werte | Out-Host } finally { $ErrorActionPreference = $alt }
    return $LASTEXITCODE
}

function RufeAdb([string[]] $werte) {
    return Rufe $AdbExe (@('-s', $Serial) + $werte)
}

# Wie RufeAdb, gibt aber die AUSGABE zurueck statt des Rueckgabewerts -
# fuer die Stellen, an denen gelesen werden muss, was das Geraet sagt.
function LiesAdb([string[]] $werte) {
    $alt = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $zeilen = & $AdbExe (@('-s', $Serial) + $werte) 2>&1 | ForEach-Object { "$_" } }
    finally { $ErrorActionPreference = $alt }
    return @($zeilen)
}

# Ist das Display an und das Telefon entsperrt? Beides nur LESEN.
function Wach {
    $strom  = LiesAdb @('shell', 'dumpsys', 'power')
    $sperre = LiesAdb @('shell', 'dumpsys', 'window')
    $an   = [bool]($strom  | Where-Object { $_ -match 'mWakefulness=Awake' })
    $zu   = [bool]($sperre | Where-Object { $_ -match 'isKeyguardShowing=true' })
    if (-not $an) {
        Schlecht 'Das Display ist aus. Telefon aufwecken und entsperren, dann noch einmal.'
        return $false
    }
    if ($zu) {
        Schlecht 'Das Telefon ist gesperrt. Entsperren, dann noch einmal.'
        return $false
    }
    return $true
}

Schritt 'Geraet'
$alt = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
$zeilen = & $AdbExe devices 2>&1 | Where-Object { $_ -match "^$Serial\s" }
$ErrorActionPreference = $alt
if (-not $zeilen) {
    Schlecht "Geraet $Serial nicht verbunden. 'adb devices' zeigt es nicht."
    exit 1
}
if ($zeilen -match 'unauthorized') {
    Schlecht 'Geraet meldet "unauthorized" - USB-Debugging am Display bestaetigen.'
    exit 1
}
Gut "$Serial verbunden"

if (-not $NurHolen) {
    Schritt 'Bauen'
    if ((Rufe '.\gradlew.bat' @('assembleDebug', 'assembleDebugAndroidTest')) -ne 0) {
        Schlecht 'Gradle ist gescheitert.'
        exit 1
    }
    Gut 'beide APKs gebaut'

    Schritt 'Installieren'
    Hinweis 'HyperOS verlangt bei JEDER Installation eine Bestaetigung am Display.'
    foreach ($apk in @(
        'app\build\outputs\apk\debug\app-debug.apk',
        'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
    )) {
        if ((RufeAdb @('install', '-r', '-d', $apk)) -ne 0) {
            Schlecht "Installation von $apk gescheitert."
            Hinweis 'INSTALL_FAILED_USER_RESTRICTED heisst wortwoertlich: Niemand hat'
            Hinweis 'den Dialog angetippt. Erscheint gar kein Dialog, ist'
            Hinweis 'Entwickleroptionen > "Installieren ueber USB" aus.'
            exit 1
        }
        Gut (Split-Path -Leaf $apk)
    }
}

Schritt 'Wach und entsperrt?'
# Nachsehen, bevor irgendetwas geloescht oder gezeichnet wird. Nur lesen:
# Aufwecken oder entsperren kann und soll dieses Script nicht.
if (-not (Wach)) { exit 1 }
Gut 'Display an, keine Sperre'

Schritt 'Alte Bilder auf dem Geraet loeschen'
RufeAdb @('shell', 'rm', '-rf', $AmQuelle) | Out-Null
Gut $AmQuelle

Schritt 'Bildschirme zeichnen'
Hinweis 'Telefon wach und entsperrt halten - sonst "No compose hierarchies found".'
# "am instrument" meldet auch dann Erfolg, wenn jeder Test gescheitert ist.
# Darum die Ausgabe lesen: Nur "OK (n tests)" zaehlt.
$ausgabe = LiesAdb @('shell', 'am', 'instrument', '-w', '-e', 'class', $Klasse, $Laeufer)
$ok = $ausgabe | Where-Object { $_ -match '^OK \((\d+) tests?\)' }
if (-not $ok) {
    $ausgabe | Where-Object { $_ -match '^(Error in|\d+\) |Tests run:|There w)' } |
        Select-Object -First 30 | ForEach-Object { Write-Host "  $_" }
    Schlecht 'Nicht jeder Bildschirm wurde gezeichnet. Die alten Bilder bleiben, wie sie sind.'
    if ($ausgabe -match 'No compose hierarchies found') {
        Hinweis 'Das Telefon hat geschlafen oder war gesperrt.'
    }
    exit 1
}
Gut ($ok | Select-Object -First 1)

Schritt 'Holen'
# Erst holen, dann ersetzen: Scheitert das Holen, bleiben die alten Bilder.
# Und nur die PNG ersetzen, nicht den Ordner - neben den Bildern liegt
# LIESMICH.md, und die hat dieses Script nicht geschrieben.
New-Item -ItemType Directory -Force -Path $Ziel | Out-Null
$zwischen = Join-Path $Ziel '_pull'
if (Test-Path $zwischen) { Remove-Item -Recurse -Force $zwischen }
New-Item -ItemType Directory -Force -Path $zwischen | Out-Null
# adb pull legt den Quellordner im Ziel an; danach eine Ebene hochziehen.
if ((RufeAdb @('pull', $AmQuelle, $zwischen)) -ne 0) {
    Remove-Item -Recurse -Force $zwischen
    Schlecht 'adb pull ist gescheitert - wurden ueberhaupt Bilder geschrieben?'
    exit 1
}
$neu = Get-ChildItem -Recurse -Filter '*.png' -Path $zwischen
if ($neu.Count -eq 0) {
    Remove-Item -Recurse -Force $zwischen
    Schlecht 'Keine Bilder angekommen. Die alten bleiben, wie sie sind.'
    exit 1
}
Get-ChildItem -Path $Ziel -Filter '*.png' -File | Remove-Item -Force
$neu | ForEach-Object { Move-Item $_.FullName (Join-Path $Ziel $_.Name) -Force }

# rollen.txt: je Aufnahme eine Zeile "Name  Bereiche  Weg". Die Tests laufen
# in beliebiger Reihenfolge, darum sortiert ablegen.
$rollen = Get-ChildItem -Recurse -Filter 'rollen.txt' -Path $zwischen | Select-Object -First 1
$zeilenRollen = @()
if ($rollen) {
    $zeilenRollen = @(Get-Content $rollen.FullName | Where-Object { $_.Trim() } | Sort-Object)
    $ohneBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllLines((Join-Path (Resolve-Path $Ziel) 'rollen.txt'), $zeilenRollen, $ohneBom)
}
Remove-Item -Recurse -Force $zwischen

# stand.txt: aus welchem gesicherten Stand diese Bilder gezeichnet wurden.
# Wer die Bilder weitergibt, muss wissen, ob sie noch zum Testrahmen
# passen - sonst zeigen sie Beispieldaten, die es nicht mehr gibt.
#   <Commit>               gebaut aus genau diesem Stand
#   <Commit>+ungesichert   beim Bauen lagen Aenderungen unter app\ herum
#   unbekannt              -NurHolen: Was auf dem Geraet lag, hat ein
#                          frueherer Lauf gebaut
# Ein Fehler hier haelt den Lauf nicht auf; dann fehlt die Datei eben.
try {
    $marke = 'unbekannt'
    if (-not $NurHolen) {
        $altEA = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
        $kopf    = (& git rev-parse HEAD 2>&1 | ForEach-Object { "$_" }) -join ''
        $offenes = @(& git status --porcelain -- app 2>&1 | ForEach-Object { "$_" } | Where-Object { $_.Trim() })
        $ErrorActionPreference = $altEA
        if ($kopf -match '^[0-9a-f]{40}$') {
            if ($offenes.Count -gt 0) { $marke = "$kopf+ungesichert" } else { $marke = $kopf }
        }
    }
    $ohneBom2 = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllLines(
        (Join-Path (Resolve-Path $Ziel) 'stand.txt'),
        @($marke, (Get-Date -Format 'yyyy-MM-dd HH:mm')), $ohneBom2)
} catch {
    Hinweis "stand.txt liess sich nicht schreiben: $($_.Exception.Message)"
}

$bilder = Get-ChildItem -Filter '*.png' -Path $Ziel | Sort-Object Name
if ($bilder.Count -eq 0) {
    Schlecht 'Keine Bilder angekommen.'
    exit 1
}
Gut "$($bilder.Count) Bilder in $Ziel"

Schritt 'Muss ein Bildschirm rollen?'
# Gefragt wurde der Bildschirm selbst (Bildschirmbilder.rollweg). Je Zeile:
#   Name  Bereiche  Weg  Ueberhang
# WEG ist, wie weit sich der Bildschirm rollen LAESST. UEBERHANG ist, wie
# weit das tiefste Element mit Text oder Knopf unter den sichtbaren Bereich
# ragt - nur das heisst, dass jemand rollen MUSS. Ein Weg ohne Ueberhang
# ist Luft am Ende des Bildschirms.
# Die Musterboegen (00...) sind keine Bildschirme und werden uebergangen.
if ($zeilenRollen.Count -eq 0) {
    Hinweis 'Keine rollen.txt angekommen - dazu ist nichts zu sagen.'
} else {
    foreach ($z in $zeilenRollen) {
        $t = $z -split '\s+'
        $name = $t[0]; $weg = [int]$t[2]; $ueber = [int]$t[3]
        if ($name -like '00*') { continue }
        if ($ueber -gt 0) {
            Write-Host ("  {0,-34} MUSS rollen: Inhalt ragt {1} Bildpunkte hinaus" -f $name, $ueber) -ForegroundColor Yellow
        } elseif ($weg -gt 0) {
            Write-Host ("  {0,-34} alles sichtbar (laesst sich um {1} Bildpunkte Luft rollen)" -f $name, $weg) -ForegroundColor Green
        } else {
            Write-Host ("  {0,-34} alles sichtbar, rollt nicht" -f $name) -ForegroundColor Green
        }
    }
    Hinweis 'Rollen duerfen: Mehr, Selbsttest, Bewegung, Verlauf, Wallet.'
    Hinweis 'Nicht rollen MUESSEN: alle Sendebildschirme (REGELN.md, Regel 29).'
}

Schritt 'Hochformat pruefen'
# Dreht sich das Telefon mitten im Lauf, kommen die Bilder im Querformat
# zurueck (2608x1200) und mit Geisterschrift - jede Messung daran ist
# wertlos. Das ist hier schon passiert, darum die Gegenprobe.
Add-Type -AssemblyName System.Drawing
$quer = 0
foreach ($b in $bilder) {
    $bild = [System.Drawing.Image]::FromFile($b.FullName)
    try {
        if ($bild.Width -gt $bild.Height) {
            Schlecht ("{0}  {1}x{2} - Querformat" -f $b.Name, $bild.Width, $bild.Height)
            $quer++
        }
    } finally { $bild.Dispose() }
}
if ($quer -gt 0) {
    Hinweis 'Telefon hochkant halten und den Lauf wiederholen.'
    exit 1
}
Gut 'alle hochkant'
Write-Host ''
