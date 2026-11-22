# KlimaPilot

Android-App zur Steuerung und Überwachung von Mitsubishi-Heavy-Klimaanlagen direkt über deren WF-RAC-WLAN-Modul, mit ioBroker als zusätzlichem Synchronisationsweg.

## Projekt

- Produktive Android-App mit Kotlin und Jetpack Compose
- Kommunikation mit ioBroker über WebSocket
- Direkte lokale Verbindung zum WF-RAC-Modul
- Synchronisation von Konten, Klimaprofilen, Räumen und Geräten zwischen gekoppelten Smartphones im lokalen WLAN (zusätzlich zum ioBroker-Weg)

Die produktive Quelle ist der Git-Branch `main`. Änderungen werden auf `test` durch die CI vorgeprüft und dann über `main` als Produktionsversion ausgeliefert.

## Technische Basis

- Kotlin, Jetpack Compose, Java 17
- minSdk 26, compileSdk/targetSdk 35
- Application-ID der Produktiv-App: `de.klimapilot.app`
- Application-ID der Debug-App: `de.klimapilot.app.debug`
- ioBroker-WebSocket über `ws.0`, Port 8084
- Die App gilt erst nach erfolgreicher Alive-Prüfung von `system.adapter.admin.0.alive` als verbunden

Zwei Klimaanlagen (SRK15ZS-WF) sind seit 19.11.2026 in Betrieb und werden mit KlimaPilot gesteuert. Welche Funktionen am realen Gerät bestätigt sind, steht in `PROJECT_STATUS.md`.

## Verbindliche Projektregeln

- `main` ist die alleinige Quelle der produktiven App.
- Änderungen zuerst auf `test` pushen und den CI-Lauf abwarten; nur bei grünem Ergebnis nach `main`. Ausgeliefert wird nur die Produktionsversion über den Updater.
- Updater und Installationsablauf dürfen nicht verändert werden (`ACTION_VIEW` + FileProvider + ClipData, mit `REQUEST_INSTALL_PACKAGES` in der Produktions-App; Details in `PROJECT_STATUS.md`, Abschnitt 2h).
- Vor Änderungen GitHub-Stand und betroffene Dateien prüfen; danach die maßgeblichen Status- und Fachunterlagen synchron halten.
- Nicht hardwarebestätigte Funktionen nicht als am realen Gerät getestet darstellen.
- Änderungen an ioBroker-Datenpunkten oder deren JSON-Aufbau immer im README-Abschnitt „ioBroker-Schnittstelle“ nachtragen und in `PROJECT_STATUS.md` als Schnittstellenänderung vermerken.

## Dokumentation

**Maßgeblicher aktueller Projektstatus:** [PROJECT_STATUS.md](PROJECT_STATUS.md)

Technische Fachkonzepte:
- [Direkte WF-RAC-Verbindung](DIRECT_WLAN_MIGRATION.md)
- [Lokale WLAN-Synchronisation](LOCAL_WIFI_SYNC_DESIGN.md)
- [Pairing- und Sicherheitsentscheidungen](PAIRING_PROTOCOL_DECISIONS.md)
- [Ausfallsichere Synchronisation](SYNC_RESILIENCE_PLAN.md)

Der aktuelle Branch-, PR-, Build- und Validierungsstand wird ausschließlich in `PROJECT_STATUS.md` gepflegt. Die Fachkonzepte beschreiben Architektur, Entscheidungen und technische Anforderungen und sollen keine konkurrierenden Statusübersichten führen.

## ioBroker-Skript: Energieaufzeichnung und Zeitpläne

Energieverlauf und Zeitpläne laufen über ein Skript im ioBroker-JavaScript-Adapter ([`scripts/iobroker/klimapilot.js`](scripts/iobroker/klimapilot.js)), damit sie auch funktionieren, wenn kein Smartphone zu Hause oder die App geschlossen ist. Das Skript wird mit der App ausgeliefert:

