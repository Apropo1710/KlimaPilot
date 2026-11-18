# KlimaPilot – verbindlicher Projektstatus

**Letzter vollständiger Abgleich:** 18.11.2026, 08:16 Uhr MEZ  
**Repository:** https://github.com/Apropo1710/KlimaPilot

## 1. Produktivstand

- `main` HEAD: `b82433038a3ced3de3ff908788fde4ef36d58f66`
- Produktionsversion aus `app/build.gradle.kts`: **0.4.64 / versionCode 94**
- Letzter bestätigter erfolgreicher Produktionsbuild: **GitHub Actions #233**, Quellcommit `b88ff7e9709bc1ea5aafd673e26bb1d4efee9c16`.
- Release `latest` enthält die signierte `KlimaPilot.apk` dieses Builds. Der aktuelle `main`-HEAD ist ein späterer Dokumentationscommit.
- Produktions-APK: https://github.com/Apropo1710/KlimaPilot/releases/download/latest/KlimaPilot.apk
- `main` wurde im aktuellen WLAN-Pairing-Arbeitsschritt nicht verändert.

## 2. Testbranch und PR

- `test` HEAD: aktueller Dokumentationsabgleich-Commit dieses Dokuments; der unmittelbar vorausgehende App-Code-Commit ist `bba8969ba4306d159ba388753f85c6638bf32f7f`.
- App-Code-Commit mit der neuesten Netzwerkänderung: `bba8969ba4306d159ba388753f85c6638bf32f7f`.
- [PR #6](https://github.com/Apropo1710/KlimaPilot/pull/6): offen, Draft, nicht gemergt. Kein Merge ohne ausdrückliche Freigabe.
- Nach dem aktuellen Doku-Commit: `test` **182 Commits ahead / 10 behind** gegenüber `main`; Merge-Base `c7f1d57e4a4661d1492e5dca42ec9a9841d3e5fd`. Branches divergent; kein Rebase/Merge.
- PR #6 umfasst den gesamten WLAN-/Pairing-Arbeitsstrang. Der Branch enthält Änderungen an Manifest, Build-Konfiguration, Pairing-UI und Sync-Code; deshalb vor jeder späteren Integration vollständiger Diff-Review gegen den aktuellen `main` erforderlich.

## 3. Letzter Code-Arbeitsschritt – Netzwerk-Lifecycle

Commit: `bba8969ba4306d159ba388753f85c6638bf32f7f`  
Datei: `app/src/main/java/de/klimapilot/app/sync/ClimateProfilePeerDiscovery.kt`

- Zusätzlich zum WLAN-spezifischen `registerNetworkCallback` wird `registerDefaultNetworkCallback` verwendet.
- `onAvailable` und `onCapabilitiesChanged` des Default-Callbacks prüfen, ob Android zu einem anderen WLAN gewechselt hat.
- Ein zusätzlich verfügbares WLAN allein bricht die Discovery nicht ab.
- Ein Wechsel des Standardnetzes zu einem anderen WLAN beendet die laufende Discovery fail-closed.
- Mobilfunk als Standardnetz ist zulässig, solange das ausgewählte WLAN selbst verfügbar bleibt.
- Verlust und Änderung der Link-Eigenschaften des ausgewählten WLANs werden weiterhin separat überwacht.
- Beide Callbacks werden beim Cleanup abgemeldet.
- Der TCP-Listener ist weiterhin reject-only: eingehende Verbindungen werden angenommen und geschlossen. Kein authentifizierter Handshake im Discovery-Pfad und kein Nutzdatentransfer.

Vorherige Netzwerk-/Adress-Commits:
- `4f35e1feb60e0f46eac360fee51a58d024bfc6bb`
- `305fc0e03df09ae2c7ff8e8511a88bc2abaeaea4`

## 4. WLAN-Adressauswahl und Discovery

- DNS-SD-Diensttyp: `_klimapilot-sync._tcp.`
- TXT-Daten sind auf Protokollversion und zufällige Discovery-ID begrenzt.
- Discovery-Metadaten sind ausdrücklich kein Vertrauens- oder Pairing-Nachweis.
- Adressauswahl: private IPv4 bevorzugt, IPv4-Link-Local als Fallback, danach IPv6-ULA und scoped IPv6-Link-Local. Öffentliche IPv4-/IPv6-Adressen werden nicht als Listener-Adresse verwendet.
- IPv6-Link-Local wird an das ausgewählte Netzwerkinterface gebunden.
- Begrenzungen für gefundene Geräte, parallele Auflösungen und Resolve-Zeitlimits sind implementiert.
- Lifecycle umfasst Startgeneration, Fehlerabbruch und zentralen Cleanup.
- Zwei WLAN-Netzwerke gleichzeitig, Defaultnetzwechsel, Interfacewechsel und reale Android-Callback-Reihenfolgen sind auf echten Geräten noch zu validieren.

## 5. Pairing und Identität

- Installationsidentität über ECDSA P-256 im Android Keystore; privater Schlüssel ist nicht exportierbar.
- Peer-ID wird aus dem öffentlichen Schlüssel abgeleitet.
- QR-Einladung ist signiert und 120 Sekunden gültig.
- Signierte Antwort bindet sich an Sitzung und Nonce der Einladung.
- Gegenseitige Benutzerbestätigung und Fingerprint-Prüfung sind Voraussetzung für die Aufnahme in den Trusted-Peer-Speicher.
- Trusted-Peer-Speicher begrenzt und validiert gespeicherte Schlüssel.
- Pairing-UI und Debug-Einstieg sind von der produktiven Release-App getrennt; Debug-Application-ID ist `de.klimapilot.app.debug`.
- TLS/mTLS wurde nach ausdrücklicher Nutzerentscheidung verworfen. Der lokale TCP-Transport ist unverschlüsselt; WLAN-Teilnehmer können den Datenverkehr passiv mitlesen.

## 6. TCP-Authentifizierung und Synchronisation – wichtige Trennung

- `ClimatePeerTcp` implementiert einen gegenseitigen signaturbasierten Challenge-/Response-Handshake mit Nonces, Rollenbindung, Peer-ID/Public-Key-Abgleich, Trust-Store-Prüfung und Zeit-/Feldgrößenlimits.
- Diese TCP-Klasse ist **noch nicht** in den Discovery-Listener integriert.
- Discovery bleibt reject-only.
- Keine Profil-, Outbox- oder sonstigen App-Nutzdaten werden über den Peer-TCP-Pfad übertragen.
- `ClimateProfileSyncEngine` ist vorhanden und wird vom bestehenden ioBroker-Sync-Pfad verwendet. Die lokale Peer-Transportintegration fehlt weiterhin.
- Vor einer späteren Übertragung sind Framing, Gesamtgrößenlimits, Schema- und Wertebereichsvalidierung, Replay-/Duplikatbehandlung, Fehlerwiederanlauf und Bestätigung nach dauerhafter Speicherung verbindlich zu implementieren und zu testen.

## 7. CI und Validierung

- Letzter bestätigter WLAN-Workflow: [Run #202](https://github.com/Apropo1710/KlimaPilot/actions/runs/44248243246), erfolgreich, aber auf älterem Code `ae1bdc03c78624ff640505b4bd255bfd885b05c5`.
- Letzter bestätigter TCP-Emulatorlauf: [Run #201](https://github.com/Apropo1710/KlimaPilot/actions/runs/44247356478), erfolgreich auf älterem Code `41b9a3ab513e7d48daa09ee21298fe718748363b`; API 26.
- Beide Runs liegen zeitlich vor dem aktuellen Netzwerk-Codecommit `bba8969...`.
- Die Actions-Abfrage nach dem Doku-Commit ergab weiterhin keinen neuen PR-Workflow-Lauf für `bba8969...` oder den aktuellen Doku-HEAD. Die jüngsten sichtbaren Runs #202/#201 liegen davor.
- `.github/workflows/wlan-validation.yml`: PR-Trigger gegen `main`, zusätzlich `workflow_dispatch`; führt `testDebugUnitTest`, `assembleDebug` und `compileReleaseKotlin) mit Java 17/Android SDK 35 aus.
- `.github/workflows/pairing-tls-validation.yml` heißt historisch noch so, führt aber TCP-Peer-Authentifizierung aus. Ausschließlich manueller `workflow_dispatch`; API 26 verpflichtend und API 35 manuell. Es steht in dieser Arbeitsumgebung kein Workflow-Dispatch-Werkzeug zur Verfügung.
- Die sieben Tests in `ClimateWifiListenerAddressSelectorTest.kt` sind vorhanden, aber für den aktuellen Stand nicht als ausgeführt/bestanden bestätigt.
- Ein isolierter JDK-Smoketest zur IPv6-Scope-API war erfolgreich; er ist kein Android-Build und kein App-Test.
- Kein Zwei-Smartphone-Test bestätigt.

## 8. Verbindliche Freigabesperren

- Kein Merge von PR #6 und keine Änderung an `main` ohne ausdrückliche Freigabe.
- Keine Produktionsversionserhöhung, kein Produktionsbuild und kein Release aus diesem WLAN-Arbeitsstrang.
- Kein Profil-/Nutzdatentransfer über Peer-TCP vor Integration, Protokollhärtung und erfolgreichem Zwei-Smartphone-Test.
- Updater und Installationsablauf sind abgeschlossen und dürfen nicht verändert werden.
- Bestehende ioBroker-Verbindung, WF-RAC-Steuerung und Produktionsfunktionen schützen.

## 9. Produkt- und Hardwaregrenzen

- Zielgerät: Mitsubishi Heavy SRK15ZS-WF.
- Klimaanlage noch nicht installiert; reale Hardware-/Firmwarefunktionen daher nicht bestätigt.
- Adapter `mhi-wfrac` noch nicht eingerichtet.
- SENEC/`energyfluss` nicht als Datenquelle einplanen.
- Produktionsverbindung zu ioBroker: WebSocket `ws.0`, Port 8084; Alive-Prüfung über `system.adapter.admin.0.alive`.

## 10. Pflegepflicht

Nach jeder Codeänderung sind mindestens `STATUS.md`, `PROJECT_STATUS.md` und die jeweils fachlich betroffenen Dokumente zu aktualisieren. Bei einem Architektur-/Sicherheits-/Protokollschritt sind zusätzlich `LOCAL_WIFI_SYNC_DESIGN.md`, `PAIRING_PROTOCOL_DECISIONS.md` und/oder `SYNC_RESILIENCE_PLAN.md) anzupassen. README und Migrationsdokumente müssen bei Produktionsversions- oder Funktionsänderungen mitgezogen werden. Dokumente dürfen keine überholten offenen Aufgaben oder Statusangaben als aktuell ausgeben.
