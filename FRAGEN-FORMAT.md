# Fragen von Hand schreiben

Wie eine Importdatei aussehen muss, damit „Der schmusigste fliegt" sie einliest.

Die Datei muss **UTF-8** sein, sonst werden Umlaute zu Fragezeichen. Eine fertige
Startdatei zum Befüllen liegt daneben: [`fragen-vorlage.json`](fragen-vorlage.json).

---

## Das Minimum

Mehr als das braucht es nicht. Alles andere setzt die App selbst:

```json
{
  "questions": [
    { "question": "Wie heißt die Hauptstadt von Italien?", "answer": "Rom" },
    { "question": "Wie viele Beine hat eine Spinne?", "answer": "Acht" }
  ]
}
```

Das importiert sauber. Die Fragen landen in der Kategorie „Allgemein" mit
Schwierigkeit 2 — beides lässt sich hinterher im Editor ändern oder gleich in der Datei
angeben.

## Mit allem, was du von Hand sinnvoll setzen kannst

```json
{
  "questions": [
    {
      "question": "Wer schrieb die Erzählung „Die Verwandlung“?",
      "answer": "Franz Kafka",
      "category": "Literatur",
      "difficulty": 3,
      "tags": ["literatur", "klassiker"],
      "distractors": ["Thomas Mann", "Hermann Hesse", "Robert Musil"],
      "flagged": false
    }
  ]
}
```

---

## Die Felder

| Feld | Pflicht | Typ | Regeln |
|---|---|---|---|
| `question` | **ja** | Text | darf nicht leer sein |
| `answer` | **ja** | Text | darf nicht leer sein |
| `category` | nein | Text | Freitext. Weglassen → „Allgemein". **Nicht** als `""` schreiben, das macht die Zeile ungültig |
| `difficulty` | nein | Zahl | 1 bis 5. Weglassen → 2. Alles außerhalb macht die Zeile ungültig |
| `tags` | nein | Liste von Text | werden klein geschrieben, getrimmt und entdoppelt |
| `distractors` | nein | Liste von Text | **nur Falschantworten**, maximal 5, siehe unten |
| `flagged` | nein | true/false | „Frage ist fehlerhaft / prüfen". Weglassen → `false` |

Diese Felder schreibt die App beim Export mit; von Hand lässt man sie **besser weg**:

| Feld | Was passiert, wenn es fehlt |
|---|---|
| `id` | die App erzeugt eine UUID |
| `contentHash` | die App rechnet ihn selbst, immer — ein falscher Wert in der Datei wird ignoriert |
| `createdAt`, `updatedAt` | werden auf „jetzt" gesetzt |
| `revision` | wird 1 |
| `deletedAt` | bleibt leer (die Frage ist nicht gelöscht) |

Auf Dateiebene gibt es noch `schemaVersion` (ohne Angabe: 1), `exportedAt`, `source` und
`stats`. Von Hand braucht man keines davon. Felder, die die App nicht kennt, werden beim
Lesen ignoriert — du kannst also eigene Notizen in die Datei schreiben.

### Zeitstempel

Falls du sie doch angibst: ISO-8601 in UTC mit `Z` am Ende.

```
"updatedAt": "2026-10-16T10:00:00Z"
```

Alles andere — `"16.10.2026"`, `"gestern"`, Zeitzonen-Offsets ohne `Z` — macht die Zeile
ungültig. Die App meldet das als „Zeitstempel ist unlesbar" und überspringt genau diese
Zeile, der Rest der Datei geht trotzdem durch.

### Falschantworten (`distractors`)

Hier stehen **ausschließlich die falschen** Antworten. Die richtige steht nur in `answer`
und darf in der Liste nicht noch einmal auftauchen.

Im Modus „Der schmusigste fliegt" werden sie nicht gebraucht. Sie lassen sich aber schon
erfassen, damit ein späterer Multiple-Choice-Modus auf einen gefüllten Bestand trifft —
der braucht dann drei davon.

Was die App beim Einlesen still korrigiert:

- leere Einträge fliegen raus
- ein Eintrag, der (ohne Rücksicht auf Groß-/Kleinschreibung und Leerzeichen) der
  richtigen Antwort entspricht, fliegt raus — sonst wären zwei Auswahlmöglichkeiten
  richtig
- Dubletten innerhalb der Liste werden zusammengefasst
- mehr als 5 werden abgeschnitten

Jede dieser Korrekturen steht im Bericht nach dem Import, du siehst also, was passiert ist.

---

## Dubletten: was passiert, wenn du dieselbe Frage zweimal schickst

