# Der schmusigste fliegt

Eine Android-App, die im Kern eine **Fragen-Datenbank mit austauschbaren Spiel-Frontends**
ist. „Der schmusigste fliegt" ist der erste Konsument dieser Datenbank, nicht ihr Zweck.

Offline, ohne Konto, ohne Netzwerk: die App deklariert *keine* `INTERNET`-Permission und
auch keine Storage-Permissions. Dateizugriff läuft ausschließlich über das Storage Access
Framework.

Die App kennt keine Spielernamen und eliminiert niemanden. Wer dran ist und wer fliegt,
entscheiden die Spieler am Tisch — die App ist das Moderator-Werkzeug für den
Fragen-Stack.

---

## Bauen

Voraussetzungen: JDK 17, Android SDK mit Platform 35 und Build-Tools 35.

```bash
./gradlew assembleDebug
```

```bash
./gradlew test
```

Das Release-APK wird mit dem lokalen Debug-Schlüssel signiert, damit es ohne weitere
Einrichtung installierbar ist. Für eine Veröffentlichung ist der `localRelease`-Block in
`app/build.gradle.kts` durch einen eigenen Upload-Key zu ersetzen.

```bash
./gradlew assembleRelease
```

---

## Module

Ein neuer Spielmodus muss ein neues Modul sein können, ohne bestehende anzufassen.

| Modul | Inhalt |
|---|---|
| `:app` | Einstieg, Navigation-Graph, Hilt-Setup, Theme-Host, Hauptmenü |
| `:core:model` | reine Datenklassen und Enums, keine Android-Abhängigkeit |
| `:core:database` | Room: Entities, DAOs, FTS4-Index |
| `:core:data` | Repository-Implementierungen, Mapper, Seed-Loader, DataStore |
| `:core:designsystem` | Theme, Farben, Typografie, geteilte Composables |
| `:core:sync` | Import/Export: Serializer, Parser, Merge-Engine, Report |
| `:feature:catalog` | Katalog-Liste, Volltextsuche, Filter |
| `:feature:editor` | Frage anlegen und bearbeiten (geteilt von Katalog und Review) |
| `:feature:settings` | Einstellungen |
| `:feature:transfer` | UI für Import/Export (SAF, Dry-Run-Vorschau, Report) |
| `:game:api` | `GameMode`-Vertrag, Session-Repository, Warteschlange |
| `:game:swipe` | Spielmodus „Der schmusigste fliegt" |

### Abhängigkeitsregeln

- `:core:model` hängt von nichts ab.
- `:core:database` und `:core:sync` hängen nur von `:core:model` ab.
- Feature- und Game-Module hängen **niemals** voneinander ab, nur auf `:core:*` und
  `:game:api`.
- Nur `:app` kennt alle Module.

Diese Regeln sind **im Build erzwungen**, nicht nur dokumentiert: die Aufgabe
`verifyModuleDependencies` in `build.gradle.kts` prüft sie, und `:app:preBuild` hängt von
ihr ab. Ein verbotener `implementation(projects.…)` lässt den Build scheitern und nennt
die Verletzung im Klartext.

```bash
./gradlew verifyModuleDependencies
```

---

## Dateiformat

Es gibt **ein** kanonisches JSON-Format. Was die App exportiert, importiert sie ohne
Konvertierung wieder. Jeder Export ist damit ein prüfbares Backup, und zwei Geräte können
sich offline abgleichen, indem sie ihre Exportdateien austauschen.

```json
{
  "schemaVersion": 1,
  "exportedAt": "2026-10-16T10:00:00Z",
  "source": "export",
  "questions": [
    {
      "id": "df4ac376-4131-4bdd-b5a6-d83c5c1a1053",
      "contentHash": "9ba4e1fc93f9bd174d35ffe27169485a910ccd8ea443409c04642cdc5ee360d8",
      "question": "Wie heißt die Hauptstadt von Italien?",
      "answer": "Rom",
      "category": "Geografie",
      "difficulty": 1,
      "tags": ["hauptstädte", "europa"],
      "distractors": [],
      "flagged": false,
      "createdAt": "2026-10-16T10:00:00Z",
      "updatedAt": "2026-10-16T10:00:00Z",
      "revision": 1,
      "deletedAt": null
    }
  ],
  "stats": []
}
```

Beim **Lesen** sind alle Felder außer `question` und `answer` optional — eine Importdatei
lässt sich von Hand schreiben:

```json
{ "questions": [ { "question": "Frage?", "answer": "Antwort" } ] }
```

