# KlimaPilot – direkte WLAN-Verbindung zum WF-RAC

**Fachlicher Stand:** 18.11.2026  
**Zielgerät:** Mitsubishi Heavy SRK15ZS-WF (noch nicht installiert)

## Zweck und Abgrenzung

Dieses Dokument beschreibt den direkten lokalen Kommunikationsweg der App zur Klimaanlage über das WF-RAC-Modul. Er ist vom neuen Smartphone-zu-Smartphone-Pairing vollständig getrennt.

- WF-RAC ist der direkte Steuerungsweg zur Klimaanlage.
- Lokale Smartphone-Synchronisation tauscht Profile/Ereignisse zwischen KlimaPilot-Installationen aus und steuert keine Klimaanlage.
- ioBroker-WebSocket und direkter WF-RAC-Weg sind getrennte Verbindungen.

## Produktiver Stand

Produktivversion, Buildnachweis und Release-Stand werden ausschließlich in `PROJECT_STATUS.md` gepflegt.

Der dokumentierte Funktionsumfang umfasst gerätebezogene Kopplung, getrennte Geräte-IDs/Verbindungen und die im App-Code vorhandenen WF-RAC-Bedienfunktionen. Dazu zählen Ein/Aus, Betriebsmodus, Solltemperatur, Lüfterstufe sowie die vom jeweiligen Modell gelieferten Luftführungsoptionen. Profilanwendung und Demo-Modus sind ebenfalls Bestandteil des App-Stands.

**Wichtige Einschränkung:** Die Klimaanlage ist noch nicht installiert. Die genannten Steuerpfade sind Code-/App-Funktionen, aber nicht am konkreten SRK15ZS-WF samt Firmware hardwarebestätigt. Keine Funktion darf vor dem Praxistest als erfolgreich am realen Gerät bestätigt gelten.

## Verbindungs- und Sicherheitsgrenzen

- Direkter lokaler Geräteverkehr ist vom ioBroker-WebSocket getrennt.
- Gespeicherte Geräteidentität und Transport-Fingerprint dienen der bewussten Zuordnung des WF-RAC-Moduls.
- Eine spätere Änderung an Verbindungsaufbau, Zertifikats-/Fingerprintprüfung oder Gerätezuordnung benötigt einen gezielten Regressionstest.
- Die lokale Smartphone-Peer-Verbindung verwendet eine andere Identität und einen eigenen Trusted-Peer-Speicher; sie darf nicht mit WF-RAC-Geräteidentität vermischt werden.

## Noch ausstehend

1. Klimaanlage und WF-RAC-Modul installieren.
2. Tatsächliche Firmware-/Datenpunkt- und Funktionszuordnung am Gerät prüfen.
3. Ein-/Ausschalten, Modi, Solltemperatur, Lüfterstufen und verfügbare Luftführungsoptionen am realen Gerät testen.
4. Verbindungsabbruch, Wiederverbindung und Neustartverhalten testen.
5. Ergebnisse mit Datum, App-Version, Firmwarestand und konkreten Testergebnissen dokumentieren.

## Nicht Bestandteil dieses Dokuments

- WLAN-Pairing zwischen zwei Smartphones: siehe `LOCAL_WIFI_SYNC_DESIGN.md`.
- Pairing-Sicherheitsentscheidungen: siehe `PAIRING_PROTOCOL_DECISIONS.md`.
- Produktions-/Branch-/Buildstatus: ausschließlich siehe `PROJECT_STATUS.md`.

## Updater

Updater und Installationsablauf sind abgeschlossen und dürfen nicht verändert werden. Keine Arbeiten am Updater im Rahmen der WF-RAC- oder WLAN-Peer-Entwicklung.
