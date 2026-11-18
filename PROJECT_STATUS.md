
## Verbindliche Statuskorrektur – 18.11.2026, 08:10 Uhr MEZ

Dieser Abschnitt ist der aktuelle operative Status und ersetzt abweichende ältere Branch-/CI-Angaben in der nachfolgenden chronologischen Dokumentation.

- Produktionsbranch `main`: HEAD `b82433038a3ced3de3ff908788fde4ef36d58f66`; Produktionsversion **0.4.64 / versionCode 94**. Produktionsbuild #233 ist erfolgreich auf Build-Quellcommit `b88ff7e9709bc1ea5aafd673e26bb1d4efee9c16`; Release `latest` enthält die signierte `KlimaPilot.apk`.
- Testbranch `test`: aktueller App-Code-Commit `bba8969ba4306d159ba388753f85c6638bf32f7f` (Default-WLAN-Wechselüberwachung); die drei Status-/Entscheidungsdateien werden in einem nachfolgenden Dokumentationscommit aktualisiert.
- Vor dem Dokumentationscommit: Vergleich `main...test` **180 ahead / 10 behind**, Merge-Base `c7f1d57e4a4661d1492e5dca42ec9a9841d3e5fd`. Die Branches sind divergent; kein Merge/Rebase.
- [PR #6](https://github.com/Apropo1710/KlimaPilot/pull/6) ist offen, Draft und nicht gemergt. Keine Produktionsfreigabe.
- Für die aktuellen Code-/Dokumentationscommits wurde kein Actions-Lauf gemeldet. Die Abfrage für den vorherigen Dokumentations-HEAD `b91706c686350c356754001a31f407e1fc705057` lieferte keine PR-Workflow-Runs. Der aktuelle Commit benötigt weiterhin einen bestätigten WLAN-Validierungslauf.
- Letzter bestätigter WLAN-Lauf [Run 44248243246](https://github.com/Apropo1710/KlimaPilot/actions/runs/44248243246) war erfolgreich auf älterem App-Code `ae1bdc03`; kein aktueller Nachweis.
- Letzter erfolgreicher TCP-Emulatorlauf [Run 44247356481](https://github.com/Apropo1710/KlimaPilot/actions/runs/44247356481) lief auf älterem Commit `41b9a3ab513e7d48daa09ee21298fe718748363b`, API 26. Kein Emulatornachweis für den aktuellen Stand; API 35 nicht bestätigt.
- `.github/workflows/wlan-validation.yml` reagiert auf PR-Updates und kann manuell gestartet werden. `.github/workflows/pairing-tls-validation.yml` ist ausschließlich `workflow_dispatch`; für diese manuelle Aktion steht hier kein Dispatch-Werkzeug zur Verfügung. Keine manuellen Nutzeraktionen oder unnötigen Doppelstarts veranlassen.
- WLAN-Discovery bindet den Reject-only-TCP-Listener an eine lokale Adresse des ausgewählten WLAN-Interfaces. Private IPv4 wird bevorzugt; IPv4-Link-Local, IPv6-ULA und scoped IPv6-Link-Local sind Fallbacks. Öffentliche Adressen werden abgelehnt.
- Netzwerküberwachung: ein WLAN-spezifischer Callback beobachtet Verlust und Link-/Adressänderungen des ausgewählten WLANs. Ein zusätzlicher Default-Network-Callback erkennt auch den Wechsel des Android-Standardnetzes zu einem anderen WLAN, selbst wenn beide WLANs gleichzeitig verfügbar bleiben. Ein Mobilfunk-Defaultnetz ist zulässig, solange das ausgewählte WLAN verfügbar bleibt. Beide Callbacks werden beim Cleanup abgemeldet.
- IPv6-Link-Local-Scope wird an das ausgewählte Interface gebunden. Sieben JVM-Testfälle sind im Quellcode vorhanden; sie wurden für den aktuellen Stand nicht ausgeführt oder bestätigt. Ein isolierter JDK-Smoketest war erfolgreich, ersetzt aber keinen Android-Build oder Unit-Test.
- TCP-Peer-Authentifizierung ist implementiert, aber nicht in Discovery integriert. Discovery bleibt **reject-only**; kein Profil-/Outbox-Transfer und keine automatische Peer-Synchronisierung.
- TLS/mTLS wurde nach ausdrücklicher Nutzerfreigabe verworfen. TCP ist unverschlüsselt und kann passiv mitgelesen werden; aktive Manipulation ist nicht vollständig ausgeschlossen.
- Pairing-UI/QR, Keystore-Identität und Trusted-Peer-Speicher sind auf die separate Debug-App `de.klimapilot.app.debug` begrenzt.
- **Freigabesperre:** keine Nutzdatenübertragung vor sicherer Integration, strikter Empfänger-/Schema-/Wertebereichsvalidierung und erfolgreichem Zwei-Smartphone-Test. Kein Merge nach `main`, keine Produktionsversionserhöhung und kein Produktionsrelease.
- Updater, Manifest, Produktionsbranch `main`, Release und bestehender WF-RAC-/ioBroker-Steuerpfad wurden durch diese Arbeit nicht verändert.

## Historischer WLAN-Bindungsstand – 18.11.2026, 07:33 Uhr MEZ (durch die operative Statuskorrektur oben ergänzt)

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



## WLAN-Callback und IPv6-Scope-Korrektur (18.11.2026, 07:44 Uhr MEZ)

- Zusätzliche verfügbare WLAN-Netze beenden die laufende Discovery nicht mehr pauschal. Ein neuer WLAN-Callback führt nur dann zum Abbruch, wenn dieses Netz zugleich Androids aktives Standardnetz ist. Verlust des ursprünglich ausgewählten WLANs und Änderungen seiner Interface-Adresse bleiben fail-closed.
- Die IPv6-Link-Local-Scope-Erzeugung ist in `ClimateWifiListenerAddressSelector.scopeLinkLocalAddress` gekapselt. Nicht-Link-Local-Adressen werden von dieser Funktion abgelehnt.
- Zwei weitere JVM-Tests prüfen die Bindung einer IPv6-Link-Local-Adresse an das ausgewählte Interface sowie die Ablehnung einer ULA-Adresse durch die Scope-Funktion. Damit enthält die Adressauswahl-Testdatei sieben Testfälle.
- Code-Commit: `4f35e1feb60e0f46eac360fee51a58d024bfc6bb`; Test-Commit: `305fc0e03df09ae2c7ff8e8511a88bc2abaeaea4`.
- Diese Änderungen sind noch nicht durch CI, Android-Build oder reale Geräte geprüft. Es wird kein Testerfolg behauptet.
- Discovery bleibt reject-only. Keine TCP-Handshake-Integration und keine Profil-/Outbox-Übertragung. `main`, Updater, Release und bestehender ioBroker-/WF-RAC-Pfad bleiben unverändert.
