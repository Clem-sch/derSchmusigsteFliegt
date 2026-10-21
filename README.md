# Der schmusigste fliegt

Eine Quiz-App für den Abend am Tisch. Du bist der Moderator, die App ist dein
Fragen-Stapel mit Timer — wer dran ist und wer rausfliegt, entscheidet eure Runde selbst.

**Komplett offline. Kein Konto, keine Anmeldung, keine Werbung.** Die App fragt nicht
einmal nach einer Internet-Berechtigung, weil sie keine braucht.

---

## Installieren

Die fertige App liegt als `DerSchmusigsteFliegt-1.0.apk` bereit.

1. Datei auf dein Android-Gerät kopieren (USB, Cloud, E-Mail an dich selbst).
2. Im Dateimanager antippen.
3. Android fragt, ob du Apps aus dieser Quelle installieren willst → **Erlauben**.
4. Installieren.

Du brauchst Android 8 oder neuer. Die Warnung von Android ist normal: sie erscheint bei
jeder App, die nicht aus dem Play Store kommt.

---

## Der erste Start

Beim ersten Öffnen sind schon **10 Beispielfragen** drin, damit du gleich loslegen kannst.
Du kannst sie behalten, löschen oder durch deinen eigenen Katalog ersetzen.

Das Hauptmenü hat vier Einträge:

| | |
|---|---|
| **Der schmusigste fliegt** | Runde starten |
| **Fragenkatalog** | Suchen, bearbeiten, markieren |
| **Frage hinzufügen** | Neue Frage anlegen |
| **Import / Export** | Backup und Geräte-Abgleich |

Oben siehst du immer, wie viele Fragen in wie vielen Kategorien du hast.

---

## Eine Runde spielen

**Hauptmenü → Der schmusigste fliegt.** Dann in drei Schritten:

**1. Kategorien wählen** — alle, oder nur „Geografie" und „Sport". Mit *Alle* / *Keine*
schnell umschalten.

**2. Schwierigkeit wählen** — 1 bis 5. Optional *Zuletzt gespielte Fragen ausschließen*:
blendet alles aus, was in den letzten 24 Stunden schon dran war. Praktisch, wenn ihr
mehrere Runden hintereinander spielt.

Passt keine Frage zu deiner Kombination, sagt die App das sofort — du startest nie in eine
leere Runde.

**3. Timer stellen** — Minuten und Sekunden, oder eine der Voreinstellungen. **Start.**

### Im Spiel

Pro Karte steht die Frage, die Schwierigkeit und die Zahl der verbleibenden Karten. Dazu
drei Knöpfe:

- **Richtig**
- **Falsch**
- **Überspringen** — wenn die Frage nicht passt oder schon bekannt war

Läuft der Timer ab, kommt *Zeit um*. Mit **Zur Auswertung** geht's weiter. Abbrechen
kannst du jederzeit — die bisherigen Wertungen bleiben erhalten.

### Auswertung

