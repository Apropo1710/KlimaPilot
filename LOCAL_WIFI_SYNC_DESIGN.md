# KlimaPilot – Design lokale Smartphone-Synchronisation

**Branch:** `feature/local-wifi-profile-sync`  
**Stand:** 17.11.2026  
**Status:** technische Bestandsaufnahme und Architekturvorschlag; noch keine Implementierung oder endgültige Freigabe des Sicherheitsprotokolls.

## 1. Ziel und Grenzen

Zwei oder mehr KlimaPilot-Installationen sollen Profiländerungen direkt austauschen können, wenn sie gleichzeitig im selben lokalen WLAN erreichbar sind. Der lokale Datenbestand und die lokale Klimaanlagensteuerung bleiben unabhängig davon nutzbar.

- ioBroker bleibt der bestehende Austauschweg zwischen unterschiedlichen Netzen.
- Direkter Peer-Austausch ist ein zusätzlicher Weg, kein Ersatz für die vorhandene Outbox.
- Kein Cloud-Dienst, Relay oder zusätzlicher Vermittlungsserver.
- Keine Änderungen am WF-RAC-Steuerungsprotokoll oder Updater.
- Synchronisiert werden ausschließlich bereits definierte KlimaPilot-Profilereignisse und -Snapshots. Keine Zugangsdaten oder WF-RAC-Steuerbefehle.

## 2. Verifizierter Ist-Zustand

### 2.1 Profil- und Ereignisspeicherung

- `ClimateProfileRepository` befindet sich in `app/src/main/java/de/klimapilot/app/ClimateProfiles.kt`.
- Profil-Upserts und Löschungen werden mit gemeinsamer Gerätekennung als lokaler Profilbestand plus dauerhaftes Journal gespeichert.
- `ClimateProfileSyncStore` hält die dauerhafte Outbox, Installation-ID, Sequenz, Logical Clock, Versionsmarker, aktive Auswahl und individuelle Profile.
- Journal-Übernahme ist über stabile Event-IDs idempotent. Bestätigte Outbox-Ereignisse werden erst nach erfolgreicher Übertragung entfernt.
- `ClimateProfileStorageLock.monitor` serialisiert Repository- und Store-Zugriffe innerhalb des Prozesses. Netzwerk-I/O darf nicht unter dieser Sperre stattfinden.
- Remote-Ereignisse werden über `ClimateProfileSyncClient.applyRemoteEvents` und `applyRemoteEventIfNewer` validiert, versionsgeprüft und angewendet. Diese Logik soll der lokale Transport wiederverwenden.

### 2.2 Bestehender ioBroker-Weg

- `ClimateProfileSyncClient.syncNow` öffnet eine eigene ioBroker-WebSocket-Verbindung.
- Vor dem Austausch werden ioBroker-Authentifizierung und `system.adapter.admin.0.alive` geprüft.
- Outbox-Ereignisse werden unter `0_userdata.0.KlimaPilot.profileSync.devices.*` übertragen; anschließend werden Remote-Ereignisse gelesen und angewendet.
- Der Profilbereich stößt den Austausch nach Änderungen und zusätzlich ungefähr minütlich an. Der direkte WLAN-Weg muss in denselben Auslöser-/Koordinationspfad integriert werden, darf aber keine parallelen konkurrierenden Läufe desselben Stores verursachen.

### 2.3 Geräteidentität ist keine Peer-Authentifizierung

- `UserDevice.syncDeviceKey` wird aus der Aircon-ID des gekoppelten WF-RAC-Moduls als `wfrac:<Aircon-ID>` gebildet.
- Die bestehende UI erklärt ausdrücklich, dass diese Kennung die Profile der physischen Klimaanlage zuordnet und **kein Zugangsschlüssel** ist.
- Die Kennung ist deshalb ausschließlich ein fachlicher Zuordnungsschlüssel für Ereignisse. Sie darf niemals als Passwort, Token oder Nachweis eines vertrauenswürdigen Smartphones verwendet werden.
- Das WF-RAC-mDNS-Verfahren in `wfrac/WfRacDiscovery.kt` sucht ausschließlich den Diensttyp `_beaver._tcp.` und erwartet den WF-RAC-Port. Es darf nicht für KlimaPilot-Peers umfunktioniert werden.

### 2.4 Android-Netzwerkbestand

- minSdk 26, targetSdk 35, Java 17.
- Manifest enthält bereits `INTERNET`, `ACCESS_NETWORK_STATE` und `CHANGE_WIFI_MULTICAST_STATE`.
- NSD/mDNS wird bereits über `NsdManager` verwendet; der WF-RAC-Scanner hält einen eigenen Multicast-Lock und beendet Listener/Lock über `stop()`.
- Neue Peer-Discovery benötigt einen separaten Diensttyp, Listener-Lifecycle und eigene Tests. Keine gemeinsame globale Listener- oder Multicast-Lock-Instanz mit dem WF-RAC-Scanner.

