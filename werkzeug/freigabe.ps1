# freigabe.ps1 - die Release-APK bauen, signieren und pruefen
#
# Baut die Release-Fassung (R8, verkleinert), signiert sie mit dem
# Schluessel, den man angibt, und prueft das Ergebnis. Heraus kommen drei
# Dateien unter .\freigabe (steht in .gitignore, kommt nie ins Repo):
#
#   yskar-wallet-<Version>.apk    die Datei zum Verteilen
#   yskar-wallet-<Version>.txt    Pruefsumme und Fingerabdruck, fuer die
#                                 Release-Seite
#   mapping-<Version>.txt         zum Entschluesseln von Stacktraces. Pro
#                                 Release aufheben, nicht veroeffentlichen.
#
# DAS PASSWORT wird hier nirgends angegeben, gespeichert oder durchgereicht.
# apksigner fragt selbst danach, verdeckt, im Terminal. Darum laeuft der
# Aufruf unten OHNE Umleitung: Mit einer Umleitung faende Java keine
# Konsole mehr und wuerde das Passwort beim Tippen anzeigen.
#
#   .\werkzeug\freigabe.ps1 -Schluessel D:\sicher\yskar.jks
#   .\werkzeug\freigabe.ps1 -Schluessel D:\sicher\yskar.jks -Alias yskar
#   .\werkzeug\freigabe.ps1 -Schluessel ... -OhneTests   # nur zum Proben
#
# Getrennt, wenn gebaut und signiert an verschiedenen Orten wird:
#   .\werkzeug\freigabe.ps1 -NurBauen
#   .\werkzeug\freigabe.ps1 -Schluessel D:\sicher\yskar.jks -NurSignieren
#
# Einen NEUEN Schluessel legt man einmal von Hand an (Passwort selbst
# tippen, Datei und Passwort zusaetzlich offline sichern - geht er
# verloren, gibt es keine Updates mehr):
#
#   keytool -genkeypair -v -keystore yskar.jks -storetype PKCS12 `
#       -alias yskar -keyalg RSA -keysize 4096 -validity 10000

[CmdletBinding()]
param(
    [string] $Schluessel = '',
    [string] $Alias = '',
    [string] $Ziel  = 'freigabe',
    [switch] $OhneTests,
    [switch] $NurBauen,
    [switch] $NurSignieren
)

$ErrorActionPreference = 'Stop'

$JdkRoot = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'

# WO DAS SDK WIRKLICH LIEGT. Auf diesem Rechner hat es ein verpacktes
# Programm eingerichtet (eine MSIX-App). Was ein solches Programm unter
# AppData\Local anlegt, liegt in Wahrheit in seinem Paketordner; nur von
# innen sieht es aus wie AppData\Local\Android\Sdk. Ein gewoehnliches
# Terminal findet dort nichts. Darum beide Orte, und ANDROID_HOME zuerst.
function FindeSdk {
    $orte = @()
    if ($env:ANDROID_HOME) { $orte += $env:ANDROID_HOME }
    $orte += (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
    $pakete = Join-Path $env:LOCALAPPDATA 'Packages'
    if (Test-Path -LiteralPath $pakete) {
        # Jeder Paketordner kommt in Frage; welcher es ist, haengt davon
        # ab, mit welchem Programm das SDK eingerichtet wurde.
        Get-ChildItem -LiteralPath $pakete -Directory -ErrorAction SilentlyContinue |
            ForEach-Object { $orte += (Join-Path $_.FullName 'LocalCache\Local\Android\Sdk') }
    }
    foreach ($o in $orte) { if (Test-Path -LiteralPath (Join-Path $o 'build-tools')) { return $o } }
    return $null
}
$SdkRoot = FindeSdk
if (-not $SdkRoot) {
    Write-Host '  [FEHL] Android-SDK nicht gefunden (ANDROID_HOME, AppData\Local\Android\Sdk, Paketordner).' -ForegroundColor Red
    exit 1
}

$env:JAVA_HOME        = $JdkRoot
$env:ANDROID_HOME     = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
foreach ($p in @((Join-Path $SdkRoot 'platform-tools'), (Join-Path $JdkRoot 'bin'))) {
    if ($env:Path -notlike "*$p*") { $env:Path = "$p;$env:Path" }
}

function Schritt($text) { Write-Host ''; Write-Host "=== $text ===" -ForegroundColor Cyan }
function Gut($text)     { Write-Host "  [OK]   $text" -ForegroundColor Green }
function Schlecht($t)   { Write-Host "  [FEHL] $t"    -ForegroundColor Red }
function Hinweis($t)    { Write-Host "  [i]    $t"    -ForegroundColor Yellow }

# Gradle und die SDK-Werkzeuge schreiben auf stderr. Mit 'Stop' wertet
# PowerShell das als Abbruch - darum 'Continue' und $LASTEXITCODE pruefen.
function Rufe([string] $datei, [string[]] $werte) {
    $alt = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & $datei @werte | Out-Host } finally { $ErrorActionPreference = $alt }
    return $LASTEXITCODE
}

