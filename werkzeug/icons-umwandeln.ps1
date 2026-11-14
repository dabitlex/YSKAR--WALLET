# icons-umwandeln.ps1 - SVG aus design-neu\icons zu Android-Vektoren
#
# Die Symbole der Vorlage sind Phosphor Icons (MIT) - 30 Dateien, auch wenn
# DESIGN-NEU.md von 31 spricht. Jede Datei besteht
# aus genau einem <path> im Feld 0 0 256 256. Daraus wird je eine Datei
# app\src\main\res\drawable\ic_<name>.xml.
#
#   .\werkzeug\icons-umwandeln.ps1
#
# Die Farbe steht im Vektor auf Weiss und wird in Compose ueber `tint`
# gesetzt - so wie die Vorlage `fill="currentColor"` benutzt.
#
# Das Script bricht ab, statt zu raten: Hat eine Datei nicht genau einen
# Pfad oder ein anderes Feld, stimmt die Annahme nicht mehr, und ein still
# falsch umgewandeltes Symbol faellt spaeter niemandem auf.

[CmdletBinding()]
param(
    [string] $Quelle = (Join-Path $PSScriptRoot '..\..\design-neu\icons'),
    [string] $Ziel   = (Join-Path $PSScriptRoot '..\app\src\main\res\drawable')
)

$ErrorActionPreference = 'Stop'

$dateien = Get-ChildItem -Path $Quelle -Filter '*.svg' | Sort-Object Name
if ($dateien.Count -eq 0) { throw "Keine SVG-Dateien in $Quelle gefunden." }

# Ohne BOM schreiben: Eine XML-Datei mit BOM uebersetzt zwar, aber jede
# andere Datei in res\drawable hat keines.
$ohneBom = New-Object System.Text.UTF8Encoding $false

$n = 0
foreach ($d in $dateien) {
    $svg = [System.IO.File]::ReadAllText($d.FullName)

    if ($svg -notmatch 'viewBox="0 0 256 256"') {
        throw "$($d.Name): anderes Feld als 0 0 256 256."
    }
    $pfade = [regex]::Matches($svg, '<path\s+d="([^"]+)"\s*/>')
    if ($pfade.Count -ne 1) {
        throw "$($d.Name): $($pfade.Count) Pfade, erwartet genau einer."
    }
    if ($svg -match '<(circle|rect|line|polyline|polygon|g)\b') {
        throw "$($d.Name): enthaelt andere Formen als einen Pfad."
    }

    $name = 'ic_' + ($d.BaseName -replace '-', '_')
    $xml  = @"
<?xml version="1.0" encoding="utf-8"?>
<!-- Phosphor Icons, MIT. Erzeugt aus design-neu/icons/$($d.Name)
     mit werkzeug/icons-umwandeln.ps1 - nicht von Hand aendern. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="256"
    android:viewportHeight="256">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="$($pfade[0].Groups[1].Value)" />
</vector>
"@
    [System.IO.File]::WriteAllText((Join-Path $Ziel "$name.xml"), $xml, $ohneBom)
    $n++
}

Write-Host "$n Symbole nach $Ziel geschrieben."
