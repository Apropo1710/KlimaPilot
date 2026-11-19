<!-- AUTO-PRODUCTION-STATUS:START -->
## Automatisch gepflegter Produktionsstatus

- Quellcommit des APK-Builds: 8eb61dab1567254ce5309d1e4ae181af71bdc569
- App-Version / versionCode: **0.4.81 / 111**
- Build-Zeit: 2026-11-19 18:27:15 UTC
- APK-Dateiname: **KlimaPilot.apk**
- APK-Signaturprüfung: erfolgreich (apksigner verify --print-certs)
- Build-Artefakt: kein Actions-Artefakt; APK wird im GitHub-Release latest veröffentlicht
- APK-SHA-256: 1f3aa38597c1fdf90ec754b0521cbd4e2ced52ef911d81bd2b07f19f8912dfcf
- GitHub-Release: Veröffentlichung wird in diesem Workflow nach dem Statuscommit abgeschlossen.
- Smartphone-Test: **nicht durch CI geprüft**; nur separat bestätigte Nutzerberichte gelten als Gerätetest.
- Updater: abgeschlossen und vom Nutzer bestätigt; Installer-Code wird durch diese Dokumentationspflege nicht verändert.
- ChatGPT-Bibliotheksakte: separate Langzeit-/Historienakte; wird durch diesen Workflow nicht verändert.

<!-- AUTO-PRODUCTION-STATUS:END -->


# KlimaPilot – verbindlicher Projektstatus

**Letzter Abgleich:** 19.11.2026, 06:55 Uhr MEZ (Dokumentation bereinigt)
**Repository:** https://github.com/Apropo1710/KlimaPilot

Diese Datei ist die **einzige maßgebliche Quelle für den aktuellen Projekt-, Branch-, PR-, Build- und Validierungsstand**. Die README dient nur als Einstieg. Die technischen Fachkonzepte enthalten Architektur und Entscheidungen, aber keine konkurrierenden Live-Statusübersichten.

## 1. Produktivstand – `main`

