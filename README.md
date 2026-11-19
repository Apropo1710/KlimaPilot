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

## GitHub

- [Repository](https://github.com/Apropo1710/KlimaPilot)
- [GitHub Actions](https://github.com/Apropo1710/KlimaPilot/actions)
- Der Workflow „KlimaPilot WLAN Branch Validation“ prüft jeden Push auf `test` (Unit-Tests, Kompilierung, Paket, Version, Signatur).
- [Produktions-APK](https://github.com/Apropo1710/KlimaPilot/releases/download/latest/KlimaPilot.apk)