## 3. Vorgeschlagene Architektur

### 3.1 Trennung von Discovery, Transport und Sync-Engine

1. **Peer Discovery:** eigener NSD-Diensttyp, beispielsweise `_klimapilot-sync._tcp.`. Veröffentlicht nur Protokollversion, zufällige Peer-ID und Port. Keine Aircon-ID, Kontonamen oder sonstige persönlichen Daten in mDNS-TXT-Records.
2. **Peer Listener/Transport:** ein lokaler TCP-Endpunkt mit begrenzter Laufzeit und klarer Lifecycle-Steuerung. Er wird nur aktiv, solange die App im Vordergrund und der lokale Sync aktiviert ist. WLAN-Wechsel und Netzwerkverlust beenden/verwerfen alte Endpunkte und lösen eine neue Discovery aus.
3. **Peer Trust:** Discovery ist nicht Authentifizierung. Vor jedem Datenaustausch muss der Peer kryptografisch als zuvor ausdrücklich gekoppelt bestätigt sein.
4. **Gemeinsame Sync-Engine:** Transportunabhängige Koordination der vorhandenen Outbox, Ereignisvalidierung, Versions-/Konfliktlogik, Remote-Anwendung und Bestätigung. ioBroker und lokaler Peer liefern/empfangen dieselben Event-Objekte. Keine zweite Konfliktlogik.
5. **Koordinator:** verhindert überlappende Synchronisierungsläufe und Schleifen, wenn ioBroker und lokaler Peer gleichzeitig erreichbar sind.

### 3.2 Ereignisaustausch

- Beide Seiten stellen ihre noch offenen Outbox-Ereignisse bereit.
- Empfangene Events durchlaufen dieselbe Validierung und Versionsprüfung wie ioBroker-Events.
- Empfangene Ereignisse werden nicht erneut als lokale Änderung in die Outbox gespiegelt (kein Echo).
- Sender bestätigt ein Event erst, wenn der Empfänger es dauerhaft und erfolgreich übernommen hat.
- Verbindungsabbruch vor Bestätigung lässt das Ereignis in der Outbox; Wiederholung muss idempotent sein.
- Nach einem erfolgreichen Austausch wird erneut geprüft, ob während des Netzwerkdialogs neue lokale Events entstanden sind. Der Lauf endet erst, wenn die übertragene Revision bestätigt oder der Lauf mit Retry-Grund beendet ist.
- Die existierende Logical-Clock-/Last-Writer-Wins-Regel bleibt unverändert. Konfliktregeln werden nicht parallel im Peer-Protokoll neu implementiert.

## 4. Sicherheitsentwurf – vor Implementierung zu finalisieren

### 4.1 Nicht ausreichend

- Gleiches WLAN, gleiche SSID oder bekannte IP-Adresse sind kein Vertrauensnachweis.
- mDNS-Service-Name und TXT-Records sind frei manipulierbar und dürfen keine Authentifizierung ersetzen.
- `syncDeviceKey` / Aircon-ID ist öffentlich ableitbar und ausdrücklich kein Geheimnis.
- Keine ungeschützte HTTP-/WebSocket-Verbindung mit Profilinhalten und keiner stillen Annahme unbekannter Peers.

### 4.2 Bevorzugter Kopplungsansatz zur Prüfung

- Jede Installation erhält eine eigene kryptografische Peer-Identität (Schlüsselpaar; privater Schlüssel nicht exportierbar im Android Keystore, sofern die API-/Geräteunterstützung dies zulässt).
- Erstkopplung erfolgt bewusst durch den Nutzer, z. B. über QR-Code mit kurzlebigem Einmal-Geheimnis und Geräte-Fingerprints oder über einen ausreichend starken, kurzlebigen Bestätigungscode.
- Beide Seiten bestätigen die angezeigte Gegenstelle. Danach werden Peer-ID und verifizierter öffentlicher Schlüssel dauerhaft als vertrauenswürdig gespeichert.
- Verbindungen verwenden verschlüsselte Kommunikation und gegenseitigen kryptografischen Identitätsnachweis (bevorzugt TLS mit gepinnten Peer-Zertifikaten oder ein gleichwertig überprüfbares Protokoll).
- Jede Nachricht ist an die authentifizierte Sitzung gebunden; Replay-Schutz, Nonces, Protokollversion, Größenlimits, Zeitlimits und Rate-Limits sind vorzusehen.
- Kopplung kann widerrufen werden. Unbekannte, widerrufene oder nicht korrekt authentifizierte Peers erhalten keinerlei Profil- oder Kontodaten.