- **Einrichten:** Einstellungen → Kachel „ioBroker-Skript“ aufklappen → „Skript in ioBroker einrichten / prüfen“ (nur Administrator). KlimaPilot legt `script.js.common.KlimaPilot` (`javascript.0`) an und startet es. Ältere Kopien werden angehalten, das frühere Energieskript `KlimaPilot_Energie` wird ersetzt.
- **Aktualisieren:** Beim Start prüft KlimaPilot, ob das eingerichtete Skript der mitgelieferten Fassung entspricht, und aktualisiert es bei Bedarf.
- **Energie:** KlimaPilot trägt Anlagen und Strompreis unter `0_userdata.0.KlimaPilot.energy.config` ein; das Skript liest alle 5 Minuten und schreibt nach `0_userdata.0.KlimaPilot.energy.units.<Geräte-ID>`. Aufgezeichnet wird ab der ersten Ablesung.
- **Zeitpläne:** KlimaPilot schreibt sie nach `0_userdata.0.KlimaPilot.schedules.config`; das Skript prüft jede Minute, schaltet fällige Anlagen direkt am WLAN-Modul und schreibt das Ergebnis nach `0_userdata.0.KlimaPilot.schedules.status`. Schlägt ein Termin fehl (z. B. beim stündlichen Neustart des Moduls), wird er bis zu 10 Minuten lang jede Minute wiederholt. Der Schalter „Zeitpläne verwenden“ in den Einstellungen hält alle Zeitpläne an und blendet sie in der App aus – über ioBroker und zusätzlich über die Handy-zu-Handy-Synchronisierung auf beiden Handys.
- **Diagnose:** Einstellungen → „Diagnose“ (nur Administrator). Eingeschaltet (`0_userdata.0.KlimaPilot.diagnostics.config`) prüft das Skript jede Minute die lokale Erreichbarkeit der Anlagen und protokolliert jeden Ausfall ab 3 Minuten mit Beginn, Dauer und Fehler in `0_userdata.0.KlimaPilot.diagnostics.log` (14 Tage). Die App ergänzt, was das Handy sieht; das Protokoll lässt sich in der App ansehen, kopieren und leeren.
- **Benachrichtigungen:** Die App prüft etwa alle 2 Stunden im Hintergrund (nur im WLAN) die Datenpunkte unten und meldet fällige Filter, endgültig fehlgeschlagene Zeitpläne und Anlagen, die seit über einer Stunde nicht erreichbar sind. Standardmäßig aus; einschalten unter Einstellungen → „Benachrichtigungen“.
- Manuell geht es auch: neues JavaScript-Skript anlegen und den Dateiinhalt einfügen (am PC).

## ioBroker-Schnittstelle

Alle Datenpunkte unter `0_userdata.0.KlimaPilot`, die andere Programme (z. B. eine spätere Desktop-Oberfläche) lesen oder schreiben dürfen. Andere Programme sollen **nur über diese Datenpunkte** arbeiten und nie selbst mit den WF-RAC-Modulen sprechen: Die Module vertragen kaum mehrere gleichzeitige Verbindungen, deshalb ist das ioBroker-Skript die einzige Stelle neben der App, die die Anlagen anspricht.

> Pflege: Jede Änderung an Datenpunkten oder JSON-Aufbau wird hier nachgetragen und in `PROJECT_STATUS.md` als Schnittstellenänderung vermerkt.

### Allgemeine Regeln

- Alle Datenpunkte sind Zustände vom Typ `string` mit Rolle `json`. Der Wert ist ein **JSON-Text**, nicht ein Objekt.
- Schreiben mit `setState(id, { val: "<JSON-Text>", ack: false })`. Das Skript schreibt seine eigenen Ergebnisse mit `ack: true`.
- Immer den **vollständigen** JSON-Text schreiben. **Unbekannte Felder unverändert übernehmen**, damit neuere Versionen nichts verlieren.
- Zeitstempel: `updatedAt` und `at` in Zeitplänen sind Epoch-Millisekunden. Texte wie `at` im Status oder `lastPollAt` sind ISO-8601 in UTC.
- `airconId` ist die Kennung des WF-RAC-Moduls (MAC-Adresse ohne Trennzeichen, z. B. `1cd1d717d1e2`).
- Kodierungen der Werte:
  - `mode`: `AUTO`, `COOL`, `HEAT`, `FAN`, `DRY`
  - `fanSpeed`: 0 Auto, 1 Leise, 2 Niedrig, 3 Mittel, 4 Hoch
  - `verticalSwing`: 0 Auto, 1 Ganz oben, 2 Mitte oben, 3 Mitte unten, 4 Ganz unten
  - `horizontalSwing`: 0 Auto, 1 Links außen, 2 Links, 3 Mitte links, 4 Mitte, 5 Mitte rechts, 6 Rechts, 7 Rechts außen
  - `threeDAuto`: true/false
  - `targetTemp`: 16–30 °C in 0,5er-Schritten
  - Wochentage nach ISO: 1 = Montag … 7 = Sonntag

