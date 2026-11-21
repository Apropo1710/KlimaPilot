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
- **Diagnose:** Einstellungen → „Diagnose“ (nur Administrator). Eingeschaltet (`0_userdata.0.KlimaPilot.diagnostics.config`) prüft das Skript jede Minute die lokale Erreichbarkeit der Anlagen und protokolliert jeden Ausfall mit Beginn, Dauer und Fehler in `0_userdata.0.KlimaPilot.diagnostics.log` (14 Tage). Die App ergänzt, was das Handy sieht; das Protokoll lässt sich in der App ansehen, kopieren und leeren.
- Manuell geht es auch: neues JavaScript-Skript anlegen und den Dateiinhalt einfügen (am PC).

## GitHub

- [Repository](https://github.com/Apropo1710/KlimaPilot)
- [GitHub Actions](https://github.com/Apropo1710/KlimaPilot/actions)
- Der Workflow „KlimaPilot WLAN Branch Validation“ prüft jeden Push auf `test` (Unit-Tests, Kompilierung, Paket, Version, Signatur).
- [Produktions-APK](https://github.com/Apropo1710/KlimaPilot/releases/download/latest/KlimaPilot.apk)
