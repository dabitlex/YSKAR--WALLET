# messen.ps1 - Behauptungen ueber die Oberflaeche nachmessen
#
# Entstanden, weil "sieht gut aus" keine Aussage ist. Gemessen wird am
# Bildschirmfoto aus werkzeug\bilder-holen.ps1, nicht geschaetzt.
#
# Zwei Messungen:
#
#   -Rand (Vorgabe)  Wo endet der Inhalt, und wie viel bleibt darunter frei?
#                    Nur noch eine ZAHL, kein Urteil mehr. Ob ein
#                    Bildschirm rollen muss (REGELN.md Regel 29), sagt
#                    seit dem 10.11.2026 der Bildschirm selbst:
#                    bilder\rollen.txt, geschrieben von Bildschirmbilder
#                    und angezeigt von bilder-holen.ps1. Am Bild liess
#                    sich das nie entscheiden - ein Bildschirm mit fest
#                    stehendem Fuss reicht immer bis unten.
#
#   -QrFuellung      Wie viel der weissen Karte fuellt der QR-Code?
#                    58 Prozent waren zu wenig; gemessen sind 85,4
#                    (REGELN.md Regel 30).
#
#   .\werkzeug\messen.ps1 bilder\09_senden_pruefen.png
#   .\werkzeug\messen.ps1 bilder\*.png
#   .\werkzeug\messen.ps1 bilder\06_empfangen.png -QrFuellung
#
# Annahmen, damit die Zahlen einzuordnen sind:
#   - Die Bilder sind 1200x2608 (Xiaomi 17 Ultra, 480 dpi, Dichte 3).
#   - Seit dem 10.11.2026 zeichnet der Testrahmen MIT den Raendern fuer
#     Status- und Navigationsleiste (oben 145, unten 48 Bildpunkte auf
#     diesem Geraet). "frei" enthaelt also immer mindestens den unteren
#     Rand. Bilder von davor haben diese Raender nicht.
#   - Als Untergrund gilt die Farbe unten links im Bild. Dort liegt bei
#     allen Bildschirmen nichts.
#   - Die Karte unter dem QR-Code ist heller als der Untergrund.

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string[]] $Pfad,
    [switch]   $QrFuellung,
    [int]      $Leisten = 48
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