### Übersicht

| Datenpunkt | Geschrieben von | Andere Programme |
|---|---|---|
| `energy.config` | App | lesen; nur `priceEurPerKwh` ändern |
| `energy.units.<airconId>` | Skript | nur lesen |
| `schedules.config` | App | lesen und schreiben (Revisionsverfahren) |
| `schedules.status` | Skript | nur lesen |
| `filter.config` | App | lesen und schreiben |
| `diagnostics.config` | App | lesen und schreiben |
| `diagnostics.log` | Skript | lesen; leeren erlaubt |
| `profileSync.*`, `accounts` | App (intern) | nicht verwenden |
| `script.owner` | Skript (intern) | nicht verwenden |

### `energy.config` – Anlagen und Strompreis

Die App schreibt alle gekoppelten Anlagen. Das Skript liest daraus, welche Anlagen es abfragt und steuert.

```json
{
  "priceEurPerKwh": 0.33,
  "units": [
    { "airconId": "1cd1d717d1e2", "host": "192.168.178.197", "name": "Klimaanlage Schlafzimmer", "room": "Schlafzimmer" },
    { "airconId": "1cd1d717ebe8", "host": "192.168.178.176", "name": "Klimaanlage Büro", "room": "Büro" }
  ]
}
```

- `units` wird von der App aus ihren gekoppelten Geräten neu geschrieben. Andere Programme ändern es nicht.
- `priceEurPerKwh` (Festpreis ohne Grundgebühr) darf geändert werden. Die App übernimmt den Preis aus ioBroker.

### `energy.units.<airconId>` – Verbrauch je Anlage (nur lesen)

```json
{
  "hourly": { "2026-11-21T10": 0.167, "2026-11-21T11": 0.167 },
  "daily": { "2026-11-20": 2.0, "2026-11-21": 1.5 },
  "totalKwh": 3.5,
  "lastRunKwh": 0.75,
  "runMinutes": 12840,
  "runDays": 52,
  "lastRunDay": "2026-11-21",
  "outdoorHourly": { "2026-11-21T10": 3.4, "2026-11-21T11": 3.9 },
  "outdoorDaily": { "2026-11-20": 1.8, "2026-11-21": 3.1 },
  "outdoorHourlyN": { "2026-11-21T11": 9 },
  "outdoorDailyN": { "2026-11-21": 130 },
  "lastPollAt": "2026-11-21T10:45:00.000Z",
  "lastError": "2026-11-21T06:10:00.000Z connect ECONNREFUSED …",
  "pendingSlots": { "2026-11-21T11": 9 }
}
```

- Schlüssel in Ortszeit von ioBroker: `hourly` als `JJJJ-MM-TTTHH` (31 Tage aufbewahrt), `daily` als `JJJJ-MM-TT` (unbegrenzt), Werte in kWh.
- Der Zähler der Anlage meldet nur 0,25-kWh-Schritte. Jeder Schritt wird anteilig auf die Stunden verteilt, in denen die Anlage seit dem letzten Schritt lief. `daily` ist genau, einzelne Stunden sind angenähert.
- `runMinutes`: Betriebsminuten der Anlage, gezählt in 5-Minuten-Schritten, solange sie eingeschaltet ist.
- `runDays`: Betriebstage, also Kalendertage, an denen die Anlage mindestens einmal lief. Grundlage der Filter-Erinnerung. `lastRunDay` (`JJJJ-MM-TT`) ist ein interner Zwischenstand dazu.
- `outdoorHourly` (31 Tage) und `outdoorDaily` (unbegrenzt): mittlere Außentemperatur in °C, gemessen vom Außenfühler der Anlage. Die Werte gibt es auch, wenn die Anlage aus ist.
- `lastRunKwh`, `pendingSlots`, `outdoorHourlyN` und `outdoorDailyN` sind interne Zwischenstände des Skripts. `lastError` fehlt, wenn die letzte Ablesung geklappt hat.

### `schedules.config` – Zeitpläne (lesen und schreiben)

