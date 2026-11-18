# KlimaPilot – aktueller Projekt- und Übergabestatus

**Abgleich:** 18.11.2026, 08:10 Uhr MEZ  
**Repository:** https://github.com/Apropo1710/KlimaPilot  
**Produktionsbranch:** `main` – HEAD `b82433038a3ced3de3ff908788fde4ef36d58f66`  
**Produktionsversion:** **0.4.64 / versionCode 94**  
**Arbeitsbranch:** `test` – App-Code-Commit `bba8969ba4306d159ba388753f85c6638bf32f7f`; PR #6 offen/Draft, nicht gemergt.

### Neuester Arbeitsstand – WLAN-Defaultnetz-Überwachung (18.11.2026, 08:10 Uhr MEZ)

- Der vorherige Callback war an eine WLAN-`NetworkRequest` gebunden. Damit war ein Wechsel des Android-Standardnetzes zu einem bereits verfügbaren zweiten WLAN nicht zuverlässig erkennbar: beide WLANs konnten verfügbar bleiben, ohne dass für das neue Standardnetz ein neues `onAvailable` ausgelöst wurde.
- Korrektur auf `test`: zusätzlicher `registerDefaultNetworkCallback`. Er prüft sowohl `onAvailable` als auch `onCapabilitiesChanged`, damit auch die nachgelieferte WLAN-Transportinformation ausgewertet wird.
- Ein Wechsel des Defaultnetzes zu einem anderen WLAN beendet die Discovery fail-closed. Ein Mobilfunk-Defaultnetz wird nicht als Fehler gewertet, solange das ausgewählte WLAN selbst verfügbar bleibt.
- Der bestehende WLAN-Callback überwacht weiterhin Verlust und Link-/Adressänderungen des ausgewählten WLANs. Beide Callbacks werden im zentralen Cleanup abgemeldet.
- Code-Commit: `bba8969ba4306d159ba388753f85c6638bf32f7f`. Vorherige WLAN-/IPv6-Commits: `4f35e1feb60e0f46eac360fee51a58d024bfc6bb` und `305fc0e03df09ae2c7ff8e8511a88bc2abaeaea4`.
- **Validierung offen:** Für den neuen Callback-Code wurden noch keine Android-Unit-Tests, Android-Builds oder Emulatorläufe bestätigt. Der vorherige PR-HEAD `b91706c686350c356754001a31f407e1fc705057` hatte keine zugehörigen Actions-Runs. Kein erfolgreicher Test wird behauptet.
- Die sieben Adressauswahl-/IPv6-Scope-JVM-Testfälle sind vorhanden, aber nicht ausgeführt/bestätigt. Der isolierte JDK-Smoketest belegt nur die Java-Scope-API.
- Discovery bleibt reject-only; TCP-Handshake-Integration, Profil-/Outbox-Transfer und automatische Peer-Synchronisierung sind nicht umgesetzt.

## Produktionsstand

- `main` ist alleiniger Produktionsbranch; PR #6 ist nicht gemergt.
- Letzter bestätigter Produktionsbuild: #233, **success** auf `b88ff7e9709bc1ea5aafd673e26bb1d4efee9c16`; Release `latest` zeigt auf diesen Build-Commit und enthält die aktualisierte signierte `KlimaPilot.apk` (0.4.64 / 94). Der aktuelle `main`-HEAD `b8243303` ist ein nachfolgender Dokumentationscommit.
- Updater ist abgeschlossen und vom Nutzer bestätigt. **Nicht verändern.**

## WLAN-/Pairing-Arbeitsstand

- [PR #6](https://github.com/Apropo1710/KlimaPilot/pull/6) offen/Draft, nicht gemergt.
- `main...test`: **divergent (kein Merge/Rebase)**, Merge-Base `c7f1d57e4a4661d1492e5dca42ec9a9841d3e5fd`. Branches sind divergent; kein Rebase/Merge ohne gesonderte Prüfung und Freigabe.
- Letzter bestätigter WLAN-Lauf [Run 44248243246](https://github.com/Apropo1710/KlimaPilot/actions/runs/44248243246): **success** auf älterem App-Code-HEAD `ae1bdc03`. Für den aktuellen WLAN-/IPv6-Code-HEAD `4f35e1f` und den Test-HEAD `305fc0e` gibt es noch keinen CI-Lauf; die GitHub-API hat nach dem Commit keinen PR-Run erzeugt.
- TCP-Emulatorlauf [44247356481](https://github.com/Apropo1710/KlimaPilot/actions/runs/44247356481) erfolgreich auf älterem Commit `41b9a3ab`, API 26. Für aktuellen HEAD kein Emulatornachweis; API 35 nicht bestätigt.
- TCP-Emulatorworkflow nur manuell (`workflow_dispatch`); WLAN-Workflow bei PR-Updates. Keine doppelten automatischen Emulatorläufe.
- QR-Pairing, lokale Bestätigung, Keystore-Identität, Trusted-Peer-Speicher und TCP-Challenge-Response sind implementiert. TLS/mTLS wurde nach Nutzerfreigabe verworfen; TCP ist unverschlüsselt.
- Discovery ist weiterhin reject-only; TCP-Handshake ist noch nicht angebunden. Kein Profil-/Outbox-Transfer und keine automatische Peer-Synchronisierung.
- Vor Profiltransfer zwingend: sichere Integration, strikte Empfänger-Validierung, erfolgreicher Zwei-Smartphone-Test und unabhängige Prüfung.
- WLAN-/Pairing-Oberfläche ist ausschließlich in der Debug-App sichtbar (`BuildConfig.DEBUG`, Application-ID `de.klimapilot.app.debug`). Updater liegt außerhalb des Debug-Guards und wurde nicht geändert.

## Nächster Arbeitsschritt

Einen CI-Lauf auf dem aktuellen Test-Code auswerten, sobald GitHub Actions ihn automatisch für PR #6 bereitstellt. Bis zu einem bestätigten Lauf keine Android-Validierung behaupten. Danach gezieltes Review der WLAN-Lifecycle-Änderung fortsetzen. Bis dahin keine Nutzdatenübertragung, kein Merge und kein Produktionsrelease.

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


## Code-Review – Zwischenstand 18.11.2026, 07:21 Uhr MEZ

- TCP-Handshake-Code und vorhandene JVM-/Instrumentierungstests geprüft. Authentifizierung und Trust-Prüfung sind beidseitig implementiert; TCP bleibt unverschlüsselt.
- Discovery ist weiterhin reject-only; keine Profil-/Outbox-Daten werden übertragen.
- Vor späterer Integration muss der Listener ausdrücklich auf das aktive WLAN-Interface begrenzt werden: aktuell wird `ServerSocket(0)` ohne Interface-Bindung erzeugt (Wildcard-Bindung).
- Pairing-Controller-Test nutzt für beide simulierten Geräte denselben Trust-Store. Getrennte Trust-Stores als zusätzliche Testabdeckung erforderlich.
- Kein neuer Testlauf und keine Codeänderung im Rahmen dieser Prüfung.
- Aktueller Branchvergleich nach den zwischenzeitlichen Dokumentationscommits: `test` **153 ahead / 10 behind** gegenüber `main`, Merge-Base unverändert `c7f1d57e4a4661d1492e5dca42ec9a9841d3e5fd`. Die Branches bleiben divergent; kein Rebase/Merge ohne Prüfung und Freigabe.

