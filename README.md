# KlimaPilot

Android-App zur Steuerung und Überwachung von Mitsubishi-Heavy-Klimaanlagen über ioBroker und den WF-RAC-Adapter.

## Projekt

- Produktive Android-App mit Kotlin und Jetpack Compose
- Kommunikation mit ioBroker über WebSocket
- Direkte lokale Verbindung zum WF-RAC-Modul
- Synchronisation von Konten, Klimaprofilen, Räumen und Geräten zwischen gekoppelten Smartphones im lokalen WLAN (zusätzlich zum ioBroker-Weg)

Die produktive Quelle ist der Git-Branch `main`. Weiterentwicklung erfolgt auf `test` und wird nach Gerätetest nach `main` übernommen. PR #6 (WLAN-Pairing) ist seit 18.11.2026 in `main` enthalten.

## Technische Basis

- Kotlin, Jetpack Compose, Java 17
- minSdk 26, compileSdk/targetSdk 35
- Application-ID der Produktiv-App: `de.klimapilot.app`
- Application-ID der Debug-App: `de.klimapilot.app.debug`
- ioBroker-WebSocket über `ws.0`, Port 8084
- Die App gilt erst nach erfolgreicher Alive-Prüfung von `system.adapter.admin.0.alive` als verbunden

Die Klimaanlage ist noch nicht installiert. Reale Geräte-, Firmware- und Steuerungsfunktionen müssen deshalb am konkreten Gerät geprüft werden.

## Verbindliche Projektregeln

- `main` ist die alleinige Quelle der produktiven App.
- Neue Funktionen zuerst auf `test` bauen und als Testversion (Pre-Release `klimapilot-test-latest`, Paket `de.klimapilot.app.debug`) prüfen; Übernahme nach `main` nach Gerätetest und auf Wunsch des Nutzers.
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

## Energieaufzeichnung (ioBroker)

Die Energieansicht in KlimaPilot (Kachel „Verbrauch“ antippen) braucht ein Skript in ioBroker, das die Anlagen rund um die Uhr ausliest:

1. Im ioBroker-Admin den Adapter **JavaScript** öffnen und ein neues **JavaScript**-Skript (kein Blockly) anlegen, z. B. „KlimaPilot Energie“.
2. Den Inhalt von [`scripts/iobroker/klimapilot-energie.js`](scripts/iobroker/klimapilot-energie.js) einfügen, speichern und starten.
3. KlimaPilot einmal öffnen: Die App trägt die gekoppelten Anlagen und den Strompreis unter `0_userdata.0.KlimaPilot.energy.config` ein. Das Skript liest danach alle 5 Minuten und schreibt nach `0_userdata.0.KlimaPilot.energy.units.<Geräte-ID>`.

Aufgezeichnet wird ab der ersten Ablesung durch das Skript; Verbrauch davor kann nicht zugeordnet werden.

## GitHub

- [Repository](https://github.com/Apropo1710/KlimaPilot)
- [GitHub Actions](https://github.com/Apropo1710/KlimaPilot/actions)
- Die Testversion wird im Workflow „KlimaPilot WLAN Branch Validation“ gebaut, geprüft (Paket, Version, Test-Signatur, ohne `REQUEST_INSTALL_PACKAGES`) und als Pre-Release veröffentlicht: [Test-APK](https://github.com/Apropo1710/KlimaPilot/releases/download/klimapilot-test-latest/KlimaPilot-test.apk). Testversionen werden über diesen Link installiert, nicht über den In-App-Updater.
- [Produktions-APK](https://github.com/Apropo1710/KlimaPilot/releases/download/latest/KlimaPilot.apk)
