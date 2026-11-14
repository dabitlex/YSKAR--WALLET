# Der Tresor — wie die zwölf Wörter auf dem Gerät liegen

Gegenstück zu `wallet/tresor.ts` und `wallet/biometrie.ts` der
[Vorlage](https://github.com/dabitlex/YSKAR--WALLET). Gleiche Aufgabe,
anderes Verfahren — und das aus einem benennbaren Grund.

---

## 1. Das Problem, das die Vorlage selbst benennt

In `tresor.ts` steht, ohne Beschönigung:

> Sechs Ziffern sind eine Million Möglichkeiten. Wer den verschlüsselten
> Tresor in die Hand bekommt, probiert sie in Stunden durch.

Das stimmt, solange der Tresor **allein aus der PIN** aufgeht. Genau da
setzt der native Nachbau an.

## 2. Die Lösung: ein Pfeffer aus dem Keystore

```
basis   = PBKDF2-HMAC-SHA512(PIN, salz, 400.000, 32 Byte)
pfeffer = HMAC-SHA256(Keystore-Schlüssel, "yskar-pfeffer-v1" ‖ salz)
VK      = HKDF-SHA256(ikm = basis, salt = pfeffer, info = "yskar-tresor-v1")

geheim  = AES-256-GCM(VK, iv, wörter), AAD = "yskar-tresor-v1"
```

Der Keystore-Schlüssel ist ein HMAC-SHA256-Schlüssel, der **nicht auslesbar**
ist: Die App kann mit ihm rechnen, aber ihn nicht herausholen. Auf Geräten
mit Sicherheitsprozessor liegt er in StrongBox.

**Damit ist der Angriff aus Abschnitt 1 tot.** Eine kopierte Tresordatei
nützt auf einem anderen Gerät nichts, auch mit bekannter PIN. Der Test
`kopierteDateiOeffnetAufAnderemGeraetNicht` prüft genau das.

Was bleibt, ist der Angriff am entsperrten Telefon in fremder Hand. Dagegen
steht die PIN und dagegen steht die Bremse.

**Was es nicht leistet:** Wer die App auf einem gerooteten, entsperrten Gerät
fernsteuert, kann den Keystore benutzen wie die App selbst. Der eigentliche
Schutz bleiben die aufgeschriebenen zwölf Wörter.

---

## 3. Biometrie — kryptografisch statt per Entscheidung der App

| | Vorlage | Nativ |
|---|---|---|
| Was liegt abgelegt | die PIN, im geschützten Speicher | nichts — der Tresorschlüssel, umschlossen |
| Wer prüft | die App liest nach der Bestätigung die PIN | der Keystore gibt ohne Sensor nichts heraus |
| Umgehbar durch | die App zu umgehen | nicht durch App-Code |

Der Schlüssel `yskar.biometrie.v1` ist AES-256-GCM mit
`setUserAuthenticationRequired(true)` und — ab API 30 —
`setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)`. Null Sekunden
Gültigkeit heißt: Bestätigung bei **jeder** Benutzung. Er umschließt den
Tresorschlüssel, nicht die Wörter.

`setInvalidatedByBiometricEnrollment(true)` macht ihn ungültig, sobald jemand
einen Fingerabdruck hinzufügt — sonst könnte, wer das entsperrte Telefon hat,
einfach den eigenen Finger anlernen. **Verloren geht dabei nichts:** Der
Tresor hängt weiter an der PIN, nur die Bequemlichkeit muss neu eingerichtet
werden.

Ein Geräte-Code als Ersatz ist bewusst nicht erlaubt (`BIOMETRIC_STRONG`
allein). Der Ersatz ist die eigene PIN der Wallet — wie in der Vorlage.

---

## 4. Die Bremse

Fünf Versuche frei, danach 30 s, 60 s, 120 s … gedeckelt bei 15 Minuten.

**Bewusst ohne Löschen nach n Fehlversuchen.** Das macht die App sonst zur
Waffe gegen ihren eigenen Besitzer: Ein Kind, das herumtippt, oder jemand,
der die PIN wirklich vergessen hat, verliert damit alles. Die zwölf Wörter
sind die Wiederherstellung, nicht die Löschung.

---

## 5. Gemessen auf dem Xiaomi 17 Ultra

| Vorgang | Dauer |
|---|---|
| Kryptokern-Selbsttest | 122 ms |
| Tresor einrichten | 2404 ms |
| Tresor öffnen | 1682 ms |
| Biometrie-Runde, zwei Bestätigungen | 9290 ms |

Die Biometrie-Runde ist am 09.11.2026 auf dem Gerät vollständig durchgelaufen:
Sensor erkannt (starke Biometrie), Tresorschlüssel umschlossen, Tresor allein
mit dem Finger wieder geöffnet, zwölf Wörter unverändert zurück, Testtresor und
Keystore-Schlüssel danach entfernt — geprüft mit `run-as ls files/`, es blieb
nichts liegen. Die neun Sekunden sind fast nur Wartezeit auf zwei
Fingerabdrücke; gerechnet wird darin zweimal PBKDF2.

Beides lief mit `androidx.biometric:biometric:1.1.0` — der stabilen Fassung
von 2021 — unter Android 17. Die Sorge, sie könnte mit API 37 nicht mehr
zurechtkommen, hat sich nicht bestätigt. Ein Wechsel auf 1.4.0-beta01 ist
damit nicht nötig.

Die Sekunden beim Einrichten und Öffnen sind die 400.000 PBKDF2-Runden —
dieselbe Zahl wie in der Vorlage. Ob sie bleiben soll, ist eine bewusste Entscheidung wert: Weil der
Pfeffer den Angriff außerhalb des Geräts ohnehin ausschließt und die Bremse
den Angriff auf dem Gerät begrenzt, trägt PBKDF2 hier weniger als in der
Vorlage. 200.000 Runden würden rund 0,85 s bedeuten. Der Wert steht in jeder
Tresorakte, lässt sich also später ändern, ohne bestehende Tresore unlesbar
zu machen.

---

## 6. Was wo liegt

| Datei | Inhalt | Android-API? |
|---|---|---|
| `tresor/Umschlag.kt` | Schlüsselableitung, AES-GCM, HKDF | nein |
| `tresor/Bremse.kt` | Wartezeiten | nein |
| `tresor/TresorAkte.kt` | die Akte als JSON | nein |
| `tresor/Tresor.kt` | Einrichten, Öffnen, PIN ändern, Biometrie | nein |
| `tresor/Pfeffer.kt` | Keystore-HMAC | ja |
| `tresor/BioSchluessel.kt` | Keystore-AES mit Sensorzwang | ja |
| `tresor/AndroidAblagen.kt` | Datei und Einstellungen | ja |

Die ersten vier sind frei von Android — deshalb prüfen **30 JVM-Unittests**
den ganzen Ablauf inklusive falscher PIN, Bremse, PIN-Wechsel und dem
Fremdgerät-Fall, nicht nur das, was man auf dem Telefon sieht.

Die Tresorakte wird **atomar** geschrieben (Nebendatei, dann umbenennen).
Ein Absturz mitten im Schreiben hinterlässt sonst eine halbe Tresordatei.

---

## 7. Was in die Oberfläche gehört

Diese drei Sätze sind keine Rechtstexte, sondern Bedienhinweise:

1. **Die zwölf Wörter sind die einzige Sicherung.** Wer sie verliert,
   verliert das Guthaben — es gibt niemanden, der sie zurücksetzen kann.
2. **Eine Kopie der Tresordatei ist wertlos.** Sie hängt am Keystore dieses
   Geräts. App deinstalliert, Daten gelöscht oder Gerät zurückgesetzt heißt:
   Tresor weg.
3. **Biometrie ersetzt die PIN nicht.** Nach einer Änderung an den
   Fingerabdrücken ist sie neu einzurichten.

---

## 8. Offen

- Automatisches Sperren nach Zeit im Hintergrund.
- Entsperrten Zustand sauber halten: Der Schlüssel lebt nur, solange die
  Wallet offen ist. Java-Strings lassen sich nicht löschen — darum gibt
  `Tresor` die Wörter als `ByteArray` heraus, das der Aufrufer mit `fill(0)`
  überschreiben kann und soll.
- Verschlüsselter Export für den Gerätewechsel (optional; die zwölf Wörter
  tun es auch).
