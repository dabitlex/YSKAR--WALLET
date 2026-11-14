# fotos.ps1 - die Fotos der Einfuehrung mit den aktuellen Bildschirmen
#
# Vier Fotos der Einfuehrung zeigen ein Telefon mit der Wallet. Dieses
# Script setzt auf deren Displays die Bildschirmbilder aus .\bilder -
# perspektivisch richtig, mit runden Ecken, hinter den Fingern, in Schaerfe
# und Helligkeit ans Foto angepasst. Die Arbeit selbst macht
# werkzeug\fotos\Einsetzen.java.
#
#   Foto              Bildschirm
#   einfuehrung_1     04_wallet            der Hauptbildschirm
#   einfuehrung_3     09_senden_pruefen    der Pruefschritt
#   einfuehrung_5     07_verlauf           der Verlauf
#   einfuehrung_7     03_sperre            die Sperre
#
# Ausgangspunkt ist je Foto ein GRUNDBILD (werkzeug\fotos\grund_N.jpg):
# das Foto mit geschwaerztem Display, Finger davor unveraendert, dazu eine
# Maske, die sagt, wo ein Finger liegt. Die Grundbilder sind einmal aus
# den urspruenglichen Fotos entstanden (-Grund) und liegen im Projekt -
# jeder weitere Lauf braucht nur sie und die Bildschirmbilder.
#
# ERST DIE BILDER, DANN DIE FOTOS. Ein Bildschirmbild, das aelter ist als
# die Beispieldaten, traegt die alten Beispieldaten ins Foto. Darum prueft
# das Script wie oeffentlich.ps1 die Datei bilder\stand.txt, die
# bilder-holen.ps1 schreibt, und weigert sich sonst. -Ungeprueft uebergeht
# das - nur fuer ein Bildschirmbild, das man sich vorher angesehen hat.
#
#   .\werkzeug\fotos.ps1                    # alle vier, nach res\drawable-nodpi
#   .\werkzeug\fotos.ps1 -Fotos 5           # nur eines
#   .\werkzeug\fotos.ps1 -Vorschau          # nach build\fotos, zum Ansehen
#   .\werkzeug\fotos.ps1 -Grund -Original <Ordner>
#                                           # Grundbilder neu, aus den
#                                           # urspruenglichen Fotos in <Ordner>

[CmdletBinding()]
param(
    [int[]]  $Fotos = @(1, 3, 5, 7),
    [switch] $Vorschau,
    [switch] $Ungeprueft,
    [switch] $Grund,
    [string] $Original = ''
)

$ErrorActionPreference = 'Stop'

$JdkRoot = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$Javac = Join-Path $JdkRoot 'bin\javac.exe'
$Java  = Join-Path $JdkRoot 'bin\java.exe'

function Schritt($text) { Write-Host ''; Write-Host "=== $text ===" -ForegroundColor Cyan }
function Gut($text)     { Write-Host "  [OK]   $text" -ForegroundColor Green }
function Schlecht($t)   { Write-Host "  [FEHL] $t"    -ForegroundColor Red }
function Hinweis($t)    { Write-Host "  [i]    $t"    -ForegroundColor Yellow }

function Lies([string] $datei, [string[]] $werte) {
    $alt = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $zeilen = & $datei @werte 2>&1 | ForEach-Object { "$_" } }
    finally { $ErrorActionPreference = $alt }
    return @($zeilen)
}

$Wurzel  = Split-Path -Parent $PSScriptRoot
$Ordner  = Join-Path $PSScriptRoot 'fotos'
$Res     = Join-Path $Wurzel 'app\src\main\res\drawable-nodpi'
$Schrift = Join-Path $Wurzel 'app\src\main\res\font\hanken_grotesk_variable.ttf'
$Bau     = Join-Path $Wurzel 'build\fotos'
Set-Location $Wurzel

