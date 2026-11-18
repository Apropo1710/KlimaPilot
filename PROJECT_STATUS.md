# KlimaPilot – verbindlicher Projektstatus

**Letzter Abgleich:** 18.11.2026, 10:20 Uhr MEZ  
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

- Aktueller `test`-HEAD: Dokumentationscommit nach dem App-/Testcommit `e55a92e1aba0961088af92d6013de2ba960ea03d`; letzter Code-/Teststand ist damit unverändert.
- Vorheriger Defaultnetz-Codecommit: `bba8969ba4306d159ba388753f85c6638bf32f7f`.
- Die anschließende Lifecycle-Snapshot-Absicherung und ihre Policy-Tests sind Bestandteil dieses Commits.
- [PR #6](https://github.com/Apropo1710/KlimaPilot/pull/6): offen, Draft, nicht gemergt. Kein Merge ohne ausdrückliche Freigabe.
- Die Branches sind weiterhin divergent; `test` liegt vor `main` und ist zugleich 10 Commits dahinter; Merge-Base `c7f1d57e4a4661d1492e5dca42ec9a9841d3e5fd`. Kein Rebase oder Merge im Rahmen der Dokumentationspflege.
- PR #6 umfasst den WLAN-/Pairing-Arbeitsstrang. Vor einer späteren Integration ist ein vollständiger Diff-Review gegen den dann aktuellen `main` erforderlich.

## 3. Letzte Codeänderung

Code-Commit: `bba8969ba4306d159ba388753f85c6638bf32f7f`  
Datei: `app/src/main/java/de/klimapilot/app/sync/ClimateProfilePeerDiscovery.kt`  
Letzter Test-Commit: `e55a92e1aba0961088af92d6013de2ba960ea03d`  
Datei: `app/src/test/java/de/klimapilot/app/sync/ClimatePeerNetworkPolicyTest.kt` (zwei zusätzliche Fälle für fehlendes Interface und fehlende Link-Adresse).

- Zusätzlich zum WLAN-spezifischen `registerNetworkCallback` wird `registerDefaultNetworkCallback` verwendet.
- Der Default-Callback erkennt einen Wechsel des Android-Standardnetzes zu einem anderen WLAN.
- Ein zusätzlich verfügbares WLAN allein beendet die Discovery nicht.
- Ein Wechsel des Standardnetzes zu einem anderen WLAN beendet die laufende Discovery fail-closed.
- Mobilfunk als Standardnetz ist zulässig, solange das ausgewählte WLAN selbst verfügbar bleibt.
- Verlust und Änderung der Link-Eigenschaften des ausgewählten WLANs werden weiterhin separat überwacht.
- Beide Callbacks werden beim Cleanup abgemeldet.
- **Review-Fund vom 18.11.2026:** Zwischen dem initialen Lesen des ausgewählten WLANs und der Registrierung der NetworkCallbacks bestand ein Race-Fenster. Ein bereits vor der Callback-Registrierung verlorenes Netzwerk muss nicht nachträglich über `onLost` gemeldet werden.
- **Gezielte Absicherung:** Nach Registrierung beider Callbacks wird der ausgewählte Network-/LinkProperties-Snapshot erneut gelesen. Die Discovery wird vor der DNS-SD-Ankündigung abgebrochen, wenn der ausgewählte Transport, das Interface oder die gewählte Adresse nicht mehr übereinstimmen oder bereits ein anderes WLAN das Defaultnetz ist.
- Die Defaultnetz- und Snapshot-Entscheidungen sind als reine Policy-Funktionen ausgelagert und durch elf gezielte JVM-Fälle abgedeckt. Die Gradle-/JUnit-Ausführung ist noch nicht bestätigt.
- Die erneute Snapshot-Prüfung liest LinkProperties null-sicher; bei fehlendem Netzwerk, Interface oder geeigneter Adresse wird der Start fail-closed abgebrochen.
- Der TCP-Listener bleibt reject-only: eingehende Verbindungen werden angenommen und unmittelbar geschlossen. Es gibt keinen integrierten authentifizierten Handshake und keinen Nutzdatentransfer.

## 4. Pairing- und Synchronisationsstatus

- ECDSA-P-256-Installationsidentität im Android Keystore; privater Schlüssel nicht exportierbar.
- Signierte QR-Einladung (120 Sekunden), signierte sitzungsgebundene Antwort und gegenseitige Fingerprint-/Nutzerbestätigung.
- Trusted-Peer-Speicher enthält nur ausdrücklich bestätigte Peers.
- DNS-SD-Discovery mit Diensttyp `_klimapilot-sync._tcp.`.
- Lokale Adressauswahl berücksichtigt private IPv4, IPv4-Link-Local, IPv6-ULA und scoped IPv6-Link-Local; öffentliche Adressen werden nicht als Listener-Adresse verwendet.
- WLAN-Callback überwacht das ausgewählte WLAN; Default-Network-Callback erkennt Wechsel zu einem anderen WLAN.
- `ClimatePeerTcp` implementiert separat einen signaturbasierten gegenseitigen Handshake mit Nonces, Rollenbindung, Peer-ID-/Schlüsselprüfung und Zeit-/Feldgrößenlimits.
- TLS/mTLS wurde nach ausdrücklicher Nutzerentscheidung verworfen. TCP ist unverschlüsselt; andere Teilnehmer desselben WLANs können Daten passiv mitlesen.
- **Discovery bleibt reject-only.** Die TCP-Handshake-Klasse ist noch nicht in den Discovery-Listener integriert.
- **Es findet kein Profil-, Outbox- oder sonstiger App-Nutzdatentransfer über Peer-TCP statt.**
- `ClimateProfileSyncEngine` wird vom bestehenden ioBroker-Sync-Pfad verwendet; die lokale Peer-Transportintegration fehlt.

## 5. CI- und Testnachweise

Die folgenden Nachweise sind die zuletzt konkret dokumentierten Ergebnisse; sie dürfen nicht als Validierung des neueren App-Code-Commits ausgegeben werden:

- WLAN-Workflow **#202**, erfolgreich auf älterem Commit `ae1bdc03c78624ff640505b4bd255bfd885b05c5`.
- WLAN-Workflow **#201**, erfolgreich auf älterem Commit `41b9a3ab513e7d48daa09ee21298fe718748363b`.
- Separater TCP-Emulatorlauf **#62**, erfolgreich auf älterem Commit `41b9a3ab513e7d48daa09ee21298fe718748363b`, API 26.
- WLAN-Workflow **#203**, erfolgreich per `workflow_dispatch` auf `test` mit Commit `1a1d9df06492b45b89d537ac2844d76c789280a6`: `testDebugUnitTest`, `assembleDebug` und `compileReleaseKotlin` erfolgreich. Damit sind die elf aktuellen Netzwerk-Policy-Tests auch über Gradle/JUnit ausgeführt.
- Die sieben JVM-Adress-/IPv6-Scope-Tests sind vorhanden; ihre Ausführung auf dem aktuellen Stand ist nicht bestätigt.
- TCP-Emulatorlauf **#63**, Commit `725fe3109aa4712abda0a4f500cb6433c8a49e19` (Workflow-only-Abzweig vom identischen App-/Teststand `1a1d9df...`): API 26 erfolgreich; Gradle-Build erfolgreich und Instrumentierungstests **3/3 bestanden**. API 35 im selben Lauf fehlgeschlagen, weil der Emulator/Package-Manager nicht rechtzeitig verfügbar war; keine bestandenen API-35-App-Tests.
- Optionale API-35-Wiederholungen #66, #67 und #68 fehlgeschlagen; #64 und #65 wurden während der Workflow-Konfigurationskorrektur abgebrochen. Lauf #68 enthielt zusätzliche KVM-Berechtigungs- und Emulator-Readiness-Prüfungen, endete aber weiterhin mit Fehler im API-35-Job. API 35 bleibt ausdrücklich optional und ist kein Freigabekriterium. Keine weiteren API-35-Wiederholungen, solange kein konkreter neuer technischer Ansatz vorliegt.
- Kein Zwei-Smartphone-Praxistest bestätigt.
- Der erfolgreiche WLAN-Lauf #203 deckt den aktuellen `test`-HEAD `1a1d9df...` ab. Der API-26-Instrumentierungslauf #63 verwendet einen separaten Workflow-only-Commit, dessen App-/Testbaum identisch zum `test`-HEAD ist.
- Die elf Netzwerk-Policy-Fälle wurden im WLAN-Workflow #203 auf dem aktuellen App-/Teststand durch `testDebugUnitTest` ausgeführt; der gesamte Workflow war erfolgreich. Zusätzlich wurden sie zuvor mit einem separaten Kotlin-Smoke-Harness 11/11 bestanden. Der Smoke-Harness ist kein Ersatz für Gradle, Android-Kompilierung oder Emulator-/Gerätetests.

Workflows:
- `.github/workflows/wlan-validation.yml`: PR-Trigger sowie `workflow_dispatch`; laut Workflowdefinition Unit-Tests, Debug-APK-Build und Release-Kotlin-Kompilierung mit Java 17/Android SDK 35.
- `.github/workflows/pairing-tls-validation.yml`: historischer Dateiname; der Workflow validiert inzwischen TCP-Peer-Authentifizierung und ist nur manuell startbar. Die API-35-Experimente liefen ausschließlich auf temporären `validation/*`-Branches; sie wurden nicht in `main`, `test` oder PR #6 übernommen.

## 6. Verbindliche Freigabesperren

- Kein Merge von PR #6 und keine Änderung an `main` ohne ausdrückliche Freigabe.
- Keine Produktionsversionserhöhung, kein Produktionsbuild und kein Release aus diesem WLAN-Arbeitsstrang.
- Kein Profil-/Nutzdatentransfer über Peer-TCP vor Handshake-Integration, Protokollhärtung und erfolgreichem Zwei-Smartphone-Test.
- Updater und Installationsablauf sind abgeschlossen und dürfen **niemals geändert** werden.
- Bestehende ioBroker-Verbindung, WF-RAC-Steuerung und Produktionsfunktionen schützen.

## 7. Produkt- und Hardwaregrenzen

- Zielgerät: Mitsubishi Heavy SRK15ZS-WF.
- Die Klimaanlage ist noch nicht installiert; reale Hardware-/Firmwarefunktionen sind daher nicht bestätigt.
- Adapter `mhi-wfrac` ist noch nicht eingerichtet und wird erst nach Installation der Klimaanlage verwendet.
- SENEC/`energyfluss` nicht als Datenquelle einplanen.
- Produktionsverbindung zu ioBroker: WebSocket `ws.0`, Port 8084; Alive-Prüfung über `system.adapter.admin.0.alive`.

## 8. Nächste Schritte

1. Die aktuelle WLAN-/Lifecycle-Basis ist durch WLAN-Workflow #203 und API-26-TCP-Emulatorlauf #63 validiert.
2. Als nächsten Entwicklungsschritt den vorhandenen gegenseitigen `ClimatePeerTcp`-Handshake kontrolliert in den Discovery-Listener und einen expliziten Verbindungsversuch zu einem bereits gekoppelten Peer integrieren. Die Verbindung wird nach erfolgreicher Authentifizierung wieder geschlossen; noch keine Nutzdaten.
3. Dafür zunächst begrenzte parallele Server-Sessions, Socket-/Executor-Cleanup, Trust-Prüfung und UI-Rückmeldungen spezifizieren und testen. Keine automatische Verbindung zu beliebigen Discovery-Ankündigungen.
4. Nach der Integration WLAN-Workflow #203-ähnliche Prüfung und den verpflichtenden API-26-Instrumentierungslauf ausführen. API 35 bleibt optional.
5. Erst danach Nachrichtenframing, Größen-/Schema-/Gerätevalidierung, Replay-/Duplikaterkennung und idempotente Wiederaufnahme umsetzen.
6. Bis zur vollständigen Protokollhärtung und zum erfolgreichen Zwei-Smartphone-Test bleibt Peer-Nutzdatentransfer gesperrt.

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
