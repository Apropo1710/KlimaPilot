<!-- AUTO-PRODUCTION-STATUS:START -->
## Automatisch gepflegter Produktionsstatus

- Quellcommit des APK-Builds: bb4c03546a25169c1db2e9e4fdf8a2a4107db81f
- App-Version / versionCode: **0.4.92 / 122**
- Build-Zeit: 2026-11-20 16:20:28 UTC
- APK-Dateiname: **KlimaPilot.apk**
- APK-Signaturprüfung: erfolgreich (apksigner verify --print-certs)
- Build-Artefakt: kein Actions-Artefakt; APK wird im GitHub-Release latest veröffentlicht
- APK-SHA-256: a1010257ba149e608d02743245725da4d68820cc247aea37f4a429861e5c3432
- GitHub-Release: **erfolgreich veröffentlicht**; Asset KlimaPilot.apk wurde aktualisiert.
- Smartphone-Test: **nicht durch CI geprüft**; nur separat bestätigte Nutzerberichte gelten als Gerätetest.
- Updater: abgeschlossen und vom Nutzer bestätigt; Installer-Code wird durch diese Dokumentationspflege nicht verändert.
- ChatGPT-Bibliotheksakte: separate Langzeit-/Historienakte; wird durch diesen Workflow nicht verändert.

<!-- AUTO-PRODUCTION-STATUS:END -->


# KlimaPilot – verbindlicher Projektstatus

**Letzter Abgleich:** 19.11.2026, 20:15 Uhr MEZ (Inbetriebnahme der Klimaanlagen, 0.4.79–0.4.82)
**Repository:** https://github.com/Apropo1710/KlimaPilot

Diese Datei ist die **einzige maßgebliche Quelle für den aktuellen Projekt-, Branch-, PR-, Build- und Validierungsstand**. Die README dient nur als Einstieg. Die technischen Fachkonzepte enthalten Architektur und Entscheidungen, aber keine konkurrierenden Live-Statusübersichten.

## 1. Produktivstand – `main`

