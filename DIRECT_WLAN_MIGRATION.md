# KlimaPilot – direkte WLAN-Verbindung zum WF-RAC

**Fachlicher Stand:** 19.11.2026  
**Geräte:** zwei Mitsubishi Heavy SRK15ZS-WF, seit 19.11.2026 in Betrieb

## Zweck und Abgrenzung

Dieses Dokument beschreibt den direkten lokalen Kommunikationsweg der App zur Klimaanlage über das WF-RAC-Modul. Er ist vom neuen Smartphone-zu-Smartphone-Pairing vollständig getrennt.

- WF-RAC ist der direkte Steuerungsweg zur Klimaanlage.
- Lokale Smartphone-Synchronisation tauscht Profile/Ereignisse zwischen KlimaPilot-Installationen aus und steuert keine Klimaanlage.
- ioBroker-WebSocket und direkter WF-RAC-Weg sind getrennte Verbindungen.

## Produktiver Stand

Produktivversion, Buildnachweis und Release-Stand werden ausschließlich in `PROJECT_STATUS.md` gepflegt.

Der dokumentierte Funktionsumfang umfasst gerätebezogene Kopplung, getrennte Geräte-IDs/Verbindungen und die im App-Code vorhandenen WF-RAC-Bedienfunktionen. Dazu zählen Ein/Aus, Betriebsmodus, Solltemperatur, Lüfterstufe sowie die vom jeweiligen Modell gelieferten Luftführungsoptionen. Profilanwendung und Demo-Modus sind ebenfalls Bestandteil des App-Stands.

**Hardwarestand:** Kopplung, Statusanzeige, Solltemperatur, Profilanwendung und Kompressor-Anzeige sind seit 19.11.2026 am realen Gerät bestätigt. Modi, Lüfterstufen und Luftführung sind noch nicht gezielt geprüft (aktueller Stand in `PROJECT_STATUS.md`, Abschnitt 5).

## Kopplung und Identität (seit 0.4.79)

- Die Modulidentität (Aircon-ID, IP, Verfahren, ggf. Zertifikat-Fingerprint) wird pro physischer Anlage gespeichert, nicht pro App-Gerät. Eine Anlage darf nur einem App-Gerät zugeordnet sein; die App verhindert Doppelzuordnungen.
- Zurücksetzen oder Löschen entfernt die Modulidentität nur, wenn kein anderes App-Gerät dieselbe Anlage nutzt.
- Ausdrückliches Koppeln durch den Nutzer baut das Vertrauen neu auf und ersetzt veraltete Zuordnungen (alte IP dieser Anlage, andere Anlage unter dieser IP).
- Fehlt die Modulidentität, obwohl das App-Gerät Aircon-ID und IP kennt, wird sie automatisch wiederhergestellt – aber nur, wenn das Modul an dieser IP genau diese Aircon-ID meldet.

## Zeitverhalten (seit 0.4.81)

- Das Modul bearbeitet eine Verbindung zur Zeit und verlangt rund 1 s Abstand zwischen Anfragen; Anfragen werden pro Anlage serialisiert.
- Die Identitätsprüfung (`getDeviceInfo`) läuft einmal pro App-Sitzung und Anlage; die erste Statusanzeige wartet nicht darauf, jeder Schreibbefehl prüft vorher.
- Die Kontoregistrierung am Modul (`updateAccountInfo`) wird dauerhaft gemerkt und nur bei Ablehnung wiederholt.
- Temperaturänderungen werden gesammelt und nach 1,2 s ohne weitere Eingabe als ein Befehl gesendet.

## Verbindungs- und Sicherheitsgrenzen

- Direkter lokaler Geräteverkehr ist vom ioBroker-WebSocket getrennt.
- Gespeicherte Geräteidentität und Transport-Fingerprint dienen der bewussten Zuordnung des WF-RAC-Moduls.
- Eine spätere Änderung an Verbindungsaufbau, Zertifikats-/Fingerprintprüfung oder Gerätezuordnung benötigt einen gezielten Regressionstest.
- Die lokale Smartphone-Peer-Verbindung verwendet eine andere Identität und einen eigenen Trusted-Peer-Speicher; sie darf nicht mit WF-RAC-Geräteidentität vermischt werden.

## Noch ausstehend

1. Modi, Lüfterstufen und verfügbare Luftführungsoptionen am realen Gerät gezielt testen.
2. Verbindungsabbruch, Wiederverbindung und Neustart des Moduls testen.
3. Ergebnisse mit Datum, App-Version und Firmwarestand in `PROJECT_STATUS.md` festhalten.

## Nicht Bestandteil dieses Dokuments

- WLAN-Pairing zwischen zwei Smartphones: siehe `LOCAL_WIFI_SYNC_DESIGN.md`.
- Pairing-Sicherheitsentscheidungen: siehe `PAIRING_PROTOCOL_DECISIONS.md`.
- Produktions-/Branch-/Buildstatus: ausschließlich siehe `PROJECT_STATUS.md`.

## Updater

Updater und Installationsablauf sind abgeschlossen und dürfen nicht verändert werden. Keine Arbeiten am Updater im Rahmen der WF-RAC- oder WLAN-Peer-Entwicklung.
