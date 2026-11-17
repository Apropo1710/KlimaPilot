<!-- AUTO-PRODUCTION-STATUS:START -->
## Automatisch gepflegter Produktionsstatus

- Quellcommit des APK-Builds: a87e0d8e2cfed5e1d2c3399466e1edaef1a66d00
- App-Version / versionCode: **0.4.64 / 94**
- Build-Zeit: 2026-11-17 11:29:06 UTC
- APK-Dateiname: **KlimaPilot.apk**
- APK-Signaturprüfung: erfolgreich (apksigner verify --print-certs)
- Build-Artefakt: kein Actions-Artefakt; APK wird im GitHub-Release latest veröffentlicht
- APK-SHA-256: 1553402f6baebe028d6876581aef235aa3abf89a7047150e4b47a429ef1730e2
- GitHub-Release: **erfolgreich veröffentlicht**; Asset KlimaPilot.apk wurde aktualisiert.
- Smartphone-Test: **nicht durch CI geprüft**; nur separat bestätigte Nutzerberichte gelten als Gerätetest.
- Updater: abgeschlossen und vom Nutzer bestätigt; Installer-Code wird durch diese Dokumentationspflege nicht verändert.
          - ChatGPT-Bibliotheksakte: separate Langzeit-/Historienakte; wird durch diesen Workflow nicht verändert.

<!-- AUTO-PRODUCTION-STATUS:END -->

# KlimaPilot – verifizierter Projekt- und Produktionsstatus