```json
{
  "enabled": true,
  "revision": 42,
  "items": [
    {
      "id": "0f3c…",
      "airconId": "1cd1d717ebe8",
      "enabled": true,
      "kind": "weekly",
      "days": [1, 2, 3, 4, 5],
      "time": "06:30",
      "action": "on",
      "profileId": "a1b2…",
      "profileName": "Grundtemperatur",
      "settings": { "mode": "HEAT", "targetTemp": 21.0, "fanSpeed": 0, "verticalSwing": 2, "horizontalSwing": 2, "threeDAuto": false },
      "updatedAt": 1795250000000
    },
    { "id": "9d1e…", "airconId": "1cd1d717d1e2", "enabled": true, "kind": "once", "days": [], "time": "", "at": 1795300000000, "action": "off", "updatedAt": 1795250100000 }
  ]
}
```

- `enabled`: globaler Schalter „Zeitpläne verwenden“. Bei `false` führt das Skript nichts aus.
- `kind`: `weekly` (mit `days` und `time` im Format `HH:MM`) oder `once` (mit `at`).
- `action`: `on` schaltet ein und setzt die Werte aus `settings`. `off` schaltet aus, `settings` wird dann nicht verwendet.
- `settings`: Nur enthaltene Felder werden gesetzt, fehlende bleiben an der Anlage unverändert.
- Mit Profil (`profileId`, `profileName`) ist `settings` eine Kopie der Profilwerte. Das Skript verwendet **nur `settings`**. Die App aktualisiert die Kopie, wenn sich das Profil ändert.
- `quick: true` kennzeichnet einen Schnell-Timer „Aus in …“: `kind` `once`, `action` `off`. Er wird in der App nicht in der Liste geführt, es gibt höchstens einen je Anlage, und die App entfernt ihn, sobald er ausgeführt oder älter als 7 h ist. Fehlt das Feld, gilt `false`.
- Die Reihenfolge von `items` ist die Anzeigereihenfolge in der App, die Nutzer selbst festlegen. Neue Zeitpläne kommen ans Ende.
- Ausgeführte einmalige Zeitpläne (`once`) setzt die App danach auf `enabled: false`.
- Ausführung: Wöchentliche Termine werden bis 10 min nach der Zeit jede Minute wiederholt, falls die Anlage nicht erreichbar ist. Einmalige werden bis 6 h nach `at` nachgeholt.

**Revisionsverfahren – Pflicht für jedes Programm, das `schedules.config` schreibt:**

1. Lesen und `revision` merken (fehlt das Feld, gilt 0).
2. Nur die gewünschten Einträge ändern, alles andere unverändert lassen, einschließlich unbekannter Felder und Reihenfolge. Bei jedem neuen oder geänderten Eintrag `updatedAt` auf die aktuelle Zeit setzen.
3. **Unmittelbar vor dem Schreiben erneut lesen.** Hat sich `revision` geändert, hat jemand anders dazwischen geschrieben: zurück zu Schritt 1 und die Änderung auf den neuen Stand anwenden. Die App versucht das bis zu 3-mal.
4. Mit `revision` = gelesene Revision + 1 schreiben.
5. Bearbeitungsmasken: `updatedAt` des Eintrags beim Öffnen merken. Ist der Eintrag beim Speichern (Schritt 3) inzwischen ein anderer (anderes `updatedAt`) oder gelöscht, **nicht stillschweigend überschreiben**, sondern nachfragen („Überschreiben“ oder „Verwerfen“).

### `schedules.status` – Ergebnisse der Zeitpläne (nur lesen)

```json
{
  "0f3c…": { "key": "2026-11-21T06:30", "at": "2026-11-21T05:30:12.000Z", "done": true, "attempts": 1, "result": "ok" },
  "9d1e…": { "key": "once:1795300000000", "at": "2026-11-22T10:01:00.000Z", "done": false, "attempts": 3, "result": "connect ETIMEDOUT …" }
}
```

- Ein Eintrag je Zeitplan-`id`, jeweils für den letzten fälligen Termin (`key`).
- `done: true` heißt ausgeführt. Bei `false` steht in `result` der letzte Fehler.
- Einträge gelöschter Zeitpläne entfernt das Skript.

### `filter.config` – Filter-Erinnerung (lesen und schreiben)

```json
{
  "airIntervalWeeks": 2,
  "cleanAirIntervalMonths": 3,
  "units": { "1cd1d717ebe8": { "airCleanedRunDays": 40, "cleanAirCleanedRunDays": 12 } },
  "updatedAt": 1795260000000
}
```

