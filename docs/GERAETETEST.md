# Gerätetest – was nur auf dem Telefon prüfbar ist

Stand: 09.11.2026 · gehört zu Phase 9

Automatisch geprüft sind: **183 Unit-Tests** (Rechnung, Regeln, Grenzen) und
der **Durchlauf durch alle 22 Bildschirme** (`npm run durchlauf`).

Was dort **nicht** geprüft werden kann, steht hier. Diese Punkte hängen an
echter Android-Hardware: am Fingerabdrucksensor, am Keystore, am
Fensterflaggen-Mechanismus, an der Kamera und daran, wie Android eine
Installation über eine ältere Fassung behandelt. Ein Browser auf einem
Linux-Server kann davon nichts nachstellen, und so zu tun wäre eine
Scheinprüfung.

**Vor dem Veröffentlichen ist diese Liste einmal vollständig durchzugehen.**

---

## Vorbereitung

| | |
|---|---|
| Gerät | Android 7 oder neuer (`minSdk 24`) |
| APK | aus **Actions → App bauen → Artifacts** |
| Wichtig | Eine Wallet mit **echtem Guthaben** nur verwenden, wenn die 12 Wörter sicher notiert sind |

> Für die meisten Punkte genügt eine frische Testwallet. Nur die mit **[echt]**
> markierten Schritte brauchen einen kleinen echten Betrag.

---

## 1. Installation und Update

| # | Schritt | Erwartet | ✓ |
|---|---|---|---|
| 1.1 | APK öffnen, Installation aus dieser Quelle erlauben | App installiert | ☐ |
| 1.2 | **Über eine ältere Fassung installieren** | Update geht durch, **die vorhandene Wallet bleibt** | ☐ |
| 1.3 | App starten | Kein Farbsprung zwischen Android-Startfenster und App (beide `#080D18`) | ☐ |
| 1.4 | Fingerabdruck des Zertifikats prüfen | Stimmt mit dem der vorigen Releases überein | ☐ |

> 1.2 ist der wichtigste Punkt der ganzen Liste. Schlägt er fehl, müssten
> Nutzer deinstallieren und ihre Wallet neu einrichten.

## 2. Wallet anlegen und wiederherstellen

| # | Schritt | Erwartet | ✓ |
|---|---|---|---|
| 2.1 | Neue Wallet erstellen | 12 Wörter erscheinen erst nach „Wörter zeigen" | ☐ |
| 2.2 | **Screenshot versuchen, während die Wörter sichtbar sind** | Android verweigert ihn | ☐ |
| 2.3 | Kontrollfrage beantworten, PIN setzen | Wallet öffnet sich | ☐ |
| 2.4 | App entfernen, neu installieren, **mit denselben 12 Wörtern wiederherstellen** | **Dieselbe Adresse** wie vorher | ☐ |
| 2.5 | Eine Wallet aus einer **älteren Fassung** wiederherstellen | Dieselbe Adresse – das Tresorformat ist unverändert | ☐ |
| 2.6 | Falsche Wörter eingeben | Klare Ablehnung, keine leere Wallet | ☐ |

## 3. PIN, Biometrie, Sperre

| # | Schritt | Erwartet | ✓ |
|---|---|---|---|
| 3.1 | Falsche PIN 5× eingeben | Wartezeit erscheint und verdoppelt sich | ☐ |
| 3.2 | Fingerabdruck einschalten, App sperren, mit Finger öffnen | Öffnet | ☐ |
| 3.3 | Finger eines **anderen** Nutzers | Öffnet **nicht** | ☐ |
| 3.4 | Sperrzeit auf „sofort", App in den Hintergrund und zurück | Sofort gesperrt | ☐ |
| 3.5 | Sperrzeit 1 Minute, 10 s weg | **Nicht** gesperrt | ☐ |
| 3.6 | Sichtschutz an, Screenshot auf beliebiger Seite | Verweigert | ☐ |
| 3.7 | App im Übersichtsfenster (Task-Switcher) betrachten | Inhalt verdeckt | ☐ |

## 4. Senden und Empfangen

| # | Schritt | Erwartet | ✓ |
|---|---|---|---|
| 4.1 | Empfangen öffnen, QR zeigen | QR erscheint, Adresse darunter | ☐ |
| 4.2 | Adresse kopieren | Vollständige Adresse in der Zwischenablage | ☐ |
| 4.3 | QR mit einem zweiten Gerät scannen | Adresse wird korrekt gelesen | ☐ |
| 4.4 | **QR-Scanner** öffnen | Kamera startet, Berechtigung wird gefragt | ☐ |
| 4.5 | Ungültige Adresse eingeben | Senden bleibt gesperrt | ☐ |
| 4.6 | Adresse eines **anderen Netzes** eingeben | Abgelehnt | ☐ |
| 4.7 | **[echt]** Kleinbetrag senden, Prüfschritt lesen | Empfänger, Betrag und Gebühr stimmen | ☐ |
| 4.8 | **[echt]** Schieben und bestätigen | Zahlung erscheint als wartend | ☐ |
| 4.9 | **[echt]** Warten, bis sie in einem Block steht | Wechselt auf bestätigt, Guthaben stimmt | ☐ |
| 4.10 | **Flugmodus an**, senden versuchen | Gesperrt mit Hinweis, **kein** halb gesendeter Zustand | ☐ |

