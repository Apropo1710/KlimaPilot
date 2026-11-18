
## Verbindliche Statuskorrektur – 18.11.2026

Dieser Abschnitt ist der aktuelle operative Status und ersetzt abweichende ältere Branch-/CI-Angaben in den nachfolgenden chronologischen Einträgen.

- Produktionsbranch `main`: HEAD `b82433038a3ced3de3ff908788fde4ef36d58f66`; Produktionsversion **0.4.64 / versionCode 94**. Produktionsbuild #233 ist erfolgreich (Build-Quellcommit `b88ff7e9709bc1ea5aafd673e26bb1d4efee9c16`); Release-Tag `latest` zeigt auf diesen Build-Commit und enthält die aktualisierte `KlimaPilot.apk`.
- Dieser Testbranch `test`: aktueller App-Code-HEAD `9cb2599ab714f71b9f0334ecc85678e31b489ed2` (nachfolgender Dokumentationscommit aktualisiert die Statusdateien).
- Vergleich `main...test` nach App-Code-Commit: **159 ahead / 10 behind**, Merge-Base `c7f1d57e4a4661d1492e5dca42ec9a9841d3e5fd`. Divergente Branches; keine automatische Zusammenführung.
- [PR #6](https://github.com/Apropo1710/KlimaPilot/pull/6) offen und Draft, nicht gemergt. Keine Produktionsfreigabe.
- Letzter bestätigter WLAN-Lauf [Run 44248243246](https://github.com/Apropo1710/KlimaPilot/actions/runs/44248243246) war erfolgreich auf älterem App-Code-HEAD `ae1bdc03`. Für `7cd800141a3b16b9f00c91669a0a922eddb509e1` liegt noch kein CI-Ergebnis vor; nach dem Commit wurde kein automatischer PR-Run erzeugt.
- Letzter erfolgreicher TCP-Emulatorlauf [44247356481](https://github.com/Apropo1710/KlimaPilot/actions/runs/44247356481) lief auf älterem Commit `41b9a3ab513e7d48daa09ee21298fe718748363b`, API 26. Für den aktuellen HEAD liegt kein bestätigter Emulatorlauf vor. API 35 ist nicht als bestanden bestätigt.
- `.github/workflows/pairing-tls-validation.yml` wird ausschließlich manuell per `workflow_dispatch` gestartet. `.github/workflows/wlan-validation.yml` validiert PR-Updates. Dadurch kein automatischer Emulatorlauf bei jedem Commit.
- TCP-Peer-Authentifizierung: gegenseitiger ECDSA-Challenge-Response-Handshake mit frischen Nonces, Peer-ID-/Rollenbindung, Limits und Socket-Timeouts. TLS/mTLS wurde nach ausdrücklicher Nutzerfreigabe verworfen. TCP ist unverschlüsselt; passives Mitlesen ist möglich, aktive Manipulation nicht vollständig ausgeschlossen.
- Pairing-UI/QR, Keystore-Identität, Trusted-Peer-Speicher und JVM-Negativtests sind vorhanden. Die UI ist durch `BuildConfig.DEBUG` auf die Debug-App mit separater Application-ID `de.klimapilot.app.debug` begrenzt. Release-UI bleibt getrennt; Updater wurde nicht verändert.
- **Nicht fertig:** TCP-Handshake ist nicht in Discovery integriert; Discovery nimmt Verbindungen weiterhin nur an und schließt sie (reject-only). Es gibt keinen Profil-/Outbox-Transfer und keine automatische Peer-Synchronisierung.
- **Freigabesperre:** keine Nutzdatenübertragung vor Integration, strikter Empfänger-Validierung und erfolgreichem Zwei-Smartphone-Test. Kein Merge nach `main`, keine Produktionsversionserhöhung und kein Produktionsrelease.
- Nächster Schritt: Discovery-, TCP-, Session-Controller- und Trusted-Peer-Pfade vollständig prüfen und ein Integrationsdesign mit Negativtests festlegen.




## WLAN-Bindung – Implementierungsstand 18.11.2026, 07:33 Uhr MEZ

Auf Testbranch `test`, App-Code-Commit `9cb2599ab714f71b9f0334ecc85678e31b489ed2`: 

- `ClimateProfilePeerDiscovery` sucht das WLAN über `ConnectivityManager.allNetworks` und bevorzugt das Default-Netz nur dann, wenn es selbst WLAN ist. So funktioniert die Suche auch, wenn Mobilfunk das Default-Netz ist. `LinkProperties.interfaceName` und `linkAddresses` liefern Interface und Adressen.
- `ClimateWifiListenerAddressSelector` wählt ausschließlich lokale Adressen: private IPv4 bevorzugt, IPv4-Link-Local als Fallback, IPv6-ULA und zuletzt IPv6-Link-Local mit Interface-Scope. Öffentliche IPv4-/IPv6-Adressen werden verworfen. Ohne lokale Adresse wird fail-closed abgebrochen.
- Der `ServerSocket` wird explizit an `InetSocketAddress(hostAddress, 0)` gebunden. `NsdServiceInfo.host` wird auf dieselbe Adresse gesetzt, damit DNS-SD nicht eine andere lokale Adresse als erreichbares Ziel veröffentlicht.
- `onLinkPropertiesChanged` erkennt Interface-/Adressänderungen. Bei WLAN-Netzwerkverlust oder geänderter Adresse wird die Discovery beendet; der alte Listener wird geschlossen und der Accept-Thread mit begrenztem Join beendet. Ein erneuter Start erzeugt einen neuen Listener.
- Neue JVM-Tests prüfen IPv4-Priorität, IPv6-Fallback, Ablehnung ungeeigneter/öffentlicher Adressen und IPv4-Link-Local-Fallback. `ClimatePairingSessionControllerTest` verwendet jetzt zwei getrennte `ClimateTrustedPeerStore`-Instanzen mit getrennten Preferences.
- **Validierung offen:** Für Commit `9cb2599` wurden noch keine Unit-Tests oder Android-Builds ausgeführt. Es existiert kein zugehöriger Actions-Run/Check-Run. Der letzte bestätigte WLAN-Lauf bleibt #202 auf `ae1bdc03`; kein Emulatorlauf auf dem neuen Code.
- Discovery bleibt reject-only. TCP-Handshake-Integration und Profiltransfer sind weiterhin ausdrücklich nicht Teil dieser Änderung.

## Code-Review-Zwischenstand – 18.11.2026, 07:21 Uhr MEZ

Die aktuelle Prüfung der Dateien auf `test` ergab:

- `ClimatePeerTcp`: gegenseitige ECDSA-Challenge-Response-Authentifizierung, frische Nonces, Rollen-/Peer-ID-Bindung, lokale Trust-Prüfung auf beiden Seiten, Größenlimits und absolute Handshake-Frist sind im Code vorhanden. Nach erfolgreichem Handshake wird das vorherige Socket-Timeout wiederhergestellt.
- JVM-Tests decken gegenseitig vertrauenswürdige Peers, manipulierte Signatur, falsche Peer-ID/Schlüssel-Kombination, übergroße Felder, abgebrochenen Client-Hello, Timeout und Slow-Drip ab. Android-Instrumentierungstests prüfen zusätzlich beide Trust-Richtungen und einen kleinen Datenaustausch nach erfolgreicher Authentifizierung.
- `ClimateProfilePeerDiscovery` ist weiterhin ausdrücklich reject-only. Es liest/schreibt keine Nutzdaten. Der TCP-Handshake ist nicht integriert.
- **Integrationsrisiko vor Umsetzung:** `ServerSocket(0)` wird aktuell ohne explizite lokale Interface-Bindung erzeugt und bindet damit standardmäßig an alle lokalen Interfaces. Der Listener darf bei einer späteren Integration nicht unbeabsichtigt auf Mobilfunk-/anderen Interfaces erreichbar sein. Vor Freigabe muss der Socket an das aktive WLAN-Netzwerk gebunden oder gleichwertig auf dieses Interface begrenzt werden; Netzwerkwechsel müssen den Listener sicher beenden und neu starten.
- **Testlücke:** `ClimatePairingSessionControllerTest` simuliert beide Geräte mit demselben `ClimateTrustedPeerStore`/SharedPreferences-Namen. Das prüft den Ablauf, modelliert aber nicht zwei unabhängige lokale Trust-Speicher. Ein Test mit getrennten Stores pro Gerät ist vor Integrationsfreigabe erforderlich.
- Die Compose-UI koppelt nur QR-Pairing; der Text weist zutreffend darauf hin, dass noch keine Klimaprofile übertragen werden. Discovery wird bei App-Hintergrund und Composable-Dispose beendet.
- Dies ist eine Codeprüfung, kein neuer Testlauf. Keine Codeänderung, kein CI-Start und keine Freigabe für Profiltransfer.



## WLAN-Callback und IPv6-Scope-Korrektur (18.11.2026, 07:41 Uhr MEZ)

- Zusätzliche verfügbare WLAN-Netze beenden die laufende Discovery nicht mehr pauschal. Ein neuer WLAN-Callback führt nur dann zum Abbruch, wenn dieses Netz zugleich Androids aktives Standardnetz ist. Verlust des ursprünglich ausgewählten WLANs und Änderungen seiner Interface-Adresse bleiben fail-closed.
- Die IPv6-Link-Local-Scope-Erzeugung ist in `ClimateWifiListenerAddressSelector.scopeLinkLocalAddress` gekapselt. Nicht-Link-Local-Adressen werden von dieser Funktion abgelehnt.
- Zwei weitere JVM-Tests prüfen die Bindung einer IPv6-Link-Local-Adresse an das ausgewählte Interface sowie die Ablehnung einer ULA-Adresse durch die Scope-Funktion. Damit enthält die Adressauswahl-Testdatei sieben Testfälle.
- Code-Commit: `4f35e1feb60e0f46eac360fee51a58d024bfc6bb`; Test-Commit: `d91b00b8c4c20535b97795e5db7fc54a735a5fad`.
- Diese Änderungen sind noch nicht durch CI, Android-Build oder reale Geräte geprüft. Es wird kein Testerfolg behauptet.
- Discovery bleibt reject-only. Keine TCP-Handshake-Integration und keine Profil-/Outbox-Übertragung. `main`, Updater, Release und bestehender ioBroker-/WF-RAC-Pfad bleiben unverändert.
