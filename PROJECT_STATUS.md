<!-- AUTO-PRODUCTION-STATUS:START -->
## Automatisch gepflegter Produktionsstatus

- Quellcommit des APK-Builds: 516b73a18946e2939b327174d30f28cbd318b271
- App-Version / versionCode: **0.4.42 / 72**
- Build-Zeit: 2026-11-15 14:49:24 UTC
- APK-Dateiname: **KlimaPilot.apk**
- APK-Signaturprüfung: erfolgreich (apksigner verify --print-certs)
- Build-Artefakt: kein Actions-Artefakt; APK wird im GitHub-Release latest veröffentlicht
- APK-SHA-256: 17483ab1b1bac69e2270ac7df0d2cc8e2ccf770d48e49c4f3877b7ee52f3ec54
- GitHub-Release: **erfolgreich veröffentlicht**; Asset KlimaPilot.apk wurde aktualisiert.
- Smartphone-Test: **nicht durch CI geprüft**; nur separat bestätigte Nutzerberichte gelten als Gerätetest.
- Updater: abgeschlossen und vom Nutzer bestätigt; Installer-Code wird durch diese Dokumentationspflege nicht verändert.
          - ChatGPT-Bibliotheksakte: separate Langzeit-/Historienakte; wird durch diesen Workflow nicht verändert.

<!-- AUTO-PRODUCTION-STATUS:END -->

## Verbindliche Aktualisierung – 15.11.2026
- Vor dieser Statusaktualisierung geprüfter `main`-Head: `d714516abe7cf5b1f27533cdd81c332e2dd9989d` (Dokumentationscommit nach erfolgreicher Veröffentlichung).
- Letzter App-Code-Commit: `485d274fbb5e10d321f76a72033d6e108ce1a2c5`; Produktionsversion **0.4.28 / versionCode 58**.
- Die Schriftgröße ist als `fontScale` im jeweiligen ioBroker-Benutzerkonto gespeichert. Verfügbar sind 85 %, 100 %, 115 %, 130 % und 150 %; neue und bestehende Konten ohne gespeicherten Wert verwenden 100 %.
- APK-Build-Quellcommit: `bd20181e86972f390901d65c442996bc3cf109d1`; GitHub Actions Run `43666033340`: **completed / success**. Der vorherige Dokumentationsbuild Run `43666030742` war ebenfalls erfolgreich.
- Release `latest` enthält `KlimaPilot.apk`, SHA-256 `0d3f44fda8d003a92268644048db1a851f7fbd7ab8c30468f4a7d6b6f55b31c2`; Signaturprüfung laut automatisch gepflegtem Produktionsnachweis erfolgreich.
- Ein Smartphone-Test der neuen Schriftgrößenfunktion ist noch nicht als Nutzerbestätigung dokumentiert.
- Klimaprofile sind als nächster Funktionsumfang vorgemerkt, aber noch nicht umgesetzt. Die verfügbaren Profileinstellungen und deren Schreibbarkeit müssen nach Installation mit den realen Mitsubishi-/ioBroker-Datenpunkten festgelegt werden.
- Der Updater bleibt unverändert und vom Nutzer bestätigt. Frühere Angaben zu Build-SHA `5548607c1689202ba0f5cd4f18b67a56a9077bbb3052a9f6d92a6e53ad23eb08` und `2ed2e26b1eb9d93f27ccbcee0764af16935ed03c46f5acec98b3b8ac9a4afe14` sind überholte Zwischenstände derselben Version 0.4.28.

## Änderung 15.11.2026 – ioBroker-Verbindung für Profil-Synchronisierung