- Zwei Filterarten mit eigenem Abstand, angegeben wie im Handbuch:
  - **Luftfilter** in Wochen (`airIntervalWeeks`, 0–4, Vorgabe 2)
  - **Allergen- und Geruchsfilter** zusammen in Monaten (`cleanAirIntervalMonths`, 0–6, Vorgabe 3)
  - `0` heißt aus.
- Gezählt werden **Betriebstage**: `runDays` aus `energy.units.<airconId>`, also nur Kalendertage, an denen die Anlage lief. Eine Woche entspricht 7, ein Monat 30 Betriebstagen.
- `airCleanedRunDays` bzw. `cleanAirCleanedRunDays`: Stand von `runDays` bei der letzten Reinigung der jeweiligen Filterart. Betriebstage seit der Reinigung = `runDays` − Stand. Fehlt der Stand, wird ab Aufzeichnungsbeginn gezählt.
- „Gereinigt“ bzw. „Erledigt“ setzt den Stand auf den aktuellen `runDays`-Wert der Anlage. Schreiben immer als Lesen – Ändern – Schreiben, andere Anlagen und Felder unverändert lassen.

### `diagnostics.config` – Diagnose an/aus (lesen und schreiben)

```json
{ "enabled": true, "updatedAt": 1795251000000 }
```

Ist die Diagnose an, prüft das Skript jede Minute die Erreichbarkeit aller Anlagen aus `energy.config`. Die App übernimmt den Schalter beim Start.

### `diagnostics.log` – Diagnoseprotokoll (lesen; leeren erlaubt)

```json
{
  "entries": [
    { "at": "2026-11-21T06:10:00.000Z", "source": "ioBroker", "subject": "Klimaanlage Schlafzimmer", "event": "nicht erreichbar", "detail": "seit 07:07 – connect ECONNREFUSED 192.168.178.197:51443" },
    { "at": "2026-11-21T11:20:00.000Z", "source": "ioBroker", "subject": "Klimaanlage Schlafzimmer", "event": "wieder erreichbar", "detail": "nach 310 min" }
  ]
}
```

- `event`: `nicht erreichbar`, `wieder erreichbar`, `Skript gestartet`, `Diagnose eingeschaltet`, `Diagnose ausgeschaltet`, `doppelte Kopie pausiert`.
- Ausfälle stehen erst ab 3 min Dauer im Protokoll (`detail` beginnt mit `seit HH:MM – `), das Ende nur zu eingetragenen Ausfällen. Die stündlichen Neustarts des WLAN-Moduls (rund 1 min) erscheinen deshalb nicht. Gleiche Einträge binnen 3 min werden nur einmal geschrieben.
- Höchstens 400 Einträge, älter als 14 Tage wird entfernt.
- Leeren: `{"entries":[]}` schreiben.
- Die Einträge der Handys (`source: "Handy"`) liegen nur auf dem jeweiligen Handy, nicht in ioBroker.

### Interne Datenpunkte (nicht verwenden)

- `profileSync.*`: Ereignisprotokoll der Klimaprofil-Synchronisierung zwischen den Handys. Das Format ist intern und kann sich ändern. Profilwerte, die Zeitpläne brauchen, stehen als Kopie in `schedules.config` → `settings`.
- `accounts`: früherer Kontodatenpunkt der App, nur noch zur Übernahme alter Daten.
- `script.owner`: welche Kopie des Skripts gerade arbeitet (`{id, script, startedAt}`). Läuft das Skript versehentlich doppelt, arbeitet nur die zuletzt gestartete Kopie, die andere pausiert und meldet sich im ioBroker-Log bzw. im Diagnoseprotokoll.
- Nicht in ioBroker, sondern nur auf den Handys (per Handy-zu-Handy-Synchronisierung): die Jahreszeit-Auswahl der Profile und das Ausblenden der Zeitpläne in der App.

## GitHub

- [Repository](https://github.com/Apropo1710/KlimaPilot)
- [GitHub Actions](https://github.com/Apropo1710/KlimaPilot/actions)
- Der Workflow „KlimaPilot WLAN Branch Validation“ prüft jeden Push auf `test` (Unit-Tests, Kompilierung, Paket, Version, Signatur).
- [Produktions-APK](https://github.com/Apropo1710/KlimaPilot/releases/download/latest/KlimaPilot.apk)