**Zuletzt abgeglichen:** 17.11.2026, 12:25 Uhr MEZ  
**Repository:** [Apropo1710/KlimaPilot](https://github.com/Apropo1710/KlimaPilot)  
**Maßgeblicher Branch:** `main`  
**Produktionsversion:** **0.4.64 / versionCode 94**

## Aktueller GitHub-, Build- und Release-Stand

- Maßgeblicher `main`-Stand vor der Versionsanhebung: `1a1d7834c22a2ac0726331fc9f49995cc253dc4a`; anschließende Statuspflege erfolgte auf `main`.
- PR #3 „Absicherung der Klimaprofil-Synchronisation durch dauerhaftes Änderungsjournal“ wurde am 17.11.2026 um 12:14:49 Uhr MEZ gemergt.
- Merge-Commit: `ee65841387824bca7d2b07ca80f71501540af157`; der Merge-Commit ist von GitHub kryptografisch als gültig verifiziert.
- GitHub Actions [Produktionsbuild #227](https://github.com/Apropo1710/KlimaPilot/actions/runs/44073072643): **completed / success** für den Versionscommit `1a1d7834c22a2ac0726331fc9f49995cc253dc4a`. Signierter Release-Build, Keystore-Prüfung, APK-Manifestprüfung und Signaturprüfung erfolgreich.
- Produktionsversion aus `app/build.gradle.kts`: **0.4.64 / versionCode 94**. Application-ID `de.klimapilot.app`; minSdk 26; compileSdk/targetSdk 35; Java/Kotlin JVM target 17.
- Build-Zeit: 17.11.2026, 12:23:51 Uhr MEZ (11:23:51 UTC).
- Release `latest`: Asset `KlimaPilot.apk`, Asset-ID `758773122`, 11.370.135 Bytes; aktualisiert 17.11.2026, 11:23:58 UTC.
- APK-SHA-256: `945dd9bacebbb16482e7acf352ccd58d91649349ebc02360102e093ccc92f3c4`; Signaturprüfung im Produktionsworkflow erfolgreich.
- Git-Tag `latest` zeigt auf den Versionscommit `1a1d7834c22a2ac0726331fc9f49995cc253dc4a`.
- APK: https://github.com/Apropo1710/KlimaPilot/releases/download/latest/KlimaPilot.apk

## Abgeschlossene Profil-Synchronisations-PR

- [PR #3](https://github.com/Apropo1710/KlimaPilot/pull/3) – dauerhaftes Änderungsjournal – **geschlossen und gemergt** am 17.11.2026, 12:14:49 Uhr MEZ.
- Gemergter PR-Head: `c111d1376b14801ec83c0eddbbc42de3dbb35ff0`; Merge-Commit auf `main`: `ee65841387824bca7d2b07ca80f71501540af157`.
- Der aktuelle PR-Head wurde durch [WLAN Branch Validation #48](https://github.com/Apropo1710/KlimaPilot/actions/runs/44071591137) mit erfolgreichen Unit-Tests und Debug-Kompilierung geprüft.
- Anschließend hat Produktionsbuild #225 zunächst den Merge-Stand veröffentlicht; nach der korrekten Versionsanhebung auf 0.4.64 / 94 hat Produktionsbuild #227 die endgültige signierte Release-APK erstellt und veröffentlicht.
- Die in der unabhängigen Claude-Prüfung genannten niedrigen Restrisiken (Fehlerbehandlung bei Upload-Stufen, Recovery eines beschädigten Journals und theoretischer Logical-Clock-Überlauf) wurden nicht zusätzlich behoben und bleiben als mögliche spätere Härtungspunkte bestehen.
- Robolectric-Tests ersetzen keine realen Prozess-Kill- oder Zwei-Smartphone-Tests. Ein Hardware-/Mehrgeräte-Praxistest ist damit nicht bestätigt.
- Updater, Berechtigungen, Installationsablauf und direkter WF-RAC-Steuerweg wurden durch PR #3 nicht verändert.

## Branches außerhalb des Produktionsstands

- `feature/direct-wf-rac-wlan`: Der ursprüngliche PR #1 wurde am 15.11.2026 gemergt; danach folgten weitere 23 Commits. Der Branch liegt **23 ahead / 235 behind** gegenüber `main`, ohne offenen PR. Sein aktueller Diff umfasst u. a. WLAN-Diagnostik, separaten Test-Updatekanal, Workflow-/Build- und App-Dateien. Diese Nachfolgeänderungen sind nicht automatisch in `main` enthalten; vor einer Wiederverwendung einzeln prüfen.
- `docs/status-refresh-2026-11-16`: PR #2 geschlossen und nicht gemergt; **1 ahead / 61 behind**, eine abweichende `STATUS.md`, kein offener PR.
- `docs/status-reconcile-2026-11-15`: **0 ahead / 235 behind**, kein eigener Dateidiff.
- Keine dieser Branches wird automatisch gemergt oder gelöscht. `main` bleibt der Produktionsbranch.

## 2. Letzter Funktionsumfang / UI-Änderungen

- **0.4.54:** Hauptansicht kompakter und an das gewünschte MyAirco-Layout angelehnt; bestehende Steuerungs-Callbacks und Datenflüsse blieben erhalten.
- **0.4.55:** Kühlen-Symbol vereinheitlicht, Power-Symbol statt problematischem Unicode-Zeichen, Luftführungs-Dropdowns und Update-Button angepasst. Updater-Installationsablauf unverändert.
- **0.4.56:** Betriebsmodus-Kacheln gleich hoch und mit identischem Symbolbereich; Schneeflocke verkleinert; Material3-Standardflächen auf helle KlimaPilot-Farben gesetzt.
- **0.4.57:** ausgewählte Flächen auf den Temperaturkarten-Hellblauton gebracht; Kopfzeile außerhalb des Scrollbereichs fixiert; Raum-Wischgeste erhalten.
- **0.4.58:** Cloud-Statussymbol rechts neben dem Zahnrad; abgeschnittene Textanzeige „Verbunden/Offline“ aus der Kopfzeile entfernt.
- **0.4.59:** Cloud-Symbol um 12 dp vom Zahnrad abgesetzt; bei Verbindung grün, offline gedimmt.
- Die lokale Verbindungsanzeige in der Klimaanlagenkarte ist von der Cloud-Anzeige in der Kopfzeile getrennt.
- CI-Builds für diese UI-Änderungen erfolgreich. Ein Nutzer-Endtest der installierten 0.4.59 ist noch nicht bestätigt.

## 3. Produkt- und Technikgrundlagen

- Native Android-App in Kotlin und Jetpack Compose/Material 3.
- Direkte lokale Klimaanlagensteuerung über das WF-RAC-WLAN-Modul; ioBroker ist nicht der direkte Steuerungsweg.
- Optionale ioBroker-WebSocket-Verbindung dient der Klimaprofil-Synchronisierung und ist von der lokalen Gerätekommunikation getrennt.
- WF-RAC-mDNS-Dienst: `_beaver._tcp`, Standardport 51443; gerätespezifische Identität und Zertifikat-Pinning gehören zur Kopplung.
- Zielgerät: Mitsubishi Heavy `SRK15ZS-WF`. Die Klimaanlage ist noch nicht installiert; echte Hardware-/Firmwaretests bleiben offen.
- Adapter `mhi-wfrac` ist noch nicht eingerichtet und für den direkten lokalen Steuerweg nicht erforderlich.
- SENEC/`energyfluss` wird nicht als Datenquelle eingeplant.
- Keine Zugangsdaten, Tokens oder Keystore-Geheimnisse dokumentieren.

## 4. Updater – abgeschlossen, nicht verändern

- Der Updater funktioniert und wurde vom Nutzer erfolgreich getestet. Es gibt keine offene Updater-Aufgabe.
- Bewährter Ablauf: APK herunterladen, Paketnamen prüfen, FileProvider-URI erzeugen und Android-Installationsdialog über `ACTION_VIEW` mit MIME-Typ `application/vnd.android.package-archive`, URI-Berechtigung und `ClipData` öffnen.
- `REQUEST_INSTALL_PACKAGES` nicht wieder einführen.
- Nicht auf `ACTION_INSTALL_PACKAGE` umstellen.
- Keine automatische Updateprüfung wieder einbauen; die manuelle Updatefunktion bleibt bestehen.
- Die UI-Arbeitspakete 0.4.54–0.4.59 haben den Updater-Installationsablauf nicht geändert.

## 5. Softwarestatus und noch offene Prüfungen

Die Codeänderungen sind implementiert und erfolgreich durch CI gebaut. Das ist kein Nachweis vollständiger Fehlerfreiheit unter realen Laufzeitbedingungen.

- **0.4.50 – Konten-Registry:** Schutz vor Überschreiben beschädigter Daten umgesetzt. Laufzeittest mit absichtlich beschädigten SharedPreferences-Daten offen.
- **0.4.52 – Pairing-Reset:** gemeinsame gerätebezogene Mutex-Sperre mit Steuerbefehlen umgesetzt. Praxistest konkurrierender Aktionen offen.
- **0.4.53 M3:** horizontale Luftführung/3D-Automatik nur bei ausdrücklich bestätigter Modellunterstützung. Hardwaretest offen.
- **0.4.53 M4:** Profil wird erst nach erfolgreicher Geräteübertragung als aktiv markiert. Hardware-/Laufzeittest offen.
- **0.4.53 M5:** automatische `wfrac:`-Kennung und Migration ausstehender Outbox-Events bei eindeutiger Zuordnung umgesetzt. Test mit zwei Smartphones und realem ioBroker offen.
- **Hardwareabhängig:** WF-RAC-Kopplung, Befehlsannahme, Firmwarekompatibilität, Messwerte, Klappen-/3D-Funktion und Verbindungsstabilität erst nach Installation prüfbar.
- **0.4.59:** Nutzer-Endtest auf dem Gerät noch nicht bestätigt.
- Der Updater ist ausdrücklich keine offene Aufgabe.

## 6. Letzte Builds

| Run | Version | Ergebnis |
|---|---:|---|
| [#227](https://github.com/Apropo1710/KlimaPilot/actions/runs/44073072643) | **0.4.64 / 94** | **success** – aktueller Produktionsrelease |
| [#226](https://github.com/Apropo1710/KlimaPilot/actions/runs/44073032166) | 0.4.63 / 93 | **success** – Zwischenrelease vor Versionsanhebung |
| [#225](https://github.com/Apropo1710/KlimaPilot/actions/runs/44072272599) | 0.4.63 / 93 | **success** – Merge-Stand |
| #211 | 0.4.59 / 89 | **success** |
| #210 | 0.4.58 / 88 | **success** |
| #209 | 0.4.57 / 87 | **success** |

## 7. Verbindliche Arbeitsregeln

1. Vor jeder Änderung `main`, Gradle-Version, betroffene Dateien und aktuelle Build-/Release-Daten live prüfen.
2. Ausschließlich produktiven Branch `main` bearbeiten.
3. Funktionierende Funktionen gezielt ändern und unnötige Umbauten vermeiden.
4. Updater unangetastet lassen.
5. Hardwarefunktionen und Praxistests nur als bestätigt markieren, wenn sie tatsächlich durchgeführt wurden.
6. Nach App-Code-Änderungen versionName/versionCode erhöhen; reine Dokumentationsänderungen erhöhen keine App-Version.
7. Build, APK-Paket-/Versionsdaten, Signatur und `latest`-Release getrennt kontrollieren.
8. `PROJECT_STATUS.md` ist die zentrale Statusquelle auf GitHub; `STATUS.md` und die ChatGPT-Library-Akten sind synchronisierte Übergabeansichten.

## 8. Neues priorisiertes Architekturvorhaben: Ausfallsichere Synchronisation

**Priorität: Hoch – zeitnah beginnen.** Das Ziel ist, KlimaPilot unabhängig von der Verfügbarkeit von ioBroker nutzbar zu halten und Synchronisationsausfälle ohne Datenverlust zu überstehen.

- Die vollständige Planung, Architekturvorschläge, Phasen, verbindlichen Entscheidungen und Erfolgskriterien stehen in [`SYNC_RESILIENCE_PLAN.md`](SYNC_RESILIENCE_PLAN.md).
- Grundprinzip: Local-first. Jedes Smartphone speichert Profile und Einstellungen dauerhaft lokal und kann sie ohne Synchronisationsserver verwenden.
- Eine Offline-Outbox hält Änderungen bei Verbindungsverlust vor und liefert sie nach Wiederherstellung zuverlässig nach.
- Die Synchronisation wird über eine austauschbare Schicht von der Klimasteuerung und lokalen Datenhaltung getrennt.
- ioBroker bleibt zunächst als bestehender Synchronisationsanbieter erhalten. Eine direkte Smartphone-zu-Smartphone-Synchronisation im selben lokalen WLAN wird als zusätzlicher Fallback umgesetzt. Cloud-, Relay- und Vermittlungsdienste sind ausdrücklich ausgeschlossen.
- Konfliktbehandlung, doppelte Events, Wiederanlauf und Löschungen müssen ausdrücklich berücksichtigt und mit mindestens zwei Smartphones getestet werden.
- Ein absolut ausfallfreies System kann nicht garantiert werden. Ziel ist, dass der Ausfall eines Synchronisationsdienstes weder die lokale Bedienung noch gespeicherte Daten gefährdet.
- Dieses Vorhaben ist keine Erlaubnis, den funktionierenden Updater oder den direkten Klimasteuerungsweg umzubauen.
- Der separate Entwicklungsbranch `feature/local-wifi-profile-sync` wurde am 17.11.2026 frisch vom damaligen `main`-HEAD `c7f1d57e4a4661d1492e5dca42ec9a9841d3e5fd` erstellt. Er enthält zunächst nur die aktualisierte Architektur-/Arbeitsdokumentation; App-Code wurde noch nicht geändert.
- Die technische Bestandsaufnahme und der Sicherheits-/Architekturvorschlag stehen in `LOCAL_WIFI_SYNC_DESIGN.md`. Darin sind Discovery, Peer-Authentifizierung, gemeinsame Sync-Engine, Android-Kompatibilität und die noch offenen Protokollentscheidungen dokumentiert. Es wurde weiterhin kein App-Code geändert.\n\n\n\n\n\n\n\n\n

### Ergänzung zur lokalen WLAN-Synchronisation (17.11.2026)

- Das Discovery-only-Paket wurde fachlich eingegrenzt und in LOCAL_WIFI_SYNC_DESIGN.md, Abschnitt 9, dokumentiert.
- Vorgesehener Diensttyp: _klimapilot-sync._tcp.; der WF-RAC-Dienst _beaver._tcp. bleibt unberührt.
- Phase 1 darf ausschließlich eine kurzlebige Dienstankündigung und lokale Diagnoseinformationen austauschen. Keine Profile, Outbox-Events, Kontodaten, Schlüssel oder ioBroker-Daten.
- mDNS-Ergebnisse gelten ausdrücklich als nicht verifiziert. Es gibt weder automatische Kopplung noch dauerhafte Speicherung entdeckter Geräte.
- Discovery/Advertising sind auf den App-Vordergrund begrenzt und müssen beim Stoppen oder Netzwerkwechsel ihre Ressourcen freigeben.
- Die konkrete kryptografische Peer-Identität und sichere Kopplung bleiben zwingende Voraussetzungen vor jedem späteren Profil-Datenaustausch.
- Implementierungsstand: weiterhin ausschließlich Dokumentation/Architektur; noch keine App-Codeänderung, kein Versionssprung und kein Build. main und der Produktionsrelease bleiben unverändert.

## 9. Lokale Smartphone-Synchronisation – Arbeitsstand 17.11.2026

- Separater Entwicklungsbranch: `feature/local-wifi-profile-sync`; der Branch liegt weiterhin auf dem damaligen `main`-Stand und enthält keine App-Codeänderungen.
- Discovery-only ist als erste technische Phase festgelegt. Profilübertragung, Pairing und Änderungen an Updater/WF-RAC-Steuerung sind ausdrücklich ausgeschlossen.
- Die Android-NSD-API wurde geprüft. Klassische Service-Registrierung ist ab API 16 verfügbar; neuere Executor-/Service-Info-Callbacks sind abhängig von API-/Extension-Stand. `resolveService()` ist seit API 34 deprecated und darf nur als kompatibler Fallback betrachtet werden.
- Die technischen API-Erkenntnisse und die geplante Kompatibilitätsstrategie sind in `LOCAL_WIFI_SYNC_DESIGN.md`, Abschnitt 10, dokumentiert.
- **Aktueller Fortschritt:** Bestandsaufnahme und API-Präzisierung abgeschlossen; Discovery-Implementierung, automatisierte Tests, Claude-Review und Zwei-Smartphone-Test stehen noch aus.
- `main`, Produktionsversion 0.4.64 / versionCode 94, Updater und WF-RAC-Steuerpfad wurden durch diese Dokumentationsänderung nicht verändert.

## 10. Lokale WLAN-Peer-Discovery – Implementierungsstand

- Discovery-Komponente und Diagnoseoberfläche in den Einstellungen auf `feature/local-wifi-profile-sync` hinzugefügt.
- Ausschließlich unbestätigte DNS-SD-Ankündigungen; keine Kopplung, kein Profil-/Konto-/Schlüsselaustausch und keine ioBroker-Datenübertragung.
- TXT-Metadaten werden strikt validiert; fremde Zusatzfelder und unbekannte Protokollversionen werden verworfen.
- TCP-Port dient nur der DNS-SD-Ankündigung. Verbindungen werden sofort geschlossen, ohne Nutzdaten zu lesen oder zu senden.
- Unit-Tests für die Metadatenvalidierung ergänzt. GitHub Actions [WLAN Branch Validation #98](https://github.com/Apropo1710/KlimaPilot/actions/runs/44120322400) auf dem Discovery-Branch: **completed / success**; Unit-Tests und Debug-Kompilierung erfolgreich.
- Ein realer Zwei-Smartphone-Test ist weiterhin nicht durchgeführt/bestätigt.
- Discovery-Branch-Validation ist abgeschlossen. Pairing wird separat in Abschnitt 11 geführt; vor späterem Merge bleiben Code-Review und die dokumentierten Integrationsprüfungen erforderlich.
- Produktionsbranch `main`, Version 0.4.64 / versionCode 94, Updater und WF-RAC-Steuerpfad bleiben unangetastet.


## 11. Pairing-Controller und QR-Integration – CI- und Reviewstand (17.11.2026)

- PR [#4 – Discovery-only](https://github.com/Apropo1710/KlimaPilot/pull/4) ist weiterhin offen und Draft, nicht gemergt. Head: `bf01442b95b16b27a98efc2d095db3aa015f26b1`.
- GitHub Actions [WLAN Branch Validation #98](https://github.com/Apropo1710/KlimaPilot/actions/runs/44120322400) für PR #4: **completed / success**. Unit-Tests und Debug-Kompilierung erfolgreich.
- PR [#5 – Pairing: lokale Bestätigung und Sitzungssteuerung](https://github.com/Apropo1710/KlimaPilot/pull/5) ist offen und Draft, nicht gemergt. Branch: `feature/local-wifi-pairing`; Basis: `feature/local-wifi-profile-sync`.
- Aktueller geprüfter PR-Head: `2657dbb99e65cbaff107737ecd040edcf401bbd4`.
- GitHub Actions [WLAN Branch Validation #122](https://github.com/Apropo1710/KlimaPilot/actions/runs/44126959488) (Push) und [#123](https://github.com/Apropo1710/KlimaPilot/actions/runs/44126971738) (Pull Request) für denselben Head: beide **completed / success**. Unit-Tests und Debug-Kompilierung erfolgreich; Abschlusszeiten 17.11.2026, 17:35:09 bzw. 17:35:53 Uhr MEZ.
- Die früheren Läufe #120 und #121 schlugen wegen falscher Google-Code-Scanner-Importpfade fehl. Die Imports wurden auf `com.google.mlkit.vision.codescanner` korrigiert. Die beiden nachfolgenden vollständigen Läufe #122 und #123 bestätigen den korrigierten Stand.
- PR #5 enthält den signierten Pairing-Session-Controller, Tests, QR-Renderer `ClimatePairingQrCode` auf Basis von ZXing Core und die Compose-Karte `ClimatePairingSettingsCard`.
- Der QR-Scan verwendet Google Code Scanner über Google Play Services (`play-services-code-scanner:16.1.0`); die App fordert dafür keine eigene Kamera-Berechtigung an. Der Manifest-Metadateneintrag ermöglicht den Download des Barcode-Scanner-Moduls durch Google Play Services.
- Der Ablauf unterstützt das Anzeigen einer signierten Einladung, Scannen einer Einladung, Anzeige/Bestätigung des Fingerprints, Erzeugen/Anzeigen der signierten Antwort, Scannen der Antwort und die zweite lokale Fingerprint-Bestätigung. Ein Peer wird jeweils erst nach der lokalen Bestätigung gespeichert.
- Ein QR-Roundtrip-Unit-Test prüft, dass ein erzeugter QR-Code seinen UTF-8-Payload unverändert zurückliefert. CI-Erfolg ist kein Ersatz für einen Praxistest auf zwei realen Smartphones.
- **Noch nicht umgesetzt:** Netzwerktransport, gegenseitiges TLS, automatische Wiedererkennung und Profil-/Outbox-Übertragung. Die QR-Kopplung allein startet keine Synchronisierung.
- Der Pairing-Entscheidungsstand ist in `PAIRING_PROTOCOL_DECISIONS.md` nachgeführt. Der ältere Architektur-/Discovery-Verlauf in `LOCAL_WIFI_SYNC_DESIGN.md` bleibt als chronologische technische Dokumentation erhalten.
- Testeinschränkung: Die Controller-Tests verwenden derzeit denselben `ClimateTrustedPeerStore` für beide simulierten Geräte. Damit wird der Bestätigungsablauf geprüft, aber die getrennte lokale Vertrauensspeicherung zweier realer Smartphones nicht vollständig modelliert. Ein eigener Test mit getrennten Stores und der Praxistest auf zwei Geräten bleiben sinnvoll.
- PR #5 liegt im Vergleich zu `feature/local-wifi-profile-sync` laut letztem PR-Snapshot 11 Commits voraus und 1 Commit zurück (diverged). Der Rückstand ist der Workflow-Commit `bf01442...`; die Workflow-Datei ist in beiden Branches identisch. Vor einer späteren Merge-Freigabe muss die Branch-Basis sauber abgeglichen werden.
- **Nächster Umsetzungsschritt:** TLS-/Android-Keystore-Kompatibilität auf API 26+ separat prüfen. Erst danach dürfen Netzwerktransport und Profil-Sync umgesetzt werden.
- Profilübertragung bleibt gesperrt, bis Pairing und gegenseitiges TLS auf realen Geräten erfolgreich geprüft wurden.
- Produktionsbranch `main`, Produktionsversion **0.4.64 / versionCode 94**, Updater, bestehender ioBroker-Sync und WF-RAC-Steuerpfad wurden nicht verändert. Es wurde keine neue APK erstellt oder veröffentlicht.

## 12. TLS-Kompatibilitätstest – Fehleranalyse und Diagnoseverbesserung (17.11.2026)

- GitHub Actions [Pairing TLS Compatibility #2](https://github.com/Apropo1710/KlimaPilot/actions/runs/44131206603) für Head `f415548b047b5062db528801a7a5f2569d906e10`: **completed / failure**. Beide Matrix-Jobs schlugen fehl; daraus ist noch keine TLS-Kompatibilität bestätigt.
- API 26: Der Instrumentierungstest wurde tatsächlich gestartet (1 Test) und meldete clientseitig `SSLHandshakeException: Connection closed by peer`. Der bisherige Test wartete anschließend auf das Server-Ergebnis, gab dessen eigentliche Ausnahme aber nicht aus. Die Ursache war deshalb im bisherigen Log nicht ausreichend sichtbar.
- API 35: Die Emulatorverbindung war beim Start zeitweise `offline`; später meldete Gradle `Starting 0 tests` und einen abgestürzten Instrumentierungsprozess. Das ist bislang ein Emulator-/Instrumentierungsfehlerbild, kein nachgewiesener TLS-Fehler.
- Diagnosekorrektur auf dem Feature-Branch: Der Instrumentierungstest hängt nun eine mögliche Server-seitige TLS-Ausnahme an den Clientfehler an und setzt ein Socket-Timeout. Dadurch soll ein erneuter API-26-Fehler die tatsächliche Gegenursache offenlegen.
- Workflowkorrektur auf dem Feature-Branch: Emulator-Logcat wird nach dem Instrumentierungslauf unabhängig vom Testergebnis ausgelesen und als Actions-Artefakt `pairing-tls-logcat-api-*` gesichert. Das soll insbesondere den API-35-Prozessabsturz diagnostizierbar machen.
- Diese Änderungen sind reine Test-/CI-Diagnostik. Es wurden weder produktiver Netzwerktransport noch Profilübertragung implementiert.
- **Claude-Review:** derzeit nicht verfügbar (Nutzerlimit; voraussichtlich wieder ab 23:00 Uhr). Der Review bleibt vor dem nächsten größeren Umsetzungsschritt vorgesehen; bis dahin wird nur die Fehlerdiagnose fortgesetzt.
- PR #5 bleibt offen und Draft. `main`, Produktionsversion **0.4.64 / versionCode 94**, Updater, ioBroker-Sync und WF-RAC-Steuerpfad bleiben unverändert. Keine Produktions-APK erstellt oder veröffentlicht.


## 13. TLS-CI-Diagnose – zweiter Emulatorlauf und robuste Artefaktsicherung (17.11.2026)

- GitHub Actions [Pairing TLS Compatibility #10](https://github.com/Apropo1710/KlimaPilot/actions/runs/44134151909) für PR-Head `e6b61401753f0b473cb42bbb1d6d823f3f6056ec`: **completed / failure** auf API 26 und API 35. Der parallele WLAN-Branch-Validation-Lauf #142 war erfolgreich.
- API 26: Der Emulator-/ADB-Schritt scheiterte bereits während der Vorbereitung mit wiederholten ADB-Exitcodes 1; der Gradle-Instrumentierungstest wurde nicht gestartet. Es liegt daher kein TLS-Handshake-Ergebnis für diesen Lauf vor.
- API 35: App und Android-Testpaket wurden erfolgreich kompiliert und installiert. Gradle meldete anschließend `Starting 0 tests` und `Instrumentation run failed due to Process crashed`. Das ist ein Fehler beim Start bzw. Betrieb der Instrumentierung; ein TLS-Test wurde nicht nachweislich ausgeführt.
- Trotz des bisherigen Logcat-Schritts wurden keine Diagnoseartefakte erzeugt. Im Job-Log endete der Emulator-Schritt vor der vorgesehenen Logcat-Sicherung; die separate Upload-Stufe fand deshalb keine Dateien.
- Gezielte CI-Korrektur auf dem Feature-Branch, Commit `3d5179d554c8de30f5eb548825f22bd81c9d0eb8`: Logcat-Sicherung per EXIT-Trap im Emulator-Skript, explizites Warten auf das ADB-Gerät, zusätzliche Host-/ADB-/AVD-Diagnose in einem separaten `if: always()`-Schritt und gemeinsamer Upload von Diagnose- und Android-Testberichten mit sieben Tagen Aufbewahrung.
- Die Änderung betrifft ausschließlich den TLS-Testworkflow. Keine Änderung an TLS-/Pairing-Produktivlogik, Updater, ioBroker-Sync, WF-RAC-Steuerweg oder `main`; keine Versionsanhebung und kein Produktionsbuild.
- **Nächster Schritt:** Den automatisch durch den neuen Branch-Commit ausgelösten TLS-Lauf auswerten. Erst wenn die Instrumentierung tatsächlich startet und Logcat/Testberichte vorliegen, lässt sich ein möglicher TLS-Fehler beurteilen. PR #5 bleibt Draft und nicht zum Merge freigegeben.


## 14. Korrektur des Emulator-Skripts nach TLS-CI-Lauf #12 (17.11.2026)

- GitHub Actions [Pairing TLS Compatibility #12](https://github.com/Apropo1710/KlimaPilot/actions/runs/44141259784) für Commit `3d5179d554c8de30f5eb548825f22bd81c9d0eb8`: beide Matrix-Jobs scheiterten bereits im Emulator-Schritt, bevor Gradle oder der TLS-Instrumentierungstest ausgeführt wurden.
- Konkrete Ursache: `/usr/bin/sh: Syntax error: end of file unexpected (expecting "}")` beim Einlesen der mehrzeiligen Shell-Funktionsdefinition `capture_guest_diagnostics()`. Der Emulator-Runner führte die Script-Zeilen einzeln über `sh -c` aus; die Funktion konnte so nicht definiert werden.
- Die beiden Diagnoseartefakte wurden trotzdem hochgeladen, enthielten aber nur die Host-Diagnose. Darin war `emulator-5554` bei API 26 und API 35 jeweils als `offline` aufgeführt. Es gab in diesem Lauf keine auswertbaren Guest-Logcat-Daten.
- Korrektur auf dem Feature-Branch, Commit `1e28779d5c67f0a4746f2735b16c4fb928bc9a5a`: Emulator-Skript auf einen gefalteten Shell-Einzeiler ohne Funktionsdefinition umgestellt. ADB-Wartezeit ist auf 45 Sekunden begrenzt; bei erfolgreichem Geräte-Start wird der Test ausgeführt und danach Logcat/Emulatorstatus unabhängig vom Testergebnis in die Datei geschrieben. Bei ADB-Timeout wird der Grund ebenfalls protokolliert.
- Diese Änderung betrifft ausschließlich die CI-Diagnose. Keine TLS-/Pairing-Produktivlogik, kein Updater, keine ioBroker-/WF-RAC-Änderung, keine Änderung an `main`, keine Versionserhöhung und kein Produktionsbuild.
- **Nächster Schritt:** Den automatisch ausgelösten Lauf nach Commit `1e28779...` prüfen. Erst danach lässt sich entscheiden, ob API 26/35 tatsächlich den Instrumentierungstest ausführen und ob ein TLS-Fehler vorliegt.


## 15. CI-Auslöser vereinheitlicht und Emulator-Skript vereinfacht (17.11.2026)

- TLS-Lauf #18 (Run-ID `44143464445`, Head `61a76e0ddeccec0b8e419dc8e6009568ad0f814f`) ist abgeschlossen und fehlgeschlagen. API 26 und API 35 erreichten weiterhin keinen Instrumentierungstest. API 26 meldete wiederholt `adb: device offline`; beide Jobs endeten beim Emulator-Skript mit `Syntax error: end of file unexpected (expecting "fi")`, weil der mehrzeilige `if`-Block vom Emulator-Runner zeilenweise über `sh -c` ausgeführt wurde.
- Doppelte CI-Ausführungen wurden beseitigt: In `.github/workflows/pairing-tls-validation.yml` und `.github/workflows/wlan-validation.yml` wurde jeweils der `push`-Trigger entfernt. Pull-Request- und manueller `workflow_dispatch`-Trigger bleiben erhalten. Neue Commits im offenen PR lösen damit weiterhin genau eine PR-Prüfung pro Workflow aus; Push- und PR-Prüfung laufen nicht mehr parallel doppelt.
- Zusätzlich wurde im TLS-Workflow das Emulator-Skript auf eine einzelne physische Shell-Zeile ohne mehrzeilige Kontrollblöcke umgestellt. ADB-Wartezeit bleibt auf 45 Sekunden begrenzt; der Gradle-Test wird nur nach erfolgreichem ADB-Warten gestartet. Geräte-/Logcat-Ausgabe wird anschließend mit tolerierten ADB-Abfragen gesichert.
- Commits: TLS-Trigger `70d24014bbe708a2ece1d0cac05192c54fec5a7c`; WLAN-Trigger `a802795ff9089f8e75facadecf704ea49e0b1f39`; TLS-Shell-Korrektur `7ddd1535884cbca106e525981d622da8d22d1bc2`.
- Änderungen ausschließlich im Feature-Branch `feature/local-wifi-pairing`. `main`, Produktionsversion 0.4.64 / versionCode 94, Updater, ioBroker-Sync und WF-RAC bleiben unverändert. Kein Produktionsbuild und kein Release.
- **Nächster Schritt:** Die durch die PR-Änderungen automatisch ausgelösten Einzelprüfungen kontrollieren. Erst wenn das Emulator-Skript sauber startet und die Logs vollständig sind, TLS-Ergebnis beurteilen. PR #5 bleibt Draft und nicht zum Merge freigegeben.


## WLAN-Pairing – Entwicklungsstand auf `test` (17.11.2026, 21:40 MEZ)

- Produktionsstand bleibt `main` mit dem zuletzt dokumentierten Release 0.4.64 / versionCode 94; dieser Abschnitt dokumentiert ausschließlich den nicht freigegebenen Test-Branch.
- `test`-HEAD nach Dokumentations-/Workflowpflege: `829b57ad34d081f1453c3856a951f43a9d0acab6`; TLS-Codeänderung: `6748bffe9e6f2d000e5729d2dbede3e5f957e1f7`. PR #6 ist offen und Draft, Base `main` `c7f1d57e4a4661d1492e5dca42ec9a9841d3e5fd`.
- WLAN-Validierung ist auf PR-Trigger nach `main` plus manuellen Start umgestellt; zusätzlicher Push-Trigger auf `test` wurde entfernt, damit derselbe Commit nicht doppelt validiert wird.
- mTLS-Clientzertifikatsauthentifizierung wird nicht weiterverfolgt. Neuer Ansatz: Standard-TLS 1.2, Serverzertifikat aus Android Keystore, Client-Pinning gegen den gespeicherten Peer-Public-Key und Clientauthentifizierung per frischer 32-Byte-Challenge plus ECDSA-Signatur. Der Server prüft zusätzlich die lokale Trusted-Peer-Liste.
- Implementiert auf `test`: `ClimatePeerTls.kt`, neuer API-26/API-35-Instrumentierungstest, aktualisierte Pairing-Entscheidungsdokumentation.
- Aktueller CI-Lauf: [Pinned TLS Peer Authentication #?](https://github.com/Apropo1710/KlimaPilot/actions); Run für Head `6748bffe` ist noch nicht abgeschlossen. Keine Testergebnisse vorwegnehmen.
- Noch kein produktiver Peer-Transport, kein Profiltransfer und kein Zwei-Smartphone-Praxistest. Discovery-Socket bleibt reject-only.
- Updater, `main`, Produktionsversion, Manifest/Berechtigungen, ioBroker-Sync und WF-RAC-Steuerpfad wurden in diesem Arbeitsschritt nicht geändert.


## 17. Lokaler Peer-Transport ohne TLS (17.11.2026, abends)

- Nach ausdrücklicher Nutzerentscheidung wird TLS/mTLS nicht weiterverfolgt. Der lokale Peer-Transport soll unverschlüsseltes TCP mit gegenseitiger ECDSA-Geräteauthentifizierung verwenden.
- Neue Klasse `ClimatePeerTcp.kt`: zwei frische 32-Byte-Nonces pro Verbindung, Rollenbindung in den signierten Transkripten, lokale Trusted-Peer-Prüfung auf beiden Seiten, explizite Serverannahme/-ablehnung, begrenzte Handshake-Felder und Socket-Timeout.
- Keystore-Konfiguration auf SHA-256withECDSA zurückgesetzt; TLS-spezifisches DIGEST_NONE entfernt.
- TLS-Hilfsklasse und TLS-Instrumentierungstest entfernt. Neuer API-26/API-35-Instrumentierungstest deckt erfolgreiche gegenseitige Authentifizierung und Ablehnung eines nicht vertrauten Clients ab.
- CI-Workflow auf lokalen TCP-Peer-Test umgestellt; Prüfung erfolgt nur über PR #6 oder manuellen Start, nicht zusätzlich bei jedem Push.
- **Noch nicht umgesetzt:** TCP-Handshake ist noch nicht an den Discovery-Socket angebunden; es gibt noch keine Peer-Profilübertragung und keinen Zwei-Smartphone-Praxistest. Nutzdaten bleiben bis dahin gesperrt.
- Sicherheitsgrenze: TCP ist nicht verschlüsselt. Teilnehmer desselben WLANs können Datenverkehr passiv mitlesen; die Peer-Authentifizierung schützt vor nicht gekoppelten Geräten, nicht vor passivem Mithören.
- PR #6 bleibt Draft. main, Produktionsversion 0.4.64 / versionCode 94, Updater, WF-RAC-Steuerung und ioBroker-Sync bleiben unangetastet. Keine Produktions-APK erstellt oder veröffentlicht.
