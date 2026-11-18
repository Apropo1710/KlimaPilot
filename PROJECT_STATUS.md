<!-- AUTO-PRODUCTION-STATUS:START -->
## Automatisch gepflegter Produktionsstatus

- Quellcommit des APK-Builds: 999ce598268ed7e0491e51414d5b9eca43de64cb
- App-Version / versionCode: **0.4.64 / 94**
- Build-Zeit: 2026-11-18 06:15:32 UTC
- APK-Dateiname: **KlimaPilot.apk**
- APK-Signaturprüfung: erfolgreich (apksigner verify --print-certs)
- Build-Artefakt: kein Actions-Artefakt; APK wird im GitHub-Release latest veröffentlicht
- APK-SHA-256: c08bcb47a82599442f75926d98b7d9294b5bde18fb162c540ad7e827fc2764a3
- GitHub-Release: **erfolgreich veröffentlicht**; Asset KlimaPilot.apk wurde aktualisiert.
- Smartphone-Test: **nicht durch CI geprüft**; nur separat bestätigte Nutzerberichte gelten als Gerätetest.
- Updater: abgeschlossen und vom Nutzer bestätigt; Installer-Code wird durch diese Dokumentationspflege nicht verändert.
          - ChatGPT-Bibliotheksakte: separate Langzeit-/Historienakte; wird durch diesen Workflow nicht verändert.

<!-- AUTO-PRODUCTION-STATUS:END -->

# KlimaPilot – verifizierter Projekt- und Produktionsstatus