# GetPixel ueber drei Millionen Punkte ist in PowerShell nicht zu
# gebrauchen. Darum einmal LockBits und danach im Byte-Puffer rechnen.
function Lade([string] $datei) {
    $bmp  = New-Object System.Drawing.Bitmap $datei
    $rect = New-Object System.Drawing.Rectangle 0, 0, $bmp.Width, $bmp.Height
    $dat  = $bmp.LockBits($rect,
                [System.Drawing.Imaging.ImageLockMode]::ReadOnly,
                [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $n      = $dat.Stride * $bmp.Height
    $puffer = New-Object byte[] $n
    [System.Runtime.InteropServices.Marshal]::Copy($dat.Scan0, $puffer, 0, $n)
    $bmp.UnlockBits($dat)
    $erg = [pscustomobject]@{
        Breite = $bmp.Width
        Hoehe  = $bmp.Height
        Stride = $dat.Stride
        Bytes  = $puffer
    }
    $bmp.Dispose()
    return $erg
}

# Reihenfolge im Puffer ist BGRA, nicht RGBA.
function Punkt($bild, [int] $x, [int] $y) {
    $i = $y * $bild.Stride + $x * 4
    return @($bild.Bytes[$i + 2], $bild.Bytes[$i + 1], $bild.Bytes[$i])
}

function Helligkeit($rgb) {
    return [int](($rgb[0] + $rgb[1] + $rgb[2]) / 3)
}

function Abstand($a, $b) {
    return [Math]::Abs($a[0] - $b[0]) +
           [Math]::Abs($a[1] - $b[1]) +
           [Math]::Abs($a[2] - $b[2])
}

function Mess-Rand($bild, [string] $name) {
    $grund = Punkt $bild 4 ($bild.Hoehe - 5)

    # Von unten nach oben die erste Zeile suchen, die sich wirklich vom
    # Untergrund abhebt. Drei Punkte als Schwelle, damit ein einzelnes
    # weichgezeichnetes Pixel keine Zeile zu Inhalt macht.
    # Indexrechnung ausgeschrieben, nicht ueber Punkt/Abstand: Bei einem
    # Bildschirm, dessen Inhalt weit oben endet, sind das ueber eine
    # Million Vergleiche, und mit Funktionsaufrufen dauert das Minuten.
    $b = $bild.Bytes; $s = $bild.Stride
    $gr = $grund[0]; $gg = $grund[1]; $gb = $grund[2]
    $ende = -1
    for ($y = $bild.Hoehe - 1; $y -ge 0; $y--) {
        $z = $y * $s
        $treffer = 0
        for ($x = 0; $x -lt $bild.Breite; $x += 2) {
            $i = $z + $x * 4
            $d = [Math]::Abs($b[$i + 2] - $gr) +
                 [Math]::Abs($b[$i + 1] - $gg) +
                 [Math]::Abs($b[$i] - $gb)
            if ($d -gt 24) {
                $treffer++
                if ($treffer -ge 3) { break }
            }
        }
        if ($treffer -ge 3) { $ende = $y; break }
    }

    if ($ende -lt 0) {
        Write-Host ("  {0,-34} leer - kein Inhalt gefunden" -f $name) -ForegroundColor Yellow
        return
    }

    $frei = $bild.Hoehe - 1 - $ende

    # Bewusst ohne Urteil. Frueher stand hier "passt" / "ZU ENG" nach einer
    # Schwelle von 192 Punkten - und das Urteil war zweimal falsch: einmal,
    # weil der Testrahmen hoeher zeichnete als die App, und danach, weil er
    # die Raender selbst abzog und die Schwelle doppelt zaehlte. Die Zahl
    # stimmt immer; was sie bedeutet, steht in bilder\rollen.txt.
    $marke = if ($frei -le $Leisten) { '   (bis an den unteren Rand)' } else { '' }
    Write-Host ("  {0,-34} Inhalt bis y={1,4}   darunter frei {2,4}{3}" -f
                $name, $ende, $frei, $marke)
}

function Mess-Qr($bild, [string] $name) {
    # Hier wird nicht gesucht, sondern gezaehlt: ueber eine Million Punkte.
    # Mit den Hilfsfunktionen Punkt/Helligkeit dauert das Minuten, darum
    # steht die Indexrechnung in dieser Funktion ausgeschrieben.
    $b = $bild.Bytes; $s = $bild.Stride
    $breite = $bild.Breite; $hoehe = $bild.Hoehe

    # Kartenzeilen sind die, in denen ein Viertel der Breite hell ist.
    $oben = -1; $unten = -1
    for ($y = 0; $y -lt $hoehe; $y++) {
        $z = $y * $s
        $hell = 0
        for ($x = 0; $x -lt $breite; $x += 2) {
            $i = $z + $x * 4
            if (($b[$i] + $b[$i + 1] + $b[$i + 2]) -ge 705) { $hell++ }
        }
        if ($hell -gt ($breite / 8)) {
            if ($oben -lt 0) { $oben = $y }
            $unten = $y
        }
    }
    if ($oben -lt 0) {
        Write-Host ("  {0,-34} keine helle Karte gefunden" -f $name) -ForegroundColor Yellow
        return
    }

    $mitte = [int](($oben + $unten) / 2)
    $z = $mitte * $s
    $links = -1; $rechts = -1
    for ($x = 0; $x -lt $breite; $x++) {
        $i = $z + $x * 4
        if (($b[$i] + $b[$i + 1] + $b[$i + 2]) -ge 705) {
            if ($links -lt 0) { $links = $x }
            $rechts = $x
        }
    }
    $kbreite = $rechts - $links + 1

    # Die Karte hat runde Ecken. Ein Rechteck um sie herum enthaelt dort
    # also Untergrund, und der ist dunkel - wer die dunklen Module
    # einfach im Rechteck einrahmt, misst die Ecken mit und bekommt
    # 100 Prozent Fuellung heraus. Darum zaehlen nur die Zeilen, in denen
    # die Karte ihre volle Breite hat.
    $cl = $breite; $cr = -1; $co = $hoehe; $cu = -1
    for ($y = $oben; $y -le $unten; $y++) {
        $z = $y * $s
        $zl = -1; $zr = -1
        for ($x = $links; $x -le $rechts; $x++) {
            $i = $z + $x * 4
            if (($b[$i] + $b[$i + 1] + $b[$i + 2]) -ge 705) {
                if ($zl -lt 0) { $zl = $x }
                $zr = $x
            }
        }
        if ($zl -lt 0 -or ($zr - $zl + 1) -lt ($kbreite - 6)) { continue }

        for ($x = $zl; $x -le $zr; $x++) {
            $i = $z + $x * 4
            if (($b[$i] + $b[$i + 1] + $b[$i + 2]) -lt 300) {
                if ($x -lt $cl) { $cl = $x }
                if ($x -gt $cr) { $cr = $x }
                if ($y -lt $co) { $co = $y }
                if ($y -gt $cu) { $cu = $y }
            }
        }
    }
    if ($cr -lt 0) {
        Write-Host ("  {0,-34} Karte gefunden, aber kein Code darin" -f $name) -ForegroundColor Yellow
        return
    }

    # Verglichen wird in der Breite: Das Bild im Inneren steht in einem
    # Kasten mit aspectRatio(1f), die Hoehe folgt der Breite.
    $karte = $kbreite
    $code  = [Math]::Max($cr - $cl + 1, $cu - $co + 1)
    $quote = [Math]::Round(100.0 * $code / $karte, 1)

    Write-Host ("  {0,-34} Karte {1} px   Code {2} px   Fuellung {3} %" -f
                $name, $karte, $code, $quote)
    Write-Host ("  {0,-34} Ruhezone links {1}  rechts {2}  oben {3}  unten {4}" -f
                '', ($cl - $links), ($rechts - $cr), ($co - $oben), ($unten - $cu))
}

$dateien = @()
foreach ($p in $Pfad) { $dateien += Get-ChildItem -Path $p -File }
if ($dateien.Count -eq 0) { throw "Keine Datei zu $Pfad gefunden." }

Write-Host ''
foreach ($d in ($dateien | Sort-Object Name)) {
    $bild = Lade $d.FullName
    if ($bild.Breite -gt $bild.Hoehe) {
        Write-Host ("  {0,-34} {1}x{2} - Querformat, Messung wertlos" -f
                    $d.Name, $bild.Breite, $bild.Hoehe) -ForegroundColor Red
        continue
    }
    if ($QrFuellung) { Mess-Qr $bild $d.Name } else { Mess-Rand $bild $d.Name }
}

if (-not $QrFuellung) {
    Write-Host ''
    Write-Host '  Ob ein Bildschirm rollen muss, steht in bilder\rollen.txt -' -ForegroundColor Yellow
    Write-Host '  nicht in diesen Zahlen.' -ForegroundColor Yellow
}
Write-Host ''