## 5. Netz und Marktdaten

| # | Schritt | Erwartet | ✓ |
|---|---|---|---|
| 5.1 | Wallet nach unten scrollen | Netzkarte zeigt Block, Hashrate, Miner, Belohnung | ☐ |
| 5.2 | Werte mit `yskar.vercel.app/api/v2/summary` vergleichen | Stimmen überein | ☐ |
| 5.3 | Markets öffnen **ohne** Schlüssel | Liste erscheint, ggf. Hinweis auf die Bremse | ☐ |
| 5.4 | Demo-Schlüssel in Einstellungen eintragen | „Schlüssel hinterlegt", nur letzte 4 Zeichen sichtbar | ☐ |
| 5.5 | Markets erneut öffnen | Lädt zügig, keine Bremse mehr | ☐ |
| 5.6 | Coin öffnen, **mit dem Finger über den Chart wischen** | Fadenkreuz folgt, Kurs und Datum werden abgelesen | ☐ |
| 5.7 | Zeiträume durchschalten (1 Std. … Max) | Jeder lädt, Prozentzahl bezieht sich auf den Zeitraum | ☐ |
| 5.8 | Flugmodus an, Markets öffnen | Letzter Stand mit Altersangabe, **Wallet unberührt** | ☐ |

## 6. Portfolio und Watchlist

| # | Schritt | Erwartet | ✓ |
|---|---|---|---|
| 6.1 | Position anlegen (Menge + Einstand) | Wert und Gewinn stimmen nachgerechnet | ☐ |
| 6.2 | **Versuchen, YSR von Hand einzutragen** | Nicht möglich, Begründung erscheint | ☐ |
| 6.3 | Position mit 8 Nachkommastellen | Exakt so gespeichert | ☐ |
| 6.4 | Watchlist füllen, Reihenfolge ändern | Reihenfolge bleibt | ☐ |
| 6.5 | App beenden und neu starten | Positionen und Watchlist sind da | ☐ |
| 6.6 | Währung auf USD stellen | Alle Fiat-Werte wechseln, **YSR bleibt in YSR** | ☐ |

## 7. Android-Eigenheiten

| # | Schritt | Erwartet | ✓ |
|---|---|---|---|
| 7.1 | Gerät drehen | Bleibt im Hochformat (`screenOrientation="portrait"`) | ☐ |
| 7.2 | Schriftgröße im System auf **größte** Stufe | Nichts abgeschnitten, nichts überlappt | ☐ |
| 7.3 | Anzeigegröße auf größte Stufe | Dito | ☐ |
| 7.4 | Gestennavigation **und** Drei-Knopf-Leiste | Inhalte liegen nicht unter der Leiste | ☐ |
| 7.5 | System-Zurück auf jeder Seite | Führt eine Ebene zurück, nicht sofort aus der App | ☐ |
| 7.6 | System-Zurück auf der Wallet | Erst auf Wallet, dann aus der App | ☐ |
| 7.7 | App im Hintergrund, Speicher knapp, zurückholen | Zustand sinnvoll, keine Leerseite | ☐ |
| 7.8 | Akkusparmodus an, Benachrichtigung bei Eingang | Kommt an, ggf. verzögert | ☐ |
| 7.9 | Auf **kleinem** Bildschirm (360 dp) prüfen | Keine abgeschnittenen Zahlen | ☐ |

## 8. Konsensfassung 6 – zeitkritisch

> Aktivierung bei **Block 10 000**. Am 09.11.2026 stand das Netz bei 8 410.
> Diese Punkte sind **am Tag der Aktivierung** zu prüfen.

| # | Schritt | Erwartet | ✓ |
|---|---|---|---|
| 8.1 | In den letzten 3 Blöcken davor senden wollen | **Sendepause** mit Erklärung | ☐ |
| 8.2 | Direkt nach Block 10 000 **[echt]** senden | Geht durch; die Wallet baut Fassung 2 | ☐ |
| 8.3 | Knoten nicht erreichbar, senden wollen | Gesperrt – die Wallet rät die Fassung nicht | ☐ |
| 8.4 | Alte wartende Zahlung über die Aktivierung hinweg | Sinnvoller Zustand, kein Absturz | ☐ |

---

## Ergebnis festhalten

```
Gerät:        ______________________  Android: ______
APK:          yskar-wallet-______.apk
Datum:        ____________
Durchgeführt: ____________

Bestanden: ____ / ____    Fehlgeschlagen: ____
```

Fehlgeschlagene Punkte mit Nummer, Beobachtung und – wenn möglich – einem
Auszug aus `adb logcat` notieren. Die App sammelt die letzten Fehler selbst:
**Einstellungen → Version antippen** öffnet die Diagnose zum Kopieren. Sie
enthält **keine** Adresse und **keine** Beträge.