- Version, Quellcommit, APK-Hash und Release-Stand stehen ausschließlich im automatisch gepflegten Block am Dateianfang (wird vom Produktionsbuild geschrieben) und werden hier nicht wiederholt.
- Funktionsumfang seit 0.4.67: automatische ioBroker-Anmeldung und QR-Kontoübernahme (2f), Ein-Scan-Pairing (2d), Abfrage der Klimaanlage alle 10 s, Abgleich von Konten, Klimaprofilen, Räumen und Geräten zwischen gekoppelten Smartphones (2i), Oberflächenpflege (2j).
- Seit 0.4.79–0.4.82 (19.11.2026): robuste WF-RAC-Kopplung, schnellere Bedienung und überarbeitete Profilbedienung, siehe Abschnitt 5.
- Updates zeigen derzeit eine Play-Protect-Warnung („Trotzdem installieren“), siehe 2h.
- [Direkter APK-Download](https://github.com/Apropo1710/KlimaPilot/releases/download/latest/KlimaPilot.apk)

## 2. Entwicklungsablauf – `test` als CI-Vorprüfung

- Nutzerentscheidung 18.11.2026: Ausgeliefert wird nur die Produktionsversion über den Updater; beide Nutzer sollen keine Testversionen installieren müssen.
- Ablauf seither: Änderung zuerst auf `test` pushen → Workflow „KlimaPilot WLAN Branch Validation“ (Unit-Tests, Kompilierung) muss grün sein → dieselbe Änderung auf `main` → Produktionsbuild und Release `latest`. Gerätetest danach durch den Nutzer mit der Produktionsversion.
- Das Pre-Release `klimapilot-test-latest` wird von der Validierung weiter erzeugt, wird aber nicht mehr aktiv genutzt.
- App-Code auf `test` und `main` identisch; `main` enthält zusätzlich die Dokumentationscommits.

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
- Offen: Negativfälle der Kopplung (Abbruch, Timeout, Verbindungsverlust, ungültige/wiederholte Nachrichten).

## 5. Klimaanlagen und WF-RAC-Betrieb

- Seit 19.11.2026 in Betrieb: zwei Mitsubishi Heavy SRK15ZS-WF (Schlafzimmer, Büro), beide über KlimaPilot gekoppelt und steuerbar; Räume richtig zugeordnet.
- Am realen Gerät bestätigt (19.11.2026): Kopplung, Statusanzeige, Solltemperatur, Profilwechsel, Kompressor-Anzeige (Statusbyte 9, Bit 0x02 → „Aktiv“ bei Betrieb).
- 0.4.79: Ursache des ersten Fehlers – dieselbe Anlage war zwei Geräten zugeordnet; das Zurücksetzen einer Zuordnung löschte die gemeinsame Modulidentität. Jetzt: Zurücksetzen/Löschen entfernt die Identität nur, wenn kein anderes Gerät die Anlage nutzt; fehlende Identität wird über die gespeicherte IP wiederhergestellt, wenn dort dieselbe Aircon-ID antwortet; ausdrückliches Koppeln ersetzt veraltete Zuordnungen; Doppelzuordnung wird verhindert; WLAN-Suche zeigt die bestehende Zuordnung je Modul.
- 0.4.80: Speichern nach Neukopplung ist auch bei geteilter Sync-Kennung möglich; das umgekoppelte Gerät erhält seine eigene Kennung.
- 0.4.81 (vom Nutzer als deutlich schneller bestätigt): Temperatur-Klicks werden gesammelt und nach 1,2 s Pause als ein Befehl gesendet, Bedienelemente sperren nicht; Identitätsprüfung nur einmal pro App-Sitzung und Anlage; Kontoregistrierung am Modul wird dauerhaft gemerkt; ältere Abfrageergebnisse überschreiben keine gerade gesendeten Werte.
- 0.4.82 (Nutzerentscheidung): Profile bleiben fest, Abweichung zeigt „(geändert)“ mit ✓ zum Übernehmen; „Individuell“ speichert jede Änderung sofort; + legt ein neues Profil an; Profilauswahl wechselt sofort, der Befehl läuft im Hintergrund.
- Selbstreinigung (Self Clean): Die Anlagen melden Modellkennung 0; laut Protokoll ist das Bit nur bei Modellkennung 1/2 über WLAN verfügbar, die Original-App bietet die Funktion ebenfalls nicht. Schalter aus 0.4.83 in 0.4.84 wieder entfernt; Einstellung nur über die Fernbedienung (Menütaste). KlimaPilot überschreibt das Bit bei Modellkennung 0 nicht.
- 0.4.85: Kachel „Verbrauch“ zeigt den Energiezähler des aktuellen Laufs (Segment 0x94/0x10, (OP3<<8|OP2)×0,25 kWh, Rücksetzung beim Einschalten). Erster Schritt zur Energiekostenanzeige; am 20.11.2026 am realen Gerät bestätigt (plausible Werte, je Anlage unterschiedlich). Die Original-App zeigte zeitgleich für beide Anlagen denselben Cloud-Wert. Ab 0.4.86 (zweiter Schritt): ioBroker-Skript `scripts/iobroker/klimapilot-energie.js` (seit 0.4.92 `klimapilot.js`) liest alle 5 min getAirconStat je Anlage (nur lesen, kein Kontoplatz), schreibt den Zähler fort (Rücksetzung = neuer Lauf, erster Wert nur als Ausgangswert) und speichert Stundenwerte (31 Tage) und Tageswerte (dauerhaft) je Anlage unter `0_userdata.0.KlimaPilot.energy.units.<airconId>`. KlimaPilot schreibt Anlagenliste und Strompreis nach `0_userdata.0.KlimaPilot.energy.config` (nur bei Änderung) und zeigt beim Antippen der Kachel „Verbrauch“ den Verlauf (Stunden/Tage/Wochen/Monate/Jahre, kWh oder €, je Anlage oder alle). Fester Strompreis ohne Grundgebühr (Nutzerentscheidung 20.11.2026; dynamischer Tarif evtl. später – Stundenwerte sind dafür vorhanden). Skript mit simuliertem Modul geprüft. 0.4.87: Das Skript wird als Asset mitgeliefert; KlimaPilot richtet es per Knopf in den Einstellungen (Aufklappkachel „Energieaufzeichnung“, nur Administrator) als `script.js.common.KlimaPilot_Energie` ein, aktualisiert es beim Start und hält andere aktive Kopien an (Einfügen im ioBroker-Editor auf dem Smartphone war nicht praktikabel). Am 20.11.2026 um 09:38 am realen ioBroker eingerichtet, erste Ablesung erfolgreich. 0.4.88: Hauptseite zeigt „Verbrauch heute“ (aufgezeichneter Tageswert plus Zuwachs des Laufzählers seit der letzten Skriptablesung, mit Kosten); ohne Aufzeichnung weiter der Laufzähler. Energieansicht mit Auswahl je Raum oder „Alle“ und blauen Auswahlknöpfen im App-Stil. 0.4.89: Kalenderzeiträume Tag/Woche/Monat/Jahr/Gesamt mit ‹ › zum Blättern (Titel antippen = aktueller Zeitraum), Balken antippen springt eine Ebene tiefer; Hinweistexte entfernt. 0.4.90: Erläuterungen nur noch hinter einem ⓘ-Symbol mit Popup (Nutzervorgabe 20.11.2026 für alle Projekte: Hinweistexte als ⓘ mit Popup, nur wichtige Hinweise direkt sichtbar).
- 20.11.2026 ab ca. 10:15: Schlafzimmer-Modul (192.168.178.197, IP unverändert) lehnt Verbindungen auf Port 51443 ab (ECONNREFUSED, App und ioBroker-Skript gleichermaßen); Büro unauffällig. Wahrscheinliche Ursache: Die Smart-M-Air-App war parallel geöffnet; nach dem Schließen der Original-App war das Modul gegen 11:30 wieder erreichbar (deckt sich mit Hinweisen anderer WF-RAC-Integrationen). Empfehlung: Original-App nur bei Bedarf öffnen und danach schließen; feste IPs in der Fritzbox (am 20.11.2026 für beide Module eingerichtet: Schlafzimmer 192.168.178.197, Büro 192.168.178.176). 5 GHz bleibt aktiv. 0.4.91: Bei gekoppelter, nicht erreichbarer Anlage zeigt die Hauptseite „nicht erreichbar“ statt der Kopplungsaufforderung.
- Fritzbox-Ereignisprotokoll 20.11.2026: Beide Module melden sich stündlich vom WLAN ab und sofort wieder an (Schlafzimmer jeweils um hh:11:29, Büro um hh:11:54 bzw. hh:12:01) – bekannter eingebauter Neustart der WF-RAC-Module; die Unterbrechung dauert nur Sekunden.
- 0.4.92 (Nutzerwunsch 20.11.2026): Zeitpläne über ioBroker, je Anlage: wöchentlich (Tage + Uhrzeit) oder einmalig (Heute/Morgen/Übermorgen + Uhrzeit oder „in 30 Min./1/2/4/8 Std.“); Aktion Ausschalten oder Einschalten mit Profil bzw. eigenen Werten (Modus, Temperatur, Lüfter, Lamellen, 3D-Auto). Kachel „Zeitpläne“ unter der Anlage mit nächstem Termin, Liste mit Schaltern und Editor; Profilwerte werden bei Profiländerungen nach ioBroker nachgeführt. Ausführung durch das Skript (jetzt `scripts/iobroker/klimapilot.js`, ioBroker-Skript `script.js.common.KlimaPilot`, ersetzt `KlimaPilot_Energie`): minütliche Prüfung, Befehl direkt am Modul (eigener Codec in JS, Paritätstest gegen Kotlin `CodecScriptParityTest`), bei Ablehnung Kontoanmeldung mit derselben operatorId bzw. Warten auf Schreibsperre, Wiederholung bis 10 min. Globaler Schalter „Zeitpläne verwenden“ in den Einstellungen (Admin) setzt `enabled` in ioBroker und blendet lokal aus. Skript mit simuliertem Modul geprüft; am realen Gerät noch unbestätigt. Hinweis: Timer der Original-App nicht parallel verwenden.
- Grenze: Das Modul verlangt rund 1 s Abstand zwischen Anfragen; ein Befehl braucht damit typischerweise 2–3 s.
- Offen: Gerätetest 0.4.82 (Profilwechsel, ✓, Individuell); Energieskript in ioBroker einrichten und Energieansicht prüfen; Modi, Lüfterstufen und Luftführung am realen Gerät gezielt prüfen; Verhalten bei Verbindungsabbruch/Neustart des Moduls.

## 6. CI und Workflows

- `.github/workflows/android.yml`: Produktionsbuild bei Push auf `main` (Commits mit `[skip ci]` ausgenommen); veröffentlicht `KlimaPilot.apk` im Release `latest` und pflegt den automatischen Block oben in dieser Datei.
- `.github/workflows/wlan-validation.yml` („KlimaPilot WLAN Branch Validation“): Push auf `test` (ausgenommen `PROJECT_STATUS.md` und `app/src/debug/**`) sowie `workflow_dispatch`. Unit-Tests, Debug-APK, Release-Kotlin-Kompilierung, Prüfung von Paket, Version, Test-Zertifikat und Fehlen von `REQUEST_INSTALL_PACKAGES`; veröffentlicht die Test-APK als Pre-Release `klimapilot-test-latest`. Maven-Downloads werden bis zu fünfmal wiederholt.
- `.github/workflows/pairing-tls-validation.yml` (historischer Dateiname): API-26-Emulator-/TCP-Instrumentierungstest, nur `workflow_dispatch`.
- **API 26:** TCP-Instrumentierungstests bestanden (u. a. Lauf #63, 3/3; Lauf #69).
- **API 35:** ungeprüft. Alle Versuche vom 18.11.2026 scheiterten an der CI-Umgebung, nicht an der App: Lauf #63 mit `Can't find service: package`, die Wiederholungen (auf inzwischen gelöschten Prüfbranches) an einem Shell-Syntaxfehler im Bereitschaftsschritt, bevor ein Test lief. API 35 ist optional und kein Freigabekriterium; keine weiteren Versuche ohne konkreten neuen Ansatz.
- CI-Ergebnisse ersetzen keine Gerätetests.

## 7. Verlauf 18.11.2026 (kompakt)

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
- 20.11.2026: Vergleich mit HomePilot (gleicher Updater, gleiche Berechtigungen inkl. `REQUEST_INSTALL_PACKAGES`, gleiche Pairing-Technik; Play Protect stuft HomePilot als sicher ein) – Updater und Berechtigungen sind damit als Ursache unwahrscheinlich, die Release-Signatur ist stabil. Vermutung: Die PHA-Einstufung von 0.4.74 hängt an Paket/Signatur. Einspruch am 20.11.2026 eingereicht (APK 0.4.91, SHA256 59227cbd…e8425, VirusTotal 0/65). Google antwortet nicht; Erfolg zeigt sich nur daran, dass beim nächsten Update keine Warnung mehr erscheint. Rückfalloption, falls die Warnung nach 1–2 Wochen bleibt: neuer Release-Signaturschlüssel ohne Rotation (einmalige Neuinstallation auf beiden Handys; laut Nutzer dank ioBroker-Sync in ca. 4 Minuten erledigt) – Entscheidung erst dann. Ursprünglich geplant: Play-Protect-Einspruch über https://support.google.com/googleplay/android-developer/contact/protectappeals mit Paketname `de.klimapilot.app` und SHA256 der jeweils aktuellen Release-APK (vorher bei VirusTotal hochladen). Keine Codeänderungen auf Verdacht.

### 2i. Synchronisation zwischen gekoppelten Smartphones (0.4.76/0.4.77)
- Commits `511a179` und `18a1dd3`: nach der gegenseitigen TCP-Authentifizierung tauschen beide Geräte einen Datenstand aus (`ClimatePeerDataSync`). Konten: höhere Revision gewinnt; Profile über `ClimateProfileSyncEngine`; Admin-Konfiguration: neuere Änderungszeit gewinnt; ioBroker-Adresse nur, wenn keine gespeichert ist.
- Unit-Tests `ClimatePeerDataSyncTest`. Gerätetest 20:45 Uhr bestanden, danach Produktionsrelease 0.4.77.

### 2j. Oberflächenpflege 0.4.78
- Kopplungskarte als Ausklappkachel; Fingerprints und Signaturhinweise aus der Oberfläche entfernt (Prüfungen laufen unverändert im Hintergrund).
- „Nach Updates suchen“ und „Update laden“ über die volle Breite (nur Layout; Updater-Ablauf unverändert).

### Übernahme nach `main`
- `main` wurde per Merge-Commit `12096f3` in `test` geholt (Konflikte zugunsten von `test`, identische Speicherschlüssel), WLAN-Workflow #250 erfolgreich, danach `main` per Fast-Forward nachgezogen (Produktionsbuild #239, 0.4.74). Seit 18.11.2026 gilt der in Abschnitt 2 beschriebene Ablauf (CI-Vorprüfung auf `test`, Auslieferung über `main`).

## 8. Verbindliche Regeln

- Updater und Installationsablauf dürfen **niemals geändert** werden: `ACTION_VIEW` + `application/vnd.android.package-archive` + FileProvider + URI-Leseberechtigung + ClipData + `REQUEST_INSTALL_PACKAGES` im Manifest (nur reine Layout-Änderungen nach ausdrücklichem Wunsch).
- Änderungen zuerst auf `test` durch die CI prüfen lassen, erst bei grünem Lauf nach `main`. Produktionsbuild nur über Push auf `main`; ausgeliefert wird nur die Produktionsversion.
- Commits, die nur Dokumentation auf `main` ändern, mit `[skip ci]` kennzeichnen, damit kein Release entsteht.
- Bestehende ioBroker-Verbindung, WF-RAC-Steuerung und Produktionsdaten schützen.

## 9. Produkt- und Hardwaregrenzen

- Geräte: zwei Mitsubishi Heavy SRK15ZS-WF, seit 19.11.2026 in Betrieb; bestätigte Funktionen siehe Abschnitt 5, alles andere gilt bis zum Test als unbestätigt.
- Adapter `mhi-wfrac` ist nicht eingerichtet; für KlimaPilot nicht erforderlich, da die App die Anlagen direkt steuert.
- SENEC/`energyfluss` nicht als Datenquelle einplanen.
- Produktionsverbindung zu ioBroker: WebSocket `ws.0`, Port 8084; Alive-Prüfung über `system.adapter.admin.0.alive`.

## 10. Nächste Schritte

1. Gerätetest 0.4.82: Profilwechsel, ✓-Übernahme, „Individuell“, auch auf dem zweiten Handy.
2. Modi, Lüfterstufen und Luftführung an beiden Anlagen gezielt prüfen.
3. Play-Protect-Einstufung klären (Updates müssen derzeit mit „Trotzdem installieren“ bestätigt werden).
4. Optional: Negativfälle der Kopplung testen; gekoppelte Smartphones in der Oberfläche entfernbar machen.

## 11. Dokumentationsstruktur und Pflege

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