Defaults: ID wird erzeugt, `contentHash` berechnet, Listen leer, `flagged=false`,
Zeitstempel = jetzt, `revision=1`, `deletedAt=null`, Kategorie „Allgemein",
Schwierigkeit 2. Unbekannte Felder werden ignoriert. Eine höhere `schemaVersion` als
bekannt lehnt die App mit klarer Meldung ab und schreibt nichts.

Beim **Schreiben** setzt die App alle Felder.

### `contentHash`

```
normalize(s) = s.trim().lowercase().replace(Regex("\s+"), " ")
hash         = sha256( normalize(question) + "\u0000" + normalize(answer) )
```

Der Hash dient der Dubletten-Erkennung bei Importdateien ohne verlässliche IDs, nicht als
Primärschlüssel. `distractors` gehen bewusst **nicht** ein: zwei Fragen mit gleichem
Wortlaut und gleicher Antwort sind Dubletten, auch wenn nur eine Falschantworten hat.

Die Normalisierung ist in `:core:sync` genau einmal implementiert und per Unit-Test
festgenagelt — ändert sie sich, erkennen zwei App-Versionen dieselbe Frage nicht mehr als
Dublette.

### Falschantworten (`distractors`)

Gespeichert werden **ausschließlich die Falschantworten**; die richtige Antwort steht nur
in `answer`. Ein künftiger Multiple-Choice-Modus baut seine Auswahlliste zur Laufzeit als
`(answer + distractors).shuffled()`.

Der naheliegende Gegenentwurf — alle vier Möglichkeiten in einer Liste, die richtige per
Index markiert — erzeugt drei Fehlerquellen, die so gar nicht entstehen: die Antwort steht
doppelt im Datensatz und kann auseinanderlaufen, der Index zeigt nach jedem Umsortieren
auf das falsche Element, und ein importierter Index kann auf die falsche Zeile zeigen —
still, bis der Modus Jahre später gebaut wird.

Im Modus „Der schmusigste fliegt" werden sie nicht verwendet. Sie lassen sich aber schon
erfassen, bearbeiten, exportieren und importieren, damit ein späterer Modus auf einen
gefüllten Bestand trifft.

---

## Import und Export

### Export

**Import / Export → Exportieren.** Die App schlägt `quizkatalog_<datum>_<zeit>.json` vor
und schreibt über `ACTION_CREATE_DOCUMENT` dorthin, wo der Nutzer es möchte. Optional
vorgefiltert nach Kategorien — die Datei bleibt abgleichfähig, weil das Format identisch
ist. Löschmarken und Statistiken gehen mit.

### Import

Jeder Import läuft zweistufig:

1. **Dry-Run:** Datei parsen, Merge rechnen, **nichts schreiben**, Ergebnis anzeigen —
   *X neu, Y unverändert, Z aktualisiert, N gelöscht, D Dubletten, K Konflikte, I
   ungültig*, mit aufklappbaren Listen je Kategorie.
2. **Bestätigung** → vor dem Schreiben legt die App automatisch einen vollständigen
   Export im App-internen Ordner ab (rollierend, die letzten fünf), dann läuft der Merge
   in **einer** Transaktion.

Zwei Modi:

| Modus | Verhalten |
|---|---|
| **MERGE** (Standard) | Fragen werden zusammengeführt, `stats` wird **ignoriert**. Das ist der Geräte-Abgleich. |
| **RESTORE** | Lokaler Bestand wird durch die Datei ersetzt, inklusive `stats`. Nur nach ausdrücklicher Bestätigung. Das ist die Wiederherstellung eines Backups. |

Statistik wird im MERGE-Modus nicht angetastet: eine Frage ist geräteübergreifend
dieselbe, „12-mal gespielt, 9-mal richtig" ist aber eine Eigenschaft *dieses* Geräts.
Würde man sie mergen, wären die Zähler nach jedem Abgleich falsch.

### Löschen und Löschmarken

Löschen setzt `deletedAt` und erhöht `revision`; die Zeile bleibt in der Datenbank. Alle
Abfragen für Katalog und Spiel filtern `deletedAt IS NULL`.

Ohne diese Löschmarke propagiert eine Löschung beim Abgleich nicht, und jede ältere
Backup-Datei spielt gelöschte Fragen wieder ein. In den Einstellungen lassen sich
Löschmarken endgültig entfernen — mit dem Hinweis, dass danach alte Importdateien diese
Fragen wieder einbringen können.

### Fragen aus einer Tabelle anlegen