- Einstellungen für ioBroker ergänzt: vorausgefüllte Adresse **192.168.178.54**, Port **8084**, Protokoll **ws://**. IP-Adresse/Hostname, Port und Protokoll sind änderbar.
- Bestehende Einstellung `iobroker_server` wird beim ersten Öffnen berücksichtigt; beim Speichern wird sie kompatibel mit der neuen Konfiguration aktualisiert.
- Verbindungstest verwendet die bestehende WebSocket-Authentifizierung und prüft `system.adapter.admin.0.alive`; danach wird die Testverbindung wieder getrennt.
- Manuelle JSON-Export-/Importfunktion aus der Profilansicht entfernt. Die lokale Profilablage bleibt bestehen.
- Version **0.4.38 / versionCode 68**. Automatische Profil-Synchronisierung ist damit noch nicht implementiert; dies ist die vorbereitende Verbindungskonfiguration.

# KlimaPilot – Projektstatus

Stand: 31.10.2026, 22:26 Uhr (MEZ)  
Repository: [Apropo1710/KlimaPilot](https://github.com/Apropo1710/KlimaPilot)  
Produktions- und Entwicklungsbranch: `main`

## Quelle der Wahrheit

Der jeweils aktuelle Code auf GitHub ist maßgeblich. Vor jeder Änderung muss der aktuelle Stand von `main` gelesen werden. Diese Datei dokumentiert Entscheidungen und Arbeitsstand, ersetzt aber niemals die Prüfung des aktuellen Quellcodes.

## Verbindliche Entwicklungsstrategie

**Die weitere KlimaPilot-Entwicklung erfolgt direkt auf `main` und ist immer für die produktive Hauptversion bestimmt.**

- Keine separaten Testversionen und keine neuen Test-Branches für die normale Weiterentwicklung.
- Vor jeder Änderung den aktuellen Stand von `main` sowie die betroffenen Dateien lesen.
- Änderungen gezielt und nachvollziehbar umsetzen; bestehende Funktionen nicht ohne Grund verändern.
- Zusammengehörige Änderungen bündeln und vor dem Push den Diff prüfen.
- Nach einem App-Code-Push den automatisch ausgelösten Produktionsbuild kontrollieren.
- APK, Signatur, Release-Tag `latest` und Asset prüfen, bevor ein Downloadlink als aktuell ausgegeben wird.
- Dokumentationsänderungen sind keine App-Version: `versionName` und `versionCode` nur bei tatsächlichen App-Code-Änderungen erhöhen.

## Aktueller Produktionsstand

Die verbindlichen aktuellen Build-, Signatur-, Artefakt- und Release-Daten stehen im automatisch gepflegten Produktionsstatus am Dokumentanfang. Ältere Einzelstände werden nicht als aktueller Produktionsstand fortgeführt.

## Build und Veröffentlichung

Die verbindlichen aktuellen Build-, Signatur-, Artefakt- und Release-Daten stehen im automatisch gepflegten Produktionsstatus am Dokumentanfang. Ältere Einzelstände werden nicht als aktueller Produktionsstand fortgeführt.

## Umgesetzter und bestätigter Stand

- Native Android-App mit Kotlin und Jetpack Compose.
- ioBroker-Anbindung über WebSocket `ws.0`, Port 8084.
- Authentifizierung, Datenpunkt-Abonnements und automatischer Reconnect sind Bestandteil der Verbindung.
- Die App gilt erst nach erfolgreicher Alive-Prüfung über `system.adapter.admin.0.alive` als verbunden.
- Der Nutzer hat Version 0.4.21 installiert und den Updateablauf anschließend erfolgreich getestet. Der Nutzer bestätigte ausdrücklich, dass der Updater funktioniert.
- **Updater-Status: abgeschlossen; keine offene Updater-Aufgabe.** Den funktionierenden Ablauf nicht unnötig verändern.
- Der aktuelle Installer-Flow verwendet `FileProvider`, `ACTION_VIEW`, MIME-Typ `application/vnd.android.package-archive`, URI-Leseberechtigung und `ClipData`.
- Keine `REQUEST_INSTALL_PACKAGES`-Permission wieder einführen und nicht auf `ACTION_INSTALL_PACKAGE` umstellen. Keine automatische Updateprüfung wieder einbauen; die manuelle Updatefunktion bleibt bestehen.
- GitHub-Workflow serialisiert Veröffentlichungen über `concurrency.group: klimapilot-release-main` und `cancel-in-progress: false`. Diese Absicherung muss erhalten bleiben.

## Technische Grundlagen und Einschränkungen

- Der Adapter `mhi-wfrac` ist noch nicht eingerichtet und soll erst bei Installation der Klimaanlage verwendet werden.
- Echte Datenpunktzuordnungen, Statuswerte und Steuerungsfunktionen müssen mit der installierten Klimaanlage geprüft werden.
- Keine erfundenen oder nicht verifizierten Datenpunkte und Hardwarefunktionen als verfügbar darstellen.
- SENEC und der zugehörige Adapter `energyfluss` werden nicht als Datenquelle eingeplant, da häufige Abfragen den Rechner überlasten können.
- Keine Zugangsdaten, Tokens, Keystore-Passwörter oder privaten Schlüssel dokumentieren.

## Bekannte Historie und Entscheidungen

- Am 31.10.2026 gab es mehrere Versuche, den Android-Installationsablauf anzupassen. Ein zwischenzeitlich eingeführter Pfad mit `REQUEST_INSTALL_PACKAGES` und `ACTION_INSTALL_PACKAGE` wurde wieder zurückgenommen.
- Commit `364e0b3` entfernte diese Berechtigung und stellte den zuvor funktionierenden `ACTION_VIEW`-Ablauf wieder her.
- In früheren Tests erschien eine Google-Play-Protect-Warnung. Die konkrete technische Ursache wurde nicht unabhängig nachgewiesen. Diese Beobachtung bleibt historische Fehlerdokumentation und ist keine offene Updater-Aufgabe.
- Der Updater wurde nach der letzten Korrektur vom Nutzer erfolgreich getestet und bestätigt.

## Offene Punkte / nächste Schritte

1. Nach Installation der Klimaanlage `mhi-wfrac` einrichten und die realen Datenpunkte, Zustände und Steuerbefehle verifizieren.
2. Die ioBroker-Verbindung einschließlich Authentifizierung, Abonnements, Reconnect und Alive-Prüfung mit der realen Anlage/Systemumgebung weiter validieren.
3. Bei künftigen Änderungen die Projektakte, Commit-Historie, Build-/Release-Daten und tatsächlich durchgeführte Smartphone-Tests aktualisieren.

Es gibt aktuell keine offene Updater-Aufgabe. Hardwareabhängige Klimaanlagenfunktionen bleiben bis zur Installation der Anlage zurückgestellt.

## Arbeits- und Dokumentationsregeln

- Vor jeder Änderung aktuellen `main`-Stand und betroffene Dateien lesen.
- Änderungen klein, gezielt und nachvollziehbar halten.
- Keine Test-Branches oder separaten Testversionen für die normale Weiterentwicklung.
- Nach Änderungen Build-Ergebnis, APK-Prüfung und Release-Status kontrollieren.
- Commit, erfolgreicher Build, Release-Aktualisierung und Dokumentationssicherung getrennt dokumentieren.
- Zeitstempel ausschließlich in deutscher Ortszeit mit Kennzeichnung MEZ/MESZ führen.
- Nur tatsächlich geprüfte Ergebnisse als bestätigt bezeichnen.
- Wichtige Entscheidungen, Fehlerbehebungen, Tests und offene Punkte in dieser Datei und in der ausführlichen Projektakte nachführen.

## Änderungsverlauf

- 31.10.2026, 22:26 Uhr (MEZ): Zentrale Projektstatusdatei angelegt. Produktionsstand 0.4.21 / Code 51, Commit `364e0b3`, Actions #76 und Release `latest` live geprüft. Updater-Status korrigiert: erfolgreich getestet, abgeschlossen, keine offene Aufgabe.\n\n\n\n\n\n

## Verbindliche Updater-Arbeitsweise (05.11.2026)

Der Nutzer hat die aktuellen In-App-Updates von HomePilot und KlimaPilot erfolgreich installiert. Diese Regeln ergänzen die bestehende Entscheidung, den funktionierenden KlimaPilot-Updater nicht unnötig zu verändern:

1. Vor Änderungen immer aktuellen `main`-Stand lesen und Update-UI, Download-/Installer-Code, Manifest, FileProvider, Gradle-Signierung und Workflow prüfen.
2. Zuerst Git-Historie und den bekannten funktionierenden Stand recherchieren. Nicht blind Installer-Implementierungen von HomePilot übernehmen oder umgekehrt: Package-ID, FileProvider, Signierung und app-spezifische Konfiguration unterscheiden sich.
3. Keine unnötigen Experimente mit `ACTION_VIEW` / `ACTION_INSTALL_PACKAGE`, MIME-Typ, URI-Quelle oder Berechtigungen. Nur gezielte Änderungen mit nachvollziehbarem Diff; bei funktionierendem Updater keine Änderung ohne konkreten Fehler.
4. **Signierung erhalten:** Updates müssen mit demselben Zertifikat signiert sein wie die installierte App. Release-Keystore, Alias, GitHub Secrets und SigningConfig nicht beiläufig ändern. Bei Installations-/Signaturfehlern Zertifikats-Fingerprints der APKs vergleichen.
5. Für installierbare Test-APK immer `versionName` und `versionCode` erhöhen; `versionCode` muss über dem installierten Stand liegen.
6. Die in der Updateprüfung angezeigte Version/Release-Notes muss dynamisch der Gradle-Version entsprechen; keine veraltete fest codierte Versionsangabe. Das bereits korrigierte Workflow-Verhalten beibehalten.
7. GitHub Actions, APK, Application-ID, Version, Signatur und Release-Asset prüfen. Erst nach erfolgreichem Build und verifiziertem Asset Download-Link nennen.
8. Nutzergerätetest: Download starten, Android-System-Installationsdialog tatsächlich öffnen, Update über die bestehende Installation durchführen und danach installierte Version kontrollieren. Ein Download oder eine Statusmeldung ist kein Installationsnachweis.
9. `REQUEST_INSTALL_PACKAGES`, Android-Einstellungen für unbekannte Apps und Play-Protect-relevante Änderungen nicht ohne Historienprüfung und ausdrückliche Absprache verändern.
10. Nach erfolgreichem Test Version/versionCode, Commit, Actions-Run und Nutzerbestätigung dokumentieren. Updater-Code bei anderen Arbeiten unangetastet lassen.

**Ergebnis 05.11.2026:** Nutzer bestätigt, dass KlimaPilot-Update erfolgreich installiert wurde. Bestätigt ist dieser konkrete Test; keine Garantie für jedes Android-Gerät.\n

## Bewährte Updater-Lösung – Gerätetest 05.11.2026

**Erfolgreich auf dem Nutzergerät bestätigt:** KlimaPilot **0.4.23 / versionCode 53**. Der erfolgreiche KlimaPilot-Pfad basiert auf `ACTION_VIEW` mit MIME-Typ `application/vnd.android.package-archive`, `FileProvider`-URI, URI-Leseberechtigung, `ClipData` und `FLAG_ACTIVITY_NEW_TASK`. `REQUEST_INSTALL_PACKAGES` wird nicht verwendet. Der Nutzer bestätigte, dass das Update erfolgreich installiert wurde.

Diese Lösung ist die KlimaPilot-Referenz und darf nicht blind auf HomePilot übertragen werden: Package-ID, FileProvider, Signierung und Berechtigungen unterscheiden sich. Die Bestätigung gilt für den getesteten Stand und das Nutzergerät.\n\n\n

## Änderung 13.11.2026 – Start-Updatehinweis

- Der Start-Updatehinweis wird analog zum bereits in HomePilot eingesetzten `StartupUpdateCheck` ergänzt.
- Die Prüfung nutzt ausschließlich die bereits gespeicherte GitHub-Geräteanmeldung und den bestehenden privaten Release-Endpunkt.
- Bei verfügbarer neuerer Version wird ein Compose-Dialog angezeigt: „Zu Einstellungen“ öffnet die bestehende Updatekarte, „Später“ schließt den Hinweis.
- Bei nicht verbundener GitHub-Anmeldung oder Fehlern bleibt der Start ohne Popup.
- Die vorhandene manuelle Updatekarte sowie der bewährte APK-Download-/Installationsablauf werden nicht verändert.
- Vorgesehene App-Version: **0.4.24 / versionCode 54**. Build, Release und Gerätetest sind nach dem Commit separat zu prüfen.\n

## Änderung 13.11.2026 – KlimaPilot-Benutzerkonten

- Umsetzung nach dem bewährten HomePilot-Kontomodell, jedoch mit eigenem Namensraum: `0_userdata.0.KlimaPilot.accounts`.
- Anmeldung ist verpflichtend; kein Gastmodus. Beim ersten erfolgreichen ioBroker-Verbindungsaufbau wird das erste Administratorkonto eingerichtet. Die bestehende automatische ioBroker-Verbindung bleibt erhalten.
- Konten und Anzeigenamen werden über den gemeinsamen ioBroker-Datenpunkt synchronisiert. Passwörter werden ausschließlich als PBKDF2-HMAC-SHA256-Hashes mit individuellen 16-Byte-Zufallssalzen (120.000 Iterationen) gespeichert.
- Rollen: `admin` und `user`. Administratoren verwalten Konten, Räume, Geräte und ioBroker-Verbindungseinstellungen. Benutzer dürfen die Klimaübersicht bedienen, das eigene Passwort ändern und den Benutzer wechseln.
- Benutzerwechsel löscht nur die lokale KlimaPilot-Kontositzung; die ioBroker-WebSocket-Verbindung bleibt bestehen.
- Schutzregeln: kein Löschen des eigenen angemeldeten Kontos oder des letzten Administrators; Kontenänderungen prüfen die Registry-Revision vor dem Schreiben.
- Die bestehende GitHub-Updateprüfung und der funktionierende APK-Installationsablauf wurden nicht verändert.
- App-Version vorgesehen: **0.4.26 / versionCode 56**. CI-Build, APK-Signatur und Release sind noch separat zu verifizieren; ein Smartphone-Test ist nicht durch CI abgedeckt.\n

## Änderung 13.11.2026 – Scrollbare Kontendialoge

- Die Eingabebereiche der Dialoge „KlimaPilot-Benutzer anlegen“ (max. 280 dp) und „Passwort zurücksetzen“ (max. 240 dp) sind begrenzt und vertikal scrollbar. Damit bleiben die Dialogaktionen auch auf kleinen Displays mit geöffneter Tastatur erreichbar.
- Kontenmodell, Rollen, Passwort-Hashing, Datenpunkt und ioBroker-Verbindung unverändert.
- Vorgesehene App-Version: **0.4.27 / versionCode 57**; CI-Build und Release noch zu prüfen.\n\n\n\n\n\n\n\n\n

## Änderung 15.11.2026 – Fixierte Einstellungs-Kopfzeile

- In `SettingsScreen` bleiben Zurück-Pfeil und Titel „Einstellungen“ außerhalb des Scrollbereichs dauerhaft sichtbar.
- Nur die darunterliegenden Einstellungs-Karten und Aktionen scrollen; Statusleisten-, Navigationsleisten- und Tastatur-Insets bleiben erhalten.
- Bestehende Einstellungsfunktionen und deren Inhalte wurden nicht verändert.
- App-Version: **0.4.34 / versionCode 64**. Produktionsbuild, APK-Signatur und Release `latest` sind nach dem Commit separat zu prüfen.
- Klimaprofile sind davon getrennt und noch nicht implementiert. Ihre Synchronisierung muss angesichts der entfernten dauerhaften ioBroker-Verbindung vorab passend zur aktuellen Architektur geplant werden.\n\n

## Änderung 15.11.2026 – Erste lokale Klimaprofil-Verwaltung

- Neue lokale Profilablage in `klimapilot_climate_profiles`, getrennt von Benutzerkonten und Geräteeinstellungen.
- Profile werden einem konkreten lokalen Gerät zugeordnet und können beliebig angelegt, umbenannt/bearbeitet und gelöscht werden.
- Jedes Profil speichert nur ausdrücklich ausgewählte Werte: Ein/Aus, Betriebsmodus (Auto/Kühlen/Heizen/Entfeuchten/Lüfter) und Solltemperatur (16–30 °C in 0,5-°C-Schritten). Nicht ausgewählte Felder bleiben null und sind für spätere Anwendung ausdrücklich unverändert zu lassen.
- Die Werte orientieren sich an den vorhandenen WF-RAC-Protokolltypen. Lüfterstufen und weitere modellabhängige Optionen sind bewusst noch nicht enthalten, weil die UI-Auswahl und die tatsächliche Gerätezuordnung noch nicht am installierten Gerät bestätigt sind.
- **Wichtige Einschränkung:** Profile können aktuell gespeichert und verwaltet, aber noch nicht angewendet werden. Die App bleibt beim Lesen; Schreibsteuerung, Account-Registrierung und 60-Sekunden-Schreibsperre müssen am realen Modul geprüft werden, bevor Anwenden aktiviert wird.
- Speicherung ist vorerst lokal auf dem Smartphone. Synchronisierung/Backup zwischen Smartphones ist noch nicht umgesetzt; die frühere ioBroker-Idee wird separat gegen die aktuelle Architektur geprüft.
- App-Version: **0.4.35 / versionCode 65**. Build- und Release-Ergebnis sind nach dem Commit separat zu verifizieren.\n\n

## Änderung 15.11.2026 – Lokaler Export und Import von Klimaprofilen

- Klimaprofile des aktuell ausgewählten Geräts können als JSON-Datei exportiert und über die Android-Dateiauswahl importiert werden.
- Beim Import werden alle Profile der Datei dem aktuell ausgewählten lokalen Gerät zugeordnet. Das ist erforderlich, weil lokale Geräte-IDs auf unterschiedlichen Smartphones nicht stabil übereinstimmen.
- Der Import validiert Formatversion, Profil-IDs, Namen, Betriebsmodus, Temperatur und alle Wertebereiche vollständig, bevor Daten gespeichert werden. Beschädigte, unbekannte oder übergroße Dateien werden abgelehnt.
- Zusammenführung statt blindem Überschreiben: neue Profil-IDs werden ergänzt; bestehende IDs werden nur bei neuerem Zeitstempel ersetzt. Nicht enthaltene Profile werden nicht gelöscht. Löschungen werden daher nicht zwischen Smartphones synchronisiert.
- Es gibt keinen Cloudserver und keine zusätzliche dauerhafte ioBroker-Verbindung. Die Funktion ist ein manueller Datei-Transfer/Backup, keine automatische Synchronisierung.
- App-Version: **0.4.37 / versionCode 67**. Build, Signatur und Release sind separat zu prüfen.

## Änderung 15.11.2026 – Erweiterte Klimaprofil-Einstellungen

- Klimaprofile speichern zusätzlich Lüftergeschwindigkeit (Auto/Leise/Stufe 1–3), vertikale Luftführung, horizontale Luftführung und 3D-Automatik.
- Die Werte entsprechen den vorhandenen WF-RAC-Codec-Indizes: Lüfter 0–4, vertikal 0–4, horizontal 0–7; 3D-Automatik wird über das separate Entrust-Bit abgebildet.
- Profil-Schema auf Version 2 erweitert. Vorhandene Schema-1-Profile werden weiterhin geladen; neue optionale Felder bleiben bei Altprofilen leer. Beim nächsten Speichern werden sie im neuen Schema gesichert.
- Nicht ausgewählte Felder bleiben null. Modell-/Firmwareabhängigkeit wird vor einer späteren Schreibfreigabe geprüft.
- Solltemperatur bleibt bewusst bei 0,5 °C, da der WF-RAC-Protokollwert als halbe Gradstufen codiert ist; 0,1 °C würde vom Modul abgeschnitten.
- Noch keine Profil-Anwendung und keine Smartphone-Synchronisierung freigegeben. Schreibzugriff, Account-Registrierung, Write-Lock und reales Geräteverhalten müssen nach Installation getestet werden.
- App-Version **0.4.36 / versionCode 66**. Build und Release sind nach dem Commit separat zu verifizieren.\n\n\n\n\n\n\n\n\n

## Klimaprofil-Synchronisierung – Implementierungsstand 15.11.2026

- Gemeinsame Gerätekennung `syncDeviceKey` ist Bestandteil von `UserDevice`, wird lokal gespeichert und im Geräteeinstellungsdialog bearbeitet. Alte Geräteeinträge ohne Feld werden rückwärtskompatibel mit leerer Kennung geladen.
- `ClimateProfileSyncStore` führt eine lokale, dauerhaft gespeicherte Outbox in `klimapilot_profile_sync`. Ereignisse besitzen Installations-ID, Sequenznummer, logischen Zähler, Ereignis-ID, Gerätekennung und Profil-Snapshot beziehungsweise Lösch-Tombstone.
- Bestehende Profile werden beim erstmaligen Hinterlegen einer gemeinsamen Gerätekennung einmalig als Initialbestand vorgemerkt. Die Initialisierung wird erst nach erfolgreichem lokalem Speichern markiert.
- Profiländerungen und Löschungen werden lokal vorgemerkt. Outbox-Einträge werden erst nach ioBroker-Schreibbestätigung entfernt; fehlgeschlagene Übertragungen bleiben zur Wiederholung erhalten.
- `ClimateProfileSyncClient` nutzt eine eigene native OkHttp-WebSocket-Verbindung, authentifiziert separat und prüft `system.adapter.admin.0.alive`. Die Hauptverbindung der App wird nicht getrennt oder umkonfiguriert.
- Vorgesehener ioBroker-Namespace: `0_userdata.0.KlimaPilot.profileSync.devices.<installationId>.<sequence>`. Ereignisse werden als JSON-String-States abgelegt und über `getStates` gelesen.
- Remote-Profile werden anhand der gemeinsamen Gerätekennung auf die jeweilige lokale Geräte-ID abgebildet. Eindeutigkeitsprüfung verhindert die Übernahme bei mehrfach verwendeter Kennung auf demselben Smartphone.
- Konfliktordnung: logischer Zähler, danach Installations-ID und Sequenznummer als deterministischer Tie-Breaker. Remote-Löschungen bleiben als Tombstones im ioBroker-Ereignislog erhalten.
- Automatischer Abgleich ist beim Laden der Profilansicht und anschließend im 60-Sekunden-Abstand vorgesehen, solange die Profilansicht aktiv ist.
- **Noch nicht als produktiv bestätigt:** APK-Build für den aktuellen vollständigen Quellstand 0.4.42 / versionCode 72, ioBroker-Rechte und tatsächliches Verhalten von `getStates` für den Namespace, sowie Praxistest mit zwei Smartphones. Vor erfolgreicher Prüfung keine Aussage, dass die Synchronisierung vollständig funktioniert.
- Kein externer Cloudserver; Benutzerkonten, Passwörter, Updater und Klimaanlagen-Steuerbefehle werden von diesem Synchronisierungsweg nicht verändert.\n