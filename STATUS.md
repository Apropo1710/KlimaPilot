# KlimaPilot – aktueller Projekt- und Übergabestatus

**Abgleich:** 18.11.2026, 21:00 Uhr MEZ  
**Repository:** https://github.com/Apropo1710/KlimaPilot  
**Produktionsbranch:** `main` – HEAD `356842b20acbc042a21cb4d06e664dbd45654d98`  
**Produktionsversion:** **0.4.64 / versionCode 94**  
**Arbeitsbranch:** `test` – HEAD `ae1bdc03c78624ff640505b4bd255bfd885b05c5`

## Produktionsstand

- `main` ist alleiniger Produktionsbranch; PR #6 ist nicht gemergt.
- Letzter bestätigter Produktionsbuild: #229, **success**; Release `latest` enthält die signierte APK 0.4.64 / 94. Dokumentationsänderungen erzeugen keinen neuen App-Build.
- Updater ist abgeschlossen und vom Nutzer bestätigt. **Nicht verändern.**

## WLAN-/Pairing-Arbeitsstand

- [PR #6](https://github.com/Apropo1710/KlimaPilot/pull/6) offen/Draft, nicht gemergt.
- `main...test`: **151 ahead / 2 behind**, Merge-Base `c7f1d57e4a4661d1492e5dca42ec9a9841d3e5fd`. Branches sind divergent; kein Rebase/Merge ohne gesonderte Prüfung und Freigabe.
- WLAN-Validierung auf exakt `ae1bdc03c78624ff640505b4bd255bfd885b05c5`: [Run 44248243246](https://github.com/Apropo1710/KlimaPilot/actions/runs/44248243246) **success** (Unit-Tests, Debug-Assembly, `compileReleaseKotlin`).
- TCP-Emulatorlauf [44247356481](https://github.com/Apropo1710/KlimaPilot/actions/runs/44247356481) erfolgreich auf älterem Commit `41b9a3ab`, API 26. Für aktuellen HEAD kein Emulatornachweis; API 35 nicht bestätigt.
- TCP-Emulatorworkflow nur manuell (`workflow_dispatch`); WLAN-Workflow bei PR-Updates. Keine doppelten automatischen Emulatorläufe.
- QR-Pairing, lokale Bestätigung, Keystore-Identität, Trusted-Peer-Speicher und TCP-Challenge-Response sind implementiert. TLS/mTLS wurde nach Nutzerfreigabe verworfen; TCP ist unverschlüsselt.
- Discovery ist weiterhin reject-only; TCP-Handshake ist noch nicht angebunden. Kein Profil-/Outbox-Transfer und keine automatische Peer-Synchronisierung.
- Vor Profiltransfer zwingend: sichere Integration, strikte Empfänger-Validierung, erfolgreicher Zwei-Smartphone-Test und unabhängige Prüfung.
- WLAN-/Pairing-Oberfläche ist ausschließlich in der Debug-App sichtbar (`BuildConfig.DEBUG`, Application-ID `de.klimapilot.app.debug`). Updater liegt außerhalb des Debug-Guards und wurde nicht geändert.

## Nächster Arbeitsschritt

Discovery-, TCP-, Session-Controller- und Trusted-Peer-Code vollständig prüfen; Integrationsdesign und Sicherheitsgrenzen festlegen. Bis dahin keine Nutzdatenübertragung, kein Merge und kein Produktionsrelease.

## Feste Projektregeln

- Vor Änderungen aktuellen `main`-Stand und betroffene Dateien prüfen.
- `main` bleibt produktiv; `test` ist nicht freigegeben.
- Keine alten CI-Runs als aktuellen Status werten.
- Keine unnötigen Workflowstarts; TCP-Emulatorlauf nur gezielt manuell.
- Updater, bestehende ioBroker-Synchronisierung und WF-RAC-Steuerpfad nicht unnötig verändern.
- Hardware-/Zwei-Smartphone-Tests nur als bestanden markieren, wenn tatsächlich ausgeführt.

---

Historische Detailchronik folgt; bei Widersprüchen gilt die Statuskorrektur oben.

# KlimaPilot – aktueller Projektstatus

**Abgleich:** 17.11.2026, 02:56 Uhr MEZ  
**Repository:** https://github.com/Apropo1710/KlimaPilot  
**Branch:** `main`  
**App-Version:** **0.4.63 / versionCode 93**

## Verifizierter Produktionsstand

- `main`-HEAD vor dieser Statuspflege: `cd26cc24659c103e0ce1f2cccc8467a06050b9b9` (Dokumentationscommit).
- Letzter App-Code-Commit: `42472a3af147` – Importkorrektur für die Rundungs-/Snapshot-Validierung; davor wurde mit `f670b9a8c252` das Erhalten und Synchronisieren individueller Klimaeinstellungen umgesetzt.
- APK-Build-Quellcommit: `c7a1c6a458f0c44533d08672db5592a20748bfc6` (Dokumentationscommit auf demselben App-Code-Baum).
- GitHub Actions [#222](https://github.com/Apropo1710/KlimaPilot/actions/runs/43957989783): **completed / success**, exakt für den APK-Build-Quellcommit.
- Version aus `app/build.gradle.kts`: **0.4.63 / versionCode 93**. APK-Paket-/Versionsprüfung und Signaturprüfung laut Produktionsnachweis erfolgreich.
- Release `latest`: Asset `KlimaPilot.apk`, Asset-ID `756488225`, 11.370.139 Bytes; aktualisiert 16.11.2026, 21:19:22 UTC.
- APK-SHA-256: `48e876e988186d3021d79364bd3405400c5a98a7168332b5d4c0d221f474f52f`.
- Git-Tag `latest` zeigt auf den APK-Build-Quellcommit `c7a1c6a458f0c44533d08672db5592a20748bfc6`.
- APK: https://github.com/Apropo1710/KlimaPilot/releases/download/latest/KlimaPilot.apk

## Offener Synchronisations-PR

- [PR #3 – dauerhaftes Änderungsjournal](https://github.com/Apropo1710/KlimaPilot/pull/3) ist offen und Draft, Head `ad84fe8d3cc7`, noch nicht gemergt. Die jüngste GitHub-Abfrage lieferte für die Mergeability `unknown` (eine frühere Abfrage meldete `clean`); daher derzeit keine abschließende Konfliktbewertung.
- Der aktuelle PR-Head ergänzt das erneute Abarbeiten ausstehender Profiländerungen während der periodischen Synchronisation. Validierungslauf #15 / Run `43964905903`: **completed / success**.
- Diese PR-Änderungen sind noch nicht Bestandteil des Produktions-Releases 0.4.63.

## Zuletzt umgesetzt

- 0.4.63: individuelle Klimaeinstellungen bleiben erhalten und werden synchronisiert; Snapshot-Rundungsvalidierung korrigiert.
- 0.4.59: Cloud-Symbol 12 dp weiter vom Zahnrad abgesetzt; bei Verbindung grün, offline gedimmt.
- 0.4.58: Cloud-Symbol rechts neben Zahnrad; abgeschnittenen Status-Text entfernt.
- 0.4.57: Kopfzeile bleibt beim Scrollen sichtbar; einheitliches Hellblau; Raum-Wischgeste erhalten.
- 0.4.56: Moduskacheln gleich hoch, Symbolbereiche vereinheitlicht, Kartenflächen angepasst.
- 0.4.55: Modus-/Power-Symbole, Luftführungs-Dropdowns und Update-Button überarbeitet.
- 0.4.54: Hauptansicht kompakter an MyAirco-Layout angelehnt.

## Technik und feste Entscheidungen

- Kotlin, Jetpack Compose/Material 3; Application-ID `de.klimapilot.app`; minSdk 26; compile/target SDK 35; Java 17.
- Direkte lokale Klimasteuerung über WF-RAC-WLAN-Modul; optionale ioBroker-WebSocket-Verbindung für Klimaprofil-Synchronisierung.
- Zielgerät Mitsubishi Heavy `SRK15ZS-WF`; Anlage noch nicht installiert, reale Hardware-/Firmwaretests offen.
- `mhi-wfrac` noch nicht eingerichtet und für direkten lokalen Steuerweg nicht erforderlich.
- SENEC/`energyfluss` wird nicht eingeplant.

## Branchstatus und nicht abgeschlossene Nebenstände

- `feature/direct-wf-rac-wlan`: PR #1 wurde am 15.11.2026 gemergt. Der Branch lief danach weiter und liegt aktuell **23 Commits voraus / 235 zurück** gegenüber `main`; es gibt keinen offenen PR. Der Diff enthält nachgelagerte WLAN-Diagnostik, einen separaten Test-Updatekanal sowie Änderungen an Build-/App-Dateien. Diese 23 Commits sind nicht automatisch Bestandteil von `main`; vor Wiederverwendung, Cherry-pick oder Löschung einzeln prüfen.
- `docs/status-refresh-2026-11-16`: PR #2 wurde geschlossen, nicht gemergt. Der Branch liegt **1 Commit voraus / 61 zurück** und enthält eine abweichende `STATUS.md`; kein offener PR. Nicht ungeprüft übernehmen.
- `docs/status-reconcile-2026-11-15`: **0 Commits voraus / 235 zurück**, kein eigener Dateidiff; historischer Restbranch ohne offenen PR.
- `main` bleibt der alleinige maßgebliche Produktionsbranch. Keine dieser Nebenbranches wurde im Rahmen dieser Statuspflege gemergt oder gelöscht.

## Offene Prüfungen

- Konten-Registry mit absichtlich beschädigten Daten (Schutz aus 0.4.50).
- Pairing-Reset bei konkurrierenden Steuerbefehlen (0.4.52).
- Modellabhängige Luftführung/3D-Automatik und Profilanwendung am echten Gerät (0.4.53 M3/M4).
- Profil-Synchronisierung mit zwei Smartphones und realem ioBroker (0.4.53 M5).
- Hardwaretests nach Installation der Klimaanlage.
- Nutzer-Endtest der installierten 0.4.63 noch nicht bestätigt.

## Updater – abgeschlossen

Der Updater funktioniert und wurde vom Nutzer erfolgreich getestet. Keine offene Updater-Aufgabe. Installationsablauf nicht verändern: `ACTION_VIEW`, MIME `application/vnd.android.package-archive`, FileProvider-URI, URI-Berechtigung und `ClipData`. `REQUEST_INSTALL_PACKAGES` nicht einführen; nicht auf `ACTION_INSTALL_PACKAGE` umstellen; keine automatische Updateprüfung ergänzen.

## Letzte Builds

| Run | Version | Ergebnis |
|---|---|---|
| #222 | 0.4.63 / 93 | success – aktueller Release |
| #211 | 0.4.59 / 89 | success – historischer Release |
| #210 | 0.4.58 / 88 | success |
| #209 | 0.4.57 / 87 | success – Color-Import korrigiert |
| #208 | 0.4.57 Zwischenstand | failure |
| #207 | 0.4.57 Zwischenstand | failure |
| #206 | 0.4.56 / 86 | success |

## Dokumentationsquellen

- `PROJECT_STATUS.md`: maßgebliche aktuelle technische und produktive Statusakte.
- `STATUS.md`: kompakte operative Übergabedatei.
- `SYNC_RESILIENCE_PLAN.md`: **priorisiertes Architekturvorhaben** für ausfallsichere, vom ioBroker unabhängige Synchronisation. Enthält Zielarchitektur, Alternativen, verbindliche Entscheidungen, Umsetzungsphasen und Erfolgskriterien.
- ChatGPT Library: `KlimaPilot_Projektstand_aktualisiert.md` ist die ausführliche Langzeitakte; `KlimaPilot_Projektstand.md` bleibt als historisches Archiv mit aktuellem Statuskopf erhalten.
- Vor jeder Entwicklungsarbeit `main` und betroffene Dateien live prüfen. CI-Erfolg nicht mit Hardwaretest oder vollständiger Fehlerfreiheit gleichsetzen.

## Priorität: Ausfallsichere Synchronisation zeitnah umsetzen

Das Vorhaben ist ausdrücklich nicht nur eine unverbindliche Zukunftsidee. KlimaPilot soll bei Ausfall von ioBroker oder eines anderen Synchronisationsdienstes lokal vollständig nutzbar bleiben.

Verbindliche Leitlinien:
1. Local-first: Profile und Einstellungen sind auf jedem Smartphone dauerhaft lokal vorhanden.
2. Offline-Outbox: Änderungen werden während eines Ausfalls vorgemerkt und später zuverlässig übertragen.
3. Synchronisationsanbieter entkoppeln: ioBroker bleibt zunächst erhalten; Cloud und direkte Smartphone-Synchronisation können ergänzend folgen.
4. Konflikte, doppelte Events, Löschungen und Wiederanlauf kontrolliert behandeln.
5. Mindestens zwei Smartphones und reale Ausfall-/Wiederherstellungsszenarien testen.
6. Direkte lokale Klimasteuerung und funktionierenden Updater nicht unnötig verändern.

Ein absolut ausfallfreies System ist nicht garantierbar. Ziel ist, dass der Ausfall eines Synchronisationsdienstes weder die lokale Bedienung noch gespeicherte Daten gefährdet.

## WLAN-Pairing – aktueller Testzweig (17.11.2026)

- Nicht freigegebener Entwicklungsstand: PR #6 gegen `main`, offen/Draft. Produktionsbranch und Release bleiben unverändert.
- WLAN- und TLS-Validierung laufen nur noch automatisch über den PR nach `main`; zusätzliche Push-Trigger auf `test` wurden entfernt. Manueller Workflow-Start bleibt verfügbar.
- mTLS-Clientzertifikate werden durch Standard-TLS 1.2 mit gepinntem Android-Keystore-Serverzertifikat und signierter Anwendungschallenge des Clients ersetzt. Der Server prüft Peer-ID/Public-Key zusätzlich gegen die lokale Trusted-Peer-Liste.
- Implementierung: `ClimatePeerTls.kt`; Instrumentierungstest für erfolgreichen TLS-/Challenge-Ablauf und falschen Server-Pin.
- Code-Commit: `6748bffe9e6f2d000e5729d2dbede3e5f957e1f7`. Der CI-Nachweis für diesen neuen Ansatz ist noch ausstehend; Folgecommits enthalten Workflow- und Dokumentationspflege.
- Kein produktiver Peer-Transport, kein Profiltransfer und kein bestätigter Zwei-Smartphone-Test. Discovery bleibt reject-only.
- Updater und `main` unangetastet.
