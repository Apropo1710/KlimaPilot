# KlimaPilot – verbindlicher Projektstatus

**Letzter Abgleich:** 18.11.2026, 08:24 Uhr MEZ  
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

- Aktueller `test`-HEAD: dieser abschließende Dokumentationscommit.
- Letzter App-Code-Commit: `bba8969ba4306d159ba388753f85c6638bf32f7f`.
- [PR #6](https://github.com/Apropo1710/KlimaPilot/pull/6): offen, Draft, nicht gemergt. Kein Merge ohne ausdrückliche Freigabe.
- Branchvergleich nach dieser Bereinigung: **194 Commits ahead / 10 behind** gegenüber `main`; Merge-Base `c7f1d57e4a4661d1492e5dca42ec9a9841d3e5fd`. Die Branches sind divergent; kein Rebase oder Merge im Rahmen der Dokumentationspflege.
- PR #6 umfasst den WLAN-/Pairing-Arbeitsstrang. Vor einer späteren Integration ist ein vollständiger Diff-Review gegen den dann aktuellen `main` erforderlich.

## 3. Letzte Codeänderung

Commit: `bba8969ba4306d159ba388753f85c6638bf32f7f`  
Datei: `app/src/main/java/de/klimapilot/app/sync/ClimateProfilePeerDiscovery.kt`

- Zusätzlich zum WLAN-spezifischen `registerNetworkCallback` wird `registerDefaultNetworkCallback` verwendet.
- Der Default-Callback erkennt einen Wechsel des Android-Standardnetzes zu einem anderen WLAN.
- Ein zusätzlich verfügbares WLAN allein beendet die Discovery nicht.
- Ein Wechsel des Standardnetzes zu einem anderen WLAN beendet die laufende Discovery fail-closed.
- Mobilfunk als Standardnetz ist zulässig, solange das ausgewählte WLAN selbst verfügbar bleibt.
- Verlust und Änderung der Link-Eigenschaften des ausgewählten WLANs werden weiterhin separat überwacht.
- Beide Callbacks werden beim Cleanup abgemeldet.
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
- Für den aktuellen App-Code-Commit `bba8969...` ist kein zugehöriger erfolgreicher Android-Build oder Testlauf bestätigt.
- Die sieben JVM-Adress-/IPv6-Scope-Tests sind vorhanden; ihre Ausführung auf dem aktuellen Stand ist nicht bestätigt.
- Kein API-35-Emulatornachweis und kein Zwei-Smartphone-Praxistest bestätigt.
- Eine Abfrage der PR-Workflow-Runs für `bba8969...` und den damaligen Doku-HEAD lieferte keine zugeordneten Runs. Es wird daher kein aktueller CI-Erfolg behauptet.

Workflows:
- `.github/workflows/wlan-validation.yml`: PR-Trigger sowie `workflow_dispatch`; laut Workflowdefinition Unit-Tests, Debug-APK-Build und Release-Kotlin-Kompilierung mit Java 17/Android SDK 35.
- `.github/workflows/pairing-tls-validation.yml`: historischer Dateiname; der Workflow validiert inzwischen TCP-Peer-Authentifizierung und ist nur manuell startbar.

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

1. Den aktuellen Netzwerk-Lifecycle-Code unabhängig reviewen.
2. Einen erfolgreichen Android-Build und die vorgesehenen Unit-Tests exakt auf dem aktuellen Codecommit nachweisen.
3. Danach gezielte Tests für WLAN-Verlust, Wechsel des Standardnetzes und parallele WLAN-Verfügbarkeit durchführen.
4. Erst nach erfolgreicher Validierung die Integration des TCP-Handshakes planen.
5. Bis zur vollständigen Protokollhärtung und zum erfolgreichen Zwei-Smartphone-Test bleibt Peer-Nutzdatentransfer gesperrt.

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
