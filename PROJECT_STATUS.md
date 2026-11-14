<!-- AUTO-PRODUCTION-STATUS:START -->
## Automatisch gepflegter Produktionsstatus

- Quellcommit des APK-Builds: 3c55b7c28cdc5455fafc8b386b5494b55567e20a
- App-Version / versionCode: **0.4.28 / 58**
- Build-Zeit: 2026-11-14 22:12:18 UTC
- APK-Dateiname: **KlimaPilot.apk**
- APK-Signaturprüfung: erfolgreich (apksigner verify --print-certs)
- Build-Artefakt: kein Actions-Artefakt; APK wird im GitHub-Release latest veröffentlicht
- APK-SHA-256: 5548607c1689202ba0f5cd4f18b67a56a9077bbb3052a9f6d92a6e53ad23eb08
- GitHub-Release: **erfolgreich veröffentlicht**; Asset KlimaPilot.apk wurde aktualisiert.
- Smartphone-Test: **nicht durch CI geprüft**; nur separat bestätigte Nutzerberichte gelten als Gerätetest.
- Updater: abgeschlossen und vom Nutzer bestätigt; Installer-Code wird durch diese Dokumentationspflege nicht verändert.
          - ChatGPT-Bibliotheksakte: separate Langzeit-/Historienakte; wird durch diesen Workflow nicht verändert.

<!-- AUTO-PRODUCTION-STATUS:END -->

## Verbindliche Aktualisierung – 14.11.2026
- App-Code-Commit: `56b49749a77f07b50f6c2bac2085edd250e56116`; Version **0.4.27 / versionCode 57**.
- Der aktuelle APK-Build wurde aus Dokumentationscommit `7984def11f7da78407f24697ab4d6128ab700706` erstellt; der App-Code entspricht weiterhin `56b49749a77f07b50f6c2bac2085edd250e56116`.
- GitHub Actions Run `43405429051` (#103) für den APK-Build-Quellcommit: **completed / success**. Der vorherige Code-Build Run `43403206733` (#101) für den App-Code-Commit war ebenfalls erfolgreich.
- Release `latest` enthält `KlimaPilot.apk`, SHA-256 `537ea7f086b88a1fecc7106dc8733d20c860c2deabfca666b39fdc11fdbe705d`; APK-Signaturprüfung erfolgreich.
- Der Nutzer hat die scrollbaren Dialoge der Kontenverwaltung auf dem Android-Gerät erfolgreich geprüft.
- Der vor dieser Statuspflege geprüfte `main`-Head `1a7c5d7460d9cdf2241027eb7de3f90a6b92ebb8` war ein Dokumentationscommit; auch diese Aktualisierung ändert den App-Code nicht.
- Alle älteren Build-Pending- und Versionsvorschau-Angaben im Änderungsverlauf sind historische Zwischenstände, soweit sie denselben Produktionsstand betreffen.
- Offen bleiben ausschließlich die hardwareabhängigen Arbeiten nach Installation der Mitsubishi-Klimaanlage; siehe Abschnitt „Offene Punkte / nächste Schritte“.

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
- Vorgesehene App-Version: **0.4.27 / versionCode 57**; CI-Build und Release noch zu prüfen.\n\n\n