Der konkrete Schlüsselaustausch, die TLS-Implementierung und die Recovery bei Gerätewechsel müssen vor Codebeginn anhand der Android-API und einer etablierten Kryptobibliothek geprüft werden. Keine eigene Kryptografie erfinden.

## 5. Android- und Netzwerkkompatibilität

- NSD unterstützt Dienstsuche und Dienstregistrierung über mDNS/DNS-SD. Der API-Aufruf ist asynchron; Registrierung muss beim Ende des Dienstes wieder aufgehoben werden.
- Für ältere Android-/Extension-Stände kann Multicast-Empfang einen Multicast-Lock erfordern. Im Vordergrund soll unnötiges dauerhaftes Halten vermieden werden.
- IP-Adressen sind flüchtig. Nach WLAN-Wechsel oder Netzwerkwechsel muss der Dienst neu aufgelöst werden; gespeicherte IPs sind keine Geräteidentität.
- Der aktuelle App-Target ist 35. Für Android 16/17 ist die lokale Netzwerkberechtigung strategisch zu beobachten: Android-Dokumentation beschreibt Änderungen für Apps, die auf API 37+ zielen. Die aktuelle Implementierung darf nicht vorschnell eine nicht benötigte Berechtigung einführen; vor einem späteren Target-SDK-Sprung ist Discovery/Picker- und Permission-Verhalten erneut zu prüfen.
- Der NSD-System-Picker ist als mögliche datensparsame Variante für spätere Android-Versionen zu evaluieren; er ersetzt nicht die KlimaPilot-Kopplung/Authentifizierung.

## 6. Geplante Umsetzung in kleinen Paketen

1. **Protokoll-/Security-Review:** Peer-Identität, Pairing, TLS, Schlüsselwiderruf, Replay-Schutz, Datenumfang und Transport-Lifecycle festlegen.
2. **Discovery-only:** Peer-Service registrieren/finden und im UI diagnostisch anzeigen. Noch keine Profilinhalte übertragen.
3. **Pairing:** vertrauenswürdige Peers explizit koppeln/widerrufen; negative Tests für unbekannte und manipulierte Peers.
4. **Transport:** authentifizierte verschlüsselte Verbindung, Limits, Timeout und WLAN-Wechselbehandlung.
5. **Sync-Engine:** gemeinsame Outbox-/Event-Verarbeitung, idempotente Bestätigung, kein Echo, keine Parallel-Läufe.
6. **Tests:** Unit-/Robolectric-Tests, simulierte Verbindungsabbrüche, Replay-/Manipulationsversuche, anschließend mindestens zwei reale Smartphones im selben WLAN.
7. **Review und PR:** unabhängiges Review, CI und Praxistest; erst danach PR zur Freigabe. `main` bleibt bis dahin unverändert.

## 7. Bewusste Nicht-Ziele

- Keine automatische WF-RAC-Steuerung über den Peer-Kanal.
- Keine Synchronisierung von Passwörtern, Konten-Geheimnissen oder ioBroker-Zugangsdaten.
- Keine Synchronisierung beliebiger Dateien oder allgemeiner App-Backups.
- Keine Internet-Peer-Verbindungen, NAT-Traversal, Cloud oder Relay.
- Keine Änderungen am Updater.
- Keine Änderungen an der bestehenden ioBroker-Eventstruktur, solange kein zwingender, getesteter Grund besteht.

## 8. Offene technische Entscheidungen

- Ob der Peer-Listener nur während aktiver App-Nutzung läuft oder später einen eng begrenzten Foreground-Service benötigt. Startpunkt: nur Vordergrund, kein permanenter Hintergrunddienst.
- Konkrete TLS-/Keystore-Implementierung und sichere Erstkopplung.
- Verhalten bei mehreren Geräten, die dieselbe Profil-Gerätekennung besitzen, und bei gleichzeitigen Updates über ioBroker und WLAN.
- UI für Kopplung, vertrauenswürdige Geräte, Widerruf und Status.
- Kompatibilitätsstrategie für unterschiedliche KlimaPilot-Protokollversionen.

## 9. Festgelegter Umfang für Phase 1: Discovery-only

Phase 1 ist ausdrücklich ein technischer Erreichbarkeitstest und noch keine Synchronisationsfunktion.

### 9.1 Dienst und veröffentlichte Metadaten

