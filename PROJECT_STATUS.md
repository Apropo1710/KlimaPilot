<!-- AUTO-PRODUCTION-STATUS:START -->
## Automatisch gepflegter Produktionsstatus

- Quellcommit des APK-Builds: b65f9d3d3b252e586f25368a66a7b2a30945d586
- App-Version / versionCode: **0.4.23 / 53**
- Build-Zeit: 2026-11-05 22:07:11 UTC
- APK-Dateiname: **KlimaPilot.apk**
- APK-Signaturprüfung: erfolgreich (apksigner verify --print-certs)
- Build-Artefakt: **KlimaPilot-release-apk**, ID 13456198892, 10694884 Bytes
- APK-SHA-256: 228e0246fddb1c41a0364d17c4b22c98e96453ad1f47bda6e56980faaeaa0ebb
- GitHub-Release: **erfolgreich veröffentlicht**; Asset KlimaPilot.apk wurde aktualisiert.
- Smartphone-Test: **nicht durch CI geprüft**; nur separat bestätigte Nutzerberichte gelten als Gerätetest.
- Updater: abgeschlossen und vom Nutzer bestätigt; Installer-Code wird durch diese Dokumentationspflege nicht verändert.
          - ChatGPT-Bibliotheksakte: separate Langzeit-/Historienakte; wird durch diesen Workflow nicht verändert.

<!-- AUTO-PRODUCTION-STATUS:END -->

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