# Wie Rufe, gibt aber die Ausgabe zurueck.
function Lies([string] $datei, [string[]] $werte) {
    $alt = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $zeilen = & $datei @werte 2>&1 | ForEach-Object { "$_" } }
    finally { $ErrorActionPreference = $alt }
    return @($zeilen)
}

# Der Schluessel wird VOR dem Wechsel ins Projekt aufgeloest: Ein
# relativer Pfad meint das Verzeichnis, in dem man gerade steht.
if ($NurBauen -and $NurSignieren) { Schlecht '-NurBauen und -NurSignieren zugleich ergibt nichts.'; exit 1 }
if (-not $NurBauen) {
    if (-not $Schluessel) { Schlecht 'Zum Signieren fehlt -Schluessel <Pfad zur .jks>.'; exit 1 }
    if (-not (Test-Path -LiteralPath $Schluessel -PathType Leaf)) {
        Schlecht "Schluesseldatei nicht gefunden: $Schluessel"
        exit 1
    }
    $Schluessel = (Resolve-Path -LiteralPath $Schluessel).Path
}

$Wurzel = Split-Path -Parent $PSScriptRoot
Set-Location $Wurzel

# Der Schluessel gehoert nicht ins Projekt. .gitignore schliesst *.jks und
# *.keystore aus, aber eine Datei mit anderer Endung waere nicht geschuetzt.
if ($Schluessel -and $Schluessel.StartsWith($Wurzel, [StringComparison]::OrdinalIgnoreCase)) {
    Schlecht 'Der Schluessel liegt im Projektordner. Bitte ausserhalb ablegen.'
    exit 1
}

# Die neuesten Build-Tools, die apksigner mitbringen.
$BuildTools = Get-ChildItem (Join-Path $SdkRoot 'build-tools') -Directory |
    Where-Object { Test-Path (Join-Path $_.FullName 'apksigner.bat') } |
    Sort-Object { [version]$_.Name } | Select-Object -Last 1
if (-not $BuildTools) { Schlecht 'Keine Build-Tools mit apksigner gefunden.'; exit 1 }
$ApkSigner = Join-Path $BuildTools.FullName 'apksigner.bat'
$ZipAlign  = Join-Path $BuildTools.FullName 'zipalign.exe'

# ------------------------------------------------------------------ Version
Schritt 'Version'
$gradle = [IO.File]::ReadAllText((Join-Path $Wurzel 'app\build.gradle.kts'))
if ($gradle -notmatch 'versionName\s*=\s*"([^"]+)"') { Schlecht 'versionName nicht gefunden.'; exit 1 }
$Version = $Matches[1]
if ($gradle -notmatch 'versionCode\s*=\s*(\d+)') { Schlecht 'versionCode nicht gefunden.'; exit 1 }
$Code = $Matches[1]
Gut "Version $Version (versionCode $Code)"

# Signiert wird, was gesichert ist. Sonst laesst sich spaeter nicht mehr
# sagen, aus welchem Stand eine verteilte Datei gebaut wurde.
$offen = Lies 'git' @('status', '--porcelain')
$stand = (Lies 'git' @('rev-parse', '--short', 'HEAD')) -join ''
if ($offen) {
    Schlecht 'Im Projekt liegen ungesicherte Aenderungen. Erst committen, dann freigeben:'
    $offen | Select-Object -First 10 | ForEach-Object { Write-Host "         $_" }
    exit 1
}
Gut "Stand $stand, nichts Ungesichertes"

