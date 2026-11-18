# KlimaPilot

Android-App zur Steuerung und Überwachung von Mitsubishi-Heavy-Klimaanlagen über ioBroker und den WF-RAC-Adapter.

## Projekt

- Produktive Android-App mit Kotlin und Jetpack Compose
- Kommunikation mit ioBroker über WebSocket
- Direkte lokale Verbindung zum WF-RAC-Modul
- Geplante lokale Profil-Synchronisation zwischen KlimaPilot-Installationen

Die produktive Quelle ist der Git-Branch `main`. Entwicklungsarbeiten am lokalen WLAN-Pairing erfolgen getrennt auf `test`; PR #6 ist der zugehörige Review- und Integrations-PR.

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
- WLAN-Pairing und Peer-Synchronisation werden ausschließlich auf `test` entwickelt.
- PR #6 bleibt Draft und wird nicht ohne ausdrückliche Freigabe gemergt.
- Updater und Installationsablauf sind abgeschlossen und dürfen nicht verändert werden.
- Keine Produktionsversionserhöhung oder Veröffentlichung im WLAN-Pairing-Arbeitsstrang.
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
- Die Debug-Testversion wird im Workflow „KlimaPilot WLAN Branch Validation“ gebaut, geprüft und als Artefakt `KlimaPilot-debug-test-0.4.68` für den Smartphone-Test bereitgestellt.
- [PR #6 – WLAN-Pairing (Draft)](https://github.com/Apropo1710/KlimaPilot/pull/6)
- [Produktions-APK](https://github.com/Apropo1710/KlimaPilot/releases/download/latest/KlimaPilot.apk)
