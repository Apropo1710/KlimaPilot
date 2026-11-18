# KlimaPilot – verbindlicher Projektstatus

**Letzter Abgleich:** 18.11.2026, 12:25 Uhr MEZ (WLAN-CI #205 erfolgreich; API-26-Workflow isoliert)  
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

- Zuletzt live geprüfter `test`-HEAD vor diesem Status-Dokumentationscommit: `36c5227ed7c54d0706592355778695828e724b84`. Darin enthalten ist der API-26-only-Workflow-Commit `e01cc74b3375297c0435e44d5c41aace341469ef`. Der getestete App-/Code-Stand des WLAN-Laufs #205 ist `f99ffb0a48a77afcd03c38950927369288fcfc5e`; die beiden Folgecommits ändern ausschließlich CI-Konfiguration und Dokumentation. Der letzte Code-/Teststand davor bleibt `63d53bb07a55bfb0207659749f8e6624d78215a7` (Socket-Cleanup-Fix `334308e40ea17b2e57a6c640ca49c20e83d22e8b` plus gezielter Regressionstest).
- Vorheriger Defaultnetz-Codecommit: `bba8969ba4306d159ba388753f85c6638bf32f7f`.
- Die anschließende Lifecycle-Snapshot-Absicherung und ihre Policy-Tests sind Bestandteil dieses Commits.
- [PR #6](https://github.com/Apropo1710/KlimaPilot/pull/6): offen, Draft, nicht gemergt. Kein Merge ohne ausdrückliche Freigabe.
- Die Branches sind weiterhin divergent; `test` liegt 217 Commits vor und 10 Commits hinter `main`; Merge-Base `c7f1d57e4a4661d1492e5dca42ec9a9841d3e5fd`. Kein Rebase oder Merge im Rahmen der Dokumentationspflege.
- PR #6 umfasst den WLAN-/Pairing-Arbeitsstrang. Vor einer späteren Integration ist ein vollständiger Diff-Review gegen den dann aktuellen `main` erforderlich.

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
- Die sieben JVM-Adress-/IPv6-Scope-Tests sind vorhanden; ihre Ausführung auf dem aktuellen Stand ist nicht bestätigt.
- TCP-Emulatorlauf **#63**, Commit `725fe3109aa4712abda0a4f500cb6433c8a49e19` (Workflow-only-Abzweig vom identischen App-/Teststand `1a1d9df...`): API 26 erfolgreich; Gradle-Build erfolgreich und Instrumentierungstests **3/3 bestanden**. API 35 im selben Lauf fehlgeschlagen, weil der Emulator/Package-Manager nicht rechtzeitig verfügbar war; keine bestandenen API-35-App-Tests.
- Optionale API-35-Wiederholungen #66, #67 und #68 fehlgeschlagen; #64 und #65 wurden während der Workflow-Konfigurationskorrektur abgebrochen. Lauf #68 enthielt zusätzliche KVM-Berechtigungs- und Emulator-Readiness-Prüfungen, endete aber weiterhin mit Fehler im API-35-Job. API 35 bleibt ausdrücklich optional und ist kein Freigabekriterium. Keine weiteren API-35-Wiederholungen, solange kein konkreter neuer technischer Ansatz vorliegt.
- Kein Zwei-Smartphone-Praxistest bestätigt. Die neue Handshake-Integration wurde noch nicht auf realen Geräten geprüft.
- Der erfolgreiche WLAN-Lauf #203 deckte den damaligen App-/Teststand `1a1d9df...` ab, nicht die neue Handshake-Integration. Der API-26-Instrumentierungslauf #63 verwendet einen separaten Workflow-only-Commit, dessen App-/Testbaum dem damaligen Stand `1a1d9df...` entspricht.
- Die elf Netzwerk-Policy-Fälle wurden im WLAN-Workflow #203 auf dem damaligen App-/Teststand durch `testDebugUnitTest` ausgeführt; der gesamte Workflow war erfolgreich. Zusätzlich wurden sie zuvor mit einem separaten Kotlin-Smoke-Harness 11/11 bestanden. Der Smoke-Harness ist kein Ersatz für Gradle, Android-Kompilierung oder Emulator-/Gerätetests.

Workflows:
- `.github/workflows/wlan-validation.yml`: Push-Trigger für `test` (Statusdatei-only-Commits ausgeschlossen) sowie `workflow_dispatch`; führt Unit-Tests, Debug-APK-Build und Release-Kotlin-Kompilierung mit Java 17/Android SDK 35 aus.
- `.github/workflows/pairing-tls-validation.yml`: historischer Dateiname; der Workflow validiert TCP-Peer-Authentifizierung und ist nur manuell startbar. Seit Commit `e01cc74...` enthält er ausschließlich den Pflichtlauf auf API 26. Der optionale API-35-Job wurde entfernt, damit ein manueller Start nicht erneut beide Emulatorjobs ausführt. Frühere API-35-Experimente liefen ausschließlich auf temporären `validation/*`-Branches und wurden nicht in `main`, `test` oder PR #6 übernommen.

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

1. Den jetzt auf API 26 begrenzten Workflow `.github/workflows/pairing-tls-validation.yml` manuell starten und den Instrumentierungstest `connectedDebugAndroidTest` prüfen. WLAN-Workflow #205 hat JVM-Unit-Tests sowie Debug-/Release-Kompilierung auf dem damaligen App-Stand erfolgreich bestätigt.
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