`question,answer,category,difficulty,tags` als CSV. Ausdrücklich **kein**
Abgleichsformat — ohne IDs und Zeitstempel ist jede Zeile eine neue Frage. In der UI heißt
es darum „Fragen aus Tabelle anlegen", nicht „Import".

---

## Beispielkatalog

`app/src/main/assets/seed/seed-questions.json` wird beim **ersten Start** eingelesen,
falls die Datenbank leer ist. Ein Flag in DataStore verhindert erneutes Einspielen, auch
wenn der Nutzer alle Fragen löscht.

Der Seed läuft **durch denselben Importpfad** wie ein Nutzer-Import (MERGE, ohne UI). Der
erste Start ist damit ein echter Test der Parser- und Merge-Logik, und es gibt keinen
zweiten, divergierenden Ladeweg. Keine vorgebaute `.db`-Datei via `createFromAsset`.

In den Einstellungen gibt es „Beispielfragen erneut einspielen" — bequem zum Prüfen, dass
der Import idempotent ist: beim zweiten Mal muss der Bericht *10 unverändert* melden und
nichts einfügen.

---

## Einen neuen Spielmodus anlegen

`:game:api` ist das Scharnier:

```kotlin
interface GameMode {
    val id: String
    val displayName: String
    /** Darf dieser Modus diese Frage ziehen? */
    fun supports(question: Question): Boolean
}
```

Die Fähigkeit wird **aus den Daten abgeleitet**, nicht als Feld deklariert — so können
Deklaration und Inhalt nicht auseinanderlaufen. Ein Millionär-Modus implementiert
`supports` als `question.distractors.size >= 3`.

Schritte:

1. **Neues Modul** `:game:<name>` anlegen und in `settings.gradle.kts` eintragen.
2. **Abhängigkeitsregel** in `build.gradle.kts` ergänzen (`allowedModuleDependencies`) —
   erlaubt sind `:core:model`, `:core:data`, `:core:designsystem` und `:game:api`.
3. **`GameMode` implementieren** und per Multibinding registrieren. Der Anzeigename kommt
   aus `strings.xml`, nicht aus dem Code:

   ```kotlin
   @Module
   @InstallIn(SingletonComponent::class)
   object MyModeModule {
       @Provides @IntoSet @Singleton
       fun provideMode(mode: MyGameMode): GameMode = mode
   }
   ```

   Das Hauptmenü listet diese Menge — der neue Eintrag erscheint dort, ohne dass
   `MainMenuViewModel` angefasst wird. Nur die Zuordnung „Modus-ID → Startroute" steht in
   `SchmusigNavHost`.
4. **Navigationsgraph** als `NavGraphBuilder`-Extension im eigenen Modul anbieten (siehe
   `SwipeNavigation.kt`) und in `SchmusigNavHost` einhängen.
5. **Fragen beziehen** über `QuestionRepository` und `SessionRepository`; `QuestionQueue`
   aus `:game:api` erledigt das faule Nachladen. Nie direkt auf Room zugreifen — die
   Abhängigkeitsprüfung verhindert es ohnehin.
6. **`modeId` nie umbenennen.** Sie steht in `GameSession.modeId` in der Datenbank. Namen
   dürfen sich ändern, Identifikatoren nicht.

---

## Tests

```bash
./gradlew test
```

Der Schwerpunkt liegt auf `:core:sync` — dem Teil, dessen Fehler stillschweigend Daten
zerstören: Normalisierung und Hash, Round-Trip (Export → Parse → Merge ergibt feldweise
denselben Bestand), Idempotenz, jede Merge-Regel einzeln, Statistik-Behandlung je Modus,
Minimaldatei, Falschantworten-Regeln und kaputte Eingaben.

Dazu DAO-Tests mit In-Memory-Room (Suche über Frage, Antwort, Kategorie und Tag,
Filter, Löschmarken-Filterung, Index-Pflege) und ein Test der Rundenlogik: eine Korrektur
der Wertung in der Auswertung wirkt korrekt auf den Statistik-Commit.

---

## Architektur in zwei Sätzen

Datenschicht und Spiellogik kennen sich nicht. Die Spielmodule lesen Fragen über eine
Repository-Schnittstelle und schreiben Spielergebnisse zurück; sie wissen nichts über
Room, Dateiformate oder Import, und der Katalog weiß nichts über Spielmodi.

```
Textdatei ──import──▶ Merge-Engine ──▶ Main-DB (Room/SQLite)
                                          │
                              ┌───────────┼───────────┐
                              ▼           ▼           ▼
                          Katalog     Spielmodus    Export
                      (suchen/edit)   (Session)   (Backup/Sync)
```
