# Phase 3 – Designsystem

Stand: 09.11.2026 · `src/stil.css`

Blau führt. Die Marke ist an jeder Stelle sichtbar, an der der Nutzer etwas
tut – und nirgends als Dekoration.

---

## 1. Farben

### Flächen

| Rolle | Wert | Variable |
|---|---|---|
| Hintergrund | `#080D18` | `--grund` |
| Karte, zweite Ebene | `#111827` | `--karte` |
| Fläche hoch, Felder, aktive Zeile | `#172235` | `--flaeche` |

Drei Ebenen, mehr nicht. Tiefe entsteht aus Fläche und Rand, nicht aus
Schatten – `--schein` ist `none`.

### Text

| Rolle | Wert | Variable |
|---|---|---|
| Haupttext | `#F4F7FC` | `--text` |
| Lesetext | `#C3CCDA` | `--lesen` |
| Sekundär | `#8D9BB0` | `--leise`, `--blass` |
| Inaktiv | `#5A6678` | `--aus` |

### Blau – eine Farbe, drei Rollen

Das ist die einzige Stelle, an der das System von der Vorgabe abweicht, und
zwar aus einem messbaren Grund.

| Variable | Wert | Rolle |
|---|---|---|
| `--blau` | `#2F7BFF` | **Die Markenfarbe.** Akzente, Zeichen, Linien, Fokusring, Rahmen, Diagramme, aktive Anzeiger |
| `--blau-fuell` | `#2563D6` | Füllung von Knöpfen, die Text tragen |
| `--blau-druck` | `#1F52BD` | Gedrückter Zustand |
| `--blau-text` | `#6BA2FF` | Blau **als Text** auf dunkler Fläche |

**Warum drei statt einer:** `#2F7BFF` trägt weißen Text nur mit **3,62 : 1**.
WCAG AA verlangt für normalen Text 4,5 : 1. Ein Hauptknopf in reinem
`#2F7BFF` mit weißer Beschriftung wäre also nicht barrierefrei – gemessen,
nicht geschätzt. Zwei Stufen dunkler löst das bei **5,48 : 1**, ohne dass die
Farbe als andere Farbe gelesen wird.

Als reine UI-Farbe – Zeichen, Rahmen, Anzeiger, Linien – gilt die Schwelle
3 : 1. Dort wird `#2F7BFF` unverändert verwendet und liegt mit 4,99 : 1 auf
Grund und 4,10 : 1 auf Fläche hoch sicher darüber. Die Marke bleibt also
überall exakt `#2F7BFF`; nur dort, wo Text daraufliegt, trägt sie etwas tiefer.

### Richtung

| Rolle | Wert | Variable |
|---|---|---|
| Gewinn | `#32C98A` | `--gruen` |
| Verlust | `#F05D68` | `--rot` |
| Achtung | `#F0B35B` | `--bern` |

### Getönte Flächen

`--blau-flaeche` (12 %), `--blau-flaeche-stark` (18 %), `--blau-rand` (32 %) –
alle aus `#2F7BFF`. Dasselbe Muster für Grün, Rot und Bernstein. Keine harten
Farbwerte mehr im Blatt: 48 davon wurden auf diese Tokens gelegt.

---

## 2. Kontraste – gerechnet

Alle Werte nach WCAG 2.1 relativer Leuchtdichte.
**AA** = 4,5 : 1 (normaler Text) · **AA-UI** = 3 : 1 (große Schrift, Bedienelemente)

| Paar | Verhältnis | AA | AA-UI |
|---|---|---|---|
| Text auf Grund | **18,09** | ✅ | ✅ |
| Text auf Karte | **16,52** | ✅ | ✅ |
| Text auf Fläche | **14,85** | ✅ | ✅ |
| Sekundär auf Grund | **6,89** | ✅ | ✅ |
| Sekundär auf Karte | **6,29** | ✅ | ✅ |
| Sekundär auf Fläche | **5,66** | ✅ | ✅ |
| Grün auf Grund | **9,12** | ✅ | ✅ |
| Grün auf Karte | **8,33** | ✅ | ✅ |
| Rot auf Grund | **5,99** | ✅ | ✅ |
| Rot auf Karte | **5,47** | ✅ | ✅ |
| `--blau` auf Grund | **4,99** | ✅ | ✅ |
| `--blau` auf Karte | **4,56** | ✅ | ✅ |
| `--blau` auf Fläche | **4,10** | – | ✅ |
| Weiß auf `--blau-fuell` | **5,48** | ✅ | ✅ |
| `--blau-text` auf Fläche | **6,26** | ✅ | ✅ |