Die App bildet aus Frage und Antwort einen Fingerabdruck. Dafür werden beide vorher
normalisiert: Leerzeichen am Rand weg, alles klein, mehrfache Leerzeichen zu einem.

Praktisch heißt das: `"  Hauptstadt   von Italien? "` und `"hauptstadt von italien?"`
gelten als dieselbe Frage. Unterschiedliche Kategorien, Tags oder Schwierigkeiten ändern
daran nichts — nur Frage und Antwort zählen.

Folge für handgeschriebene Dateien ohne `id`: importierst du dieselbe Datei zweimal, wird
beim zweiten Mal **nichts doppelt angelegt**. Die Zeilen erscheinen im Bericht unter
*Dubletten* mit Verweis auf die Frage, die schon im Katalog steht. Du kannst eine Datei
also gefahrlos nachbessern und erneut importieren.

Achtung: *Dublette* ist nicht dasselbe wie *unverändert*. „Unverändert" bekommst du nur
bei Dateien mit `id` — also bei Exporten der App. Eine handgeschriebene Datei hat keine
IDs, deshalb kann die App eine bestehende Frage nicht aktualisieren, sondern nur als
Dublette abweisen. **Korrekturen an schon importierten Fragen machst du im Editor**, nicht
durch erneuten Import derselben Datei mit geändertem Text — geänderter Text heißt neuer
Fingerabdruck, und das wird eine zweite Frage.

---

## Der Ablauf beim Importieren

1. **Import / Export → Datei auswählen**, Modus *Zusammenführen* (Standardfall).
2. Die App rechnet den Import durch und **schreibt erst einmal nichts**. Du siehst, was
   passieren würde: *X neu, Y unverändert, Z aktualisiert, D Dubletten, I ungültig* — mit
   aufklappbaren Listen.
3. Erst nach **Import ausführen** wird geschrieben, und davor legt die App automatisch ein
   vollständiges Backup im App-Ordner ab.

Eine ungültige Zeile bricht den Import nicht ab. Sie wird verworfen, mit Begründung im
Bericht aufgeführt, und alles andere geht durch.

Kaputtes JSON — eine fehlende Klammer, ein Komma zu viel — lehnt die App dagegen komplett
ab und schreibt nichts. Wenn du unsicher bist, ob die Datei gültig ist: in einem
JSON-Prüfer oder Editor öffnen, bevor du sie aufs Handy kopierst.

Der Modus *Wiederherstellen* ist etwas anderes: der **ersetzt den gesamten Bestand** durch
die Datei. Für neue Fragen nimmt man ihn nicht, nur zum Zurückspielen eines Backups.

---

## Alternative: Tabelle statt JSON

Wenn du Fragen in Excel oder Google Tabellen sammelst, geht es auch ohne JSON — in der App
unter **Import / Export → Tabelle auswählen**.

Kopfzeile ist Pflicht, die Spalten heißen:

```csv
question,answer,category,difficulty,tags
Wie heißt die Hauptstadt von Italien?,Rom,Geografie,1,hauptstädte|europa
Wie viele Beine hat eine Spinne?,Acht,Biologie,1,tiere
```

- **Trennzeichen** Komma oder Semikolon, die App erkennt es an der Kopfzeile. Deutsche
  Excel-Installationen schreiben üblicherweise Semikolon.
- **Mehrere Tags** mit `|` trennen. Das Semikolon geht auch, ist aber riskant, wenn das
  Semikolon schon das Spaltentrennzeichen ist.
- Felder mit Komma oder Semikolon darin in `"Anführungszeichen"` setzen; ein
  Anführungszeichen im Text wird als `""` geschrieben.
- `category`, `difficulty` und `tags` dürfen leer bleiben, dann greifen dieselben Defaults
  wie oben.

Eine Tabelle hat keine IDs und keine Zeitstempel. Sie ist deshalb **kein Format zum
Abgleichen zwischen Geräten** — darum heißt es in der App auch „Fragen aus Tabelle
anlegen" und nicht „Import". Jede Zeile ist eine neue Frage; Dubletten fängt der
Fingerabdruck ab.

---

## Für den Abgleich zwischen zwei Geräten

Dafür schreibst du keine Datei von Hand, sondern nimmst einen **Export**: der enthält IDs,
Zeitstempel und Löschmarken, und nur damit kann die App bestehende Fragen aktualisieren
statt sie als Dubletten abzuweisen. Beide Geräte exportieren, tauschen die Dateien und
importieren sie im Modus *Zusammenführen* — der jüngere Stand gewinnt pro Frage.

Spielstatistiken stehen in der Datei, werden beim Zusammenführen aber bewusst **nicht**
übernommen: „12-mal gespielt" ist eine Eigenschaft des Geräts, nicht der Frage.
