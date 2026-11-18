# KlimaPilot – verbindlicher Projektstatus

**Letzter Abgleich:** 18.11.2026, 13:40 Uhr MEZ (Kontenübertragung für frische Geräte ergänzt; CI-Läufe #79 und #214 laufen)
**Repository:** https://github.com/Apropo1710/KlimaPilot

Diese Datei ist die **einzige maßgebliche Quelle für den aktuellen Projekt-, Branch-, PR-, Build- und Validierungsstand**. Die README dient nur als Einstieg. Die technischen Fachkonzepte enthalten Architektur und Entscheidungen, aber keine konkurrierenden Live-Statusübersichten.

## 1. Produktivstand – `main`

- Aktueller `main`-HEAD: `b82433038a3ced3de3ff908788fde4ef36d58f66`
- Produktivversion aus `app/build.gradle.kts`: **0.4.64 / versionCode 94**
- Letzter bestätigter erfolgreicher Produktionsbuild: **GitHub Actions #233**, Quellcommit `b88ff7e9709bc1ea5aafd673e26bb1d4efee9c16`.
- Release `latest` enthält die signierte APK dieses Builds. Der aktuelle `main`-HEAD ist ein späterer Dokumentationscommit; Build-Commit und aktueller Branch-HEAD sind daher ausdrücklich zu unterscheiden.
- [Direkter APK-Download](https://github.com/Apropo1710/KlimaPilot/releases/download/latest/KlimaPilot.apk)
- `main` wurde bei der hier dokumentierten Bereinigung nicht verändert.

## 2. Entwicklungsstand – `test` und PR #6

- Aktueller `test`-HEAD: `6036b7835c287dff028fd4ce43a4e464d2198095`. Der Debug-only Manifest-Fix wurde in `f31380e...` ergänzt; `app/build.gradle.kts` wurde in `9ccf66f...` auf 0.4.65 / versionCode 95 angehoben. Der API-26-Workflow prüft für die neue APK `de.klimapilot.app.debug`, Version `0.4.65-debug`, versionCode 95. API-26-Lauf #73 und WLAN-Lauf #209 sind beide erfolgreich abgeschlossen. #73 hat die Debug-APK `0.4.65-debug` erstellt, Paket-/Versionsprüfung, Signaturprüfung und Artefakt-Upload bestanden. Das Artefakt wurde heruntergeladen; SHA-256 stimmt mit der im Artefakt enthaltenen Prüfsummendatei überein. Zusätzlich wurde nachträglich die CI-Prüfung ergänzt, die im APK-Manifest `REQUEST_INSTALL_PACKAGES` explizit ausschließen muss. Diese zusätzliche Prüfung läuft noch in einem Folgeworkflow. Der Workflow-Commit baut nach erfolgreicher API-26-Instrumentierung die Debug-APK, prüft Paketkennung/Version/Signatur und lädt sie als CI-Artefakt hoch. Der WLAN-Workflow ignoriert Workflow-only-Änderungen. Der getestete App-/Code-Stand des WLAN-Laufs #205 ist `f99ffb0a48a77afcd03c38950927369288fcfc5e`; die Folgecommits ändern ausschließlich CI-Konfiguration und Dokumentation. Der letzte Code-/Teststand davor bleibt `63d53bb07a55bfb0207659749f8e6624d78215a7` (Socket-Cleanup-Fix `334308e40ea17b2e57a6c640ca49c20e83d22e8b` plus gezielter Regressionstest).
- Vorheriger Defaultnetz-Codecommit: `bba8969ba4306d159ba388753f85c6638bf32f7f`.
- Die anschließende Lifecycle-Snapshot-Absicherung und ihre Policy-Tests sind Bestandteil dieses Commits.
- [PR #6](https://github.com/Apropo1710/KlimaPilot/pull/6): offen, Draft, nicht gemergt. Kein Merge ohne ausdrückliche Freigabe.
- Die Branches sind weiterhin divergent; `test` liegt 217 Commits vor und 10 Commits hinter `main`; Merge-Base `c7f1d57e4a4661d1492e5dca42ec9a9841d3e5fd`. Kein Rebase oder Merge im Rahmen der Dokumentationspflege.
- PR #6 umfasst den WLAN-/Pairing-Arbeitsstrang. Vor einer späteren Integration ist ein vollständiger Diff-Review gegen den dann aktuellen `main` erforderlich.

## 2a. Kontenübertragung für neue Geräte (18.11.2026)

- Auf `test` wurde die Ersteinrichtung erweitert: Bei leerer lokaler Kontenliste kann eine zuvor exportierte Konten-Registry übernommen werden. Danach erfolgt die normale Anmeldung mit Benutzername und bestehendem Passwort; eine neue Administratoranlage ist nicht erforderlich.
- Ein Administrator kann in der Benutzerverwaltung die Registry über den Android-Teilen-Dialog exportieren. Die Übertragung enthält ausschließlich die vorhandenen PBKDF2-Passwortprüfwerte, keine Klartextpasswörter. Die Exportdaten sind vertraulich zu behandeln und nur direkt an eigene Geräte zu übertragen.
- Der Import akzeptiert nur eine gültige Registry mit mindestens einem Konto und schreibt ausschließlich in eine leere lokale Registry. Vorhandene oder beschädigte lokale Kontendaten werden nicht überschrieben.
- Die bisherige lokale Speicherung und Anmeldung auf bereits eingerichteten Geräten bleiben bestehen. Der Updater wurde nicht geändert.
- Codecommits: `964a8518` (geschützte Übernahme-Methode), `c9afb782` (UI/Import), `d306f8bb` (Export-/Import-UI), `0288959c` (Share-Export/Import-Verarbeitung), `ec760486` (Vertraulichkeitshinweis).
- Die CI-Validierung auf dem finalen App-Code-Stand `ec760486` läuft noch: WLAN-Workflow #214 und API-26-Workflow #79. Smartphone-Test steht aus.
- Diese Funktion ist zunächst auf `test` umgesetzt. `main` ist weiterhin unverändert, bis die Testimplementierung geprüft ist.

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
- Signierte QR-Einladung (120 Sekunden), signierte sitzungsgebundene Antwort und gegenseitige Fingerprint-/Nutzerbestätigung.
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
- Kein Zwei-Smartphone-Praxistest bestätigt. Die neue Handshake-Integration wurde noch nicht auf realen Geräten geprüft.
- Der erfolgreiche WLAN-Lauf #203 deckte den damaligen App-/Teststand `1a1d9df...` ab, nicht die neue Handshake-Integration. Der API-26-Instrumentierungslauf #63 verwendet einen separaten Workflow-only-Commit, dessen App-/Testbaum dem damaligen Stand `1a1d9df...` entspricht.
- Die elf Netzwerk-Policy-Fälle wurden im WLAN-Workflow #203 auf dem damaligen App-/Teststand durch `testDebugUnitTest` ausgeführt; der gesamte Workflow war erfolgreich. Zusätzlich wurden sie zuvor mit einem separaten Kotlin-Smoke-Harness 11/11 bestanden. Der Smoke-Harness ist kein Ersatz für Gradle, Android-Kompilierung oder Emulator-/Gerätetests.

Workflows:
- `.github/workflows/wlan-validation.yml`: Push-Trigger für `test`; reine `PROJECT_STATUS.md`- und `.github/workflows/**`-Änderungen sind ausgeschlossen, damit Workflow-Konfigurationspflege keinen zweiten redundanten Build auslöst. Zusätzlich `workflow_dispatch`; führt Unit-Tests, Debug-APK-Build und Release-Kotlin-Kompilierung mit Java 17/Android SDK 35 aus.
- `.github/workflows/pairing-tls-validation.yml`: historischer Dateiname; der Workflow validiert TCP-Peer-Authentifizierung und ist nur manuell startbar. Seit Commit `e01cc74...` enthält er ausschließlich den Pflichtlauf auf API 26. Da die Workflow-Datei nicht auf dem Default-Branch `main` liegt, bietet GitHub in der Actions-Ansicht keinen manuellen `Run workflow`-Start an. Stattdessen wird der Workflow seit Commit `3fbe06d...` bei relevanten Pushes auf `test` automatisch gestartet (Pfadfilter für App-/Gradle-/Workflow-Dateien). Statusdokumentationsänderungen lösen ihn nicht aus. Commit `e685389...` ergänzt die verifizierte Debug-APK-Ausgabe als Artefakt nach erfolgreichem Test. Frühere API-35-Experimente liefen ausschließlich auf temporären `validation/*`-Branches und wurden nicht in `main`, `test` oder PR #6 übernommen.

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

1. Folgeworkflow mit expliziter APK-Manifest-Prüfung abwarten. Nur wenn `apkanalyzer manifest permissions` bestätigt, dass `REQUEST_INSTALL_PACKAGES` fehlt, und Paket/Version/Signatur stimmen, die Debug-APK für den Zwei-Smartphone-Test freigeben. Noch kein Profiltransfer.
2. Anschließend die explizite Authentifizierung zwischen zwei realen Smartphones prüfen: beidseitig gekoppelte Peers erfolgreich, unbekannter Peer abgewiesen, WLAN-Socket-Bindung bei mobilem Defaultnetz, Abbruch während Handshake und Cleanup beim Stop.
3. Falls CI und Geräteprüfung erfolgreich sind, Nachrichtenframing, Größen-/Schema-/Gerätevalidierung, Replay-/Duplikaterkennung und idempotente Wiederaufnahme spezifizieren.
4. Regressionstests für den bestehenden ioBroker-Sync-Pfad ergänzen.
5. Bis zur vollständigen Payload-Härtung und zum erfolgreichen Zwei-Smartphone-Test bleibt Peer-Nutzdatentransfer gesperrt.

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