- Eigener DNS-SD-Diensttyp: `_klimapilot-sync._tcp.`; der WF-RAC-Dienst `_beaver._tcp.` bleibt vollständig getrennt.
- Ein Gerät registriert den Dienst und sucht parallel nach gleichartigen Diensten. Die eigene Registrierung wird anhand der lokalen Dienstinstanz ignoriert.
- Der Dienstname und eine zufällige, nur für die laufende Discovery gültige Instanzkennung enthalten keine Konten-, Personen-, Aircon- oder Gerätekennungen.
- TXT-Daten werden auf eine Protokoll-Hauptversion und eine zufällige, kurzlebige Discovery-Kennung begrenzt. Keine IP-Adresse, Zugangsdaten, öffentlichen Schlüssel, Profilinformationen oder Namen werden dort veröffentlicht.
- Die vom Betriebssystem aufgelöste IP-Adresse gilt ausschließlich als momentaner Netzwerk-Locator. Sie wird nicht als Identität gespeichert.
- Da DNS-SD/TXT-Daten manipulierbar sind, werden gefundene Einträge nur als „nicht verifizierte KlimaPilot-Dienstankündigung“ behandelt.

### 9.2 Netzwerk-Endpunkt ohne Datenaustausch

- DNS-SD/TCP benötigt einen Port. Für Phase 1 darf deshalb ein eigener, kurzlebiger TCP-Endpunkt auf einem vom Betriebssystem zugewiesenen Port geöffnet und angekündigt werden.
- Der Endpunkt nimmt in Phase 1 keine Nutzdaten an: eingehende Verbindungen werden entweder sofort geschlossen oder gar nicht erst angenommen. Es gibt keinen HTTP-, WebSocket- oder Profil-Endpunkt.
- Es werden weder Profile noch Outbox-Ereignisse, Kontodaten, Schlüssel oder ioBroker-Daten übertragen.
- Unbekannte Dienste lösen keine Verbindung mit Dateninhalt aus. Discovery-Ergebnisse werden nur lokal diagnostisch angezeigt.
- Es gibt keine automatische Kopplung und keine dauerhafte Speicherung gefundener Peers.

### 9.3 Lifecycle und Fehlerverhalten

- Discovery und Advertising laufen ausschließlich bei aktivem App-Vordergrund und explizit gestarteter bzw. sichtbarer Diagnosefunktion. Kein Hintergrunddienst und keine dauerhafte Netzwerküberwachung.
- Registrierung, Suche, Auflösung, Listener und eventuell erforderlicher Multicast-Lock besitzen einen eigenen Lifecycle. Beim Stoppen, Verlassen der Diagnoseansicht, Netzwerkwechsel oder Fehler werden alle Ressourcen bestmöglich freigegeben.
- Wiederholte Start-/Stop-Aufrufe dürfen keine doppelten Listener oder Locks erzeugen. Ein fehlgeschlagener Teilstart muss bereits geöffnete Ressourcen zurückrollen.
- Nach einem WLAN-/Netzwechsel werden alte Auflösungen verworfen und nur nach erneutem Start aktuelle Dienste gesucht.
- Ergebnisse werden nach Dienstinstanz dedupliziert. Auflösung und Callback-Verarbeitung müssen gegen verspätete Rückrufe nach `stop()` abgesichert sein.
- Die WF-RAC-Discovery bleibt unverändert und darf nicht als Hilfsinstanz verwendet werden.

### 9.4 Abnahmekriterien für Phase 1

1. Zwei Android-Geräte im selben WLAN können die KlimaPilot-Dienstankündigung gegenseitig finden.
2. Ein einzelnes Gerät zeigt sich nicht selbst als Peer an.
3. Ein fremder oder manipulierter mDNS-Eintrag wird niemals als vertrauenswürdig oder gekoppelt dargestellt.
4. Während Phase 1 werden nachweislich keine Profil-, Konto-, Schlüssel- oder ioBroker-Daten übertragen.
5. Start, Stop, Wiederstart, WLAN-Wechsel und Fehlerpfade hinterlassen keine laufenden Discovery-Listener oder gehaltenen Multicast-Locks.
6. WF-RAC-Suche und lokale Klimasteuerung bleiben unverändert.

Automatisierte Tests sollen insbesondere Service-TXT-Parsing, Versions-/Instanzvalidierung, Deduplizierung und Lifecycle-Zustände abdecken. Die tatsächliche mDNS-Erkennung und das Verhalten verschiedener Android-Versionen erfordern zusätzlich einen Test mit zwei realen Smartphones.

**Freigabegrenze:** Erst nach erfolgreicher Discovery-only-Prüfung wird das separate Pairing-Paket begonnen. Die kryptografische Geräteidentität, Erstkopplung, TLS-/Keystore-Ausgestaltung, Widerruf und Replay-Schutz bleiben zwingende Sicherheits-Gates vor jedem Profil-Datenaustausch.