Zwei Zeilen tragen bewusst kein AA: `--blau` auf Fläche hoch wird
ausschließlich für Zeichen, Rahmen und Anzeiger verwendet, nie für Fließtext.
Für diese Verwendung ist 3 : 1 die Anforderung, und die ist erfüllt.

Nachrechnen: `python3 docs/werkzeug/kontrast.py`

---

## 3. Typografie

**Instrument Sans** für alles, **Geist Mono** nur für Adressen und Kennungen.
Zahlen stehen in der Textschrift mit Tabellenziffern, damit Beträge in Listen
untereinander stehen.

| Rolle | Größe / Zeile / Schnitt |
|---|---|
| Betrag groß | 56 / 60 / 600 |
| Seitentitel | 28 / 34 / 600 |
| Mittiger Titel | 17 / 22 / 600 |
| Abschnitt | 22 / 28 / 600 |
| Fließtext | 15 / 21 / 400 |
| Zeilentext | 15 / 20 / 500 |
| Nebentext | 13 / 18 / 400 |

---

## 4. Maße

**Radien** 12 (klein) · 18 (Knopf) · 28 (Karte) · 999 (Pille)
**Abstände** 4 · 8 · 12 · 16 · 20 · 24 · 32
**Trefferflächen** mindestens 44 px, Hauptknöpfe 56 px

Sichere Bereiche: `--oben` und `--unten` aus
`--safe-area-inset-*`, von Capacitor eingetragen. Android zeichnet bis unter
Statusleiste und Gestenbalken.

---

## 5. Bausteine

| Baustein | Klasse | Aussehen |
|---|---|---|
| Hauptknopf | `.knopf` | `--blau-fuell`, weiß, Radius 18, 56 px hoch |
| Zweitknopf | `.knopf.zweit` | `--karte`, Text hell |
| Gefahrknopf | `.knopf.gefahr` | `--rot-flaeche`, Text `--rot` |
| Textknopf | `.knopf.text` | ohne Fläche, Text `--blau-text` |
| Karte | `.karte` | `--karte`, Radius 28 |
| Pille / Filter | `.chip` | aktiv `--blau-fuell` mit weißem Text |
| Hinweis | `.kasten` | getönte Fläche + Zeichen, nie nur Farbe |
| Fokusring | `:focus-visible` | 2 px `--blau`, 2 px Abstand |

---

## 6. Was dieses System nicht tut

Ausdrücklich ausgeschlossen, weil es sonst nach Baukasten aussieht:

- Keine Glasflächen, keine Unschärfe als Dekoration.
- Keine Farbverläufe außer den beiden verjüngten Markenbändern aus dem Logo.
- Keine farbigen Leuchtschatten.
- Keine Karte, die nur Luft umschließt.
- Keine Farbe als einziger Bedeutungsträger – Grün und Rot stehen immer
  zusammen mit Vorzeichen oder Zeichen, damit sie auch bei Farbfehlsicht
  lesbar bleiben.

---

## 7. Native Hülle

Der Android-Starthintergrund muss der Oberfläche entsprechen, sonst springt
die Farbe beim Start sichtbar.

| Ort | Wert |
|---|---|
| `android/app/src/main/res/values/colors.xml` → `grund` | `#080D18` |
| `colorPrimary`, `colorPrimaryDark` | `#080D18` |
| `colorAccent` | `#2F7BFF` |
| `capacitor.config.ts` → `backgroundColor` | `#080D18` |

---

## 8. Geprüft

Am 09.11.2026 in Chromium bei 412 × 915, Gerätefaktor 2:

- Einstieg, Wiederherstellen, PIN, Wallet, Activity, Kontakte, Einstellungen
- `--grund` kommt als `rgb(8, 13, 24)` an, `--blau` als `#2F7BFF`,
  Hauptknopf als `rgb(37, 99, 214)` mit weißer Schrift
- **Keine Konsolenfehler** im gesamten Durchlauf
- Live-Daten: `200 /api/v2/account/…`, `200 /api/v2/summary`
