# KlimaPilot – verifizierter Projekt- und Produktionsstatus

**Zuletzt abgeglichen:** 16.11.2026, 15:24 Uhr MEZ  
**Repository:** [Apropo1710/KlimaPilot](https://github.com/Apropo1710/KlimaPilot)  
**Maßgeblicher Branch:** `main`  
**Produktionsversion:** **0.4.59 / versionCode 89**

> Dieser Kopfbereich ist der aktuelle, live geprüfte Stand. Ausführliche historische Projektakten liegen in der ChatGPT-Library-Datei `KlimaPilot_Projektstand_aktualisiert.md`. Alte Versionsstände sind keine aktuellen Produktionsangaben.

## 1. Aktueller GitHub-, Build- und Release-Stand

- Repository: `Apropo1710/KlimaPilot`; produktive Arbeit ausschließlich auf `main`.
- `main`-HEAD bei der technischen Vorprüfung vor dem Dokumentationsabgleich: `3291d3786a5c0574b737c04329a98c8600a88b4c`. Die nachfolgenden Commits dieses Abgleichs ändern ausschließlich Status-/README-Dokumentation, keinen App-Code.
- App-Code-/APK-Quellcommit: `058c3cc456beecccaab8b102e6af3d1e6a42017f`.
- App-Version aus `app/build.gradle.kts`: **0.4.59**, `versionCode = 89`.
- Application-ID: `de.klimapilot.app`; minSdk 26; compileSdk/targetSdk 35; Java/Kotlin JVM target 17.
- GitHub Actions **Build #211**, Run-ID `43866655609`: `completed / success`, exakt für den APK-Quellcommit. Abschluss 16.11.2026, 13:29:31 Uhr MEZ.
- Java 17, Android SDK, Gradle 8.9, Keystore-Bereitstellung/-Prüfung, Release-APK-Build, APK-Benennung, Paket-/Versionsprüfung und `apksigner verify --print-certs` waren erfolgreich.
- APK-SHA-256: `18ad8cbaf473a88e7a8ae1da7c28ff95e0fcbfa90b5cf005d40c12d3485116b3`.
- Release `latest`: Asset `KlimaPilot.apk`, Asset-ID `754773422`, 11.370.135 Bytes, aktualisiert 16.11.2026, 13:29:15 Uhr MEZ.
- Git-Tag `latest` zeigt auf den APK-Quellcommit `058c3cc456beecccaab8b102e6af3d1e6a42017f`. `main` enthält danach einen Dokumentationscommit; das ist beabsichtigt.
- APK: https://github.com/Apropo1710/KlimaPilot/releases/download/latest/KlimaPilot.apk
- Actions: https://github.com/Apropo1710/KlimaPilot/actions
- Builds #209 (0.4.57) und #210 (0.4.58) waren ebenfalls erfolgreich. Die Zwischenläufe #207/#208 schlugen fehl; #208 wegen eines fehlenden `Color`-Imports, der vor #209 ergänzt wurde.

<!-- AUTO-PRODUCTION-STATUS:START -->
## Automatisch gepflegter Produktionsstatus

- Quellcommit des APK-Builds: 42472a3af147f45c99f990e55986d8dff02088f2
- App-Version / versionCode: **0.4.63 / 93**
- Build-Zeit: 2026-11-16 21:00:39 UTC
- APK-Dateiname: **KlimaPilot.apk**
- APK-Signaturprüfung: erfolgreich (apksigner verify --print-certs)
- Build-Artefakt: kein Actions-Artefakt; APK wird im GitHub-Release latest veröffentlicht
- APK-SHA-256: 62431eeadeb892cfbe0e14583798a018adb44c2b70344ed31176642d8901003f
- GitHub-Release: **erfolgreich veröffentlicht**; Asset KlimaPilot.apk wurde aktualisiert.
- Smartphone-Test: **nicht durch CI geprüft**; nur separat bestätigte Nutzerberichte gelten als Gerätetest.
- Updater: abgeschlossen und vom Nutzer bestätigt; Installer-Code wird durch diese Dokumentationspflege nicht verändert.
          - ChatGPT-Bibliotheksakte: separate Langzeit-/Historienakte; wird durch diesen Workflow nicht verändert.

<!-- AUTO-PRODUCTION-STATUS:END -->

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
8. `PROJECT_STATUS.md` ist die zentrale Statusquelle auf GitHub; `STATUS.md` und die ChatGPT-Library-Akten sind synchronisierte Übergabeansichten.\n\n\n\n