## 10. Präzisierung nach Prüfung der Android-NSD-API (17.11.2026)

Die aktuelle Android-Referenz bestätigt, dass NSD-Aufrufe asynchron über Listener/Callbacks arbeiten und Registrierung sowie Discovery ausdrücklich beendet werden müssen.

- Die ältere API `registerService(NsdServiceInfo, PROTOCOL_DNS_SD, RegistrationListener)` ist ab API 16 verfügbar und damit mit minSdk 26 kompatibel.
- Die neueren Executor-basierten NSD-Methoden sind ab API 33 verfügbar. Die kombinierte `registerServiceInfoCallback(DiscoveryRequest, Executor, ServiceInfoCallback)`-Variante ist laut Android-Referenz auf Geräten mit Android 14+ und Extension-Version 22 verfügbar; die Geräte-/Extension-Unterstützung muss zur Laufzeit geprüft werden.
- `resolveService(NsdServiceInfo, ResolveListener)` ist seit API 34 deprecated. Für ältere Plattformstände bleibt es der kompatible Fallback, sofern die klassische Discovery-API verwendet wird. Auf unterstützten neueren Plattformständen ist die Service-Info-Callback-API vorzuziehen.
- TXT-Attribute sind laut Referenz erst bei einem aufgelösten Service zuverlässig über `getAttributes()` verfügbar. Parsing und Validierung müssen deshalb nach der Auflösung erfolgen; gefundene, aber nicht aufgelöste Dienste bleiben reine unbestätigte Kandidaten.
- Die vorhandene klassische Registrierung ist ausreichend für den ersten Implementierungsschritt; neuere APIs dürfen nicht ohne Laufzeitprüfung aufgerufen werden.
- Registrierung, Discovery und ggf. Service-Info-Callbacks benötigen getrennte Referenzen und müssen beim Stoppen exakt deregistriert werden. Callbacks aus einer vorherigen Startgeneration dürfen keine UI oder Peer-Liste mehr verändern.

**Implementierungsfolge:** Zuerst eine isolierte, testbare Discovery-Komponente mit klarer Startgeneration und vollständigem Stop/Rollback. Danach gezielte Tests für TXT-Validierung, Versions-/Instanzprüfung und Lifecycle. Erst wenn CI und unabhängiges Claude-Review bestanden sind, folgt der manuelle Zwei-Smartphone-Test. Die API-Prüfung ändert nichts an der Freigabegrenze: In Phase 1 findet kein Profil- oder Kontodatenaustausch statt.

**Referenz:** Android Developers, `NsdManager` und `NsdServiceInfo`: https://developer.android.com/reference/android/net/nsd/NsdManager und https://developer.android.com/reference/android/net/nsd/NsdServiceInfo

## 11. Discovery-only – erster Implementierungsstand

- Neue isolierte Komponente: `app/src/main/java/de/klimapilot/app/sync/ClimateProfilePeerDiscovery.kt`.
- Eigener DNS-SD-Diensttyp `_klimapilot-sync._tcp.`; keine Wiederverwendung der WF-RAC-Erkennung.
- Eigene zufällige, kurzlebige Dienstinstanz und Discovery-ID; TXT-Daten sind strikt auf Protokoll-Hauptversion `v=1` und `id` begrenzt.
- TXT-Parser verwirft unbekannte Felder, falsche Versionen, ungültige IDs, ungültige Ports/Adressen und die eigene Instanz/Discovery-ID. Ein Treffer bleibt ausdrücklich `UnverifiedClimatePeer` und ist kein Vertrauensnachweis.
- Für die angekündigte TCP-Portnummer wird ein lokaler Socket geöffnet. Eingehende Verbindungen werden sofort geschlossen; es werden keine Bytes gelesen/geschrieben und kein Anwendungsprotokoll angeboten.
- Discovery und Advertising sind nur über die sichtbare Einstellungsdiagnose startbar. Beim Verlassen der Einstellungsansicht wird über `DisposableEffect` gestoppt. Stop beendet Discovery, deregistriert den Dienst, schließt den Socket und gibt den eigenen Multicast-Lock frei.
- Startgeneration schützt vor verspäteten NSD-Callbacks; gefundene Dienstinstanzen werden dedupliziert und bei Service-Lost wieder freigegeben.
- Die klassische `resolveService`-API bleibt für minSdk 26 als Kompatibilitätsweg im Einsatz; sie ist auf neueren Android-Versionen deprecated. Vor einer späteren API-Modernisierung sind neue NSD-Callbacks gezielt zu testen.
- Unit-Tests für strikte TXT-/Metadatenvalidierung liegen in `ClimatePeerAnnouncementTest.kt`.

