<!-- AUTO-PRODUCTION-STATUS:START -->
## Automatisch gepflegter Produktionsstatus

- Quellcommit des APK-Builds: bb7d4e764d71b87803feaba404843b9113e61b49
- App-Version / versionCode: **0.4.43 / 73**
- Build-Zeit: 2026-11-15 16:05:35 UTC
- APK-Dateiname: **KlimaPilot.apk**
- APK-Signaturprüfung: erfolgreich (apksigner verify --print-certs)
- Build-Artefakt: kein Actions-Artefakt; APK wird im GitHub-Release latest veröffentlicht
- APK-SHA-256: 45194ba55d0e8148b3beee3f4b397bc11d5f178c1a7b6dffdd7b4b90fd4cd308
- GitHub-Release: Veröffentlichung wird in diesem Workflow nach dem Statuscommit abgeschlossen.
- Smartphone-Test: **nicht durch CI geprüft**; nur separat bestätigte Nutzerberichte gelten als Gerätetest.
- Updater: abgeschlossen und vom Nutzer bestätigt; Installer-Code wird durch diese Dokumentationspflege nicht verändert.
          - ChatGPT-Bibliotheksakte: separate Langzeit-/Historienakte; wird durch diesen Workflow nicht verändert.

<!-- AUTO-PRODUCTION-STATUS:END -->

# KlimaPilot – Projektstatus und technische Dokumentation