**Zuletzt abgeglichen:** 18.11.2026, 21:00 Uhr MEZ  
**Repository:** [Apropo1710/KlimaPilot](https://github.com/Apropo1710/KlimaPilot)  
**Maßgeblicher Branch:** `main`  
**Produktionsversion:** **0.4.64 / versionCode 94**

## Aktueller GitHub-, Build- und Release-Stand

- Aktueller `main`-HEAD: `356842b20acbc042a21cb4d06e664dbd45654d98` (Dokumentationscommit vom 17.11.2026, 11:29:12 UTC).
- Letzter funktionaler Merge: PR #3, Merge-Commit `ee65841387824bca7d2b07ca80f71501540af157`; Versionscommit für 0.4.64 / 94: `1a1d7834c22a2ac0726331fc9f49995cc253dc4a`.
- GitHub Actions [Produktionsbuild #229](https://github.com/Apropo1710/KlimaPilot/actions/runs/44073807709): **completed / success** für APK-Build-Quellcommit `a87e0d8e2cfed5e1d2c3399466e1edaef1a66d00`.
- Produktionsversion aus `app/build.gradle.kts`: **0.4.64 / versionCode 94**. Application-ID `de.klimapilot.app`; minSdk 26; compileSdk/targetSdk 35; Java/Kotlin JVM target 17.
- Build-Zeit: 17.11.2026, 11:29:06 UTC.
- Release `latest`: Asset `KlimaPilot.apk`, Asset-ID `758790648`, 11.370.135 Bytes; aktualisiert 17.11.2026, 11:29:11 UTC.
- APK-SHA-256: `1553402f6baebe028d6876581aef235aa3abf89a7047150e4b47a429ef1730e2`; Signaturprüfung im Produktionsworkflow erfolgreich.
- APK: https://github.com/Apropo1710/KlimaPilot/releases/download/latest/KlimaPilot.apk

## WLAN-/Pairing-Testzweig – Stand 18.11.2026

- Produktionsbranch `main`: App-Version **0.4.64 / versionCode 94**, letzter bestätigter Produktionsbuild #229 und Release `latest`. Keine Produktions-App-Änderung durch diese Dokumentationspflege.
- Testbranch: letzter fachlich geprüfter App-Code-Stand `ae1bdc03c78624ff640505b4bd255bfd885b05c5`; danach wurden auf `test` ausschließlich Statusdokumente aktualisiert (Dokumentationscommit `4d3c1fbfdae32bcd22527d1e2408d24dc48def88`).
- PR #6 bleibt offen/Draft und nicht gemergt. `main` bleibt Produktionsquelle.
- Vor den reinen Dokumentationsaktualisierungen betrug der Vergleich `main...test` 151 ahead / 2 behind; Merge-Base `c7f1d57e4a4661d1492e5dca42ec9a9841d3e5fd`. Die Branches sind divergent; die Dokumentationscommits ändern diese fachliche Aussage nicht.
- WLAN-Validierung für App-Code-HEAD `ae1bdc03`: [Run 44248243246](https://github.com/Apropo1710/KlimaPilot/actions/runs/44248243246) erfolgreich (Unit-Tests, Debug-Assembly, `compileReleaseKotlin`).
- TCP-Emulatorlauf [44247356481](https://github.com/Apropo1710/KlimaPilot/actions/runs/44247356481) erfolgreich auf älterem Commit `41b9a3ab`, API 26. Kein Emulatornachweis für `ae1bdc03`; API 35 nicht bestätigt.
- TCP-Workflow ausschließlich manuell (`workflow_dispatch`); WLAN-Validierung über PR-Updates.
- Pairing/QR und TCP-Challenge-Response sind implementiert; TLS/mTLS wurde nach Nutzerfreigabe verworfen. TCP ist unverschlüsselt. Discovery bleibt reject-only, TCP ist nicht integriert, kein Profil-/Outbox-Transfer.
- Vor Nutzdatentransfer zwingend: sichere Discovery-Integration, strikte Empfänger-Validierung und erfolgreicher Zwei-Smartphone-Praxistest. Keine Merge-/Release-Freigabe.
- Debug-Pairing-UI ist durch `BuildConfig.DEBUG` auf die separate Debug-App begrenzt. Updater unverändert.

## Abgeschlossene Profil-Synchronisations-PR

- [PR #3](https://github.com/Apropo1710/KlimaPilot/pull/3) – dauerhaftes Änderungsjournal – **geschlossen und gemergt** am 17.11.2026, 12:14:49 Uhr MEZ.
- Gemergter PR-Head: `c111d1376b14801ec83c0eddbbc42de3dbb35ff0`; Merge-Commit auf `main`: `ee65841387824bca7d2b07ca80f71501540af157`.
- Der aktuelle PR-Head wurde durch [WLAN Branch Validation #48](https://github.com/Apropo1710/KlimaPilot/actions/runs/44071591137) mit erfolgreichen Unit-Tests und Debug-Kompilierung geprüft.
- Anschließend hat Produktionsbuild #225 zunächst den Merge-Stand veröffentlicht; nach der korrekten Versionsanhebung auf 0.4.64 / 94 hat Produktionsbuild #227 die endgültige signierte Release-APK erstellt und veröffentlicht.
- Die in der unabhängigen Claude-Prüfung genannten niedrigen Restrisiken (Fehlerbehandlung bei Upload-Stufen, Recovery eines beschädigten Journals und theoretischer Logical-Clock-Überlauf) wurden nicht zusätzlich behoben und bleiben als mögliche spätere Härtungspunkte bestehen.
- Robolectric-Tests ersetzen keine realen Prozess-Kill- oder Zwei-Smartphone-Tests. Ein Hardware-/Mehrgeräte-Praxistest ist damit nicht bestätigt.
- Updater, Berechtigungen, Installationsablauf und direkter WF-RAC-Steuerweg wurden durch PR #3 nicht verändert.

## Branches außerhalb des Produktionsstands

- `test`: [PR #6](https://github.com/Apropo1710/KlimaPilot/pull/6) offen und Draft, **139 ahead / 0 behind**; Head `5175c47d3eb743fdb31a50e9d8c674b17dd2ff00`. WLAN Branch Validation #190 / Run `44193818224` und Local TCP Peer Authentication #51 / Run `44193818323` sind für exakt diesen Head erfolgreich. Der vorherige Fehler #50 betraf den älteren Commit `982a6e25` und ist durch den späteren erfolgreichen Stand abgelöst.
- `feature/local-wifi-pairing`: **101 ahead / 0 behind**, kein eigener offener PR; aktueller Head `de214bca54195534b3b14735242052ba05046886`.
- `feature/local-wifi-profile-sync`: **58 ahead / 0 behind**, kein eigener offener PR; aktueller Head `f2ff6072e38d94ae6faf93bc06d78be9de415a1f`.
- Die drei WLAN-/Pairing-Branches sind getrennte Arbeitsstände. Keine automatische Übernahme oder Zusammenführung.
- `feature/direct-wf-rac-wlan`: PR #1 am 15.11.2026 gemergt; danach 23 weitere Commits. Aktueller Vergleich **23 ahead / 316 behind**, kein offener PR. Der Diff enthält WLAN-Diagnostik, separaten Test-Updatekanal sowie Änderungen an Build-/App-Dateien und ist nicht Bestandteil von `main`.
- `fix/durable-profile-change-journal`: PR #3 gemergt; **0 ahead / 15 behind**, keine zusätzlichen Commits gegenüber `main`.
- `docs/status-refresh-2026-11-16`: PR #2 geschlossen, nicht gemergt; abweichende Statusdatei, nicht ungeprüft übernehmen.
- `docs/status-reconcile-2026-11-15`: historischer Dokumentationsbranch ohne offenen PR.
- Keine Nebenbranch wird automatisch gemergt oder gelöscht. `main` bleibt der Produktionsbranch.

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
- **0.4.64:** Nutzer-Endtest der aktuellen Release-APK auf dem Gerät noch nicht bestätigt.
- Der Updater ist ausdrücklich keine offene Aufgabe.

## 6. Letzte Builds

| Run | Version | Ergebnis |
|---|---:|---|
| [#229](https://github.com/Apropo1710/KlimaPilot/actions/runs/44073807709) | **0.4.64 / 94** | **success** – aktueller Produktionsrelease |
| [#227](https://github.com/Apropo1710/KlimaPilot/actions/runs/44073072643) | 0.4.64 / 94 | success – vorheriger Produktionslauf |
| [#226](https://github.com/Apropo1710/KlimaPilot/actions/runs/44073032166) | 0.4.63 / 93 | **success** – Zwischenrelease vor Versionsanhebung |
| [#225](https://github.com/Apropo1710/KlimaPilot/actions/runs/44072272599) | 0.4.63 / 93 | **success** – Merge-Stand |
| #211 | 0.4.59 / 89 | **success** |
| #210 | 0.4.58 / 88 | **success** |
| #209 | 0.4.57 / 87 | **success** |


## Aktualisierung WLAN-/Pairing-Arbeitsstand (18.11.2026)

- Aktueller Produktionsbranch `main`: `356842b20acbc042a21cb4d06e664dbd45654d98`. Die vorherige Kopfzeile mit `c7f1d57...` war veraltet.
- Aktueller Testbranch `test`: `ae1bdc03c78624ff640505b4bd255bfd885b05c5`.
- Vergleich `main...test`: **151 Commits voraus / 2 zurück**, Merge-Base `c7f1d57e4a4661d1492e5dca42ec9a9841d3e5fd`. Die beiden nachfolgenden Main-Commits sind Dokumentationsänderungen. Branches bleiben divergent; kein Rebase/Merge ohne gesonderte Prüfung und Freigabe.
- [PR #6](https://github.com/Apropo1710/KlimaPilot/pull/6) ist offen und Draft, nicht gemergt. `main` bleibt unverändert und alleiniger Produktionsbranch.
- Letzter Testbranch-Commit: `ae1bdc03`, Workflow-Steuerung: WLAN-Validierung läuft über PR-Updates; TCP-Emulatorvalidierung ausschließlich manuell (`workflow_dispatch`), damit PR-Updates keine doppelten/alten Emulatorläufe starten.
- WLAN-Validierung für `ae1bdc03`: [Run 44248243246](https://github.com/Apropo1710/KlimaPilot/actions/runs/44248243246) **completed / success** (Unit-Tests, Debug-Assembly und `compileReleaseKotlin`).
- Letzter erfolgreicher TCP-Emulatorlauf: [Run 44247356481](https://github.com/Apropo1710/KlimaPilot/actions/runs/44247356481) auf älterem Commit `41b9a3ab513e7d48daa09ee21298fe718748363b`; API 26 erfolgreich. Daraus folgt kein Emulatornachweis für `ae1bdc03`. API 35 bleibt manuell/ohne bestätigten Erfolg.
- Implementiert auf `test`: QR-basiertes beidseitig bestätigtes Pairing, Android-Keystore-Identität und gegenseitiger TCP-Challenge-Response-Handshake mit ECDSA, Nonces, Rollen-/Peer-ID-Bindung, Zeit-/Feldlimits sowie JVM-Negativtests.
- TLS/mTLS wurde nach Nutzerfreigabe verworfen. TCP ist **nicht verschlüsselt**: lokaler Netzwerkverkehr kann passiv mitgelesen werden; die Authentifizierung verhindert außerdem nicht jede aktive Manipulation. Vor Nutzdatentransfer sind strikte Empfänger-Validierung und ein Zwei-Smartphone-Test zwingend.
- Discovery-Server ist noch reject-only; `ClimatePeerTcp` ist noch nicht in Discovery integriert. Es findet **keine Profil-/Outbox-Übertragung** statt. Keine Sync-Freigabe.
- Die WLAN-/Pairing-Oberfläche ist durch `BuildConfig.DEBUG` auf die separate Debug-App mit Application-ID-Suffix `.debug` beschränkt. Release-UI bleibt davon getrennt. Updater liegt außerhalb des Debug-Guards und wurde nicht geändert.
- Nächste Schritte: fachliche Prüfung der Discovery-/Session-/Trust-Integration und Sicherheitsgrenzen; danach erst ein kleines Integrationspaket planen. Kein Merge und kein Produktionsbuild im Rahmen dieses Testzweigs.


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
- ioBroker bleibt zunächst als bestehender Synchronisationsanbieter erhalten. Eine unabhängige Cloud oder direkte Smartphone-Synchronisation kann später ergänzend hinzukommen.
- Konfliktbehandlung, doppelte Events, Wiederanlauf und Löschungen müssen ausdrücklich berücksichtigt und mit mindestens zwei Smartphones getestet werden.
- Ein absolut ausfallfreies System kann nicht garantiert werden. Ziel ist, dass der Ausfall eines Synchronisationsdienstes weder die lokale Bedienung noch gespeicherte Daten gefährdet.
- Dieses Vorhaben ist keine Erlaubnis, den funktionierenden Updater oder den direkten Klimasteuerungsweg umzubauen.\n\n\n\n\n\n\n\n\n\n