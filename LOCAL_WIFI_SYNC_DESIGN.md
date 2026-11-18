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
- Direkt nach der Callback-Registrierung wird der ausgewählte WLAN-Snapshot erneut gelesen (Transport, Interface, Adresse und aktuelles Defaultnetz). Hat sich das Netzwerk bereits während des Starts geändert, wird die Discovery vor der DNS-SD-Ankündigung abgebrochen. Damit wird ein möglicher Verlust im Zeitfenster zwischen erster Netzwerkerkennung und Callback-Registrierung abgefangen.
- Die Entscheidungslogik für Defaultnetzwechsel und Snapshot-Revalidierung ist als reine Funktion ausgelagert und mit JVM-Tests abgedeckt.
- Der aktuelle Code-Commit und seine Validierung werden ausschließlich in `PROJECT_STATUS.md` geführt.

### Authentifizierter Handshake-Listener

- Der TCP-Port wird weiterhin über DNS-SD angekündigt; Discovery-Metadaten sind nur ein Locator und kein Vertrauensnachweis.
- Eingehende Verbindungen durchlaufen den gegenseitigen `ClimatePeerTcp`-Handshake. Nur Peers, deren Schlüssel auf beiden Geräten im Trusted-Peer-Speicher liegen, können ihn abschließen.
- Ein ausgehender Handshake wird ausschließlich durch eine ausdrückliche Nutzeraktion gestartet. Der Client-Socket wird über die `SocketFactory` des ausgewählten WLAN-Netzes erstellt, auch wenn Android Mobilfunk als Defaultnetz verwendet.
- Serverseitige Sessions sind auf 1–2 Worker mit vier wartenden Verbindungen begrenzt; clientseitig läuft ein Worker mit vier Wartenden. Überlast wird abgewiesen.
- Verbindungen werden nach dem Handshake geschlossen. Stop/Cleanup schließt aktive Sockets und beendet die Session-Executor.
- Socket-Erzeugung und Aufnahme in die aktive Socket-Verwaltung sind atomar gegen `close()` abgesichert. Liefert eine SocketFactory einen Socket erst nach Beginn des Stop-Vorgangs zurück, wird er unmittelbar geschlossen und nicht mehr verbunden. Ein gezielter Instrumentierungstest deckt dieses Race ab; die Ausführung auf dem aktuellen Stand steht noch aus.
- Es werden weiterhin keine Profil-/Outbox- oder sonstigen App-Nutzdaten übertragen.

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

**Implementierungsstand:** Der Handshake ist seit Code-Commit `3ea93c9...` mit Listener und explizitem Client-Verbindungsversuch verbunden. Die neue Integration ist noch nicht durch CI oder einen Zwei-Smartphone-Test bestätigt.

## 6. Freigabegates vor Profiltransfer

Vor jeglicher Peer-Nutzdatenübertragung sind mindestens umzusetzen und zu prüfen:

1. Die Handshake-Integration durch CI und reale Zwei-Smartphone-Tests absichern; Fehler-, Timeout-, Netzwechsel- und Stop-Pfade prüfen.
2. Eindeutiges Nachrichtenframing und Protokollversion.
3. Harte Obergrenzen für Frame-, Event- und Gesamtnachrichtengröße.
4. Schema-, Typ-, Geräte- und Wertebereichsvalidierung.
5. Replay-/Duplikaterkennung und sichere Wiederaufnahme nach Verbindungsabbruch.
6. Bestätigung erst nach dauerhafter Übernahme empfangener Events.
7. Schutz gegen parallele Sessions und sauberes Socket-/Callback-Cleanup ist implementiert; CI-/Geräteprüfung steht aus.
8. Regressionstests für den bestehenden ioBroker-Sync-Pfad.
9. Erfolgreiche Android-CI-Validierung und Praxistest mit zwei Smartphones.

## 7. Validierung

Build-, CI- und Geräte-Testnachweise für den aktuellen Stand werden ausschließlich in `PROJECT_STATUS.md` geführt. Die dort dokumentierten älteren erfolgreichen Läufe sind kein Nachweis für den jeweils neuesten Codecommit.

## 8. Weiteres Vorgehen

Die Reihenfolge der nächsten Arbeitsschritte und deren Freigabestatus werden ausschließlich in `PROJECT_STATUS.md` gepflegt.