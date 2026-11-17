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
