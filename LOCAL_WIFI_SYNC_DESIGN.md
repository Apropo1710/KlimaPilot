# KlimaPilot – lokale Smartphone-Synchronisation

**Fachlicher Stand:** 18.11.2026  
**Aktueller Branch-/PR-/CI-Status:** ausschließlich in `PROJECT_STATUS.md`.

## 1. Ziel und Grenzen

Zwei KlimaPilot-Installationen sollen nach bewusster gegenseitiger Kopplung Profile/Ereignisse lokal über WLAN austauschen können. Der Weg ist unabhängig von Cloud-Diensten und vom direkten WF-RAC-Steuerpfad.

Bis zur abgeschlossenen Integration und einem erfolgreichen Test mit zwei realen Smartphones werden keine Profil- oder sonstigen App-Nutzdaten über den Peer-TCP-Weg übertragen.

## 2. Bestehende Synchronisationsbasis

- Profile und Änderungen werden lokal gespeichert.
- Eine dauerhafte Outbox hält noch nicht bestätigte Ereignisse.
- Ereignisse enthalten Installations-/Gerätebezug, Sequenz und Logical Clock.
- Der bestehende ioBroker-Sync-Client nutzt eine eigene WebSocket-Verbindung und prüft Authentifizierung sowie `system.adapter.admin.0.alive`.
- `ClimateProfileSyncEngine` verarbeitet Remote-Ereignisse transportneutral mit Versionsvergleich, Gerätezuordnung und Fehlerisolation.
- Diese Engine wird aktuell vom ioBroker-Sync-Pfad verwendet. Die lokale Peer-Verbindung ist noch nicht daran angeschlossen.

## 3. Implementierter Peer-Aufbau

### Discovery

- DNS-SD-Diensttyp: `_klimapilot-sync._tcp.`
- TXT-Metadaten enthalten nur Protokollversion und zufällige Discovery-ID.
- Entdeckte Geräte sind `UnverifiedClimatePeer`, keine vertrauten Peers.
- Metadaten werden streng validiert; eigene Instanz/ID, ungültige Versionen, Ports und nicht-lokale Adressen werden verworfen.
- Listener wird an eine ausgewählte lokale Adresse gebunden: private IPv4 bevorzugt, IPv4-Link-Local Fallback, danach IPv6-ULA und scoped IPv6-Link-Local. Öffentliche Adressen werden nicht als Listener-Adresse verwendet.
- Discovery hat Grenzen für Peer-Anzahl, parallele Resolve-Vorgänge, Retry und Timeout.

### Netzwerk-Lifecycle

- WLAN-spezifischer Callback überwacht Verlust und Link-/Adressänderungen des ausgewählten WLANs.
- Ein zusätzlicher Default-Network-Callback überwacht Wechsel des Android-Standardnetzes.
- Ein zweites WLAN, das lediglich verfügbar wird, beendet die Discovery nicht.
- Wird ein anderes WLAN zum Standardnetz, wird fail-closed abgebrochen.
- Mobilfunk als Standardnetz ist zulässig, solange das ausgewählte WLAN verfügbar bleibt.
- Beide Callbacks werden beim Cleanup abgemeldet.
- Der aktuelle Code-Commit und seine Validierung werden ausschließlich in `PROJECT_STATUS.md` geführt.

### Reject-only Listener

Der TCP-Port wird für Discovery angekündigt. Eingehende Verbindungen werden angenommen und unmittelbar geschlossen. Der Listener authentifiziert noch keine Peers und verarbeitet keine Nutzdaten.

## 4. Identität und QR-Pairing

- ECDSA P-256-Identität im Android Keystore; privater Schlüssel nicht exportierbar.
- Peer-ID wird aus dem öffentlichen Schlüssel abgeleitet.
- Signierte QR-Einladung mit 120 Sekunden Gültigkeit, Sitzungs-ID und Nonce.
- Signierte Antwort bindet den Responder an Einladung, Sitzung und Nonce.
- Beide Seiten müssen den Peer/Fingerprint explizit bestätigen.
- Erst danach darf der Peer in den Trusted-Peer-Speicher übernommen werden.
- Debug-Pairing ist von der produktiven Release-App getrennt (`de.klimapilot.app.debug`).

## 5. TCP-Peer-Authentifizierung und Transportentscheidung

`ClimatePeerTcp` enthält einen gegenseitigen Challenge-/Response-Handshake:
- zufällige 32-Byte-Nonces auf beiden Seiten,
- Rollenbindung und signiertes Transkript,
- Peer-ID/Public-Key-Konsistenz,
- Prüfung gegen den lokalen Trusted-Peer-Speicher,
- ECDSA-Signaturprüfung,
- begrenzte Feldgrößen sowie Connect-/Handshake-Zeitlimits.

TLS/mTLS wurde nach ausdrücklicher Nutzerentscheidung verworfen. TCP ist nicht transportverschlüsselt; andere Teilnehmer desselben WLANs können den Datenverkehr passiv mitlesen. Signaturen authentifizieren Peers und erschweren aktive Manipulation, ersetzen aber keine Verschlüsselung.

**Wichtig:** Der Handshake ist noch nicht mit dem Discovery-Listener verbunden. Die Existenz der Klasse bedeutet nicht, dass der Netzwerkpfad bereits authentifiziert arbeitet.

## 6. Freigabegates vor Profiltransfer

Vor jeglicher Peer-Nutzdatenübertragung sind mindestens umzusetzen und zu prüfen:

1. Handshake sicher in den Listener-/Client-Lifecycle integrieren.
2. Eindeutiges Nachrichtenframing und Protokollversion.
3. Harte Obergrenzen für Frame-, Event- und Gesamtnachrichtengröße.
4. Schema-, Typ-, Geräte- und Wertebereichsvalidierung.
5. Replay-/Duplikaterkennung und sichere Wiederaufnahme nach Verbindungsabbruch.
6. Bestätigung erst nach dauerhafter Übernahme empfangener Events.
7. Schutz gegen parallele Sessions und sauberes Socket-/Callback-Cleanup.
8. Regressionstests für den bestehenden ioBroker-Sync-Pfad.
9. Erfolgreiche Android-CI-Validierung und Praxistest mit zwei Smartphones.

## 7. Validierung

Build-, CI- und Geräte-Testnachweise für den aktuellen Stand werden ausschließlich in `PROJECT_STATUS.md` geführt. Die dort dokumentierten älteren erfolgreichen Läufe sind kein Nachweis für den jeweils neuesten Codecommit.

## 8. Weiteres Vorgehen

Die Reihenfolge der nächsten Arbeitsschritte und deren Freigabestatus werden ausschließlich in `PROJECT_STATUS.md` gepflegt.