**Noch nicht nachgewiesen:** Build/Unit-Tests, tatsächliche mDNS-Erkennung auf zwei Smartphones, Android-Versionsmatrix, vollständige Netzwerkwechseltests und Claude-Review. Phase 1 überträgt weiterhin keinerlei Profil-, Konto-, Schlüssel- oder ioBroker-Daten. Pairing bleibt gesperrt.

## 12. Zielarchitektur nach Produktentscheidung (17.11.2026)

Die manuelle Discovery-Diagnose aus Phase 1 ist **nicht** das spätere Bedienkonzept. Der Nutzer hat verbindlich festgelegt, dass die lokale Synchronisierung nach einmaliger Kopplung vollautomatisch arbeiten muss. Ein Suchbutton darf keine Voraussetzung für den Datenaustausch sein.

### 12.1 Geräteidentität und Kopplung

- Keine MAC-Adresse als dauerhafte Identität oder Vertrauensanker: Android kann WLAN-MAC-Adressen randomisieren bzw. Apps können sie nicht zuverlässig auslesen; außerdem ist eine MAC-Adresse kein kryptografischer Identitätsnachweis.
- Jede KlimaPilot-Installation erhält eine eigene zufällige, dauerhafte Peer-ID und ein eigenes asymmetrisches Schlüsselpaar.
- Der private Schlüssel bleibt nicht exportierbar im Android Keystore; der öffentliche Schlüssel wird beim Pairing gegenseitig verifiziert und dauerhaft als vertrauenswürdiger Peer gespeichert.
- Erstkopplung erfolgt bewusst durch den Nutzer, bevorzugt durch QR-Code mit kurzlebigem Pairing-Geheimnis und beidseitiger Anzeige/Bestätigung der Geräte-Fingerprints.
- Ein Gerätewechsel bedeutet eine neue Installation/Peer-Identität und erfordert einmaliges erneutes Pairing. Alte Peer-Schlüssel können widerrufen werden.
- Peer-ID und öffentlicher Schlüssel sind unabhängig von IP-Adresse, SSID, Router und physischer Klimaanlagenkennung.
- Die vorhandene `syncDeviceKey` / Aircon-ID bleibt ausschließlich fachliche Zuordnung für Klimaprofile und darf nicht als Smartphone-Identität oder Geheimnis verwendet werden.

### 12.2 Automatisches Wiederfinden statt manuelle Suche

- Nach Aktivierung der lokalen Synchronisierung startet KlimaPilot im normalen App-Vordergrund automatisch Dienstankündigung und Peer-Auflösung; es gibt keinen verpflichtenden Suchbutton.
- mDNS/DNS-SD wird als Netzwerk-Locator verwendet, nicht als Authentifizierung. Gespeicherte Peer-Identitäten begrenzen, mit welchen gefundenen Endpunkten überhaupt ein Handshake versucht wird.
- Eine IP-Adresse wird nur flüchtig verwendet und nach WLAN-Wechsel neu aufgelöst. IP-/MAC-Adressen werden nicht als dauerhafte Identität gespeichert.
- Beim Zurückkehren in die App wird die automatische Erkennung/Wiederverbindung fortgesetzt. Ein manueller Diagnose-Start ist allenfalls für Support/Fehlersuche vorgesehen.
- Kein permanenter Hintergrunddienst als Startannahme. Android beendet/limitiert Netzwerkaktivität im Hintergrund; zunächst wird automatische Synchronisierung bei aktiver App und bei Wiederaufnahme umgesetzt. Falls später echte Hintergrundzustellung verlangt wird, ist ein separater Foreground-Service-Entscheid mit sichtbarer Benachrichtigung und Akku-Auswirkungen nötig.
- Keine dauerhafte Suche im Sinn eines ständig laufenden aktiven Scans: Dienstankündigung und gezielte Auflösung bekannter Peers dienen dem Wiederfinden; die Verbindung wird nur für den Abgleich benötigt.

### 12.3 Eine Synchronisierungs-Engine für ioBroker und Peer

Der bestehende ioBroker-Weg bleibt erhalten und läuft parallel als zusätzlicher Transport. Beide Transportwege verwenden dieselben Ereignisse, Outbox, Versionsmarker, Validierung, Remote-Anwendung und Bestätigungsregeln.