# -------------------------------------------------------------------- Bauen
# Bauen und Signieren lassen sich trennen (-NurBauen, dann -NurSignieren):
# Gebaut wird dort, wo Gradle das SDK findet; signiert wird im Terminal
# des Besitzers, weil nur er das Passwort tippt. Damit dazwischen nichts
# verwechselt wird, schreibt der Bau neben die Datei, aus welchem Stand er
# stammt und ob die Tests gelaufen sind - und signiert wird nur, wenn das
# der Stand von jetzt ist.
$Roh    = Join-Path $Wurzel 'app\build\outputs\apk\release\app-release-unsigned.apk'
$Beleg  = Join-Path $Wurzel 'app\build\outputs\apk\release\gebaut-aus.txt'
$ganz   = (Lies 'git' @('rev-parse', 'HEAD')) -join ''
if ($NurSignieren) {
    Schritt 'Gebaute Datei'
    if (-not (Test-Path $Roh) -or -not (Test-Path $Beleg)) {
        Schlecht 'Es gibt keine gebaute Release-Datei mit Beleg. Erst: .\werkzeug\freigabe.ps1 -NurBauen'
        exit 1
    }
    $b = [IO.File]::ReadAllLines($Beleg)
    if ($b.Count -lt 3 -or $b[0] -ne $ganz) {
        Schlecht 'Die gebaute Datei stammt aus einem anderen Stand als dem jetzigen. Neu bauen.'
        exit 1
    }
    if ($b[1] -ne 'tests=ja') {
        Schlecht 'Die gebaute Datei ist ohne Unittests entstanden. Neu bauen.'
        exit 1
    }
    $summeRoh = (Get-FileHash -LiteralPath $Roh -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($b[2] -ne $summeRoh) {
        Schlecht 'Die gebaute Datei ist nicht mehr die, die der Bau abgelegt hat. Neu bauen.'
        exit 1
    }
    Gut "gebaut aus Stand $stand, mit Unittests, unveraendert"
} else {
    Schritt 'Bauen'
    if (Test-Path $Beleg) { Remove-Item -LiteralPath $Beleg -Force }
    $aufgaben = @('assembleRelease')
    if (-not $OhneTests) { $aufgaben = @('testDebugUnitTest') + $aufgaben }
    else { Hinweis 'Ohne Unittests - nur zum Proben, nicht zum Verteilen.' }
    if ((Rufe '.\gradlew.bat' $aufgaben) -ne 0) {
        Schlecht 'Gradle ist gescheitert. Nichts wird signiert.'
        exit 1
    }
    if (-not (Test-Path $Roh)) {
        Schlecht "Die gebaute Datei fehlt: $Roh"
        exit 1
    }
    $tests = 'tests=ja'; if ($OhneTests) { $tests = 'tests=nein' }
    $summeRoh = (Get-FileHash -LiteralPath $Roh -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllLines($Beleg, @($ganz, $tests, $summeRoh))
    Gut 'Release gebaut'
    if ($NurBauen) {
        Write-Host ''
        Hinweis 'Gebaut, nicht signiert. Jetzt im eigenen Terminal:'
        Write-Host '         .\werkzeug\freigabe.ps1 -Schluessel <Pfad zur .jks> -NurSignieren'
        exit 0
    }
}

# ---------------------------------------------------------------- Ausrichten
# Signiert wird NACH dem Ausrichten; danach darf an der Datei nichts mehr
# geaendert werden. Gradle richtet schon aus - das hier prueft es nur und
# holt es nach, falls nicht. -P 16: die nativen Bibliotheken der Kamera
# auf 16-KB-Seiten, wie Android 15 und neuer es erwarten.
Schritt 'Ausrichten'
$null = New-Item -ItemType Directory -Force -Path (Join-Path $Wurzel $Ziel)
$Apk      = Join-Path $Wurzel "$Ziel\yskar-wallet-$Version.apk"
$Zwischen = Join-Path $Wurzel "$Ziel\ausgerichtet.tmp"
foreach ($alt in @($Apk, "$Apk.idsig", $Zwischen)) {
    if (Test-Path $alt) { Remove-Item -LiteralPath $alt -Force }
}
if ((Rufe $ZipAlign @('-c', '-P', '16', '4', $Roh)) -eq 0) {
    Copy-Item -LiteralPath $Roh -Destination $Zwischen
    Gut 'war schon ausgerichtet'
} else {
    if ((Rufe $ZipAlign @('-f', '-P', '16', '4', $Roh, $Zwischen)) -ne 0) {
        Schlecht 'zipalign ist gescheitert.'
        exit 1
    }
    Gut 'ausgerichtet'
}

# ---------------------------------------------------------------- Signieren
Schritt 'Signieren'
Hinweis "Schluessel: $Schluessel"
Hinweis 'apksigner fragt jetzt nach dem Passwort. Es wird nicht gespeichert.'
# Ohne v4: Das ergaebe eine zweite Datei (.idsig), die nur fuer das
# Streamen von Installationen ueber adb gebraucht wird.
$werte = @('sign', '--ks', $Schluessel, '--v4-signing-enabled', 'false')
if ($Alias) { $werte += @('--ks-key-alias', $Alias) }
$werte += @('--out', $Apk, $Zwischen)

# OHNE Umleitung und ohne Pipeline - siehe Kopf dieser Datei.
$ErrorActionPreference = 'Continue'
& $ApkSigner @werte
$ergebnis = $LASTEXITCODE
$ErrorActionPreference = 'Stop'

Remove-Item -LiteralPath $Zwischen -Force -ErrorAction SilentlyContinue
if ($ergebnis -ne 0 -or -not (Test-Path $Apk)) {
    Schlecht 'Signieren ist gescheitert (falsches Passwort, falscher Alias?).'
    if (Test-Path $Apk) { Remove-Item -LiteralPath $Apk -Force }
    exit 1
}
Gut 'signiert'

# ------------------------------------------------------------------- Pruefen
Schritt 'Pruefen'
$pruef = Lies $ApkSigner @('verify', '--print-certs', '--min-sdk-version', '26', $Apk)
if ($LASTEXITCODE -ne 0) {
    Schlecht 'Die Signatur laesst sich nicht pruefen:'
    $pruef | ForEach-Object { Write-Host "         $_" }
    Remove-Item -LiteralPath $Apk -Force
    exit 1
}
$zeile = $pruef | Where-Object { $_ -match 'certificate SHA-256 digest:\s*([0-9a-fA-F]+)' } | Select-Object -First 1
if (-not $zeile -or $zeile -notmatch 'digest:\s*([0-9a-fA-F]{64})') {
    Schlecht 'Kein Fingerabdruck in der Ausgabe von apksigner.'
    Remove-Item -LiteralPath $Apk -Force
    exit 1
}
$roh = $Matches[1].ToUpperInvariant()
$Fingerabdruck = (0..31 | ForEach-Object { $roh.Substring($_ * 2, 2) }) -join ':'
$inhaber = ($pruef | Where-Object { $_ -match 'certificate DN:\s*(.+)$' } | Select-Object -First 1)
if ($inhaber -match 'certificate DN:\s*(.+)$') { $inhaber = $Matches[1] } else { $inhaber = '?' }

# Mit dem Debug-Schluessel von Android wird nicht verteilt: Den hat jeder.
if ($inhaber -match 'Android Debug') {
    Schlecht 'Das ist der Debug-Schluessel von Android. Damit wird nichts verteilt.'
    Remove-Item -LiteralPath $Apk -Force
    exit 1
}

$Summe   = (Get-FileHash -LiteralPath $Apk -Algorithm SHA256).Hash.ToLowerInvariant()
$Groesse = [math]::Round((Get-Item -LiteralPath $Apk).Length / 1MB, 2)
Gut "Signatur gueltig, $Groesse MB"

# --------------------------------------------------------------- Aufbewahren
$Mapping = Join-Path $Wurzel 'app\build\outputs\mapping\release\mapping.txt'
if (Test-Path $Mapping) {
    Copy-Item -LiteralPath $Mapping -Destination (Join-Path $Wurzel "$Ziel\mapping-$Version.txt") -Force
    Gut "mapping-$Version.txt aufgehoben (nicht veroeffentlichen)"
} else {
    Hinweis 'mapping.txt fehlt - Stacktraces dieses Release lassen sich nicht entschluesseln.'
}

$zettel = @(
    "yskar-wallet-$Version.apk",
    "Version       $Version (versionCode $Code)",
    "Stand         $stand",
    "Groesse       $Groesse MB ($((Get-Item -LiteralPath $Apk).Length) Bytes)",
    "SHA-256       $Summe",
    "Fingerabdruck $Fingerabdruck",
    "Zertifikat    $inhaber"
)
[IO.File]::WriteAllLines((Join-Path $Wurzel "$Ziel\yskar-wallet-$Version.txt"), $zettel)

# Der Text fuer die Release-Seite: Vorlage mit Version, Pruefsumme und
# Fingerabdruck gefuellt. Ueber .NET gelesen und geschrieben, damit die
# Umlaute bleiben, wie sie sind.
$Vorlage = Join-Path $PSScriptRoot 'oeffentlich\release.md'
if (Test-Path -LiteralPath $Vorlage) {
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    $text = [IO.File]::ReadAllText($Vorlage, $utf8)
    $text = $text.Replace('@@VERSION@@', $Version).Replace('@@SHA256@@', $Summe).Replace('@@FINGERABDRUCK@@', $Fingerabdruck)
    [IO.File]::WriteAllText((Join-Path $Wurzel "$Ziel\release-$Version.md"), $text, $utf8)
    Gut "release-$Version.md geschrieben (Text fuer die Release-Seite)"
}

Schritt 'Fertig'
$zettel | ForEach-Object { Write-Host "  $_" }
Write-Host ''
Hinweis "Datei: $Apk"
Hinweis 'Vor dem Verteilen am Geraet proben - die Release-Fassung ist verkleinert'
Hinweis 'und umbenannt (R8), der Debug-Bau nicht:'
Write-Host "         adb install -r `"$Apk`""
Hinweis 'Dann in der App: Einstellungen -> Erweitert -> Selbsttest.'
Hinweis 'Sie liegt neben der Debug-Fassung (andere Kennung), nicht darueber.'
