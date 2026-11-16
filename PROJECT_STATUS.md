## Änderung 16.11.2026 – Farb- und Kachelkorrektur (0.4.56)

- Ursache der weiterhin rosa/lila Flächen: mehrere Material3-Cards verwendeten die nicht explizit gesetzte Standard-Card-Oberfläche. Konto-, Schriftgrößen-, Verbindungs- und weitere Einstellungsflächen setzen nun ausdrücklich den KlimaPilot-Hellblauton; auch die äußere Profilkarte nutzt die definierte helle Surface-Variante.
- Die fünf Betriebsmodus-Kacheln haben jetzt eine feste gemeinsame Höhe von 88 dp und einen identischen 24-dp-Symbolbereich. Die Schneeflocke ist auf 18 dp verkleinert und entspricht in der visuellen Gewichtung den übrigen Symbolen.
- Steuerungslogik, Profil- und Synchronisierungsabläufe sowie der Updater-Installationsablauf bleiben unverändert.
- Build-, Signatur- und Release-Nachweis werden nach Abschluss der GitHub-Actions-Prüfung ergänzt.

<!-- AUTO-PRODUCTION-STATUS:START -->
## Automatisch gepflegter Produktionsstatus

- Quellcommit des APK-Builds: bdc63d8ac3a9c5426069bd0c391d811dd2428684
- App-Version / versionCode: **0.4.56 / 86**
- Build-Zeit: 2026-11-16 11:34:48 UTC
- APK-Dateiname: **KlimaPilot.apk**
- APK-Signaturprüfung: erfolgreich (apksigner verify --print-certs)
- Build-Artefakt: kein Actions-Artefakt; APK wird im GitHub-Release latest veröffentlicht
- APK-SHA-256: 39e674437cd2b2e8a0b20fde9b9111e4064f7494e328f5c55acc65fb388de13a
- GitHub-Release: Veröffentlichung wird in diesem Workflow nach dem Statuscommit abgeschlossen.
- Smartphone-Test: **nicht durch CI geprüft**; nur separat bestätigte Nutzerberichte gelten als Gerätetest.
- Updater: abgeschlossen und vom Nutzer bestätigt; Installer-Code wird durch diese Dokumentationspflege nicht verändert.
          - ChatGPT-Bibliotheksakte: separate Langzeit-/Historienakte; wird durch diesen Workflow nicht verändert.

<!-- AUTO-PRODUCTION-STATUS:END -->

## Änderung 16.11.2026 – UI-Feinschliff (0.4.55)

- Das Kühlen-Symbol nutzt nun die gleich große einfarbige Material-Schneeflocke wie die übrigen Betriebsmodus-Symbole.
- Der Ein-/Ausschaltknopf zeigt das Material-Power-Symbol statt des auf manchen Geräten fehlerhaft dargestellten Unicode-Zeichens.
- Die Auswahl der vertikalen und horizontalen Luftführung wurde von horizontal scrollenden Optionen auf jeweils ein Dropdown umgestellt.
- Die gemeinsame helle Flächenfarbe für hervorgehobene Auswahl-/Profilflächen wurde auf das vorhandene helle Blau vereinheitlicht.
- Der Button „Update laden“ erhält eine größere Mindesthöhe und großzügigere Innenabstände. Der Download-, APK-Prüf- und Android-Installationsablauf wurde nicht verändert.
- Build- und Release-Nachweis wird nach Abschluss des GitHub-Actions-Laufs im automatisch gepflegten Produktionsstatus ergänzt.

# KlimaPilot – Projektstatus und technische Dokumentation