- Die fachliche Synchronisierungslogik wird von der ioBroker-WebSocket-Transportimplementierung getrennt.
- Gemeinsame Engine: Outbox-Journal flushen, offene Events lesen, Remote-Events validieren/sortieren, `applyRemoteEventIfNewer` wiederverwenden, Versionsstände und Logical Clock fortschreiben, Bestätigungen erst nach dauerhafter Übernahme erteilen.
- ioBroker-Transport kapselt weiterhin Authentifizierung, `system.adapter.admin.0.alive`, Objekt-/State-Upload und Remote-State-Lesen.
- Peer-Transport kapselt ausschließlich die authentifizierte, verschlüsselte direkte Verbindung und den Austausch derselben Event-Objekte.
- Ein prozessweiter Sync-Koordinator serialisiert alle Läufe für denselben Store. ioBroker- und Peer-Läufe dürfen nicht konkurrierend denselben Outbox-Stand bestätigen oder Remote-Events gleichzeitig anwenden.
- Kein Echo: Ein empfangenes Event wird lokal angewendet und als Version erfasst, aber nicht als neue lokale Änderung in die Outbox gespiegelt.
- Ein Event wird erst vom jeweiligen Transport bestätigt, wenn die Gegenseite es dauerhaft übernommen hat. Abbruch vor Bestätigung lässt es in der Outbox; Wiederholung bleibt idempotent.
- Nach jedem Austausch werden neue lokale Revisionen erneut berücksichtigt; während eines Laufs entstandene Änderungen dürfen nicht verloren gehen.
- Kein grundsätzlicher Vorrang von Smartphone oder ioBroker. Alle Replikate konvergieren anhand der bestehenden Ereignisversionen (Logical Clock, Installation-ID, Sequenz). Bei konkurrierenden Änderungen desselben Profils bleibt die vorhandene deterministische Versions-/Last-Writer-Wins-Regel maßgeblich; unterschiedliche Ereignisse bleiben erhalten, sofern sie nicht dieselbe Profilversion überschreiben.
- Die bisherige periodische ioBroker-Synchronisierung bleibt bestehen. Direkte Peer-Synchronisierung wird zusätzlich bei neuem lokalem Event, neu erreichbarem gekoppeltem Peer und App-Wiederaufnahme angestoßen; zusätzlich wird ein gemeinsamer Koordinator gegen Parallel-Läufe eingesetzt.

### 12.4 Umsetzungsreihenfolge und Freigabegates

1. Architektur-/Review-Paket: aktuelle SyncClient- und SyncStore-Verträge dokumentieren; gemeinsame Transport-Schnittstelle und prozessweiten Koordinator festlegen. Noch keine Nutzdatenübertragung.
2. Peer Identity & Pairing: Keystore-Schlüssel, QR-Handshake, beidseitige Bestätigung, persistente Peer-Liste und Widerruf; negative Tests für unbekannte/manipulierte Schlüssel.
3. Secure Transport: etablierte TLS-/Krypto-Bibliothek, gegenseitige Peer-Authentifizierung, Replay-Schutz, Nonces, Protokollversion, Größen-/Zeit-/Rate-Limits.
4. Sync Engine: Transport-neutraler Event-Abgleich mit identischer Anwendung und Versionierung wie ioBroker; ioBroker-Verhalten durch Regressionstests schützen.
5. Automatic lifecycle: automatischer Start im App-Vordergrund, Wiederaufnahme, WLAN-Wechsel, Reconnect und keine manuelle Suchabhängigkeit.
6. Tests: Unit-/Robolectric-Tests, Verbindungsabbrüche/Replay/Unbekannte Peers, parallele Trigger beider Transportwege, mindestens zwei reale Smartphones und bestehender ioBroker-Praxistest.
7. Unabhängiges Review und CI; erst danach Freigabe/Merge. Kein Release-Build allein für Feature-Branch-Arbeit.

**Priorität:** ioBroker und Smartphone-zu-Smartphone sind gleichberechtigte Transportwege. Es gibt keine Quelle, die pauschal gewinnt. Die vorhandene Event-Versionierung entscheidet nur bei tatsächlichen Konflikten.

**Nicht verhandelbar:** Vor Abschluss von Pairing und gegenseitiger Authentifizierung werden über den lokalen Peer-Kanal keinerlei Profilinhalte oder andere vertrauliche Daten übertragen.

## 13. Umsetzungsschritt – gemeinsame Event-Anwendung (17.11.2026)