Du bekommst die Zusammenfassung („7 richtig, 2 falsch, 1 übersprungen von 10 Fragen") und
darunter jede Frage einzeln. Dort kannst du:

- **Eine Wertung korrigieren** — antippen schaltet zwischen richtig / falsch /
  übersprungen um. Falls ihr am Tisch doch anders entschieden habt.
- **Die Frage direkt bearbeiten** — wenn ein Tippfehler oder eine falsche Lösung
  aufgefallen ist.

Dann **Runde abschließen**, oder **Neue Runde mit gleichen Einstellungen** für die nächste
Runde ohne erneutes Einstellen.

---

## Eigene Fragen

### Einzeln anlegen

**Hauptmenü → Frage hinzufügen.** Pflicht sind nur **Frage**, **Antwort** und
**Kategorie**. Dazu optional Schwierigkeit (1–5), Tags und Falschantworten.

Bei der Kategorie schlägt die App dir vor, was du schon verwendest — so entstehen nicht
versehentlich „Sport" und „sport" nebeneinander.

Die **Falschantworten** braucht „Der schmusigste fliegt" nicht. Du kannst sie trotzdem
schon eintragen; ein späterer Multiple-Choice-Modus würde sie nutzen.

### Viele auf einmal

Für größere Mengen lohnt eine Datei. Zwei Wege:

**JSON** — das volle Format, auch zum Abgleich zwischen Geräten geeignet. Im Minimum
reicht das:

```json
{ "questions": [ { "question": "Hauptstadt von Italien?", "answer": "Rom" } ] }
```

Alles Weitere beschreibt [`FRAGEN-FORMAT.md`](FRAGEN-FORMAT.md), eine Startdatei zum
Befüllen liegt als [`fragen-vorlage.json`](fragen-vorlage.json) daneben.

**Tabelle (CSV)** — Spalten `question, answer, category, difficulty, tags`. Gut, um Fragen
in Excel oder Google Sheets zu sammeln. Achtung: Jede Zeile wird eine **neue** Frage, das
ist kein Abgleich. Deshalb heißt es in der App „Fragen aus Tabelle anlegen".

### Im Katalog suchen und aufräumen

**Hauptmenü → Fragenkatalog.** Die Suche greift über Fragen, Antworten, Kategorien und
Tags gleichzeitig. Filtern kannst du nach Kategorie, Schwierigkeit, *nur markierte* und
*nur mit Falschantworten*; sortieren nach letzter Änderung, Kategorie oder Schwierigkeit.

Fällt dir im Spiel ein Fehler auf, markiere die Frage als **fehlerhaft**. Später holst du
im Katalog über den Filter *nur markierte* alle auf einmal hervor und korrigierst sie in
Ruhe.

---

## Backup und zweites Gerät

**Hauptmenü → Import / Export.**

### Exportieren

**Exportieren** schreibt eine JSON-Datei mit allen Fragen und Statistiken dorthin, wo du
sie haben willst — Downloads, Google Drive, USB-Stick. Optional nur ausgewählte
Kategorien.

Diese Datei ist gleichzeitig dein **Backup** und dein **Abgleichspaket**: Die App liest
exakt das Format, das sie schreibt.

> **Mach das regelmäßig.** Deine Fragen liegen nur auf diesem Gerät. Geht das Handy
> verloren, sind sie ohne Export weg.

### Importieren

Die App schreibt **nie sofort**. Sie rechnet den Import erst nur durch und zeigt dir, was
passieren würde: *X neu, Y unverändert, Z aktualisiert, D Dubletten, I ungültig* — nach
Kategorien aufklappbar. Erst wenn du bestätigst, wird geschrieben. Und davor legt sie
automatisch ein internes Backup an.

Zwei Modi:

**Zusammenführen** (Standard) — der Weg fürs zweite Gerät. Beide Kataloge werden
vereinigt, dieselbe Frage landet nicht doppelt. Eure Spielstatistiken bleiben dabei
jeweils lokal, weil „12-mal gespielt" eine Eigenschaft *dieses* Geräts ist.

**Wiederherstellen** — ersetzt deinen gesamten Bestand durch die Datei, Statistiken
inklusive. Das ist der Weg zurück nach einem Datenverlust, nicht der Abgleich. Die App
fragt hier ausdrücklich nach.

### Zwei Geräte abgleichen

1. Auf Gerät A exportieren.
2. Datei zu Gerät B bringen.
3. Auf B importieren mit **Zusammenführen**.
4. Für die Gegenrichtung dasselbe umgekehrt.

Kein Server, keine Anmeldung, kein Internet — nur eine Datei.

### Automatische Backups

Vor **jedem** Import legt die App selbst eine vollständige Sicherung an. Die letzten fünf
findest du unter **Einstellungen → Interne Auto-Backups** und kannst sie von dort
exportieren. Falls ein Import doch nicht das war, was du wolltest.

---

## Einstellungen

**Antwort auf der Spielkarte**

- *Sofort sichtbar* — Moderator-Betrieb: Frage und Lösung stehen zusammen auf der Karte.
- *Erst nach Tap aufdecken* — Selbststudium: erst raten, dann aufdecken.

**Standard-Timerdauer** für neue Runden.

**Erscheinungsbild** — System, Hell oder Dunkel.

**Beispielfragen erneut einspielen** — holt die 10 Startfragen zurück.

**Gelöschte Fragen endgültig entfernen** — siehe unten.

---

## Gut zu wissen

**Gelöschte Fragen sind erst „halb" gelöscht.** Die App merkt sich, dass du gelöscht
hast — sonst würde ein älteres Backup die Frage beim nächsten Import wieder einschleppen.
Diese Löschmarken kannst du in den Einstellungen endgültig entfernen; danach können alte
Dateien die Fragen aber wieder mitbringen.

**Die App kennt keine Spielernamen.** Sie zählt nicht mit, wer vorn liegt, und wirft
niemanden raus. Das macht euer Tisch. Die App verwaltet den Fragen-Stapel und die Zeit.

**Dieselbe Frage zweimal importieren ist unproblematisch.** Die App erkennt sie an
Wortlaut und Antwort — Groß-/Kleinschreibung und zusätzliche Leerzeichen spielen keine
Rolle — und legt sie nicht doppelt an.

**Umlaute brauchen UTF-8.** Speicherst du eine eigene Datei in einer anderen Kodierung,
werden ä/ö/ü zu Fragezeichen.

---

## Für Entwickler

Die App ist ein Multi-Modul-Android-Projekt (Kotlin, Compose, Room, Hilt). Im Kern ist sie
eine Fragen-Datenbank mit austauschbaren Spiel-Frontends — „Der schmusigste fliegt" ist
der erste Spielmodus, nicht der Zweck.

Bauen:

```bash
./gradlew assembleDebug
```

Tests:

```bash
./gradlew test
```

Modulaufbau, Abhängigkeitsregeln, das kanonische Dateiformat, die Merge-Semantik und wie
man einen eigenen Spielmodus anlegt: **[`ARCHITEKTUR.md`](ARCHITEKTUR.md)**.
