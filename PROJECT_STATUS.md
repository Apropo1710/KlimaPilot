# KlimaPilot – verbindlicher Projektstatus

**Letzter Abgleich:** 18.11.2026, 18:10 Uhr MEZ (automatische ioBroker-Einrichtung und QR-Kontoübernahme; WLAN-Workflow #246 erfolgreich; Test-APK 0.4.70)
**Repository:** https://github.com/Apropo1710/KlimaPilot

Diese Datei ist die **einzige maßgebliche Quelle für den aktuellen Projekt-, Branch-, PR-, Build- und Validierungsstand**. Die README dient nur als Einstieg. Die technischen Fachkonzepte enthalten Architektur und Entscheidungen, aber keine konkurrierenden Live-Statusübersichten.

## 1. Produktivstand – `main`

- Produktionsstand zuletzt separat bestätigt: **0.4.67 / versionCode 97**, Build #238 erfolgreich auf Commit `608912f99e2653d271104aa2f620d2c64cdef651`. Dieser Test-Branch-Arbeitsgang verändert `main` nicht.
- Release `latest` enthält die signierte APK dieses Builds. Der aktuelle `main`-HEAD ist ein späterer Dokumentationscommit; Build-Commit und aktueller Branch-HEAD sind daher ausdrücklich zu unterscheiden.
- [Direkter APK-Download](https://github.com/Apropo1710/KlimaPilot/releases/download/latest/KlimaPilot.apk)
- `main` wurde bei der hier dokumentierten Bereinigung nicht verändert.

## 2. Entwicklungsstand – `test` und PR #6

- Aktueller `test`-Code-Commit: `7f266a766d483d5c5ab267fd1ccd96e133655e78` – Version **0.4.70 / versionCode 100**, Debug `0.4.70-debug`, Paket `de.klimapilot.app.debug`. Testsignatur siehe 2e, automatische Einrichtung siehe 2f. Ältere Hash-/Build-Angaben in den Abschnitten 2a–2d und 5 sind historische Nachweise.
- Vorheriger Defaultnetz-Codecommit: `bba8969ba4306d159ba388753f85c6638bf32f7f`.
- Die anschließende Lifecycle-Snapshot-Absicherung und ihre Policy-Tests sind Bestandteil dieses Commits.
- [PR #6](https://github.com/Apropo1710/KlimaPilot/pull/6): offen, Draft, nicht gemergt. Kein Merge ohne ausdrückliche Freigabe.
- Die Branches sind weiterhin divergent; `test` liegt 217 Commits vor und 10 Commits hinter `main`; Merge-Base `c7f1d57e4a4661d1492e5dca42ec9a9841d3e5fd`. Kein Rebase oder Merge im Rahmen der Dokumentationspflege.
- PR #6 umfasst den WLAN-/Pairing-Arbeitsstrang. Vor einer späteren Integration ist ein vollständiger Diff-Review gegen den dann aktuellen `main` erforderlich.

## 2a. Kontenübernahme direkt aus ioBroker (18.11.2026)

- Auf einem Gerät ohne lokale Konten kann die vorhandene Registry über die WebSocket-Adresse direkt aus `0_userdata.0.KlimaPilot.accounts` in ioBroker geladen werden.
- Anschließend erfolgt die Anmeldung mit dem vorhandenen Benutzernamen und Passwort. Kein neuer Administrator und kein Datei-Export/-Import oder manuelles Kopieren erforderlich.
- Import überschreibt keine bereits befüllte oder beschädigte lokale Registry.
- Testversion: **0.4.68 / versionCode 98**, Debug-Paketkennung `de.klimapilot.app.debug`, versionName `0.4.68-debug`.
- Codeänderungen: `ab7da3a`, `9d15852`, `0ad1d42`, `f8f4261`, `86cd86d`, `7f75f4d`. Aktueller Branch-HEAD nach CI-/Dokumentationsänderungen: siehe Branch `test`.
- WLAN-Validierung #220 war auf Commit `7f75f4d534d646cf9f06540a7ceeef62ae2e9216` erfolgreich (Unit-Tests und Debug-Kompilierung).
- API-26-Lauf #86 auf Commit `f1fd7af3f6ba5ff61a4ccd27bd72f8df39c37b42` ist fehlgeschlagen: 4/5 Instrumentierungstests bestanden; der Fehler war `ClimatePeerTcpCompatibilityTest.clientSocketCreatedDuringShutdownIsClosedBeforeConnect`. Die APK-Upload-Schritte wurden dadurch übersprungen. Dieser Emulatorfehler betrifft den TCP-Peer-Test, nicht den Kontenimport.
- Zur Bereitstellung der APK wurde der WLAN-Workflow erweitert: Nach Unit-Tests und Debug-Build prüft er Paketkennung, Version, Abwesenheit von `REQUEST_INSTALL_PACKAGES` und APK-Signatur und lädt anschließend `KlimaPilot-debug-test-0.4.68` hoch. Der dazugehörige WLAN-Lauf #221 ist **erfolgreich abgeschlossen**. Das Artefakt `KlimaPilot-debug-test-0.4.68` (ID `14431108516`, 18.174.150 Bytes) wurde heruntergeladen und bereitgestellt. SHA-256: `c638579dbed18ed5de5b129cc5c5d4de72cb02cb091b01b8272825368258c816`. Das Artefakt läuft am 25.11.2026 ab. Kein neuer API-26-Lauf wurde durch diese Workflow-/README-Änderung ausgelöst.
- Der Nutzer hat die Anmeldung auf einem zweiten Smartphone mit einem bestehenden ioBroker-Konto erfolgreich begonnen/bestätigt. Der vollständige Zwei-Smartphone-Test läuft noch; Peer-Nutzdatentransfer bleibt bis zur erfolgreichen Geräteprüfung gesperrt.
- **Login-Komfort – teilweise auf `test` implementiert (18.11.2026):** Die gespeicherte ioBroker-Adresse wird aus `klimapilot_iobroker_address` beziehungsweise als Rückfall aus `iobroker_server` gelesen. Bei fehlender lokaler Registry wird die gespeicherte Adresse automatisch für die Kontoübernahme verwendet. Die Adresse wird nach erfolgreichem Import in beiden Schlüsseln vereinheitlicht gespeichert. Solange keine Registry geladen werden konnte, zeigt der Login die Adressangabe und eine erneute Verbindungs-/Ladeaktion; bei laufender Verbindung erscheint ein Ladezustand. Änderungen: `77c91053901967851c9c684b9448d2679bfe626e` und `0dccd771732b414c85259dce80dcc25c2be7e3f3`. Noch nicht gebaut oder auf Geräten geprüft. Der Versionsstand bleibt bis zum gebündelten Abschluss bei 0.4.68 / versionCode 98.
- **Geplanter Fallback (noch nicht getestet):** Wenn ioBroker nicht erreichbar ist, soll zusätzlich eine direkte Smartphone-zu-Smartphone-Übernahme per QR-Code angeboten werden. Vor Umsetzung/Praxisfreigabe sind Ablauf und Sicherheit zu prüfen; Zugangsdaten dürfen nicht ungeschützt im QR-Code stehen. Dieser Fallback ist ausdrücklich noch nicht als funktionsfähig bestätigt.
- `main` und der produktive Updater bleiben unverändert.

## 2b. Pairing-ACK und CI-Steuerung (18.11.2026)

- Commits `308f3f80c1f8d005f0b0976260ea0ceee601dd4f` und `b5f890248b3128a504fc5e48af6bdb9b2d26944c`: Der Responder speichert den Einladenden nicht mehr unmittelbar nach Zustimmung. Die Controller-Grundlage verlangt eine nachgelagerte Speicherbestätigung; Tests decken falsche ACK-Daten und Abbruch ab.
- Der nachfolgende Pairing-Arbeitsstand ergänzt den realen WLAN-Rückkanal, das signierte ACK und die Ein-Scan-Oberfläche; Details und Grenzen siehe Abschnitt 2d. Ein erfolgreicher CI-Lauf ersetzt weiterhin keinen Zwei-Smartphone-Praxistest.
- Commit `2e6a8170eedc318e7c33977c6b7ae2364e743116`: `.github/workflows/pairing-tls-validation.yml` startet nicht mehr bei Pushes auf `test`; nur noch `workflow_dispatch` ist eingetragen. Reguläre Validierung erfolgt über `.github/workflows/wlan-validation.yml`. Damit werden weitere normale Code-Pushes nicht mehr automatisch den API-26-Emulator starten.
- Der zuvor bereits gestartete API-26-Lauf #90 (Run ID `44323759302`) war zum letzten Abruf noch aktiv. Die Workflow-Änderung verhindert neue automatische Starts, beendet aber keinen bereits laufenden Run.
- WLAN-Workflow #225 (Run ID `44323759391`) auf Commit `b5f890248b3128a504fc5e48af6bdb9b2d26944c` erfolgreich: Unit-Tests, Debug-Kompilierung, APK-Prüfung und Artefaktbereitstellung abgeschlossen. Dieser Lauf enthält die Pairing-Controller-/Teständerungen, aber noch keinen echten WLAN-Rückkanal.

## 2c. Kryptografisches Speicher-ACK (18.11.2026)

- Commit `f7f6eaf220b671f9a5b624ac5efe2110dd5866a9`: Neue Klasse `ClimatePairingAcknowledgement`. Das ACK ist eine ECDSA-signierte Nachricht mit Sitzungs-ID, Einladenden-ID, Responder-ID, Responder-Nonce und Zeitstempel.
- Commit `b0e430e38f6fa8a645b1041b878d16db2b4aa1ce`: `ClimatePairingSessionController.acknowledgeIncomingInvitation` nimmt jetzt das ACK-Payload entgegen und verifiziert dessen Signatur gegen den öffentlichen Schlüssel der ursprünglichen Einladung. Eine bloße Übergabe von Sitzungs-ID und Peer-ID reicht nicht mehr.
- Commit `5dcc6ed7cd102d2aaf46800a2a966ffb3aed3d55`: Controller-Methode für den Einladenden erzeugt das signierte ACK nach erfolgreicher lokaler Bestätigung und Peer-Speicherung.
- Commit `aa633a1a4a29e2233bbb8a5d95d5ce9194c945c2`: Controller-Tests verwenden den signierten ACK-Ablauf und prüfen eine gefälschte Signatur sowie ACK nach Abbruch.
- WLAN-Workflow #230 (Run `44326084183`) auf Commit `aa633a1a4a29e2233bbb8a5d95d5ce9194c945c2` erfolgreich. Der neuere Stand ist in Abschnitt 2d dokumentiert.


## 2d. Ein-Scan-Pairing über lokalen WLAN-Rückkanal (18.11.2026)

- Implementiert auf Branch `test` in PR #6; aktueller Code-Commit: `07a774c92d19579ad0d0f53f9cefd55c4894de47`.
- Einladungsgerät wählt ein privates IPv4-WLAN, öffnet einen One-shot-TCP-Listener und bindet Host/Port in die signierte QR-Einladung ein.
- Zweites Gerät scannt den QR-Code einmal, prüft die Einladung und zeigt nur die Nutzerentscheidung „Koppeln“ oder „Abbrechen“. Nach Zustimmung wird die signierte Antwort über die SocketFactory des ausgewählten WLAN-Netzes übertragen.
- Einladungsgerät prüft die Antwort, erstellt über den Controller ein signiertes Speicher-ACK und speichert den Peer. Das ACK geht über dieselbe TCP-Verbindung zurück; das zweite Gerät speichert den Einladenden erst nach erfolgreicher ACK-Prüfung.
- Der Rückkanal verwendet längenpräfixierte Frames, eine maximale Nachrichtengröße von 4 KiB und begrenzte Listener-/Connect-/Read-Zeiten. Der Endpoint-Parser akzeptiert nur numerische lokale IPv4-/IPv6-Adressen und gültige Ports; DNS-Namen aus dem QR werden abgewiesen.
- **Transport-Sicherheit:** TCP im lokalen WLAN ist unverschlüsselt. Die Pairing-Nachrichten sind signiert, aber nicht vertraulich; andere Teilnehmer desselben WLANs können sie passiv mitlesen. Die UI weist ausdrücklich darauf hin. TLS/mTLS bleibt entsprechend Nutzerentscheidung verworfen.
- Es werden keine Profile oder sonstigen Nutzdaten übertragen. Der bestehende Updater, `main`, Produktionsrelease `latest` und PR-Merge bleiben unangetastet.
- Korrektur des Kotlin-Aufrufs der ACK-Factory: Commit `07a774c9` verwendet benannte Parameter entsprechend der tatsächlichen Controller-Signatur.

### CI-Nachweis des Ein-Scan-Pairings

- WLAN-Workflow **#239** (Run `44330933413`) auf Commit `bec8b59629eaeb6f304123b4590c6af40f2fe69b`: fehlgeschlagen wegen falsch positionierter `createAcknowledgement`-Lambda in `ClimatePairingSettingsCard.kt`. Keine APK bereitgestellt.
- WLAN-Workflow **#240** (Run `44331588695`) auf Commit `07a774c92d19579ad0d0f53f9cefd55c4894de47`: **erfolgreich**. `testDebugUnitTest`, Debug-/Release-Kotlin-Kompilierung, Debug-APK-Prüfung und Artefakt-Upload erfolgreich.
- Artefakt: `KlimaPilot-debug-test-0.4.68`, ID `14438439474`, 18.188.574 Bytes, verfügbar bis 25.11.2026. SHA-256 der APK: `c3fb5f9e9ffb4d44136a53a8bdbfbdb4502654c169e8d603ce61cab57ee7c6fe`; SHA-256 des Artifact-ZIP: `f9a8678d9534668e3c218ba1e40ac67e18f579eb0a7bb721657eb460567bddd6`.
- APK-Signaturprüfung mit `apksigner` erfolgreich (v2). CI prüfte außerdem Paketkennung/Version und das Fehlen von `REQUEST_INSTALL_PACKAGES`. Der Debug-Build bleibt ausschließlich ein Testartefakt, kein Produktionsrelease.
- **Praxistest 18.11.2026, 18:04 Uhr (0.4.69-debug, Tablet als Einladender, Smartphone als Antwortender):** erfolgreiche Kopplung mit einem einzigen QR-Scan bestätigt. Einladender meldet „Gerät erfolgreich gekoppelt. Die signierte Speicherbestätigung wurde übertragen.“, Antwortender meldet „… Speicherbestätigung wurde geprüft.“; beide zeigen „Gekoppelte Geräte: 1“ bei unterschiedlichen Geräte-Fingerprints. Damit sind Testpunkt 1 (erfolgreiche Kopplung) und 2 (beidseitige Speicherung) bestanden. Der Pairing-Code ist in 0.4.70 unverändert (Commit `7f266a7` ändert nur Login/Konten/ioBroker-Adresse).
- **Noch offen (Negativfälle):** Abbruch, Timeout, Verbindungsverlust während Antwort/ACK und Verhalten bei ungültigen/gefälschten/wiederholten Nachrichten müssen noch praktisch geprüft werden. Bis dahin keine Freigabe für Profil-/Nutzdatentransfer und kein Merge.

## 2e. Feste Test-Signatur und Test-APK 0.4.69 (18.11.2026)

- Fehler WLAN-Workflow **#241** (Run `44339096984`, Commit `985c050`): eingecheckter `app/klimapilot-test-debug.keystore` war ungültig (PKCS12-Integritätsprüfung fehlgeschlagen). Keine APK.
- Commit `56eb4e1`: ungültige Keystore-Datei aus dem Repository entfernt (bleibt in der Git-Historie, ist aber unbrauchbar). Lauf **#242** (Run `44344737195`) scheiterte, weil die zugehörige Gradle-Änderung erst im Folgecommit lag.
- Commit `43526e6`: Debug-Signierung liest `KLIMAPILOT_TEST_KEYSTORE_PATH` / `KLIMAPILOT_TEST_*` aus der Umgebung; ohne diese Variablen (lokale Builds) wird der Android-Standard-Debugschlüssel verwendet. Lauf **#243** (Run `44345348385`) scheiterte erwartungsgemäß am noch fehlenden Secret.
- Commit `695e9ab`: Nur noch **ein** Secret nötig: `KLIMAPILOT_TEST_KEYSTORE_BASE64` (vom Nutzer angelegt). Alias `klimapilot-test` und Keystore-Passwort stehen im Workflow (ohne die Schlüsseldatei wertlos). Workflow-Änderungen lösen jetzt ebenfalls einen Build aus.
- Testschlüssel: PKCS12, RSA 2048, gültig bis 10.11.2056. **Zertifikat SHA-256: `230865b90b0c37e6a7935fd4f5e0fad0626853a44921a7a2f7d6185b9665ecfb`.** Die CI bricht ab, wenn die APK nicht genau einen Signierer mit diesem Zertifikat hat. Produktionssignierung und Produktions-Secrets unverändert. Sicherungskopie des Schlüssels liegt beim Nutzer.
- WLAN-Workflow **#244** (Run `44346124733`, Commit `695e9ab`): erfolgreich, Zertifikat bestätigt.
- Commit `2bcbffa`: Der Workflow veröffentlicht die geprüfte Debug-APK zusätzlich als Pre-Release unter dem separaten Tag **`klimapilot-test-latest`** (nie `latest`; der Updater liest ausschließlich `releases/tags/latest`).
- WLAN-Workflow **#245** (Run `44348138478`, Commit `2bcbffa94997ad5219e8a5d432ea18431313f6e1`): **erfolgreich** – Unit-Tests, Debug-Build, Release-Kotlin-Kompilierung, Paket/Version, kein `REQUEST_INSTALL_PACKAGES`, Signatur und Zertifikats-Fingerprint geprüft, Artefakt und Pre-Release hochgeladen.
- Artefakt `KlimaPilot-debug-test-0.4.69`, ID `14448245408`, läuft am 25.11.2026 ab.
- **Direkter Download:** https://github.com/Apropo1710/KlimaPilot/releases/download/klimapilot-test-latest/KlimaPilot-test.apk (18.813.180 Bytes), APK-SHA-256 `c5fa0649e94371950c9a56477e4540ed7196dbb5d239ad96a80df682f2d1072c`. Unabhängig nachgeprüft: heruntergeladen, Hash identisch mit CI, Manifest `de.klimapilot.app.debug` / `0.4.69-debug` / 99, minSdk 26, targetSdk 35, ohne `REQUEST_INSTALL_PACKAGES`, ein v2-Signierer mit obigem Zertifikat.
- **Migration:** Bereits installierte ältere Testversionen (z. B. aus #221/#240) tragen eine andere Signatur und lassen sich nicht direkt aktualisieren. Einmalige Neuinstallation der Testversion nötig; davor lokale Daten prüfen. Alle künftigen Testbuilds verwenden dieselbe Signatur und sind als Update installierbar.
- **Noch offen:** Zwei-Smartphone-Praxistest des Ein-Scan-Pairings (siehe 2d). Kein Merge, kein Nutzdatentransfer.

## 2f. Automatische ioBroker-Einrichtung und QR-Kontoübernahme (18.11.2026)

- Anlass: Bei Neuinstallation zeigte 0.4.69 sofort die Adresseingabe (doppelte Fehlermeldung). Zusätzlich speicherte die Einstellungskarte in `klimapilot_iobroker_address` nur den Host, der Login erwartete dort eine vollständige URL.
- Neu in `IoBrokerAutoSetup.kt` (Commit `7f266a7`):
  - `IoBrokerAddress`: einheitliche Ablage (`iobroker_server` = ws-URL, dazu Host/Port/Protokoll), tolerantes Lesen alter Formate, Eingaben wie `192.168.178.54` oder `host:port` werden zu `ws://…:8084`.
  - `IoBrokerAutoSetup`: ohne Nutzereingabe zuerst gespeicherte Adresse, dann Suche im eigenen WLAN-/24 nach Port 8084 (48 parallele Proben, 500 ms). Jeder Treffer gilt erst nach der regulären Verbindungs- und Alive-Prüfung als ioBroker (10 s Timeout je Kandidat). Danach automatischer Import aus `0_userdata.0.KlimaPilot.accounts`.
  - Login zeigt währenddessen nur einen Ladekreis. Adresseingabe, „Automatisch erneut suchen“ und QR-Fallback erscheinen nur, wenn nichts gefunden wurde.
  - `KlimaPilotSetupTransfer`: Administratoren können unter Einstellungen → Benutzerkonto → „Anderes Smartphone einrichten“ einen 10 Minuten gültigen QR-Code anzeigen (Kontenliste mit PBKDF2-Hashes, keine Klartextpasswörter, plus ioBroker-Adresse). Das neue Smartphone scannt ihn auf dem Anmelde- oder Ersteinrichtungsbildschirm. Vorhandene lokale Konten werden nie überschrieben. Bewusst einfache Lösung nach Nutzervorgabe (Heimnetz, zwei Personen).
- Unit-Tests `IoBrokerAutoSetupTest` (Adressnormalisierung, Altformate, Subnetzbereich, QR-Roundtrip, abgelaufene/fremde/leere Codes).
- CI liest erwartete Version jetzt aus `app/build.gradle.kts`.
- WLAN-Workflow **#246** (Run `44353514087`, Commit `7f266a766d483d5c5ab267fd1ccd96e133655e78`): **erfolgreich**. Artefakt `KlimaPilot-debug-test-0.4.70`, ID `14450381008`, läuft am 25.11.2026 ab. Pre-Release `klimapilot-test-latest` aktualisiert; APK-SHA-256 `6863f57439676dda5d33977508ab8357b4fefeafc0012790a1e2212dc1ce488a`, unabhängig nachgeprüft (Paket, 0.4.70-debug/100, ohne `REQUEST_INSTALL_PACKAGES`, Test-Zertifikat `230865b9…65ecfb`).
- **Noch offen:** Praxistest auf dem Smartphone (automatisches Finden, manuelle Adresse, QR-Übernahme). Der Updater und `main` sind unverändert.

## 3. Letzte Codeänderung

Code-/Integrations-Commit: `3ea93c97a7db0de2a5c287d6666713e40af76cf7`  
Vorheriger Cleanup-Fix: `84a555d0d226c4505d0136ad0b208b5197bbdf67`  
Socket-Lifecycle-Fix: `334308e40ea17b2e57a6c640ca49c20e83d22e8b`  
Regressionstest: `63d53bb07a55bfb0207659749f8e6624d78215a7`  
Betroffene Dateien: `ClimateProfilePeerDiscovery.kt`, `ClimatePeerTcpConnectionManager.kt`, `MainActivity.kt` und `ClimatePeerTcpCompatibilityTest.kt`.

- Zusätzlich zum WLAN-spezifischen `registerNetworkCallback` wird `registerDefaultNetworkCallback` verwendet.
- Der Default-Callback erkennt einen Wechsel des Android-Standardnetzes zu einem anderen WLAN.
- Ein zusätzlich verfügbares WLAN allein beendet die Discovery nicht.
- Ein Wechsel des Standardnetzes zu einem anderen WLAN beendet die laufende Discovery fail-closed.
- Mobilfunk als Standardnetz ist zulässig, solange das ausgewählte WLAN selbst verfügbar bleibt.
- Verlust und Änderung der Link-Eigenschaften des ausgewählten WLANs werden weiterhin separat überwacht.
- Beide Callbacks werden beim Cleanup abgemeldet.
- **Review-Fund vom 18.11.2026:** Zwischen dem initialen Lesen des ausgewählten WLANs und der Registrierung der NetworkCallbacks bestand ein Race-Fenster. Ein bereits vor der Callback-Registrierung verlorenes Netzwerk muss nicht nachträglich über `onLost` gemeldet werden.
- **Gezielte Absicherung:** Nach Registrierung beider Callbacks wird der ausgewählte Network-/LinkProperties-Snapshot erneut gelesen. Die Discovery wird vor der DNS-SD-Ankündigung abgebrochen, wenn der ausgewählte Transport, das Interface oder die gewählte Adresse nicht mehr übereinstimmen oder bereits ein anderes WLAN das Defaultnetz ist.
- Die Defaultnetz- und Snapshot-Entscheidungen sind als reine Policy-Funktionen ausgelagert und durch elf gezielte JVM-Fälle abgedeckt. Die Gradle-/JUnit-Ausführung wurde im WLAN-Workflow #203 auf App-/Teststand `1a1d9df...` bestätigt.
- Die erneute Snapshot-Prüfung liest LinkProperties null-sicher; bei fehlendem Netzwerk, Interface oder geeigneter Adresse wird der Start fail-closed abgebrochen.
- Der Listener nimmt eingehende Verbindungen für den begrenzten gegenseitigen `ClimatePeerTcp`-Handshake an. Der Handshake wird in einem begrenzten Session-Executor ausgeführt; unbekannte/nicht vertraute Peers werden abgewiesen. Nach erfolgreichem oder fehlgeschlagenem Handshake wird der Socket geschlossen.

## 4. Pairing- und Synchronisationsstatus

- ECDSA-P-256-Installationsidentität im Android Keystore; privater Schlüssel nicht exportierbar.
- Bisheriger Pairing-Ablauf: signierte QR-Einladung (120 Sekunden), signierte sitzungsgebundene Antwort und beidseitige Fingerprint-/Nutzerbestätigung. Dieser Ablauf benötigt aktuell mehr als einen Scan und speichert in zwei getrennten Bestätigungsschritten.
- Trusted-Peer-Speicher enthält nur ausdrücklich bestätigte Peers.
- DNS-SD-Discovery mit Diensttyp `_klimapilot-sync._tcp.`.
- Lokale Adressauswahl berücksichtigt private IPv4, IPv4-Link-Local, IPv6-ULA und scoped IPv6-Link-Local; öffentliche Adressen werden nicht als Listener-Adresse verwendet.
- WLAN-Callback überwacht das ausgewählte WLAN; Default-Network-Callback erkennt Wechsel zu einem anderen WLAN.
- `ClimatePeerTcp` implementiert einen signaturbasierten gegenseitigen Handshake mit Nonces, Rollenbindung, Peer-ID-/Schlüsselprüfung und Zeit-/Feldgrößenlimits. Er ist seit Commit `3ea93c9...` in den Discovery-Listener und einen ausdrücklich gestarteten Client-Verbindungsversuch integriert.
- TLS/mTLS wurde nach ausdrücklicher Nutzerentscheidung verworfen. TCP ist unverschlüsselt; andere Teilnehmer desselben WLANs können Daten passiv mitlesen.
- Discovery-Metadaten bleiben unverifiziert. Der ausgehende Authentifizierungsversuch wird nur nach ausdrücklichem UI-Klick gestartet und verwendet die SocketFactory des ausgewählten WLAN-Netzes (auch wenn Mobilfunk Android-Defaultnetz ist).
- Eingehende und ausgehende Handshake-Sessions laufen in begrenzten ThreadPools (Server: 1–2 Threads/4 Wartende; Client: 1 Thread/4 Wartende). Stop/Cleanup schließt aktive Sockets und beendet die Executor-Queues.
- Nach dem Handshake werden beide Seitenverbindungen sofort geschlossen. Es existiert weiterhin keine API zum Übertragen von Profil-, Outbox- oder sonstigen App-Nutzdaten.
- `ClimateProfileSyncEngine` wird vom bestehenden ioBroker-Sync-Pfad verwendet; die lokale Peer-Nutzdaten-Transportintegration fehlt; bisher ist nur die Handshake-Stufe angeschlossen.

## 5. CI- und Testnachweise

Die folgenden Nachweise sind konkrete ältere Ergebnisse; sie dürfen nicht als Validierung der neuen Handshake-Integration ausgegeben werden:

- WLAN-Workflow **#202**, erfolgreich auf älterem Commit `ae1bdc03c78624ff640505b4bd255bfd885b05c5`.
- WLAN-Workflow **#201**, erfolgreich auf älterem Commit `41b9a3ab513e7d48daa09ee21298fe718748363b`.
- Separater TCP-Emulatorlauf **#62**, erfolgreich auf älterem Commit `41b9a3ab513e7d48daa09ee21298fe718748363b`, API 26.
- WLAN-Workflow **#203**, erfolgreich per `workflow_dispatch` auf `test` mit Commit `1a1d9df06492b45b89d537ac2844d76c789280a6`: `testDebugUnitTest`, `assembleDebug` und `compileReleaseKotlin` erfolgreich. Damit sind die elf Netzwerk-Policy-Tests auf diesem Stand über Gradle/JUnit ausgeführt.
- WLAN-Workflow **#205**, manuell gestartet auf `test` mit HEAD `f99ffb0a48a77afcd03c38950927369288fcfc5e`, erfolgreich abgeschlossen. `testDebugUnitTest`, `assembleDebug` und `compileReleaseKotlin` liefen im Schritt „Unit-Tests und Debug-Kompilierung“ erfolgreich. Dies bestätigt JVM-Unit-Tests und Kompilierung auf diesem App-Stand, ersetzt aber keinen Android-Instrumentierungstest.
- WLAN-Workflow **#206**, Push-Trigger auf CI-Konfigurationscommit `e01cc74...`, erfolgreich abgeschlossen. Derselbe Unit-/Debug-/Release-Kompilierungsschritt war erfolgreich; kein Emulator-/Instrumentierungstest.
- TCP-Peer-Emulatorworkflow **#69** auf API 26, Commit `3fbe06d0f8cf357bb787ca2a95a47a41fb55767d`, erfolgreich abgeschlossen. Alle Steps einschließlich „Android Emulator und TCP-Peer-Test“ und Diagnose-Artefakt waren erfolgreich. Der Step führt `testDebugUnitTest connectedDebugAndroidTest` aus; damit ist der Android-Instrumentierungslauf auf API 26 erfolgreich. Der genaue einzelne Testfallzähler ist aus den verfügbaren Jobmetadaten nicht ersichtlich.
- Nachträgliche Workflow-Ergänzung: Nach erfolgreicher API-26-Prüfung wird `app-debug.apk` auf Paketkennung, versionCode und versionName geprüft, mit `apksigner` verifiziert und als Artefakt `KlimaPilot-debug-api26-test` hochgeladen. API-26-Lauf **#70** auf Commit `e685389a30aed387561815b506bdf1e6cc673ff9` erfolgreich abgeschlossen. Der Emulator-/Instrumentierungsschritt, die Paket-/Versions-/Signaturprüfung und der Artefakt-Upload waren erfolgreich. Das Artefakt `KlimaPilot-debug-api26-test` wurde heruntergeladen und enthält `app-debug.apk` (18.796.844 Bytes) sowie SHA-256 `197b2f069a20d4fd845c6499c736ca87e5254291774fa4d910a4e1987cc0d7bd`. Geprüfte erwartete Werte des Laufs #70: Paket `de.klimapilot.app.debug`, versionCode `94`, versionName `0.4.64-debug`; `apksigner verify` erfolgreich. Dieser ältere Artefaktstand enthält noch die Installationsberechtigung und darf nicht erneut verwendet werden.
- WLAN-Workflow **#207** auf demselben Commit `3fbe06d...` ebenfalls erfolgreich; er war ein redundanter zusätzlicher Lauf mit Unit-Tests und Debug-/Release-Kompilierung, nicht der Emulatornachweis.
- Debug-Test-App: `app/src/debug/AndroidManifest.xml` entfernt `REQUEST_INSTALL_PACKAGES` ausschließlich aus dem Debug-Variant-Manifest per Manifest-Merger-Marker `tools:node="remove"`. `main` und das gemeinsame `app/src/main/AndroidManifest.xml` wurden nicht geändert; der bestehende Release-Updater bleibt unangetastet. Die neue Debug-APK ist noch nicht freigegeben, bis die explizite APK-Manifest-Prüfung auf `REQUEST_INSTALL_PACKAGES` erfolgreich durchlaufen ist. Die bisherige CI-Prüfung #73 kontrollierte Paketkennung/Version/Signatur, aber noch nicht ausdrücklich die Abwesenheit dieser Berechtigung.
- Testversion auf Branch `test`: 0.4.65 / versionCode 95; Debug-Paket `de.klimapilot.app.debug`, erwarteter versionName `0.4.65-debug`. Das ist keine Produktionsversion und kein Release.
- Die sieben JVM-Adress-/IPv6-Scope-Tests sind vorhanden; ihre Ausführung auf dem aktuellen Stand ist nicht bestätigt.
- TCP-Emulatorlauf **#63**, Commit `725fe3109aa4712abda0a4f500cb6433c8a49e19` (Workflow-only-Abzweig vom identischen App-/Teststand `1a1d9df...`): API 26 erfolgreich; Gradle-Build erfolgreich und Instrumentierungstests **3/3 bestanden**. API 35 im selben Lauf fehlgeschlagen, weil der Emulator/Package-Manager nicht rechtzeitig verfügbar war; keine bestandenen API-35-App-Tests.
- Optionale API-35-Wiederholungen #66, #67 und #68 fehlgeschlagen; #64 und #65 wurden während der Workflow-Konfigurationskorrektur abgebrochen. Lauf #68 enthielt zusätzliche KVM-Berechtigungs- und Emulator-Readiness-Prüfungen, endete aber weiterhin mit Fehler im API-35-Job. API 35 bleibt ausdrücklich optional und ist kein Freigabekriterium. Keine weiteren API-35-Wiederholungen, solange kein konkreter neuer technischer Ansatz vorliegt.
- **Neue gewünschte Kopplungs-UX (noch nicht implementiert):** Einladungsgerät zeigt einen QR-Code; das zweite Gerät scannt einmal und erhält nur die Auswahl „Koppeln“ oder „Abbrechen“. Die Fingerprint-Prüfung bleibt automatisch im Hintergrund. Nach Zustimmung muss die signierte Antwort über einen zeitlich begrenzten lokalen WLAN-Rückkanal zum Einladungsgerät gelangen. Das Einladungsgerät speichert den Peer und bestätigt den Speichervorgang; erst nach dieser Bestätigung speichert auch das zweite Gerät den Einladenden. Fehler, Timeout, Abbruch oder fehlende Bestätigung dürfen auf keiner Seite eine erfolgreiche Kopplung anzeigen. Der bestehende Handshake-only-TCP-Kanal ist dafür nicht ausreichend und darf nicht als bereits vorhandener Pairing-Rückkanal missverstanden werden.
- Kein Zwei-Smartphone-Praxistest bestätigt. Die neue Handshake-Integration wurde noch nicht auf realen Geräten geprüft.
- Der erfolgreiche WLAN-Lauf #203 deckte den damaligen App-/Teststand `1a1d9df...` ab, nicht die neue Handshake-Integration. Der API-26-Instrumentierungslauf #63 verwendet einen separaten Workflow-only-Commit, dessen App-/Testbaum dem damaligen Stand `1a1d9df...` entspricht.
- Die elf Netzwerk-Policy-Fälle wurden im WLAN-Workflow #203 auf dem damaligen App-/Teststand durch `testDebugUnitTest` ausgeführt; der gesamte Workflow war erfolgreich. Zusätzlich wurden sie zuvor mit einem separaten Kotlin-Smoke-Harness 11/11 bestanden. Der Smoke-Harness ist kein Ersatz für Gradle, Android-Kompilierung oder Emulator-/Gerätetests.

Workflows:
- `.github/workflows/wlan-validation.yml`: Push-Trigger für `test`; nur `PROJECT_STATUS.md` und `app/src/debug/**` sind ausgeschlossen (Workflow-Änderungen bauen seit `695e9ab` neu). Zusätzlich `workflow_dispatch`; führt Unit-Tests, Debug-APK-Build und Release-Kotlin-Kompilierung mit Java 17/Android SDK 35 aus, prüft Manifest, Signatur und Test-Zertifikat und veröffentlicht die APK als Pre-Release `klimapilot-test-latest`.
- `.github/workflows/pairing-tls-validation.yml`: historischer Dateiname; API-26-Emulator-/Instrumentierungstest. Seit Commit `2e6a817...` kein Push-Trigger mehr; nur `workflow_dispatch` ist konfiguriert. Reguläre Änderungen auf `test` lösen diesen Emulatorworkflow nicht mehr aus. Manuelle Auslösung bleibt aufgrund der Workflow-Datei nur auf `test` möglicherweise nicht über die GitHub-Oberfläche verfügbar. Der WLAN-Workflow bleibt der reguläre Prüfweg.

## 6. Verbindliche Freigabesperren

- Kein Merge von PR #6 und keine Änderung an `main` ohne ausdrückliche Freigabe.
- Keine Produktionsversionserhöhung, kein Produktionsbuild und kein Release aus diesem WLAN-Arbeitsstrang.
- Kein Profil-/Nutzdatentransfer über Peer-TCP vor vollständiger Nachrichten-/Payload-Härtung und erfolgreichem Zwei-Smartphone-Test.
- Updater und Installationsablauf sind abgeschlossen und dürfen **niemals geändert** werden.
- Bestehende ioBroker-Verbindung, WF-RAC-Steuerung und Produktionsfunktionen schützen.

## 7. Produkt- und Hardwaregrenzen

- Zielgerät: Mitsubishi Heavy SRK15ZS-WF.
- Die Klimaanlage ist noch nicht installiert; reale Hardware-/Firmwarefunktionen sind daher nicht bestätigt.
- Adapter `mhi-wfrac` ist noch nicht eingerichtet und wird erst nach Installation der Klimaanlage verwendet.
- SENEC/`energyfluss` nicht als Datenquelle einplanen.
- Produktionsverbindung zu ioBroker: WebSocket `ws.0`, Port 8084; Alive-Prüfung über `system.adapter.admin.0.alive`.

## 8. Nächste Schritte

1. Test-APK 0.4.69 (Pre-Release `klimapilot-test-latest`) auf beiden Smartphones installieren; bei alter Testinstallation einmalige Neuinstallation nach Datenprüfung.
2. Zwei-Smartphone-Test des Ein-Scan-Pairings: erfolgreiche Kopplung mit beidseitiger Speicherung, Abbruch, Timeout, Verbindungsverlust während Antwort/ACK, ungültige/gefälschte/wiederholte Nachrichten, ungültiger WLAN-Endpunkt. Ein abgebrochener Ablauf darf nirgends als gekoppelt erscheinen.
3. Nach bestandenem Gerätetest: Nachrichtenframing, Schema-/Größenvalidierung, Replay-/Duplikaterkennung und idempotente Wiederaufnahme für den Nutzdatentransfer spezifizieren.
4. Regressionstests für den bestehenden ioBroker-Sync-Pfad ergänzen.
5. Bis dahin bleibt Peer-Nutzdatentransfer gesperrt; kein Merge von PR #6 ohne Freigabe.

## 9. Dokumentationsstruktur und Pflege

### Einstieg
- `README.md`: kurze Projektbeschreibung, technische Basis, feste Grundregeln und Links. Keine zweite Statusübersicht.

### Einzige Statusquelle
- `PROJECT_STATUS.md`: Produktivversion, Branches, Commits, PR, Builds, CI-Nachweise, aktueller Arbeitsstand, offene Gates und nächste Schritte.

### Technische Fachkonzepte
- `DIRECT_WLAN_MIGRATION.md`: direkter WF-RAC-Kommunikationsweg und Hardwaretestanforderungen.
- `LOCAL_WIFI_SYNC_DESIGN.md`: Architektur und Ablauf der lokalen Smartphone-Synchronisation.
- `PAIRING_PROTOCOL_DECISIONS.md`: Identität, Pairing, Trust und Sicherheitsentscheidungen.
- `SYNC_RESILIENCE_PLAN.md`: Outbox, Konfliktbehandlung, Wiederanlauf und Zuverlässigkeitsanforderungen.

Die frühere Datei `STATUS.md` wurde entfernt. Ihr Übergabe-/Statusinhalt ist in diese Datei übernommen. Es darf keine neue parallele Statusdatei angelegt werden, solange kein konkreter, nicht anders lösbarer Bedarf besteht.

### Verbindliche Aktualisierungsroutine
1. Vor jeder Änderung den aktuellen GitHub-Stand von `main`, `test`, PR und relevanten Actions prüfen.
2. Betroffene Code- und Fachdateien vollständig beziehungsweise ausreichend lesen.
3. Nach Änderungen den maßgeblichen Status in dieser Datei aktualisieren.
4. Fachkonzepte nur dann ändern, wenn sich deren Architektur, Entscheidung oder technische Anforderungen ändern – nicht für jede Statuswiederholung.
5. README nur bei Änderungen an Projektbeschreibung, Einstieg oder festen Grundregeln aktualisieren.
6. Nach dem Commit alle geänderten Dateien erneut von GitHub lesen und Links, Status, Commit-Hashes sowie Querverweise auf Konsistenz prüfen.
7. Produktions-HEAD, letzter erfolgreicher Build und Release-Stand immer getrennt ausweisen.