- Neue `ClimateProfileSyncCoordinator` serialisiert Sync-Läufe pro App-Prozess. Der ioBroker-Client verwendet diese gemeinsame Sperre; künftige Peer-Transporte müssen dieselbe Sperre verwenden.
- Neue `ClimateProfileSyncEngine` enthält jetzt die transportneutrale Remote-Event-Anwendung: Sortierung nach Logical Clock / Installation-ID / Sequenz, Gerätezuordnung, Validierungsisolation pro Event, Aufruf von `applyRemoteEventIfNewer`, dauerhafte Versionsaufzeichnung und Fehlerbehandlung.
- `ClimateProfileSyncClient` delegiert die Event-Anwendung an diese Engine. Bestehende Methoden-Signaturen bleiben als dünne interne Delegates erhalten, damit vorhandene Regressionstests und Aufrufer unverändert bleiben.
- Die ioBroker-spezifische Authentifizierung, Alive-Prüfung, State-/Object-Übertragung und das Lesen der States bleiben vorerst im ioBroker-Client. Kein ioBroker-Protokoll wurde verändert.
- Bestehende Tests in `ClimateProfileStorageConcurrencyTest` prüfen weiterhin Remote-Anwendung, Versionskonflikte und Ablehnung ungültiger Events über die Client-Delegates.
- Prozessweite Koordination verhindert konkurrierende Transporte innerhalb eines App-Prozesses. Sie ersetzt keine geräteübergreifende Idempotenz oder Serverbestätigung.
- Peer-Transport existiert weiterhin nicht. Kein Profilinhalt wird lokal über WLAN übertragen. Kein Pairing und kein automatischer Peer-Betrieb ist bereits fertig.
- Aktueller Code-Commit: `66802ce044fed2495e8be085177927451515ed69`. Branch-Validation #65 ist für diesen Head vorgemerkt/läuft noch; Ergebnis vor weiteren Integrationsschritten kontrollieren.

## 14. Umsetzungsschritt – kryptografische Installationsidentität (17.11.2026)

- Neue `ClimatePeerIdentityStore`-Grundlage: pro Installation wird ein ECDSA-Schlüsselpaar auf Kurve `secp256r1` im Android Keystore erzeugt.
- Der private Schlüssel bleibt nicht exportierbar; die Klasse stellt nur die Signierfunktion für begrenzte Challenges bereit.
- Die Peer-ID wird deterministisch als SHA-256-Fingerprint des DER-kodierten öffentlichen Schlüssels gebildet; zusätzlich wird ein gruppierter Fingerprint zur manuellen Vergleichsanzeige geliefert.
- Öffentlicher Schlüssel wird als Base64-X.509-DER ausgegeben. Signaturprüfung akzeptiert nur begrenzte Schlüssel-, Challenge- und Signaturgrößen und schlägt bei ungültigen Daten geschlossen fehl.
- Tests prüfen Signaturgültigkeit, Challenge-Bindung und Ablehnung fehlerhafter/übergroßer Eingaben.
- Dies ist nur die lokale Identitätsgrundlage. Es gibt noch keine Peer-Persistenz, kein QR-Pairing, keine Vertrauensentscheidung, keinen Widerruf und keinen Netzwerk-Handshake. Die Discovery-Daten bleiben untrusted.
- Keine Manifest-, Berechtigungs-, Abhängigkeits-, Updater-, WF-RAC-, ioBroker-Protokoll- oder Produktionsversionsänderung.
- Die Android Keystore-Generierung und Hardware-/Software-Key-Backends müssen auf realen Geräten separat getestet werden; CI-Unit-Tests beweisen diese Geräteintegration nicht.

## 15. Umsetzungsschritt – persistente Liste vertrauenswürdiger Peers (17.11.2026)

- Neue `ClimateTrustedPeerStore` speichert ausschließlich explizit als vertrauenswürdig übergebene Peer-Schlüssel in privaten App-SharedPreferences.
- Vor Speicherung wird der EC/X.509-Public-Key dekodiert und validiert; Peer-ID und Fingerprint werden selbst aus SHA-256 des kanonischen öffentlichen Schlüssels berechnet. Übergebene IDs werden nicht blind übernommen.
- Anzeigenamen sind auf 1–48 Zeichen begrenzt.
- `isTrusted` verlangt übereinstimmende Peer-ID und denselben kanonischen öffentlichen Schlüssel; bloße Discovery-Metadaten reichen nicht.
- `revokePeer` entfernt den gespeicherten Schlüssel; unbekannte/ungültige IDs werden abgelehnt.
- Robolectric-Tests decken Persistenz über Store-Neuinstanz, Key-Mismatch, ungültige Schlüssel/Namen und Widerruf ab.
- Wichtige Grenze: Die Klasse führt selbst kein Pairing aus. `trustPeer` darf erst nach erfolgreichem gegenseitigem QR-/Challenge-Handshake und expliziter Fingerprint-Bestätigung auf beiden Geräten aufgerufen werden. UI/Handshake existieren noch nicht.
- SharedPreferences speichern öffentliche Schlüssel, keine privaten Schlüssel oder Geheimnisse. Der private Schlüssel verbleibt im Android Keystore.
- Noch kein Netzwerktransport, keine automatische Synchronisierung, keine Änderung an Manifest, Berechtigungen, ioBroker-Protokoll, Updater, WF-RAC oder Produktionsversion.
