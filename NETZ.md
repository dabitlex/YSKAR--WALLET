# Die Netzschicht — Lesen vom Spiegel, Senden an den Knoten

Portiert aus `src/netz/api.ts`, `wallet/betrag.ts`, `wallet/notiz.ts` und
`wallet/fassung.ts` der [Vorlage](https://github.com/dabitlex/YSKAR--WALLET).

Aber nicht nach den TypeScript-Typen gebaut, sondern nach den **echten
Antworten** der laufenden API, mitgeschnitten am 10.11.2026 bei Höhe 8560.
Das war kein Übereifer: Die beiden weichen an fünf Stellen voneinander ab,
und jede einzelne hätte einen Parser zum Absturz gebracht.

---

## 1. Wo die API anders antwortet, als die Typen sagen

| Feld | Typ sagt | API liefert |
|---|---|---|
| `balance` | `string` | **Zahl**: `82345092565` |
| `nonce` / `nextNonce` | beide `string` | **Zahl** bzw. **Zeichenkette** — nebeneinander |
| `timestamp` | `string \| null` (ISO) | **Unixsekunden als Text**: `"1794261818"` |
| `memo` | `string \| null` | **Hex der Rohbytes**: `"59534b4152204d41494e"` = `YSKAR MAIN` |
| `amount`, `fee` | `string` | Zeichenkette ✓ |

Deshalb liest `Spiegel.zahl()` beide Formen und `Notiz.ausHex()` die Notiz.
Die App sieht danach nur noch **eine** Form: `Long` in kleinsten Einheiten
und lesbaren Text.

Die Mitschnitte liegen unter `app/src/test/resources/netz/` und sind die
Grundlage der Parsertests. Die drei ohne Konto (`summary`, `fees`,
`tx-410`) sind unverändert. In den Antworten zu einem Konto sind seit dem
13.11.2026 die **Werte erfunden** — Adressen, Kennungen, Höhen, Zeiten,
Beträge —, damit kein echtes Konto im Repository steht. Feldnamen,
Feldtypen und Reihenfolge sind die echten, und um die geht es. Ein Test, der ins Internet greift, ist
kein Test, sondern eine Wettervorhersage.

---

## 2. Ein Verdacht, der sich nicht bestätigt hat

Beim Prüfen fiel auf, dass `verlauf?richtung=ein` **byte-gleich** zur
ungefilterten Abfrage antwortet. Das sah nach einem ignorierten Parameter
aus.

Ist es nicht. `richtung=aus` liefert sauber fünf `out`-Einträge. Die ersten
fünf Einträge dieser Adresse sind schlicht alle eingehend — Pool-Anteile
zählen dazu. **Der Filter arbeitet korrekt**, und die Blätterung über `vor=`
ebenfalls.

Festgehalten, weil der Verdacht naheliegt und der nächste, der darauf
stößt, sonst dieselbe halbe Stunde verliert.

---

## 3. Wo der Knoten steht, sagt der Spiegel

```
POST https://yskar.vercel.app/api/v2/tx   →   HTTP 410
{"error":"moved_to_fullnode","fullnode":"https://pool.yskar.app", …}
```

Ein Umzug des Knotens braucht damit keine neue APK. Klug gelöst — und
übernommen.

Mit einer Verschärfung: Die genannte Adresse wird gegen
`^https://[a-z0-9.-]+(:\d{1,5})?/?$` geprüft, bevor sie übernommen wird.
Was aus einer Antwort kommt, bestimmt, wohin signierte Überweisungen gehen;
`http://`, ein Pfad oder `javascript:` werden verworfen und die bekannte
Vorgabe gilt weiter. Vier Testfälle decken das ab.

Der Transport lässt ohnehin nur `https` zu, und das Manifest setzt
`usesCleartextTraffic="false"`.

---

## 4. Keine HTTP-Bibliothek

`HttpURLConnection`, sonst nichts. Diese App spricht mit genau zwei
Adressen und holt dort JSON ab. Dafür eine weitere Abhängigkeit in eine
Wallet zu ziehen wäre Angriffsfläche ohne Gegenwert — und Androids
`HttpURLConnection` wird seit Jahren intern von OkHttp bedient, ist also
ein erprobter Unterbau ohne eigenes Paket.

---

## 5. Beträge: ganzzahlig, abgeschnitten, nie gerundet

`0,1 + 0,2` ist als `Double` nicht `0,3`. Bei Geld ist das kein
Schönheitsfehler, sondern ein Fehler. Darum durchgängig `Long` in
kleinsten Einheiten.

Und **nie aufrunden**: `0,99999999 YSR` erscheint als `0,99`, nicht als
`1,00`. Aufrunden zeigte mehr, als da ist.

| Einheiten | Anzeige |
|---|---|
| `128457210946` | `1.284,57210946` |
| `172839` | `0,00172839` |
| `1680` | `0,0000168` |
| `1` | `0,00000001` |

Notizen werden nach **Bytes** gekürzt, nicht nach Zeichen — die Kette
erlaubt 32 Byte. Ein Umlaut belegt zwei, ein Emoji vier, und getrennt wird
nie mitten in einem Zeichen.

---

## 6. Live gemessen, vom Telefon aus

Am 10.11.2026, Xiaomi 17 Ultra, in 1981 ms:

```
OK  Hoehe der Kette        Block 8561, Mempool 0
OK  Gebuehrenlage          mindestens 0,0000168 YSR, Fassung 1, 168 Byte
OK  Basisgroesse = Port    168 Byte, Port sagt 168
OK  Sendefassung           Fassung 1, senden erlaubt
OK  Full Node erkannt      https://pool.yskar.app
OK  Konto gelesen          ysr1…
OK  Guthaben               … YSR
OK  Verlauf                40 Eintraege, 0 wartend (fullnode)
OK  juengster Eintrag      POOL … YSR bei Block … - "YSKAR MAIN"
OK  Verlauf blaettern      3 Eintraege, weiter bei …
```

Adresse, Guthaben und die Stellen im Verlauf sind hier ausgelassen: Sie
gehörten zu einem echten Konto.

Die dritte Zeile ist die schönste: Der Knoten nennt **168 Byte** als Größe
einer Überweisung ohne Notiz — und `Tx.TRANSFER_BASE_BYTES` aus dem
portierten Kern sagt dieselbe Zahl, unabhängig davon hergeleitet. Zwei
getrennte Wege, dasselbe Ergebnis.

---

## 7. Wer wann fragt

Drei Takte, und keiner davon ist Push.

| Wann | Wie oft | Wer |
|---|---|---|
| App offen, Hauptbildschirm / Verlauf / Empfangen | alle 30 s | `WalletModel.beobachten` |
| App offen, nach dem Senden | alle 10 s | dasselbe, anderer Takt |
| App zu, Wächter eingeschaltet | alle 15 min | `Waechterdienst` |

Die Schleife im Vordergrund hängt an `repeatOnLifecycle(RESUMED)`. Sie läuft
also nur, solange jemand hinsieht, und hört beim Wegschalten auf. Eine
Schleife im ViewModel ohne diese Klammer liefe weiter, während das Telefon
in der Tasche steckt.

**Leise heißt leise.** Ein Hintergrundlauf setzt den Knopf nicht auf
„lädt …", und ein Funkloch alle dreißig Sekunden blinkt nicht rot über dem
Guthaben. Beides wäre Lärm für etwas, das niemand angestoßen hat.

Zwei Dinge sind dabei schiefgegangen und mussten repariert werden, bevor es
stimmte:

1. **Der Verlauf klappte zusammen.** `kontoLaden` setzte die nachgeladenen
   Seiten zurück. Im Vordergrundtakt hieß das: Wer blätterte, stand alle
   dreißig Sekunden wieder auf Seite eins.
2. **Einträge standen doppelt.** Kommt eine Bewegung hinzu, verschiebt sich
   die Seitengrenze um eins — und der letzte Eintrag der ersten Seite stand
   danach auch in der zweiten. `Stand.verlauf` entdoppelt darum über
   `Waechter.schluessel`.

### Warum kein Push

Echtes Push über Firebase hieße: Ein Server kennt die Adresse dieses Geräts
und beobachtet sie. Diese Wallet redet mit genau einem Gegenüber, und
niemand sonst weiß, welche Adresse hier liegt — das aufzugeben, um eine
Mitteilung zwei Minuten früher zu bekommen, wäre ein schlechter Tausch.
Dazu käme wieder die Abhängigkeit von den Play Services, die schon beim
Scanner vermieden wurde.

Der Wächter braucht nur die **Adresse**. Die steht unverschlüsselt in der
Tresorakte, weil sie öffentlich ist. Er öffnet keinen Tresor, kennt keine
PIN und sieht kein Wort; er könnte nichts unterschreiben, selbst wenn er
wollte.

Fünfzehn Minuten sind nicht gewählt, sondern die Untergrenze von
WorkManager. Bei einer Blockzeit von rund zehn Minuten passt das.

**Er ist aus, bis der Besitzer ihn einschaltet.** Er kostet Akku und fragt
regelmäßig nach genau dieser Adresse. Das ist eine Entscheidung, keine
Voreinstellung.

**Und er ist nicht garantiert.** WorkManager verspricht „irgendwann", nicht
„pünktlich" — und HyperOS ist dafür bekannt, Hintergrundarbeit früh
abzuräumen. Bleiben Mitteilungen aus, liegt es mit großer
Wahrscheinlichkeit daran und nicht am Code: In den Android-Einstellungen
der App muss die Akkusparbeschränkung aus und der Autostart erlaubt sein.
Das ist nachzuprüfen, sobald der Wächter einmal lief. **Eine Wallet darf
sich darauf nicht verlassen**: Die Bewegung steht im Verlauf, ob eine
Mitteilung kam oder nicht. Die Mitteilung ist Bequemlichkeit, keine
Buchführung.

Was er meldet, rechnet `Waechter.pruefe` aus — reine Funktion, ohne
Android-API, acht Unittests (`WaechterTest`). Die beiden Fälle, die wirklich
wehtun, stehen darunter:

- **Der erste Durchgang meldet nichts.** Sonst bekäme jeder, der den
  Wächter einschaltet, als Erstes seinen gesamten Verlauf als Mitteilungen.
- **Eine Überweisung an sich selbst ergibt zwei Meldungen.** Sie steht
  zweimal im Verlauf, als Ein- und als Ausgang. Mit der `txid` allein als
  Kennung fiele eine davon weg — und tauchte beim Nachladen als neu wieder
  auf. Der Schlüssel ist darum `txid:idx:art`.

---

## 8. Was fehlt

- Kein Zwischenspeicher: Jede Abfrage geht ans Netz. Für die Oberfläche
  braucht es einen letzten bekannten Stand, damit beim Start nicht auf eine
  leere Seite gestarrt wird (`wallet/letzterstand.ts` der Vorlage).
- Keine Wiederholung bei Netzaussetzern im Vordergrund. Der Wächter im
  Hintergrund hat sie (`Result.retry`).
- Der Selbsttest fragt das Netz beim App-Start. **In der fertigen App darf
  das nicht passieren** — dort fragt die Wallet, wenn der Nutzer es will.

---

## 9. Der Guthabenverlauf: zwei Messungen und eine Lücke

Der Spiegel nennt nur das heutige Guthaben. Für das Diagramm wird
rückwärts gerechnet (`netz/Guthabenverlauf.kt`). Dafür war zweierlei zu
klären, und beides ist am 10.11.2026 an einem echten Konto gemessen
worden — von Hand, einmalig, nicht im Testlauf.

**Wie viel gibt der Spiegel auf einmal?** `…/verlauf?limit=N`:

| verlangt | bekommen |
|---|---|
| 40 | 40 |
| 100 | 100 |
| 200 | 200 |
| 500, 1000, 5000 | 200, ohne Fehlermeldung |

Mehr als 200 je Anfrage gibt es nicht; wer mehr verlangt, bekommt
stillschweigend 200. `Verlaufslader.SEITE` ist darum 200. Die erste Seite,
die mit dem Konto kommt (`history`), hat weiter 40.

**Erklärt der Verlauf das Guthaben?** Der vollständige Verlauf dieses
Kontos: über tausend Bewegungen aus gut einem Monat in sechs Seiten
(Pool-Anteile, Eingänge, Ausgänge), keine ohne Zeit, keine unbekannter
Art, keine doppelt. Die Summe der Eingänge (Betrag) minus die Summe der
Ausgänge (Betrag + Gebühr) ergab das Guthaben, das der Spiegel nannte —
auf die kleinste Einheit gleich. Die Zahlen selbst stehen hier nicht
mehr; sie gehörten zu einem echten Konto.

Damit steht die Regel: **Ein Ausgang
kostet Betrag plus Gebühr; ein Eingang bringt den Betrag.** Der Spiegel
nennt auch an Eingängen eine Gebühr — die hat der Absender bezahlt. Wer
sie abzieht, landet um genau diese Gebühren daneben; der Test
`GuthabenverlaufAmMitschnittTest` fällt dann um (am echten Verlauf
ausprobiert).

Unter `app/src/test/resources/netz/vollstaendig/` liegen sieben Antworten
derselben Form mit **erfundenen Werten** (seit dem 13.11.2026: 1068
Bewegungen, nach derselben Regel gebaut). Der Test hält die Regel damit
fest und schlägt an, wenn jemand anders rechnet; gemessen wurde sie am
echten Verlauf.

**Die Lücke, die dabei aufgefallen ist.** Abschnitt 7 beschreibt, dass der
leise Lauf die nachgeladenen Seiten stehen lässt und nur die erste
erneuert. Was dabei aus der ersten Seite herausrutschte, weil oben Neues
dazukam, stand danach in KEINER Seite mehr: nicht in der neuen ersten und
nicht in den nachgeladenen. Im Verlauf-Bildschirm fehlte dann eine Zeile;
für eine Rechnung, die sich von Bewegung zu Bewegung hangelt, wäre es ein
falsches Ergebnis ohne Warnzeichen. `Verlaufslader.erneuert` schließt das:
Die neue erste Seite kommt oben auf den ganzen bisherigen Bestand, und
schließt sie nicht lückenlos an, wird neu begonnen. Dazu gehörte ein
zweiter Fehler: Kam die allererste Antwort über den leisen Lauf, blieb der
Zeiger auf die zweite Seite leer — der Verlauf galt als vollständig, obwohl
er es nicht war.

**Was bleibt.** Die Rechnung selbst und der Lader sind ohne Netz geprüft
(60 Unittests). Die Stelle, an der beides im `WalletModel` zusammenkommt —
wann geladen wird, und dass ein überholtes Ergebnis verworfen wird —, hat
keinen Test; sie braucht Android.
