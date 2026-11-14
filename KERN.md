# YSKAR-Kryptokern in Kotlin — Stand und Beweislage

Portiert aus [dabitlex/YSKAR--WALLET](https://github.com/dabitlex/YSKAR--WALLET)
(MIT, The YSKAR developers), Stand `2f218d0`. Die Vorlage ist eine
funktionierende non-custodial Wallet in TypeScript auf Capacitor; dieses
Projekt baut sie nativ in Kotlin nach.

**Reihenfolge mit Absicht: erst der Kern, dann die Oberfläche.** Eine Wallet,
die Transaktionen unterschreibt, darf sich keinen Byte-Fehler erlauben. Ein
falsches Bit im Sighash signiert etwas anderes als angezeigt — im besten Fall
lehnt der Knoten ab, im schlimmsten geht Guthaben an eine Adresse, die
niemandem gehört.

---

## 1. Was bewiesen ist

Maßstab sind die Vektoren aus `tests/vergleichswerte.json` der Vorlage —
fünf Merkwortsätze, je zwei fertig signierte Transaktionen. Sie wurden von
der laufenden Wallet erzeugt, nicht von mir.

| Prüfung | Abdeckung |
|---|---|
| Merkwörter → öffentlicher Schlüssel | BIP39, PBKDF2-HMAC-SHA512, SLIP-0010, Ed25519 |
| Merkwörter → Adresse Index 0 und 1 | zusätzlich SHA-256 und bech32m |
| Rohbytes → Objekt → Rohbytes | Serialisierung beider Fassungen |
| txid aller zehn Transaktionen | SHA-256d über die Leitungsform |
| Signatur der Vorlage gilt über unserem Sighash | Sighash-Aufbau |
| **Neu unterschrieben ergibt dieselben 64 Byte** | **Sighash Bit für Bit** |

Die letzte Zeile ist der eigentliche Beweis. Ed25519 ist deterministisch
(RFC 8032): Dieselben Felder und derselbe Schlüssel ergeben immer dieselbe
Signatur. Weicht der Sighash um ein einziges Bit ab, ist die Signatur
vollständig anders. Ein bloßer Byte-Vergleich der Rohform würde das nicht
zeigen — der hätte die Signatur ja nur durchgereicht.

**35 Unittests, 0 Fehler.** Zusätzlich läuft derselbe Satz als Selbsttest auf
dem Gerät (Xiaomi 17 Ultra, Android 17, arm64): alle Prüfungen bestanden in
49 ms. Das war nicht überflüssig — es beweist, dass BouncyCastles Ed25519 auf
echter Hardware dasselbe rechnet wie auf der JVM.

```powershell
.\gradlew.bat testDebugUnitTest      # 35 Tests, laufen ohne Gerät
.\android.ps1 run -Retry             # Selbsttest auf dem Telefon
```

---

## 2. Was portiert ist

| Datei | Vorlage | Inhalt |
|---|---|---|
| `kern/Params.kt` | `params.ts` | Konsensparameter, soweit die Wallet sie braucht |
| `kern/Hashes.kt` | `hash.ts` | SHA-256, SHA-256d, HMAC-SHA512, Merkle mit Bereichstrennung |
| `kern/Codec.kt` | `codec.ts` | Little-Endian-Writer/Reader, Hex |
| `kern/Bech32m.kt` | `@scure/base` | bech32m nach BIP-350, streng |
| `kern/Address.kt` | `address.ts` | Adressen Version 0 und 1, Leitungsform, Sortierung |
| `kern/Bip39.kt` | `@scure/bip39` | Wortliste, Prüfsumme, Seed |
| `kern/Wallet.kt` | `wallet.ts` | SLIP-0010, Ed25519, Schlüsselpaar |
| `kern/Tx.kt` | `tx.ts` (Teil) | Überweisung Typ 1, Fassungen 1 und 2 |

Das Paket `kern` benutzt **keine einzige Android-API**. Darum laufen die
Tests auf der JVM in unter zwei Sekunden statt auf dem Gerät.

---

## 3. Was bewusst fehlt

| Nicht portiert | Warum |
|---|---|
| Typ 0 — Coinbase | baut der Miner, nie die Wallet |
| Typ 2 — Skript-Ausgabe | braucht den Skript-Interpreter (`script.ts`, 352 Zeilen) |
| Typ 3 — Sammelzahlung | mehrere Empfänger; nachrüstbar, derzeit ohne Anwendungsfall |
| Difficulty, Blockregeln | gehören in den Knoten, nicht ins Telefon |
| ZIP-215-Signaturprüfung | siehe unten |

`Tx.deserializeTransfer` wirft bei jedem anderen Typ eine klare Ausnahme,
statt Felder halb zu lesen. Den Verlauf liest die Wallet ohnehin als JSON vom
Spiegel, nicht als Rohbytes.

---

## 4. Die eine offene Sicherheitsfrage: ZIP-215

Der Konsens prüft Ed25519 **nicht** nach RFC 8032, sondern nach ZIP-215
(`CONSENSUS_V6.md` 7.8, in der Vorlage `sigcheck.ts` mit `zip215: true`):

- nicht-kanonische Punktkodierungen sind erlaubt,
- `S` muss kleiner als die Gruppenordnung sein,
- es gilt die Gleichung **mit Kofaktor**: `[8][S]B = [8]R + [8][k]A`.

**BouncyCastle prüft nach RFC 8032 und entscheidet in Grenzfällen anders.**

Für diese Wallet ist das heute folgenlos, weil sie nur eigene, frisch
erzeugte Signaturen nachrechnet — und dort stimmen beide Regeln überein. Das
Signieren selbst ist ohnehin identisch; RFC 8032 schreibt es deterministisch
vor, und genau das belegen die Vektoren.

Gefährlich würde es, wenn die App anfinge, **fremde** Signaturen zu bewerten.
Dann entschiede sie in konstruierbaren Grenzfällen anders als die Kette.
Deshalb heißt die Funktion `Tx.verifyOwnSignature` und nicht `verify`, und
deshalb steht die Warnung im Quelltext. Wer diese Grenze aufweichen will,
braucht vorher einen echten ZIP-215-Prüfer.

---

## 5. Netz

Aus `src/netz/api.ts` der Vorlage, unverändert gültig:

| Zweck | Adresse |
|---|---|
| Lesen (Spiegel) | `https://yskar.vercel.app` |
| Senden (Full Node) | meldet der Spiegel selbst, Vorgabe `https://pool.yskar.app` |
| Explorer | `https://www.yskar.app/explorer` |

Lesen geht an den Spiegel: `/api/v2/account/{adresse}`, `/api/v2/summary`,
`/api/v2/fees`, `/api/v2/account/{adresse}/verlauf`. Senden geht an den Full
Node, weil nur er Überweisungen in Blöcke bringt.

Die Adresse des Knotens ist **nicht** fest verdrahtet: Die stillgelegte Route
`POST /api/v2/tx` des Spiegels antwortet mit HTTP 410 und nennt sie. So
braucht ein Umzug des Knotens keine neue APK. Das ist eine kluge Lösung und
wird im Port übernommen.

Was hinausgeht, sind fertig signierte Bytes. Kein Server kann Betrag oder
Empfänger ändern, ohne die Signatur zu brechen.

---

## 6. Abhängigkeiten und was sie kosten

| Bibliothek | Zweck | Warum nötig |
|---|---|---|
| `org.bouncycastle:bcprov-jdk18on:1.86` | Ed25519 | Androids eigenes Ed25519 gibt es erst ab API 33, `minSdk` ist 26 |
| `org.json:json:20260814` | nur Test | liest die Vergleichswerte |

Die leichte API von BouncyCastle registriert **keinen** JCE-Provider und kann
darum auch keinen anderen stören. Das Debug-APK wächst dadurch auf 17,9 MB;
der Release-Build verkleinert das mit R8 deutlich, weil nur Ed25519 benutzt
wird.

PBKDF2 ist absichtlich selbst ausgeschrieben statt über `SecretKeyFactory`
geholt: Ob `PBKDF2WithHmacSHA512` vorhanden ist, hängt bei Android von
Provider und API-Stufe ab, und ein stiller Rückfall auf SHA-1 erzeugte eine
**völlig andere Wallet**. Die Wortliste wird beim Laden gegen ihren SHA-256
geprüft, damit eine verfälschte Liste nicht unbemerkt andere Adressen liefert.

---

## 7. Nächste Schritte

1. ~~**Sicherer Tresor**~~ — **erledigt**, siehe `TRESOR.md`. Der
   Tresorschlüssel entsteht aus PIN und einem Keystore-Pfeffer; eine
   kopierte Tresordatei ist auf fremder Hardware wertlos.
2. ~~**Netzanbindung**~~ — **erledigt**, siehe `NETZ.md`. Lesen läuft
   live gegen den Spiegel; Senden ist gebaut, aber noch nie ausgeführt.
3. ~~**Oberfläche**~~ — **erledigt**, siehe `OBERFLAECHE.md` und
   `SENDEN.md`. Anlegen, Wiederherstellen, Sperre, Guthaben, Verlauf,
   Empfangen, Senden mit Prüfschritt.
4. ~~**Erster echter Versand**~~ — **erledigt im November 2026**: ein
   Kleinstbetrag, Guthaben danach auf die letzte Stelle wie vorausgesagt.
   Siehe `SENDEN.md` Abschnitt 7.

Damit ist die Kette vom Merkwort bis zum Block einmal vollständig
durchlaufen — auf echtem Geld, ohne Testnetz.
