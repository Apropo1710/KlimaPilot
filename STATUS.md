# KlimaPilot – aktueller Projektstatus

## Aktueller Kontenlogin-Arbeitsstand – 18.11.2026, 13:52 MEZ

- Live geprüfter `main`-HEAD vor Fix: `9872d3e5462729f2bfdd38af0b049505ab4a90b9`.
- Letzter bestätigter erfolgreicher Produktionsbuild: #234, Version 0.4.65 / 95, Commit `f61b523`.
- #235 fehlgeschlagen (Login-Screen-Parameter/entfernter Export-Callback); #236 abgebrochen; #237 fehlgeschlagen: fehlende Repository-Methode `replaceEmptyWithImported`.
- Fehlerursache in #237: `MainActivity.kt` ruft die nicht vorhandene Methode auf `KlimaPilotAccountRepository` auf.
- Ziel bleibt direkter Abruf der bestehenden ioBroker-Kontenregistry beim Login auf einem neuen Gerät – ausdrücklich kein Datei-Export.
- Vorbereiteter Fix: Methode ergänzt, die nur fehlende oder gültig leere lokale Konten-Registry ersetzt; vorhandene/beschädigte lokale Daten bleiben geschützt. Vier Robolectric-Tests ergänzt.
- Vorbereitete Version: **0.4.67 / versionCode 97**. Noch kein Commit, Build oder Release bestätigt.
- Updater bleibt unangetastet.

---

**Abgleich:** 18.11.2026, 13:52 Uhr MEZ (Live-Prüfung GitHub)  
**Repository:** https://github.com/Apropo1710/KlimaPilot  
**Branch:** `main`  
**Quellversion auf main vor Fix:** **0.4.66 / versionCode 96**; vorbereiteter Fix: **0.4.67 / 97** (noch nicht gebaut)

## Verifizierter Produktionsstand

