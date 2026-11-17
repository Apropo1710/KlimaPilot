# KlimaPilot – verifizierter Projekt- und Produktionsstatus

**Zuletzt abgeglichen:** 17.11.2026, 02:56 Uhr MEZ  
**Repository:** [Apropo1710/KlimaPilot](https://github.com/Apropo1710/KlimaPilot)  
**Maßgeblicher Branch:** `main`  
**Produktionsversion:** **0.4.63 / versionCode 93**

## Aktueller GitHub-, Build- und Release-Stand

- `main`-HEAD vor dieser Statuspflege: `cd26cc24659c103e0ce1f2cccc8467a06050b9b9` (Dokumentationscommit).
- Letzter App-Code-Commit: `42472a3af147`; die vorherige Funktionsänderung `f670b9a8c252` erhält und synchronisiert individuelle Klimaeinstellungen.
- APK-Build-Quellcommit: `c7a1c6a458f0c44533d08672db5592a20748bfc6` (Dokumentationscommit; der App-Code-Baum entspricht dem oben genannten App-Code-Stand).
- Version aus `app/build.gradle.kts`: **0.4.63 / 93**. Application-ID `de.klimapilot.app`; minSdk 26; compileSdk/targetSdk 35; Java/Kotlin JVM target 17.
- GitHub Actions [Build #222](https://github.com/Apropo1710/KlimaPilot/actions/runs/43957989783): **completed / success**, exakt für den APK-Build-Quellcommit.
- Release `latest`: Asset `KlimaPilot.apk`, Asset-ID `756488225`, 11.370.139 Bytes; aktualisiert 16.11.2026, 21:19:22 UTC.
- APK-SHA-256: `48e876e988186d3021d79364bd3405400c5a98a7168332b5d4c0d221f474f52f`; Signaturprüfung laut Produktionsworkflow erfolgreich.
- Git-Tag `latest` zeigt auf `c7a1c6a458f0c44533d08672db5592a20748bfc6`; `main` enthält danach nur weitere Dokumentationscommits.
- APK: https://github.com/Apropo1710/KlimaPilot/releases/download/latest/KlimaPilot.apk

## Offener Synchronisations-PR

- [PR #3](https://github.com/Apropo1710/KlimaPilot/pull/3) – dauerhaftes Änderungsjournal – ist offen und Draft; Head `ad84fe8d3cc7`. Die jüngste GitHub-Abfrage lieferte für die Mergeability `unknown` (eine frühere Abfrage meldete `clean`); daher derzeit keine abschließende Konfliktbewertung.
- Die PR-Validierung #15 / Run `43964905903` war **completed / success**. Der PR-Stand ist noch nicht gemergt und nicht Teil des Releases 0.4.63.

<!-- AUTO-PRODUCTION-STATUS:START -->
## Automatisch gepflegter Produktionsstatus

- Quellcommit des APK-Builds: 6e73d53edb3bc73b8bf78013917b25bb5104581d
- App-Version / versionCode: **0.4.63 / 93**
- Build-Zeit: 2026-11-17 08:18:59 UTC
- APK-Dateiname: **KlimaPilot.apk**
- APK-Signaturprüfung: erfolgreich (apksigner verify --print-certs)
- Build-Artefakt: kein Actions-Artefakt; APK wird im GitHub-Release latest veröffentlicht
- APK-SHA-256: 6ffd87190d19a214ba2bb841e7622c8ad1f346eed7adf9932497b41aa58bd114
- GitHub-Release: **erfolgreich veröffentlicht**; Asset KlimaPilot.apk wurde aktualisiert.
- Smartphone-Test: **nicht durch CI geprüft**; nur separat bestätigte Nutzerberichte gelten als Gerätetest.
- Updater: abgeschlossen und vom Nutzer bestätigt; Installer-Code wird durch diese Dokumentationspflege nicht verändert.
          - ChatGPT-Bibliotheksakte: separate Langzeit-/Historienakte; wird durch diesen Workflow nicht verändert.

<!-- AUTO-PRODUCTION-STATUS:END -->

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
| #211 | 0.4.59 / 89 | **success** – aktueller Release |
| #210 | 0.4.58 / 88 | **success** |
| #209 | 0.4.57 / 87 | **success** – fehlender Color-Import korrigiert |
| #208 | 0.4.57 Zwischenstand | failure – fehlender Color-Import |
| #207 | 0.4.57 Zwischenstand | failure – Zwischenstand |
| #206 | 0.4.56 / 86 | **success** |

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
- Dieses Vorhaben ist keine Erlaubnis, den funktionierenden Updater oder den direkten Klimasteuerungsweg umzubauen.\n\n\n