**Dokumentationsstand:** 15.11.2026, 22:16 Uhr MEZ  
**Repository:** [Apropo1710/KlimaPilot](https://github.com/Apropo1710/KlimaPilot)  
**Maßgeblicher Branch:** `main`

## 1. Maßgeblicher Stand

- Aktuelle Produktionsversion: **0.4.53 / versionCode 83**.
- APK-Quellcommit: `8eefbf5233784ba250b6f84940ccdd54fbae1ac7`.
- GitHub-Actions-Run **#198** wurde am 15.11.2026 um 22:42 Uhr MEZ mit `success` abgeschlossen.
- Release-APK-Build, APK-Benennung, Signaturprüfung (`apksigner verify --print-certs`), Produktionsstatus-Aktualisierung, Git-Tag `latest` und Veröffentlichung waren erfolgreich.
- Das Release `latest` enthält `KlimaPilot.apk`; der Git-Tag `latest` zeigt auf den APK-Quellcommit.
- APK-SHA-256: `39c3eba2aba7e4fd6d20e66f8ef64a5738247eab0a750aece7b6081c87a71bdd`.
- Der automatisch gepflegte Nachweis oben ist die maßgebliche Quelle für Buildzeit, Version, Signatur und Release.
- Ein Dokumentationscommit verändert weder App-Version noch APK. Versionsnummer und versionCode werden nur bei App-Code-Änderungen erhöht.

Vor jeder Entwicklungsarbeit müssen der aktuelle Stand von `main` und alle betroffenen Dateien erneut eingelesen werden. Diese Datei ist eine Orientierung und ersetzt die Quellcodeprüfung nicht.

## 2. Technische Basis und feste Leitplanken

- Android-App mit Kotlin und Jetpack Compose.
- Application-ID: `de.klimapilot.app`; Mindestversion API 26; Compile-/Target-SDK 35; Java 17.
- Die Klimaanlagensteuerung erfolgt direkt und lokal über das WF-RAC-WLAN-Modul (mDNS-Dienst `_beaver._tcp`, Standardport 51443). Die ioBroker-Verbindung ist nicht der Steuerungsweg.
- Benutzerkonten werden lokal verwaltet. Eine einmalige Übernahme älterer Konten aus ioBroker bleibt für Upgrades erhalten.
- Die Klimaprofil-Synchronisierung verwendet eine getrennte, optionale ioBroker-WebSocket-Verbindung; sie ist unabhängig von der direkten Gerätekommunikation.
- Jede physische Klimaanlage erhält eine eigene Gerätezuordnung und WF-RAC-Identität. HTTPS-Zertifikate werden pro Gerät gepinnt; unbekannte Module werden nur durch eine bewusste Kopplungsaktion aufgenommen.
- Der Adapter `mhi-wfrac` ist für die direkte lokale Steuerung nicht erforderlich. Die konkrete Anlage und Firmware sind noch nicht installiert und daher noch nicht hardwareseitig geprüft.
- SENEC/`energyfluss` wird nicht als Datenquelle eingeplant.
- Keine Zugangsdaten, Tokens, Keystore-Passwörter oder privaten Schlüssel dokumentieren.

## Änderung 16.11.2026 – Kompakte Klimasteuerungsoberfläche (0.4.54)

- Die Klima-Hauptansicht wurde an das gewünschte MyAirco-Layout angelehnt: kompakte Temperaturkarte mit Innen-/Außentemperatur, zentraler Solltemperatur, Plus/Minus, Slider und Ein/Aus-Schaltfläche.
- Betriebsmodi werden als Auswahlkacheln dargestellt; Lüfterstufen als horizontale Auswahl mit den Bezeichnungen Auto, Leise, Niedrig, Mittel und Hoch.
- Vertikale und – nur bei bestätigter Modellunterstützung – horizontale Luftführung werden nebeneinander kompakt dargestellt. Die 3D-Automatik bleibt modellabhängig.
- Raum-/Geräteauswahl und Klimaprofile bleiben erhalten. Steuerungs-Callbacks, Profilanwendung, Statusabfrage und Synchronisierungslogik wurden nicht neu implementiert.
- Der Updater und sein Installationsablauf wurden nicht verändert.
- Build- und Release-Nachweis wird nach Abschluss des GitHub-Actions-Laufs im automatisch gepflegten Produktionsstatus ergänzt.

## Änderung 15.11.2026 – Klimaprofile modellabhängig, Anwendung bestätigt und Sync-Kennung migriert (0.4.53)

- Horizontale Luftführung und 3D-Automatik werden nur angeboten bzw. angewendet, wenn die ausgelesene Modellkennung die Unterstützung ausdrücklich bestätigt. Unbekannte Modelle werden nicht mehr pauschal als kompatibel behandelt; vorhandene Profilwerte bleiben bei unbekannter Modellfähigkeit erhalten.
- Ein Klimaprofil wird in der Auswahl erst dann als aktiv markiert, wenn die Übertragung an das Gerät erfolgreich abgeschlossen wurde. Bei fehlendem Gerät, laufendem Steuerbefehl, fehlender Kopplung oder Übertragungsfehler bleibt das zuvor aktive Profil ausgewählt.
- Automatisch erzeugte `wfrac:`-Synchronisierungskennungen werden bei einem Wechsel der Aircon-ID auf die neue Kennung umgestellt. Manuell vergebene Kennungen bleiben unverändert. Ausstehende Outbox-Ereignisse werden bei eindeutiger Gerätezuordnung auf den neuen Schlüssel migriert; bestätigte Ereignisse bleiben beim alten Schlüssel. Mehrdeutige Zuordnungen blockieren die Migration.
- GitHub Actions Run **#198**: Build erfolgreich; APK-Signaturprüfung, Produktionsstatus-Aktualisierung, Git-Tag `latest` und Release-Veröffentlichung erfolgreich. APK-SHA-256 siehe automatisch gepflegten Produktionsstatus oben.
- Die vorherigen Runs #196 und #197 scheiterten an einer inkompatiblen Parameterreihenfolge der neuen Callback-Funktion. Die Signatur wurde so korrigiert, dass bestehende abschließende Lambdas der Gerätesteuerung erhalten bleiben. Der korrigierte Stand ist in Run #198 erfolgreich gebaut.
- Profil-Synchronisierung mit zwei realen Smartphones/ioBroker sowie Modell- und Steuerungsverhalten an der noch nicht installierten Klimaanlage bleiben Praxistests. Der Updater wurde nicht verändert.

## Änderung 15.11.2026 – Schutz der lokalen Konten-Registry vor Datenverlust (0.4.50)

- `KlimaPilotAccountRepository.load()` unterscheidet jetzt ausdrücklich zwischen einer tatsächlich fehlenden Registry, einer gültig geladenen Registry und vorhandenen, aber ungültigen/beschädigten Daten.
- Vorhandene beschädigte Rohdaten werden unter einem inhaltsabhängigen internen Backup-Schlüssel gesichert. Die lokale Registry wird weder als leer interpretiert noch automatisch durch eine ioBroker-Altlast ersetzt.
- Die Ersteinrichtung und einmalige ioBroker-Migration sind ausschließlich bei tatsächlich fehlendem lokalem Registry-Schlüssel möglich.
- `saveInitial()` verweigert ein Überschreiben, sobald der lokale Schlüssel bereits existiert. Reguläre Speicherungen prüfen den aktuellen Registry-Zustand und verweigern Änderungen bei fehlenden oder beschädigten Daten.
- Die Anmeldeansicht zeigt bei beschädigten Daten eine verständliche Fehlermeldung und lässt die Kontenliste gesperrt, statt eine neue Ersteinrichtung anzubieten.
- GitHub Actions Run **#186**: Build erfolgreich; APK-Signaturprüfung, Tag `latest` und Release-Veröffentlichung erfolgreich. APK-SHA-256 siehe automatisch gepflegten Produktionsstatus oben.
- Keine Hardwareprüfung erforderlich; ein gezielter Laufzeittest mit absichtlich beschädigtem SharedPreferences-Inhalt steht noch aus.
- Der Updater wurde nicht geändert.

## Änderung 15.11.2026 – WF-RAC-Kopplungsverwaltung abgesichert (0.4.52)

- Das Zurücksetzen einer WF-RAC-Identität verwendet jetzt dieselbe pro Klimaanlage verwaltete Mutex-Sperre wie laufende Steuerbefehle. Ist ein Steuerbefehl aktiv, wird das Zurücksetzen abgelehnt, statt die Lock-Instanz zu entfernen und dadurch parallele Schreibvorgänge auf unterschiedlichen Mutex-Instanzen zu ermöglichen.
- Die Lock-Instanz bleibt nach dem Zurücksetzen im Repository erhalten. Bereits wartende Steuerbefehle sehen nach Freigabe der Sperre die entfernte Identität und können nicht mehr mit ihr schreiben.
- Beim erfolgreichen Zurücksetzen bleiben gleichzeitig im Geräteeinstellungsdialog geänderte Modell-, Sync-Kennungs- und Demo-Einstellungen erhalten; nur Aircon-ID und Host werden geleert.
- Diese Änderung betrifft ausschließlich die Kopplungsverwaltung und Dialogzustände. Der Updater wurde nicht verändert.
- Laufzeit-/Hardwaretest steht noch aus; die Nebenläufigkeitssicherung wird durch den Mutex-Ablauf im Code hergestellt und im CI-Build kompiliert.

## 3. Änderung 15.11.2026 – Profil-Synchronisierung und kompaktere Oberfläche (0.4.43)

- Beim Löschen eines Profils wird zuerst die lokale Löschung dauerhaft gespeichert. Erst danach wird das Löschereignis in der Synchronisierungs-Outbox vorgemerkt. Damit kann ein fehlgeschlagenes lokales Löschen nicht mehr versehentlich eine Löschung auf anderen Smartphones auslösen.
- Die Profilansicht startet keinen zusätzlichen Synchronisierungslauf mehr allein deshalb, weil sich die Anzahl der Profile durch einen empfangenen Remote-Stand geändert hat. Der Abgleich läuft beim Öffnen, nach lokalen Änderungen und weiterhin im 60-Sekunden-Rhythmus.
- Die Klimaprofil-Kachel wurde kompakter gestaltet; die Geräte-Unterzeile entfällt. Erfolgs- und Fehlermeldungen werden farblich unterschieden.
- Der Downloadknopf „Update laden“ erhält eine feste Mindesthöhe und nutzt die volle verfügbare Kartenbreite, damit seine Beschriftung nicht abgeschnitten wird.
- Historischer Quellstand dieser Änderung: `bb7d4e764d71b87803feaba404843b9113e61b49`. Die Änderung ist in der aktuellen Version 0.4.44 enthalten.
- GitHub Actions: Run **#170** war der erfolgreiche Build dieser Zwischenversion; der aktuelle Produktionsbuild ist Run **#173** (siehe Produktionsstatus oben).
- Kein Praxistest der Synchronisierung mit zwei Smartphones oder realem ioBroker durchgeführt. Dieser bleibt ausdrücklich offen.

## Änderung 15.11.2026 – Einstellungsbereiche einklappbar und Profilverwaltung vereinheitlicht (0.4.49)

- Profilverwaltung berücksichtigt Status- und Navigationsleisten; Titel und Zurück-Pfeil sind nicht mehr abgeschnitten. Der Zurück-Pfeil verwendet denselben Material-IconButton-Stil wie die übrigen Seiten.
- „Klimaprofile verwalten“ verwendet denselben hervorgehobenen Button-Stil wie „Räume & Geräte verwalten“.
- Diskettensymbol in der Profilkachel verkleinert.
- Benutzerkonto, Schriftgröße und ioBroker-Verbindung lassen sich einzeln auf- und zuklappen; im geschlossenen Zustand bleiben kompakte Zusammenfassungen sichtbar.

## Änderung 15.11.2026 – Kompakter Profil-Speicherablauf (0.4.48)

- Profil-Kachel zeigt nur noch das Symbol und den Namen des aktiven Profils bzw. „Individuell“; Profilanzahl und Wertzusammenfassung entfallen.
- Speichern erfolgt über ein Diskettensymbol neben der Profilauswahl. Der Dialog fragt nur nach dem Profilnamen; alle aktuellen Geräteeinstellungen werden automatisch übernommen.
- Bei geändertem aktivem Profil fragt das Diskettensymbol, ob das bestehende Profil aktualisiert oder ein neues angelegt werden soll.
- Eine manuelle Änderung wechselt nicht mehr automatisch auf „Individuell“; das aktive Profil bleibt markiert, damit es aktualisiert werden kann.

## Änderung 15.11.2026 – Profile als kompakte Auswahl und direkte Zustandsübernahme (0.4.47)

- Auf der Startseite wird nur das aktuell gewählte Profil angezeigt. Antippen öffnet eine Dropdown-Auswahl mit allen Profilen und „Individuell“.
- Ändert sich ein gespeicherter Profilwert durch manuelle Bedienung, wechselt die Anzeige automatisch auf „Individuell“. Die Auswahl bleibt nur aktiv, solange die gespeicherten Profilwerte zum Gerätezustand passen.
- Neue Profile werden mit den aktuellen Geräteeinstellungen vorausgefüllt; der Nutzer muss nur den Namen festlegen. Werte können im Editor bei Bedarf angepasst werden.
- In den Einstellungen gibt es einen eigenen Bereich zum Bearbeiten, Umbenennen und Löschen der Profile.
- Die Solltemperatur-Anzeige folgt der Sliderbewegung live; der Steuerbefehl wird weiterhin erst beim Loslassen gesendet. Die Statusfarbe folgt dem Betriebsmodus (Heizen rot, Kühlen blau, Entfeuchten türkis, Lüfter grünlich, Automatik amber).

## Änderung 15.11.2026 – Navigation und kompaktere Einstellungen (0.4.46)

- Die Kopfzeile „Räume & Geräte“ bleibt beim Scrollen fixiert.
- Zurück-Pfeil und Android-System-Zurück führen aus der Raum-/Geräteverwaltung zurück zu den Einstellungen, von denen die Verwaltung geöffnet wurde.
- Die ioBroker-Verbindung wurde durch kompaktere Felder, Abstände und Schaltflächen verkleinert.
- „Räume & Geräte verwalten“ steht direkt unter der Anmeldekachel und ist als hervorgehobene Hauptaktion gestaltet.
- Die Anmeldekachel ist kompakter; der Update-Downloadknopf wurde auf 42 dp Höhe verkleinert. Der bewährte APK-Installationsablauf wurde nicht geändert.

## Änderung 15.11.2026 – automatische Kennung für Profil-Synchronisierung (0.4.45)

- Die manuelle Eingabe „Gemeinsame Gerätekennung“ wurde aus den Geräteeinstellungen entfernt.
- Nach erfolgreicher Kopplung wird die Profil-Sync-Kennung automatisch aus der vom WF-RAC-Modul gelieferten physischen Aircon-ID gebildet (Präfix `wfrac:` plus Aircon-ID). Damit erhalten Installationen auf mehreren Smartphones für dieselbe physische Klimaanlage denselben Zuordnungsschlüssel.
- Bereits vorhandene, nichtleere Sync-Kennungen bleiben aus Rückwärtskompatibilitätsgründen erhalten; bestehende Zuordnungen werden nicht stillschweigend umgestellt.
- Ohne gekoppeltes WF-RAC-Modul wird keine Kennung erfunden oder erzeugt. Die Synchronisierung wird erst nach dem Pairing für dieses Gerät aktiv.
- Die Aircon-ID bleibt eine vom Modul gelieferte technische Identität und wird nicht vom Nutzer eingegeben.
- Hardware- und Mehrgeräte-Synchronisierungstest stehen weiterhin aus.

## 4. Aktuell vorhandene App-Funktionen

### Neue Steuerungsbasis 0.4.44
- Pro Gerät können WF-RAC-Module separat über mDNS-Suche oder manuelle IP gekoppelt werden. Die Geräte-ID, das Transportverfahren und bei HTTPS der Zertifikat-Fingerprint werden geprüft und dauerhaft gespeichert.
- Die Vertrauensstellung für ein zusätzliches Modul erfolgt ausschließlich durch eine ausdrücklich gestartete Kopplungsaktion.
- Die Startseite liest den Status des ausgewählten Geräts direkt vom WF-RAC-Modul. Die Statusabfrage läuft für das ausgewählte Gerät im Hintergrund weiter, solange die Hauptansicht aktiv ist.
- Ein/Aus, Betriebsmodus (Automatik, Kühlen, Heizen, Entfeuchten, Lüfter), Solltemperatur in 0,5-°C-Schritten, Lüfterstufe und vertikale Luftführung sind an die WF-RAC-Schreibfunktion angebunden.
- Horizontale Luftführung und 3D-Automatik werden anhand der Modellkennung angeboten, wenn die Modellreihe diese Funktionen unterstützt.
- Statusanzeige enthält Raum-/Außentemperatur, Verbindungszustand, Kompressorstatus und gemeldeten Fehlercode, sofern das Modul Werte liefert.
- Klimaprofile besitzen eine Schaltfläche „Anwenden“ und übertragen nur die im Profil gespeicherten Werte; nicht gesetzte Werte bleiben unverändert.
- Schreibvorgänge lesen vor der Änderung den aktuellen Zustand und übernehmen nicht veränderte Modell-/Protokollfelder. Die gemeinsame WF-RAC-Operator-ID wird bei Bedarf registriert; erkannte Schreibsperren anderer Clients werden nicht übergangen.
- Pro Gerät kann ein isolierter Demo-Modus aktiviert werden. Er simuliert eine Anlage im Arbeitsspeicher und verwendet den produktiven WF-RAC-Frame-Encoder/Decoder. Es werden keine echten Netzwerkbefehle gesendet. Der Demo-Zustand ist nicht hardwarebestätigt und wird beim Prozessneustart zurückgesetzt.
- **Noch nicht durch echte Hardware bestätigt:** Befehlsannahme, Firmware-Kompatibilität, verfügbare Messwerte, Klappen-/3D-Funktion und Schreibverhalten an der installierten SRK15ZS-WF.
- Ausschalt-Timer, wiederkehrende Zeitpläne, automatische Wiedererkennung nach DHCP-IP-Wechsel sowie zusätzliche Sonderfunktionen wie Vacant/Self Clean sind noch nicht integriert.


### Benutzerkonten und Einstellungen
- KlimaPilot besitzt ein verpflichtendes Benutzerkonto-/Anmeldemodell mit den Rollen `admin` und `user`.
- Kontodaten werden lokal gespeichert; Passwörter werden als PBKDF2-HMAC-SHA256-Hashes mit individuellen Salts gespeichert. Bei einem Upgrade können ältere Konten einmalig aus `0_userdata.0.KlimaPilot.accounts` übernommen werden. Eine fehlende lokale Registry wird strikt von vorhandenen beschädigten Daten unterschieden; beschädigte Rohdaten werden gesichert und nicht automatisch überschrieben (seit 0.4.50).
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
3. Nach Installation der Klimaanlage beide WF-RAC-Module koppeln und Ein/Aus, Modi, Temperatur, Lüfter, Luftklappen, 3D-Automatik, Statuswerte und Fehlercodes praktisch verifizieren.
4. Ausschalt-Timer und wiederkehrende Zeitpläne ergänzen und vor der Hardwareinstallation mit dem Demo-Modus testen.
5. Automatische Wiedererkennung bereits gekoppelter Module nach DHCP-IP-Wechsel ergänzen.
6. Vacant/Self Clean und weitere durch das konkrete Modell bestätigte Sonderfunktionen samt Profilunterstützung ergänzen.
7. Profil-Synchronisierung mit zwei realen Installationen und ioBroker testen.

## 8. Verbindliche Arbeitsweise

- Ausschließlich die produktive Hauptversion auf `main` weiterentwickeln.
- Vor Änderungen `main` und die vollständigen betroffenen Dateien prüfen.
- Bestehende Funktionen und Abhängigkeiten berücksichtigen; gezielte Änderungen statt unnötiger Neuaufbauten.
- Keine nicht verifizierten Datenpunkte, Hardwarefunktionen oder Testergebnisse behaupten.
- Nach App-Code-Änderungen Version/versionCode erhöhen, Änderungen pushen und den automatischen Build kontrollieren.
- Build-Erfolg, APK-Signatur, Release-Aktualisierung und Smartphone-Test sind getrennte Nachweise.
- Zeitangaben in deutscher Ortszeit mit MEZ/MESZ dokumentieren.
- Diese Datei nach jedem abgeschlossenen Arbeitspaket aktualisieren. Veraltete Versionsstände gehören allenfalls in eine ausdrücklich als Historie gekennzeichnete Chronik, nicht in den aktuellen Projektstatus.\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n