- Aktueller `main`-HEAD: `356842b20acbc042a21cb4d06e664dbd45654d98` (Dokumentationscommit vom 17.11.2026, 11:29:12 UTC).
- Letzter funktionaler Merge: PR #3 „Absicherung der Klimaprofil-Synchronisation durch dauerhaftes Änderungsjournal“, Merge-Commit `ee65841387824bca7d2b07ca80f71501540af157`.
- Versionscommit: `1a1d7834c22a2ac0726331fc9f49995cc253dc4a`; Version aus `app/build.gradle.kts`: **0.4.64 / versionCode 94**.
- APK-Build-Quellcommit: `a87e0d8e2cfed5e1d2c3399466e1edaef1a66d00` (Dokumentationscommit auf demselben App-Code-Baum).
- GitHub Actions [Build #229](https://github.com/Apropo1710/KlimaPilot/actions/runs/44073807709): **completed / success** für exakt diesen Build-Quellcommit.
- Release `latest`: Asset `KlimaPilot.apk`, Asset-ID `758790648`, 11.370.135 Bytes; zuletzt aktualisiert 17.11.2026, 11:29:11 UTC.
- APK-SHA-256 laut automatisch gepflegtem Produktionsnachweis: `1553402f6baebe028d6876581aef235aa3abf89a7047150e4b47a429ef1730e2`; Signaturprüfung erfolgreich.
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

- [PR #3](https://github.com/Apropo1710/KlimaPilot/pull/3) wurde am 17.11.2026 um 12:14:49 Uhr MEZ geschlossen und gemergt.
- Gemergter PR-Head: `c111d1376b14801ec83c0eddbbc42de3dbb35ff0`; Merge-Commit auf `main`: `ee65841387824bca7d2b07ca80f71501540af157`.
- Die PR-Validierung war erfolgreich; der Produktionsbuild #229 enthält den gemergten Code.
- Die in der unabhängigen Prüfung genannten niedrigen Restrisiken (Fehlerbehandlung einzelner Upload-Stufen, Recovery eines beschädigten Journals und theoretischer Logical-Clock-Überlauf) bleiben mögliche spätere Härtungspunkte.
- Robolectric-Tests ersetzen keine realen Prozess-Kill- oder Zwei-Smartphone-Tests.

## Aktueller WLAN-Pairing-Teststand

- [PR #6](https://github.com/Apropo1710/KlimaPilot/pull/6) ist offen und Draft; Head-Branch `test`, aktueller Head `5175c47d3eb743fdb31a50e9d8c674b17dd2ff00`, Mergeability aktuell **clean**. Nicht gemergt.
- Für exakt diesen Head sind WLAN Branch Validation #190 / Run `44193818224` und Local TCP Peer Authentication #51 / Run `44193818323` **completed / success**.
- Der vorherige Local-TCP-Lauf #50 / Run `44193730745` schlug auf dem älteren Commit `982a6e25` fehl; der nachfolgende Commit `5175c47` wurde erfolgreich geprüft. Der alte Fehler ist daher kein aktueller Fehlschlag des Branch-Heads.
- Der jüngste Commit stellt API 26 als verpflichtenden Test ein und macht API 35 manuell. Das ist eine CI-Matrix-Einstellung, keine Änderung an `minSdk`, `targetSdk` oder der Gerätekompatibilität der App.

## Zuletzt umgesetzt

- 0.4.64: das dauerhafte Änderungsjournal aus PR #3 ist in main integriert; individuelle Klimaeinstellungen und ausstehende Profiländerungen werden abgesichert synchronisiert.
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

- `main` bleibt der alleinige Produktionsbranch. Es wird nichts automatisch gemergt oder gelöscht.
- `test`: PR #6, **139 Commits voraus / 0 zurück** gegenüber `main`; aktiver zentraler WLAN-/Pairing-Testbranch.
- `feature/local-wifi-pairing`: **101 Commits voraus / 0 zurück**; kein eigener offener PR.
- `feature/local-wifi-profile-sync`: **58 Commits voraus / 0 zurück**; kein eigener offener PR.
- Die drei lokalen WLAN-/Pairing-Branches sind getrennte Arbeitsstände. Ihre Commits werden nicht automatisch untereinander oder nach `main` übernommen.
- `feature/direct-wf-rac-wlan`: PR #1 wurde am 15.11.2026 gemergt; danach weiterentwickelt. Aktueller Vergleich **23 Commits voraus / 316 zurück**, kein offener PR. Der Diff enthält WLAN-Diagnostik, separaten Test-Updatekanal und Änderungen an Build-/App-Dateien; diese Folgeänderungen sind nicht Bestandteil von `main`.
- `fix/durable-profile-change-journal`: PR #3 ist gemergt; Branch liegt **0 Commits voraus / 15 zurück** und enthält keinen zusätzlichen Commit gegenüber `main`.
- `docs/status-refresh-2026-11-16`: PR #2 wurde geschlossen, nicht gemergt. Branch behalten, aber nicht ungeprüft übernehmen.
- `docs/status-reconcile-2026-11-15`: historischer Dokumentationsbranch ohne offenen PR.

## Offene Prüfungen

- Konten-Registry mit absichtlich beschädigten Daten (Schutz aus 0.4.50).
- Pairing-Reset bei konkurrierenden Steuerbefehlen (0.4.52).
- Modellabhängige Luftführung/3D-Automatik und Profilanwendung am echten Gerät (0.4.53 M3/M4).
- Profil-Synchronisierung mit zwei Smartphones und realem ioBroker (0.4.53 M5).
- Hardwaretests nach Installation der Klimaanlage.
- Nutzer-Endtest der installierten 0.4.64 noch nicht bestätigt.

## Updater – abgeschlossen

Der Updater funktioniert und wurde vom Nutzer erfolgreich getestet. Keine offene Updater-Aufgabe. Installationsablauf nicht verändern: `ACTION_VIEW`, MIME `application/vnd.android.package-archive`, FileProvider-URI, URI-Berechtigung und `ClipData`. `REQUEST_INSTALL_PACKAGES` nicht einführen; nicht auf `ACTION_INSTALL_PACKAGE` umstellen; keine automatische Updateprüfung ergänzen.

## Letzte Builds

| Run | Version | Ergebnis |
|---|---|---|
| [#229](https://github.com/Apropo1710/KlimaPilot/actions/runs/44073807709) | **0.4.64 / 94** | **success** – aktueller Release |
| [#227](https://github.com/Apropo1710/KlimaPilot/actions/runs/44073072643) | 0.4.64 / 94 | success – vorheriger Release-Lauf |
| [#226](https://github.com/Apropo1710/KlimaPilot/actions/runs/44073032166) | 0.4.63 / 93 | success – Zwischenrelease |
| [#225](https://github.com/Apropo1710/KlimaPilot/actions/runs/44072272599) | 0.4.63 / 93 | success – Merge-Stand |
| #222 | 0.4.63 / 93 | success – historischer Release |
| #211 | 0.4.59 / 89 | success – historischer Release |
| #210 | 0.4.58 / 88 | success |
| #209 | 0.4.57 / 87 | success |
| #208 / #207 | 0.4.57 Zwischenstände | failure – historisch, durch spätere Builds abgelöst |
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