# Je Foto: die vier Ecken des Displays (oben links, oben rechts, unten
# rechts, unten links - gesehen vom aufrecht gehaltenen Telefon), das
# Bildschirmbild, und was beim Grundbild und beim Einsetzen anders ist als
# die Vorgabe.
#
# DIE ECKEN SIND GEMESSEN, NICHT GESCHAETZT: an Helligkeitsprofilen quer
# ueber jede Kante, je Kante drei bis sechs Stellen, durch die Stellen eine
# Gerade, die Ecken als Schnittpunkte. Das Display endet nicht am hellen
# Metallrahmen, sondern einige Bildpunkte davor - wer nach Augenmass den
# Rahmen trifft, setzt das Bild zu breit.
$Plan = [ordered]@{
    '1' = @{ Ecken  = '540.8,312.9,723.7,315.2,715.9,728.4,533.9,725.6'
           Schirm = '04_wallet'
           Grund  = @('finger=0', 'ueberstand=26')
           Setze  = @('weich=0.35') }
    # Schraeg gehalten, der Daumen liegt unscharf vor dem Display. Seine
    # Unterseite ist im Schatten fast schwarz, der Nagel glaenzt - beides
    # sieht nicht nach Haut aus. Mit der gewoehnlichen Suche fehlte dem
    # Daumen die halbe Dicke (am 13.11.2026 vom Besitzer am Geraet
    # bemerkt). Darum: auch dunkle, roetliche Toene zaehlen (schattenhaut),
    # Buchten schliessen, und die Maske ist die FLAECHE des Daumens.
    '3' = @{ Ecken  = '421.7,440.5,622.7,406.1,840.3,846.1,641.0,894.2'
           Schirm = '09_senden_pruefen'
           # dazu: die Unterseite des Daumens bis knapp ueber die alte
           # Schriftzeile und rechts hinunter zum Handballen. Dort ist das
           # Foto tiefer Schatten, an der Farbe nicht mehr vom Display zu
           # trennen; die Kontur ist am aufgehellten Original abgelesen.
           # schriftweg: Was vom alten Bildschirm am Rand durchscheint,
           # wird zu Schatten.
           Grund  = @('finger=1', 'ueberstand=12', 'schattenhaut=1', 'schliessen=4', 'flaechig=1', 'einzug=1', 'maskenweich=1.8', 'schriftweg=1',
                      'dazu=672,668,762,660,766,694,757,688,751,682,746,677,740,677.5,730,679.5,720,681.5,710,683,700,684,690,685,678,684,670,680')
           Setze  = @('weich=0.6', 'schattenweite=6') }
    '5' = @{ Ecken  = '741.8,503.4,892.2,555.7,713.7,859.9,553.2,798.1'
           Schirm = '07_verlauf'
           Grund  = @('finger=0', 'ueberstand=30')
           Setze  = @('weich=0.5') }
    '7' = @{ Ecken  = '447.5,157.5,804.7,157.5,804.9,939.5,446.5,939.5'
           Schirm = '03_sperre'
           Grund  = @('finger=1', 'ueberstand=14')
           Setze  = @('weich=0.5') }
}

# Die urspruenglichen Fotos, an ihrer Pruefsumme erkannt. Nur aus ihnen
# darf ein Grundbild entstehen: Aus einem schon zusammengesetzten Foto
# hielte die Fingersuche den warmen Hauptknopf fuer Haut.
$Originale = @{
    '1' = 'e6b0668e378a3361b0dc25f2b6bc47b90b800f470b0fe95c472e874765dfae0d'
    '3' = 'e2ef229a981b74f25eba0f6f5492824130a814c61f148abaa04e80b978770f85'
    '5' = 'fcd223bd8fed78436d7019b45adb0ce425c3dba70b9ac70cf9d3641d4cd2f71a'
    '7' = '3bda93b5c4a36275494eba5b359e2f7eafded9df734b6e570a5e56289e350abf'
}

