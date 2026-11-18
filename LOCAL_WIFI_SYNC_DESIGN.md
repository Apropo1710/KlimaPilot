# KlimaPilot – lokale Smartphone-Synchronisation

**Fachlicher Stand:** 18.11.2026  
**Aktueller Branch-/PR-/CI-Status:** ausschließlich in `PROJECT_STATUS.md`.

## 1. Ziel und Grenzen

Zwei KlimaPilot-Installationen sollen nach bewusster gegenseitiger Kopplung Profile/Ereignisse lokal über WLAN austauschen können. Der Weg ist unabhängig von Cloud-Diensten und vom direkten WF-RAC-Steuerpfad.

Seit 0.4.76/0.4.77 (18.11.2026) werden nach gegenseitiger Authentifizierung Konten, Profilereignisse, Admin-Konfiguration (Räume, Geräte, WF-RAC-Modulidentitäten) und die ioBroker-Adresse übertragen; siehe Abschnitt 6. Zwei-Geräte-Test bestanden, in Produktion seit 0.4.77.

## 2. Bestehende Synchronisationsbasis

- Profile und Änderungen werden lokal gespeichert.
- Eine dauerhafte Outbox hält noch nicht bestätigte Ereignisse.
- Ereignisse enthalten Installations-/Gerätebezug, Sequenz und Logical Clock.
- Der bestehende ioBroker-Sync-Client nutzt eine eigene WebSocket-Verbindung und prüft Authentifizierung sowie `system.adapter.admin.0.alive`.
- `ClimateProfileSyncEngine` verarbeitet Remote-Ereignisse transportneutral mit Versionsvergleich, Gerätezuordnung und Fehlerisolation.
- Diese Engine wird vom ioBroker-Sync-Pfad und vom Peer-Abgleich (`ClimatePeerDataSync`) verwendet.

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
- Ein ausgehender Handshake wird automatisch gestartet, sobald ein angekündigtes Gerät gefunden wird, und danach alle 30 s wiederholt (nur bei geöffneter App und mindestens einem gekoppelten Gerät). Der Client-Socket wird über die `SocketFactory` des ausgewählten WLAN-Netzes erstellt, auch wenn Android Mobilfunk als Defaultnetz verwendet.
- Serverseitige Sessions sind auf 1–2 Worker mit vier wartenden Verbindungen begrenzt; clientseitig läuft ein Worker mit vier Wartenden. Überlast wird abgewiesen.
- Nach dem Handshake folgt der Datenabgleich (Abschnitt 6), danach wird die Verbindung geschlossen. Stop/Cleanup schließt aktive Sockets und beendet die Session-Executor.
- Socket-Erzeugung und Aufnahme in die aktive Socket-Verwaltung sind atomar gegen `close()` abgesichert. Liefert eine SocketFactory einen Socket erst nach Beginn des Stop-Vorgangs zurück, wird er unmittelbar geschlossen und nicht mehr verbunden. Ein gezielter Instrumentierungstest deckt dieses Race ab; die Ausführung auf dem aktuellen Stand steht noch aus.

## 4. Identität und QR-Pairing

- ECDSA P-256-Identität im Android Keystore; privater Schlüssel nicht exportierbar.
- Peer-ID wird aus dem öffentlichen Schlüssel abgeleitet.
- Signierte QR-Einladung mit 120 Sekunden Gültigkeit, Sitzungs-ID und Nonce.
- Signierte Antwort bindet den Responder an Einladung, Sitzung und Nonce.
- Ein-Scan-Ablauf: Das zweite Gerät bestätigt mit „Koppeln“; die Fingerprint-Prüfung läuft im Hintergrund (keine Fingerprint-Anzeige in der Oberfläche).
- Erst danach darf der Peer in den Trusted-Peer-Speicher übernommen werden.
- Kopplung ist in Produktions- und Testversion verfügbar; die beiden Apps haben getrennte Identitäten und müssen jeweils für sich gekoppelt werden.

## 5. TCP-Peer-Authentifizierung und Transportentscheidung

`ClimatePeerTcp` enthält einen gegenseitigen Challenge-/Response-Handshake:
- zufällige 32-Byte-Nonces auf beiden Seiten,
- Rollenbindung und signiertes Transkript,
- Peer-ID/Public-Key-Konsistenz,
- Prüfung gegen den lokalen Trusted-Peer-Speicher,
- ECDSA-Signaturprüfung,
- begrenzte Feldgrößen sowie Connect-/Handshake-Zeitlimits.

TLS/mTLS wurde nach ausdrücklicher Nutzerentscheidung verworfen. TCP ist nicht transportverschlüsselt; andere Teilnehmer desselben WLANs können den Datenverkehr passiv mitlesen. Signaturen authentifizieren Peers und erschweren aktive Manipulation, ersetzen aber keine Verschlüsselung.

**Implementierungsstand:** Handshake und anschließender Datenabgleich sind in Listener und automatischen Client-Abgleich integriert; Zwei-Geräte-Test am 18.11.2026 bestanden.

## 6. Datenabgleich nach der Authentifizierung (umgesetzt)

- Rahmen: Magic `KPSD`, 4-Byte-Länge, UTF-8-JSON, höchstens 4 MiB, Lese-Timeout 20 s, Protokollversion 1. Client sendet zuerst, Server antwortet; beide Seiten wenden den empfangenen Stand an.
- Inhalt: Kontenliste; neuestes Profilereignis je (Gerätekennung, Profil) aus Outbox und Peer-Ereignisprotokoll (inkl. über ioBroker gelernter Ereignisse); Admin-Konfiguration mit Änderungszeit; ioBroker-Adresse.
- Profilereignisse laufen durch `ClimateProfileSyncEngine` (Versionsvergleich, Gerätezuordnung über `syncDeviceKey`, Fehlerisolation); Wiederholungen sind idempotent.
- Konten: höhere Revision gewinnt; bei gleicher Revision und anderem Inhalt entscheidet ein SHA-256-Digest, sodass beide Geräte gleich enden. Beschädigte lokale Daten werden nie überschrieben.
- Konfiguration: neuere Änderungszeit gewinnt (nur Administratoren können Räume/Geräte ändern). WF-RAC-Modulidentitäten werden übernommen; jedes Handy registriert sich beim ersten Befehl selbst am Modul.
- ioBroker-Adresse wird nur übernommen, wenn lokal keine gespeichert ist.
- Grenzen: höchstens 20 000 Ereignisse je Abgleich; Transport unverschlüsselt (Nutzerentscheidung), Inhalte inkl. Passwort-Hashes sind im WLAN mitlesbar.
- Bekannte Vereinfachung: Konflikte bei gleichzeitigen Kontoänderungen auf zwei Geräten werden ganz zugunsten eines Stands aufgelöst (keine Zusammenführung einzelner Konten).

## 7. Validierung

Build-, CI- und Geräte-Testnachweise für den aktuellen Stand werden ausschließlich in `PROJECT_STATUS.md` geführt. Die dort dokumentierten älteren erfolgreichen Läufe sind kein Nachweis für den jeweils neuesten Codecommit.

## 8. Weiteres Vorgehen

Die Reihenfolge der nächsten Arbeitsschritte und deren Freigabestatus werden ausschließlich in `PROJECT_STATUS.md` gepflegt.