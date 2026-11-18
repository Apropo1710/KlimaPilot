
## Verbindliche Statuskorrektur – 18.11.2026

Dieser Abschnitt ist der aktuelle operative Status und ersetzt abweichende ältere Branch-/CI-Angaben in den nachfolgenden chronologischen Einträgen.

- Produktionsbranch `main`: HEAD `356842b20acbc042a21cb4d06e664dbd45654d98`; Produktionsversion **0.4.64 / versionCode 94**. Letzter bestätigter Produktionsbuild #229 und Release `latest` bleiben unverändert.
- Dieser Testbranch `test`: HEAD `ae1bdc03c78624ff640505b4bd255bfd885b05c5`.
- Vergleich `main...test`: **151 ahead / 2 behind**, Merge-Base `c7f1d57e4a4661d1492e5dca42ec9a9841d3e5fd`. Divergente Branches; keine automatische Zusammenführung.
- [PR #6](https://github.com/Apropo1710/KlimaPilot/pull/6) offen und Draft, nicht gemergt. Keine Produktionsfreigabe.
- WLAN-Validierung auf exakt diesem HEAD: [Run 44248243246](https://github.com/Apropo1710/KlimaPilot/actions/runs/44248243246) **completed / success** (Unit-Tests, Debug-Assembly, `compileReleaseKotlin`).
- Letzter erfolgreicher TCP-Emulatorlauf [44247356481](https://github.com/Apropo1710/KlimaPilot/actions/runs/44247356481) lief auf älterem Commit `41b9a3ab513e7d48daa09ee21298fe718748363b`, API 26. Für den aktuellen HEAD liegt kein bestätigter Emulatorlauf vor. API 35 ist nicht als bestanden bestätigt.
- `.github/workflows/pairing-tls-validation.yml` wird ausschließlich manuell per `workflow_dispatch` gestartet. `.github/workflows/wlan-validation.yml` validiert PR-Updates. Dadurch kein automatischer Emulatorlauf bei jedem Commit.
- TCP-Peer-Authentifizierung: gegenseitiger ECDSA-Challenge-Response-Handshake mit frischen Nonces, Peer-ID-/Rollenbindung, Limits und Socket-Timeouts. TLS/mTLS wurde nach ausdrücklicher Nutzerfreigabe verworfen. TCP ist unverschlüsselt; passives Mitlesen ist möglich, aktive Manipulation nicht vollständig ausgeschlossen.
- Pairing-UI/QR, Keystore-Identität, Trusted-Peer-Speicher und JVM-Negativtests sind vorhanden. Die UI ist durch `BuildConfig.DEBUG` auf die Debug-App mit separater Application-ID `de.klimapilot.app.debug` begrenzt. Release-UI bleibt getrennt; Updater wurde nicht verändert.
- **Nicht fertig:** TCP-Handshake ist nicht in Discovery integriert; Discovery nimmt Verbindungen weiterhin nur an und schließt sie (reject-only). Es gibt keinen Profil-/Outbox-Transfer und keine automatische Peer-Synchronisierung.
- **Freigabesperre:** keine Nutzdatenübertragung vor Integration, strikter Empfänger-Validierung und erfolgreichem Zwei-Smartphone-Test. Kein Merge nach `main`, keine Produktionsversionserhöhung und kein Produktionsrelease.
- Nächster Schritt: Discovery-, TCP-, Session-Controller- und Trusted-Peer-Pfade vollständig prüfen und ein Integrationsdesign mit Negativtests festlegen.




## Code-Review-Zwischenstand – 18.11.2026, 07:21 Uhr MEZ

Die aktuelle Prüfung der Dateien auf `test` ergab:

- `ClimatePeerTcp`: gegenseitige ECDSA-Challenge-Response-Authentifizierung, frische Nonces, Rollen-/Peer-ID-Bindung, lokale Trust-Prüfung auf beiden Seiten, Größenlimits und absolute Handshake-Frist sind im Code vorhanden. Nach erfolgreichem Handshake wird das vorherige Socket-Timeout wiederhergestellt.
- JVM-Tests decken gegenseitig vertrauenswürdige Peers, manipulierte Signatur, falsche Peer-ID/Schlüssel-Kombination, übergroße Felder, abgebrochenen Client-Hello, Timeout und Slow-Drip ab. Android-Instrumentierungstests prüfen zusätzlich beide Trust-Richtungen und einen kleinen Datenaustausch nach erfolgreicher Authentifizierung.
- `ClimateProfilePeerDiscovery` ist weiterhin ausdrücklich reject-only. Es liest/schreibt keine Nutzdaten. Der TCP-Handshake ist nicht integriert.
- **Integrationsrisiko vor Umsetzung:** `ServerSocket(0)` wird aktuell ohne explizite lokale Interface-Bindung erzeugt und bindet damit standardmäßig an alle lokalen Interfaces. Der Listener darf bei einer späteren Integration nicht unbeabsichtigt auf Mobilfunk-/anderen Interfaces erreichbar sein. Vor Freigabe muss der Socket an das aktive WLAN-Netzwerk gebunden oder gleichwertig auf dieses Interface begrenzt werden; Netzwerkwechsel müssen den Listener sicher beenden und neu starten.
- **Testlücke:** `ClimatePairingSessionControllerTest` simuliert beide Geräte mit demselben `ClimateTrustedPeerStore`/SharedPreferences-Namen. Das prüft den Ablauf, modelliert aber nicht zwei unabhängige lokale Trust-Speicher. Ein Test mit getrennten Stores pro Gerät ist vor Integrationsfreigabe erforderlich.
- Die Compose-UI koppelt nur QR-Pairing; der Text weist zutreffend darauf hin, dass noch keine Klimaprofile übertragen werden. Discovery wird bei App-Hintergrund und Composable-Dispose beendet.
- Dies ist eine Codeprüfung, kein neuer Testlauf. Keine Codeänderung, kein CI-Start und keine Freigabe für Profiltransfer.

