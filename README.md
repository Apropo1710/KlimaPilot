# KlimaPilot

Android-App zur Steuerung und Überwachung von Mitsubishi-Heavy-Klimaanlagen über ioBroker und den WF-RAC-Adapter.

## Aktueller Stand

- **Produktive Version:** 0.4.64 (versionCode 94)
- **Produktionsbranch:** `main`
- **Arbeitsbranch für lokale Smartphone-Synchronisation:** `test`
- **Dokumentationsabgleich:** 18.11.2026, 08:16 Uhr MEZ
- **Produktions-App-ID:** `de.klimapilot.app`
- **Debug-App-ID:** `de.klimapilot.app.debug`

Die Klimaanlage ist noch nicht installiert. Deshalb sind reale Geräte-, Firmware- und Steuerungsfunktionen erst nach einem Hardwaretest bestätigt.

## Technische Basis

- Kotlin, Jetpack Compose, Java 17
- minSdk 26, compileSdk/targetSdk 35
- ioBroker-WebSocket über `ws.0`, Port 8084
- Verbindung gilt erst nach erfolgreicher Alive-Prüfung von `system.adapter.admin.0.alive` als verbunden
- Lokale Profil-Synchronisation über einen separaten ioBroker-WebSocket und ein dauerhaftes Outbox-/Ereignismodell

## Entwicklungsregeln

- `main` ist die alleinige Quelle der produktiven App.
- WLAN-Pairing und Peer-Synchronisation werden ausschließlich auf `test` entwickelt. PR #6 bleibt Draft und wird nicht ohne ausdrückliche Freigabe gemergt.
- Updater und Installationsablauf sind abgeschlossen und tabu.
- Keine Produktionsversionserhöhung oder Veröffentlichung im WLAN-Pairing-Arbeitsstrang.
- Vor Änderungen GitHub-Stand und betroffene Dateien prüfen; danach alle relevanten Dokumente aktualisieren.
- Keine Funktion als hardwarebestätigt darstellen, solange kein entsprechender Gerätetest erfolgt ist.

## Dokumentation

- [Aktueller Projektstatus](STATUS.md)
- [Verbindlicher Projektstatus und technische Details](PROJECT_STATUS.md)
- [Lokale WLAN-Synchronisation – Design und Implementierung](LOCAL_WIFI_SYNC_DESIGN.md)
- [Pairing- und Sicherheitsentscheidungen](PAIRING_PROTOCOL_DECISIONS.md)
- [Ausfallsichere Synchronisation](SYNC_RESILIENCE_PLAN.md)
- [Direkte WF-RAC-WLAN-Verbindung](DIRECT_WLAN_MIGRATION.md)

## GitHub

- [Repository](https://github.com/Apropo1710/KlimaPilot)
- [Actions](https://github.com/Apropo1710/KlimaPilot/actions)
- [PR #6 – WLAN-Pairing (Draft)](https://github.com/Apropo1710/KlimaPilot/pull/6)
- [Produktions-APK](https://github.com/Apropo1710/KlimaPilot/releases/download/latest/KlimaPilot.apk)