- Version, Quellcommit, APK-Hash und Release-Stand stehen ausschließlich im automatisch gepflegten Block am Dateianfang (wird vom Produktionsbuild geschrieben) und werden hier nicht wiederholt.
- Funktionsumfang seit 0.4.67: automatische ioBroker-Anmeldung und QR-Kontoübernahme (2f), Ein-Scan-Pairing (2d), Abfrage der Klimaanlage alle 10 s, Abgleich von Konten, Klimaprofilen, Räumen und Geräten zwischen gekoppelten Smartphones (2i), Oberflächenpflege (2j).
- Updates zeigen derzeit eine Play-Protect-Warnung („Trotzdem installieren“), siehe 2h.
- [Direkter APK-Download](https://github.com/Apropo1710/KlimaPilot/releases/download/latest/KlimaPilot.apk)

## 2. Entwicklungsstand – `test`

- `test` enthält denselben Code wie `main`; `main` ist nur um Dokumentationscommits voraus.
- Weiterentwicklung auf `test`, Testbuilds als Pre-Release `klimapilot-test-latest` (`de.klimapilot.app.debug`, Testsignatur `230865b9…65ecfb`, Installation über den Direktlink). Übernahme nach `main` nach Gerätetest und auf Wunsch des Nutzers.
- Letzter Test-Build: WLAN-Workflow #255 erfolgreich (#254 scheiterte an einem Maven-Netzwerkfehler, siehe 2j).

## 3. Branches und Pull Requests

- Offene PRs: **keine**.
- PR #6 (`test` → `main`, WLAN-Sync und Pairing) am 18.11.2026 gemergt. PR #3 (Änderungsjournal) am 17.11.2026 und PR #1 (direkte WF-RAC-Anbindung) am 15.11.2026 gemergt. PR #2, #4 und #5 ohne Merge geschlossen.
- Remote-Branches: nur `main` und `test`. Alle alten Feature-, Validierungs- und Dokumentationsbranches wurden am 19.11.2026 gelöscht.
- Releases: `latest` (Produktion, nur `KlimaPilot.apk`) und Pre-Release `klimapilot-test-latest` (Testversion). Das alte Pre-Release `wlan-test-latest` wurde am 19.11.2026 gelöscht.

## 4. Pairing- und Synchronisationsstatus

- Kopplung: Ein-Scan-QR mit signiertem ACK, in Produktions- und Testversion; Fingerprints werden nur im Hintergrund geprüft und nicht mehr angezeigt.
- Abgleich zwischen gekoppelten Geräten: Konten, Klimaprofile (inkl. aktivem Profil und individuellen Einstellungen), Räume, Geräte, WF-RAC-Modulidentitäten, ioBroker-Adresse. Automatisch bei geöffneter App, alle 30 s. Details in `LOCAL_WIFI_SYNC_DESIGN.md`, Abschnitt 6.
- Aktueller Zustand der Klimaanlage: jedes Smartphone liest ihn direkt per WF-RAC alle 10 s (kein ioBroker nötig).
- ioBroker-Profilsync läuft unverändert parallel; die gemeinsame `ClimateProfileSyncEngine` sorgt für gleiche Konfliktregeln.
- TCP ist unverschlüsselt (Nutzerentscheidung, TLS/mTLS verworfen); Inhalte inkl. Passwort-Hashes sind im eigenen WLAN mitlesbar. Authentifizierung über gegenseitige ECDSA-Signaturen mit Keystore-Schlüsseln.
- Gerätetests 18.11.2026: Kopplung Smartphone/Tablet (0.4.69-debug, 18:04 Uhr) und Raumübertragung (0.4.77, 20:45 Uhr) bestanden; Produktions-Apps beider Nutzer gekoppelt.
- 19.11.2026: Klimaanlagen in Betrieb. Erster Praxistest deckte auf: Wurde dieselbe Klimaanlage zwei Geräten zugeordnet und eine Zuordnung zurückgesetzt, ging die Modulidentität für beide verloren (Gerät „Offline“), und erneutes Koppeln scheiterte an veralteten Zuordnungen. Behoben in 0.4.79: Zurücksetzen/Löschen entfernt die Identität nur, wenn kein anderes Gerät die Anlage nutzt; fehlende Identität wird über die gespeicherte IP wiederhergestellt, wenn dort dieselbe Aircon-ID antwortet; ausdrückliches Koppeln ersetzt veraltete Zuordnungen; Doppelzuordnung wird verhindert; WLAN-Suche zeigt die bestehende Zuordnung je Modul.
- 0.4.80: Speichern nach Neukopplung scheiterte, weil die Sync-Kennung aus der früheren Doppelkopplung noch zwei Geräten zugeordnet war. Jetzt bekommt das umgekoppelte Gerät seine eigene Kennung; die geteilte bleibt beim anderen Gerät samt ausstehender Änderungen.
- 0.4.81: Beide Anlagen laufen (Nutzertest 19.11.2026). Schnellere Bedienung: Temperatur-Klicks werden gesammelt und nach 1,2 s Pause als ein Befehl gesendet, +/- und Schieberegler bleiben bedienbar; Identitätsprüfung (getDeviceInfo) nur einmal pro App-Sitzung und Anlage, erste Anzeige ohne vorherige Prüfung; Kontoregistrierung am Modul wird dauerhaft gemerkt; ältere Abfrageergebnisse überschreiben keine gerade gesendeten Werte.
- Offen: Gerätetest 0.4.81 (Anzeigedauer, Temperaturbedienung) mit beiden Anlagen, langsame erste Statusanzeige prüfen, Negativfälle der Kopplung (Abbruch, Timeout, Verbindungsverlust, ungültige/wiederholte Nachrichten).

## 5. CI und Workflows

- `.github/workflows/android.yml`: Produktionsbuild bei Push auf `main` (Commits mit `[skip ci]` ausgenommen); veröffentlicht `KlimaPilot.apk` im Release `latest` und pflegt den automatischen Block oben in dieser Datei.
- `.github/workflows/wlan-validation.yml` („KlimaPilot WLAN Branch Validation“): Push auf `test` (ausgenommen `PROJECT_STATUS.md` und `app/src/debug/**`) sowie `workflow_dispatch`. Unit-Tests, Debug-APK, Release-Kotlin-Kompilierung, Prüfung von Paket, Version, Test-Zertifikat und Fehlen von `REQUEST_INSTALL_PACKAGES`; veröffentlicht die Test-APK als Pre-Release `klimapilot-test-latest`. Maven-Downloads werden bis zu fünfmal wiederholt.
- `.github/workflows/pairing-tls-validation.yml` (historischer Dateiname): API-26-Emulator-/TCP-Instrumentierungstest, nur `workflow_dispatch`.
- **API 26:** TCP-Instrumentierungstests bestanden (u. a. Lauf #63, 3/3; Lauf #69).
- **API 35:** ungeprüft. Alle Versuche vom 18.11.2026 scheiterten an der CI-Umgebung, nicht an der App: Lauf #63 mit `Can't find service: package`, die Wiederholungen (auf inzwischen gelöschten Prüfbranches) an einem Shell-Syntaxfehler im Bereitschaftsschritt, bevor ein Test lief. API 35 ist optional und kein Freigabekriterium; keine weiteren Versuche ohne konkreten neuen Ansatz.
- CI-Ergebnisse ersetzen keine Gerätetests.

## 6. Verlauf 18.11.2026 (kompakt)

### 2a–2c. Kontenübernahme aus ioBroker und Pairing-ACK
- Kontenübernahme direkt aus `0_userdata.0.KlimaPilot.accounts` über die WebSocket-Adresse; vorhandene lokale Konten werden nie überschrieben (0.4.68).
- Pairing: Responder speichert den Einladenden erst nach einem ECDSA-signierten Speicher-ACK (`ClimatePairingAcknowledgement`, Commits `f7f6eaf`, `b0e430e`, `5dcc6ed`, `aa633a1`).
- `pairing-tls-validation.yml` startet seit Commit `2e6a817` nicht mehr automatisch.

### 2d. Ein-Scan-Pairing über lokalen WLAN-Rückkanal
- Einladungsgerät öffnet einen One-shot-TCP-Listener und bindet Host/Port in die signierte QR-Einladung ein; das zweite Gerät scannt einmal und wählt „Koppeln“ oder „Abbrechen“. Längenpräfixierte Frames, max. 4 KiB, begrenzte Zeiten; nur numerische lokale IP-Adressen.
- Grundlage Commit `07a774c` (WLAN-Workflow #240), Praxistest bestanden (siehe Abschnitt 4).

### 2e. Feste Test-Signatur
- Debug-Builds werden in der CI mit dem Testschlüssel aus dem Secret `KLIMAPILOT_TEST_KEYSTORE_BASE64` signiert (PKCS12, RSA 2048, gültig bis 10.11.2056, Zertifikat SHA-256 `230865b90b0c37e6a7935fd4f5e0fad0626853a44921a7a2f7d6185b9665ecfb`). Lokale Builds ohne Variablen nutzen den Android-Standard-Debugschlüssel. Sicherungskopie des Schlüssels liegt beim Nutzer.
- Ältere Testversionen mit anderer Signatur erfordern einmalig eine Neuinstallation.

### 2f. Automatische ioBroker-Einrichtung und QR-Kontoübernahme
- `IoBrokerAutoSetup.kt` (Commit `7f266a7`): einheitliche Adressablage, tolerantes Lesen alter Formate, automatischer Kontenimport.
- Administratoren können unter Einstellungen → Benutzerkonto → „Anderes Smartphone einrichten“ einen 10 Minuten gültigen QR-Code anzeigen (PBKDF2-Hashes, keine Klartextpasswörter, plus ioBroker-Adresse).
- Die ursprüngliche automatische Suche im WLAN (Port 8084) wurde in 0.4.75 wieder entfernt (siehe 2h). Anmeldung nutzt die gespeicherte Adresse, sonst `ws://192.168.178.54:8084`, sonst Adressfeld oder QR-Übernahme.

### 2g. Updater der Testversion – überholt
- Die Test-Updater-Anpassung (`febef43`, `e8cb6f7`) wurde in 0.4.75 vollständig zurückgenommen. Testversionen werden über den Direktlink installiert.

### 2h. Play-Protect-Blockade und Updater-Korrektur
- 0.4.74 wurde beim Update von Play Protect als „Schädliche App blockiert“ abgewiesen.
- Ohne `REQUEST_INSTALL_PACKAGES` öffnet „Update laden“ keinen Installationsdialog (Gerätetest 18.11.2026). Die frühere Doku-Aussage, die Berechtigung werde nicht verwendet, war falsch: Seit 0.4.23 enthielt jede Produktionsversion sie.
- 0.4.75: `GithubPrivateUpdates.kt` und `GithubUpdateCard.kt` byte-identisch mit dem 0.4.67-Build (`608912f`), WLAN-Suche entfernt. Gerätetest 19:57–19:59 Uhr: Installationsdialog öffnet sich, Play Protect bietet „Trotzdem installieren“, Update auf 0.4.75 erfolgreich, Anmeldung und Einstellungen erhalten.
- **Offen:** Ursache der Play-Protect-Einstufung. Kein weiterer Umbau am Updater.

### 2i. Synchronisation zwischen gekoppelten Smartphones (0.4.76/0.4.77)
- Commits `511a179` und `18a1dd3`: nach der gegenseitigen TCP-Authentifizierung tauschen beide Geräte einen Datenstand aus (`ClimatePeerDataSync`). Konten: höhere Revision gewinnt; Profile über `ClimateProfileSyncEngine`; Admin-Konfiguration: neuere Änderungszeit gewinnt; ioBroker-Adresse nur, wenn keine gespeichert ist.
- Unit-Tests `ClimatePeerDataSyncTest`. Gerätetest 20:45 Uhr bestanden, danach Produktionsrelease 0.4.77.

### 2j. Oberflächenpflege 0.4.78
- Kopplungskarte als Ausklappkachel; Fingerprints und Signaturhinweise aus der Oberfläche entfernt (Prüfungen laufen unverändert im Hintergrund).
- „Nach Updates suchen“ und „Update laden“ über die volle Breite (nur Layout; Updater-Ablauf unverändert).

### Übernahme nach `main`
- `main` wurde per Merge-Commit `12096f3` in `test` geholt (Konflikte zugunsten von `test`, identische Speicherschlüssel), WLAN-Workflow #250 erfolgreich, danach `main` per Fast-Forward nachgezogen (Produktionsbuild #239, 0.4.74). Seitdem werden Versionen nach Gerätetest von `test` nach `main` übernommen.

## 7. Verbindliche Regeln

- Updater und Installationsablauf dürfen **niemals geändert** werden: `ACTION_VIEW` + `application/vnd.android.package-archive` + FileProvider + URI-Leseberechtigung + ClipData + `REQUEST_INSTALL_PACKAGES` im Manifest (nur reine Layout-Änderungen nach ausdrücklichem Wunsch).
- Neue Funktionen zuerst auf `test` mit der Testversion prüfen; Übernahme nach `main` auf Wunsch des Nutzers. Produktionsbuild nur über Push auf `main`.
- Commits, die nur Dokumentation auf `main` ändern, mit `[skip ci]` kennzeichnen, damit kein Release entsteht.
- Bestehende ioBroker-Verbindung, WF-RAC-Steuerung und Produktionsdaten schützen.

## 8. Produkt- und Hardwaregrenzen

- Zielgerät: Mitsubishi Heavy SRK15ZS-WF.
- Die Klimaanlage wird am 19.11.2026 in Betrieb genommen; reale Hardware-/Firmwarefunktionen sind bis zum Test nicht bestätigt.
- Adapter `mhi-wfrac` ist noch nicht eingerichtet und wird erst nach Installation der Klimaanlage verwendet.
- SENEC/`energyfluss` nicht als Datenquelle einplanen.
- Produktionsverbindung zu ioBroker: WebSocket `ws.0`, Port 8084; Alive-Prüfung über `system.adapter.admin.0.alive`.

## 9. Nächste Schritte

1. Inbetriebnahme der Klimaanlage: auf dem Admin-Handy hinzufügen und WF-RAC koppeln; prüfen, dass sie auf dem zweiten Handy erscheint, von dort steuerbar ist und Änderungen nach ~10 s auf beiden Geräten sichtbar sind.
2. Profilübertragung mit realer Anlage prüfen.
3. Play-Protect-Einstufung klären (Updates müssen derzeit mit „Trotzdem installieren“ bestätigt werden).
4. Optional: Negativfälle der Kopplung testen; gekoppelte Geräte in der Oberfläche entfernbar machen.

## 10. Dokumentationsstruktur und Pflege

### Einstieg
- `README.md`: kurze Projektbeschreibung, technische Basis, feste Grundregeln und Links. Keine zweite Statusübersicht.

### Einzige Statusquelle
- `PROJECT_STATUS.md`: Produktivversion, Branches, Builds, CI-Nachweise, aktueller Arbeitsstand, Regeln und nächste Schritte.

### Technische Fachkonzepte
- `DIRECT_WLAN_MIGRATION.md`: direkter WF-RAC-Kommunikationsweg und Hardwaretestanforderungen.
- `LOCAL_WIFI_SYNC_DESIGN.md`: Architektur und Ablauf der lokalen Smartphone-Synchronisation.
- `PAIRING_PROTOCOL_DECISIONS.md`: Identität, Pairing, Trust und Sicherheitsentscheidungen.
- `SYNC_RESILIENCE_PLAN.md`: Outbox, Konfliktbehandlung, Wiederanlauf und Zuverlässigkeitsanforderungen.

Es darf keine parallele Statusdatei angelegt werden. Neue Stände werden in die passenden Abschnitte eingearbeitet, nicht als zusätzliche Abgleich-Abschnitte oben angehängt.

### Verbindliche Aktualisierungsroutine
1. Vor jeder Änderung den aktuellen GitHub-Stand von `main`, `test`, PRs und relevanten Actions prüfen.
2. Betroffene Code- und Fachdateien ausreichend lesen.
3. Nach Änderungen den maßgeblichen Status in dieser Datei aktualisieren; überholte Aussagen ersetzen statt neue daneben zu stellen.
4. Fachkonzepte nur bei Änderungen an Architektur, Entscheidungen oder technischen Anforderungen ändern.
5. README nur bei Änderungen an Projektbeschreibung, Einstieg oder festen Grundregeln aktualisieren.
6. Version, Build und Release-Stand nicht von Hand pflegen – sie stehen nur im automatischen Block.