**Dokumentationsstand:** 15.11.2026, 17:02 Uhr MEZ  
**Repository:** [Apropo1710/KlimaPilot](https://github.com/Apropo1710/KlimaPilot)  
**Maßgeblicher Branch:** `main`

## 1. Maßgeblicher Stand

- Vor der Dokumentationskorrektur geprüfter `main`-Head: `7d6935712137bc3ddef58820292882fb9b893eba`.
- Dokumentationskorrektur: Commit `c947416add05e134c63c367298e0556db462b950` (15.11.2026, 16:40 Uhr MEZ).
- Letzter erfolgreich veröffentlichter APK-Quellcommit: `c947416add05e134c63c367298e0556db462b950` (Version **0.4.42 / versionCode 72**).
- Aktueller App-Code-Stand auf `main`: `bb7d4e764d71b87803feaba404843b9113e61b49`, vorgesehen als **0.4.43 / versionCode 73**; Produktionsbuild und Veröffentlichung sind noch nicht abgeschlossen.
- Der automatisch gepflegte Nachweis oben dokumentiert den letzten erfolgreichen APK-Build, die Signaturprüfung und die Aktualisierung des Releases `latest`.
- Ein Dokumentationscommit verändert weder App-Version noch APK. Versionsnummer und versionCode werden nur bei App-Code-Änderungen erhöht.

Vor jeder Entwicklungsarbeit müssen der aktuelle Stand von `main` und alle betroffenen Dateien erneut eingelesen werden. Diese Datei ist eine Orientierung und ersetzt die Quellcodeprüfung nicht.

## 2. Technische Basis und feste Leitplanken

- Android-App mit Kotlin und Jetpack Compose.
- Application-ID: `de.klimapilot.app`; Mindestversion API 26; Compile-/Target-SDK 35; Java 17.
- ioBroker-Verbindung über WebSocket (`ws.0`, Standardport 8084), mit Authentifizierung, Datenpunkt-Abonnements und Reconnect.
- Die Hauptverbindung gilt erst nach erfolgreicher Prüfung von `system.adapter.admin.0.alive` als verbunden.
- Der Adapter `mhi-wfrac` ist noch nicht eingerichtet. Reale Datenpunktzuordnungen und Steuerfunktionen werden erst mit installierter Klimaanlage verifiziert.
- SENEC/`energyfluss` wird nicht als Datenquelle eingeplant.
- Keine Zugangsdaten, Tokens, Keystore-Passwörter oder privaten Schlüssel dokumentieren.

## 3. Änderung 15.11.2026 – Profil-Synchronisierung und kompaktere Oberfläche

- Beim Löschen eines Profils wird zuerst die lokale Löschung dauerhaft gespeichert. Erst danach wird das Löschereignis in der Synchronisierungs-Outbox vorgemerkt. Damit kann ein fehlgeschlagenes lokales Löschen nicht mehr versehentlich eine Löschung auf anderen Smartphones auslösen.
- Die Profilansicht startet keinen zusätzlichen Synchronisierungslauf mehr allein deshalb, weil sich die Anzahl der Profile durch einen empfangenen Remote-Stand geändert hat. Der Abgleich läuft beim Öffnen, nach lokalen Änderungen und weiterhin im 60-Sekunden-Rhythmus.
- Die Klimaprofil-Kachel wurde kompakter gestaltet; die Geräte-Unterzeile entfällt. Erfolgs- und Fehlermeldungen werden farblich unterschieden.
- Der Downloadknopf „Update laden“ erhält eine feste Mindesthöhe und nutzt die volle verfügbare Kartenbreite, damit seine Beschriftung nicht abgeschnitten wird.
- Aktueller Quellstand: `bb7d4e764d71b87803feaba404843b9113e61b49`. App-Version vorgesehen: **0.4.43 / versionCode 73**.
- GitHub Actions: Run **#166** (Zwischenstand vor dem Versionssprung) war beim Dokumentationsupdate noch in Bearbeitung; Run **#170** für den aktuellen Quellstand war vorgemerkt. Der Build, die APK-Signatur und das Release `latest` sind daher noch nicht als erfolgreich bestätigt.
- Kein Praxistest der Synchronisierung mit zwei Smartphones oder realem ioBroker durchgeführt. Dieser bleibt ausdrücklich offen.

## 4. Aktuell vorhandene App-Funktionen

### Benutzerkonten und Einstellungen
- KlimaPilot besitzt ein verpflichtendes Benutzerkonto-/Anmeldemodell mit den Rollen `admin` und `user`.
- Kontodaten liegen im ioBroker-Namensraum `0_userdata.0.KlimaPilot.accounts`; Passwörter werden als PBKDF2-HMAC-SHA256-Hashes mit individuellen Salts gespeichert.
- Administratoren verwalten Konten, Räume, Geräte und ioBroker-Verbindungseinstellungen. Benutzer können die Klimaübersicht bedienen, ihr eigenes Passwort ändern und den Benutzer wechseln.
- Die Schriftgröße wird als `fontScale` im jeweiligen Benutzerkonto gespeichert. Verfügbare Stufen: 85 %, 100 %, 115 %, 130 % und 150 %; Standardwert ist 100 %.
- Die Einstellungen besitzen eine fixierte Kopfzeile; die darunterliegenden Inhalte scrollen.

### Klimaprofile
- Profile werden lokal auf dem Gerät gespeichert und können erstellt, bearbeitet/umbenannt und gelöscht werden.
- Profil-Schema 2 unterstützt Ein/Aus, Betriebsmodus, Solltemperatur, Lüftergeschwindigkeit, vertikale und horizontale Luftführung sowie 3D-Automatik. Optionale, nicht ausgewählte Werte bleiben leer.
- Temperaturauflösung: 0,5 °C. Die Profilwerte orientieren sich an vorhandenen WF-RAC-Protokolltypen; die tatsächliche Zuordnung und Schreibbarkeit sind erst an der installierten Anlage zu verifizieren.
- Die Profile dürfen nicht mit einer bereits freigegebenen Klimaanlagen-Steuerung verwechselt werden. Profil-Anwendung/Schreibzugriffe bleiben bis zur Hardwareprüfung gesperrt.

### Automatische Profil-Synchronisierung – implementiert, Praxistest offen
- Geräte können eine gemeinsame `syncDeviceKey` erhalten. Nur Geräte mit identischer, auf jedem Smartphone eindeutiger Kennung werden einander zugeordnet.
- `ClimateProfileSyncStore` speichert eine dauerhafte lokale Outbox unter `klimapilot_profile_sync`. Ereignisse enthalten Installations-ID, Sequenz, logischen Zähler, Ereignis-ID, Gerätekennung und Profil-Snapshot oder Löschereignis.
- Bestehende Profile werden beim erstmaligen Einrichten der gemeinsamen Gerätekennung als Initialbestand vorgemerkt. Änderungen und Löschungen werden ebenfalls lokal vorgemerkt.
- Outbox-Einträge werden erst nach ioBroker-Schreibbestätigung entfernt; nicht bestätigte Übertragungen bleiben für Wiederholungen erhalten.
- `ClimateProfileSyncClient` verwendet eine separate OkHttp-WebSocket-Verbindung. Sie authentifiziert sich eigenständig und prüft `system.adapter.admin.0.alive`; die Hauptverbindung der App wird nicht getrennt oder umkonfiguriert.
- Vorgesehener ioBroker-Namensraum: `0_userdata.0.KlimaPilot.profileSync.devices.<installationId>.<sequence>`. Ereignisse werden als JSON-String-States geschrieben und über `getStates` gelesen.
- Remote-Ereignisse werden anhand der gemeinsamen Gerätekennung zugeordnet. Mehrdeutige Gerätekennungen führen zum Überspringen statt zu einer unsicheren Zuordnung.
- Konfliktordnung: logischer Zähler, danach Installations-ID und Sequenznummer als deterministische Tie-Breaker. Remote-Löschungen werden als Tombstones verarbeitet.
- Der Abgleich wird beim Öffnen der Profilansicht und danach alle 60 Sekunden angestoßen, solange die Ansicht aktiv ist.
- **Nicht als produktiv bestätigt:** erfolgreicher Live-Abgleich mit realen ioBroker-Rechten und zwei Smartphones. Insbesondere das tatsächliche Verhalten von `getStates` für den verwendeten Namespace sowie Upload, Empfang, Konfliktfälle und Löschungen müssen praktisch geprüft werden. Bis dahin ist die Synchronisierung als implementiert, aber ungetestet zu kennzeichnen.

## 5. Updater – abgeschlossen, nicht verändern

Der Nutzer hat den KlimaPilot-Updater erfolgreich auf dem Gerät getestet. Der bewährte Installationsablauf bleibt erhalten:

1. APK herunterladen.
2. Paketnamen prüfen.
3. FileProvider-URI erzeugen.
4. Android-Installationsdialog über `ACTION_VIEW` öffnen.
5. MIME-Typ `application/vnd.android.package-archive` verwenden.
6. URI-Leseberechtigung und `ClipData` mitgeben.

Nicht wieder einführen: `REQUEST_INSTALL_PACKAGES`; nicht auf `ACTION_INSTALL_PACKAGE` umstellen. Keine automatische Updateprüfung neu einbauen. Die manuelle Updatefunktion bleibt bestehen. Der Updater-Code darf bei anderen Arbeiten nicht beiläufig verändert werden.

## 6. Build und Veröffentlichung

- Workflow: `.github/workflows/android.yml`.
- Pushes auf `main` starten den Produktionsbuild automatisch.
- Veröffentlichungen sind über `concurrency.group: klimapilot-release-main` serialisiert; `cancel-in-progress: false` muss erhalten bleiben.
- Der Workflow baut die Release-APK mit Java 17 und dem geschützten Release-Keystore, prüft Paket-ID/Version und APK-Signatur und aktualisiert anschließend das Release `latest`.
- Bei künftigen Änderungen Buildlauf, Quellcommit, APK-Signatur, Release-Tag und Release-Asset getrennt prüfen und dokumentieren.
- Direkter Download (nur nach erneuter Release-Prüfung als aktuell bezeichnen): https://github.com/Apropo1710/KlimaPilot/releases/download/latest/KlimaPilot.apk
- GitHub Actions: https://github.com/Apropo1710/KlimaPilot/actions

## 7. Offene Prüfungen

1. Profil-Synchronisierung mit zwei realen Installationen und ioBroker testen: erstmaliger Profilbestand, neue/geänderte Profile, Löschungen, Wiederholungen nach Verbindungsabbruch und Konflikte.
2. Prüfen, ob ioBroker die benötigten Objekte/States im vorgesehenen Namespace mit den tatsächlichen Rechten korrekt anlegt, schreibt und über `getStates` zurückliefert.
3. Nach Installation der Klimaanlage `mhi-wfrac` einrichten und alle realen Datenpunkte, Statuswerte und Steuerbefehle verifizieren.
4. Erst nach erfolgreicher Hardwareprüfung entscheiden, welche Profilwerte sicher angewendet werden können.

## 8. Verbindliche Arbeitsweise

- Ausschließlich die produktive Hauptversion auf `main` weiterentwickeln.
- Vor Änderungen `main` und die vollständigen betroffenen Dateien prüfen.
- Bestehende Funktionen und Abhängigkeiten berücksichtigen; gezielte Änderungen statt unnötiger Neuaufbauten.
- Keine nicht verifizierten Datenpunkte, Hardwarefunktionen oder Testergebnisse behaupten.
- Nach App-Code-Änderungen Version/versionCode erhöhen, Änderungen pushen und den automatischen Build kontrollieren.
- Build-Erfolg, APK-Signatur, Release-Aktualisierung und Smartphone-Test sind getrennte Nachweise.
- Zeitangaben in deutscher Ortszeit mit MEZ/MESZ dokumentieren.
- Diese Datei nach jedem abgeschlossenen Arbeitspaket aktualisieren. Veraltete Versionsstände gehören allenfalls in eine ausdrücklich als Historie gekennzeichnete Chronik, nicht in den aktuellen Projektstatus.\n\n\n