function Vor([string] $p) { return $p.Replace('\', '/') }

foreach ($n in $Fotos) {
    if (-not $Plan.Contains("$n")) { Schlecht "Foto $n kennt dieses Script nicht (1, 3, 5, 7)."; exit 1 }
}

# -------------------------------------------------------------- Uebersetzen
Schritt 'Werkzeug'
$Klassen = Join-Path $Bau 'kl'
$null = New-Item -ItemType Directory -Force -Path $Klassen
$aus = Lies $Javac @('-encoding', 'UTF-8', '-d', $Klassen, (Join-Path $Ordner 'Einsetzen.java'))
if ($LASTEXITCODE -ne 0) { Schlecht 'Einsetzen.java laesst sich nicht uebersetzen:'; $aus | ForEach-Object { Write-Host "         $_" }; exit 1 }
Gut 'Einsetzen.java uebersetzt'

function Rufe([string[]] $werte) {
    $aus = Lies $Java (@('-cp', $Klassen, 'Einsetzen') + $werte)
    if ($LASTEXITCODE -ne 0) { $aus | Select-Object -First 8 | ForEach-Object { Write-Host "         $_" }; return $false }
    return $true
}

# ---------------------------------------------------------------- Grundbild
if ($Grund) {
    Schritt 'Grundbilder aus den urspruenglichen Fotos'
    foreach ($n in $Fotos) {
        # Die urspruenglichen Fotos liegen nicht mehr in der App. Wer ein
        # Grundbild neu braucht, holt sie aus dem Verlauf des Repositorys in
        # einen Ordner AUSSERHALB des Projekts und nennt ihn mit -Original.
        if ($Original) { $foto = Join-Path $Original "einfuehrung_$n.jpg" } else { $foto = Join-Path $Res "einfuehrung_$n.jpg" }
        if (-not (Test-Path -LiteralPath $foto)) { Schlecht "Fehlt: $foto"; exit 1 }
        $summe = (Get-FileHash -LiteralPath $foto -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($summe -ne $Originale["$n"]) {
            Schlecht "einfuehrung_$n.jpg ist nicht mehr das urspruengliche Foto. Grundbild bleibt, wie es ist."
            Hinweis 'Das urspruengliche Foto liegt im Verlauf des Repositorys.'
            exit 1
        }
        $werte = @('grund', "foto=$(Vor $foto)", "ecken=$($Plan["$n"].Ecken)",
                   "aus=$(Vor (Join-Path $Ordner "grund_$n.jpg"))", "maske=$(Vor (Join-Path $Ordner "maske_$n.png"))") + $Plan["$n"].Grund
        if (-not (Rufe $werte)) { Schlecht "Grundbild $n gescheitert."; exit 1 }
        Gut "grund_$n.jpg, maske_$n.png"
    }
    Hinweis 'Grundbilder ansehen (aufgehellt): Das Display muss ganz schwarz sein.'
    exit 0
}

# ------------------------------------------------------- Bilder aktuell?
Schritt 'Bildschirmbilder'
if ($Ungeprueft) {
    Hinweis 'UNGEPRUEFT: Ob die Bildschirmbilder zu den Beispieldaten passen, wurde nicht nachgesehen.'
} else {
    $standDatei = Join-Path $Wurzel 'bilder\stand.txt'
    $bildStand = ''
    if (Test-Path -LiteralPath $standDatei) { $bildStand = "$([IO.File]::ReadAllLines($standDatei) | Select-Object -First 1)".Trim() }
    if ($bildStand -notmatch '^[0-9a-f]{40}$') {
        Schlecht 'bilder\stand.txt fehlt oder nennt keinen gesicherten Stand. Erst .\werkzeug\bilder-holen.ps1.'
        exit 1
    }
    $null = Lies 'git' @('diff', '--quiet', $bildStand, 'HEAD', '--', 'app/src/androidTest/java/com/yskar/wallet/Bildschirmbilder.kt')
    if ($LASTEXITCODE -ne 0) {
        Schlecht 'Der Testrahmen hat sich seit der Aufnahme geaendert. Erst .\werkzeug\bilder-holen.ps1.'
        exit 1
    }
    Gut "aufgenommen aus Stand $($bildStand.Substring(0, 7)), Testrahmen seither unveraendert"
}

# ----------------------------------------------------------------- Einsetzen
Schritt 'Einsetzen'
foreach ($n in $Fotos) {
    $p = $Plan["$n"]
    $grundbild = Join-Path $Ordner "grund_$n.jpg"; $maske = Join-Path $Ordner "maske_$n.png"
    $schirm = Join-Path $Wurzel "bilder\$($p.Schirm).png"
    foreach ($d in @($grundbild, $maske, $schirm)) {
        if (-not (Test-Path -LiteralPath $d)) { Schlecht "Fehlt: $d"; exit 1 }
    }
    if ($Vorschau) { $ziel = Join-Path $Bau "vorschau_$n.png" } else { $ziel = Join-Path $Res "einfuehrung_$n.jpg" }
    $werte = @('setze', "foto=$(Vor $grundbild)", "maske=$(Vor $maske)", "schirm=$(Vor $schirm)",
               "schrift=$(Vor $Schrift)", "ecken=$($p.Ecken)", "aus=$(Vor $ziel)", 'guete=0.90') + $p.Setze
    if (-not (Rufe $werte)) { Schlecht "Foto $n gescheitert."; exit 1 }
    $kb = [math]::Round((Get-Item -LiteralPath $ziel).Length / 1KB)
    Gut ("einfuehrung_{0}  <-  {1}   ({2} KB)" -f $n, $p.Schirm, $kb)
}

Write-Host ''
if ($Vorschau) { Hinweis "Vorschau unter $Bau - nichts in der App geaendert." }
else { Hinweis 'Die Fotos in der App sind ersetzt. Ansehen, dann sichern.' }
