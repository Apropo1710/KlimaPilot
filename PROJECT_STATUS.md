<!-- AUTO-PRODUCTION-STATUS:START -->
## Automatisch gepflegter Produktionsstatus

- Quellcommit des APK-Builds: 6bf356e7b7590b5d6252eb33aa5b240851c02065
- App-Version / versionCode: **0.5.17 / 147**
- Build-Zeit: 2026-11-21 15:58:23 UTC
- APK-Dateiname: **KlimaPilot.apk**
- APK-Signaturprüfung: erfolgreich (apksigner verify --print-certs)
- Build-Artefakt: kein Actions-Artefakt; APK wird im GitHub-Release latest veröffentlicht
- APK-SHA-256: a1113c06c9f41aa86efaa09d7794851dfc97c790fc3aa625a8272e39c00ac400
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
- 0.4.92 (Nutzerwunsch 20.11.2026): Zeitpläne über ioBroker, je Anlage: wöchentlich (Tage + Uhrzeit) oder einmalig (Heute/Morgen/Übermorgen + Uhrzeit oder „in 30 Min./1/2/4/8 Std.“); Aktion Ausschalten oder Einschalten mit Profil bzw. eigenen Werten (Modus, Temperatur, Lüfter, Lamellen, 3D-Auto). Kachel „Zeitpläne“ unter der Anlage mit nächstem Termin, Liste mit Schaltern und Editor; Profilwerte werden bei Profiländerungen nach ioBroker nachgeführt. Ausführung durch das Skript (jetzt `scripts/iobroker/klimapilot.js`, ioBroker-Skript `script.js.common.KlimaPilot`, ersetzt `KlimaPilot_Energie`): minütliche Prüfung, Befehl direkt am Modul (eigener Codec in JS, Paritätstest gegen Kotlin `CodecScriptParityTest`), bei Ablehnung Kontoanmeldung mit derselben operatorId bzw. Warten auf Schreibsperre, Wiederholung bis 10 min. Globaler Schalter „Zeitpläne verwenden“ in den Einstellungen (Admin) setzt `enabled` in ioBroker und blendet lokal aus. 0.4.93: Der Schalter wird zusätzlich über die Handy-zu-Handy-Synchronisierung übertragen (neuere Änderung gewinnt), damit er auch ohne ioBroker auf beiden Handys gilt. Am 20.11.2026 vom Nutzer getestet: einmaliger Zeitplan hat geschaltet. 0.4.94: Zeitplan-Editor neu gestaltet (Kopfzeile mit ✕/Sichern, Hero-Karte mit Uhrzeit und Vorschau, kompakte Segment-Umschalter, runde Wochentage, Einstellungen als Listenzeilen mit Schieberegler/Auswahlmenü); Statuszeile der Klimaprofile zeigt nur noch Fehler. 0.4.95: Ausgeführte einmalige Zeitpläne werden automatisch deaktiviert. Seit 20.11.2026 wiederholt „failed to connect to 192.168.178.54 (port 8084) after 8000ms“ beobachtet (zeitlich mit den neuen ioBroker-Funktionen): alle ioBroker-Verbindungen nutzen jetzt einen gemeinsamen HTTP-Client (vorher ein eigener pro Verbindung, dessen Thread-/Verbindungspools liegen blieben), die Zeitplan-Kachel fragt nur noch alle 5 statt jede Minute ab, und ein einzelner fehlgeschlagener Profil-Abgleich wird nicht mehr angezeigt (erst ab dem zweiten in Folge). Ursache noch nicht sicher; weiter beobachten, ggf. Logs des ws-Adapters prüfen. Meldung trat mit 0.4.95 weiterhin auf; Nutzer prüft ioBroker am PC. 0.4.96: Raumauswahl fest unter der Kopfzeile (ohne Überschrift „Raum“), Reihenfolge der Anlagenseite: … 3D-Auto, Zeitpläne, Verbrauch, Kompressor/Verbindung. ws.0-Protokoll vom 20.11.2026 zeigt keine Fehler, nur kurze, saubere Verbindungen (ca. 100–150 ms); die Zeitüberschreitung entsteht also schon beim TCP-Aufbau, bevor ioBroker etwas davon sieht (WLAN des Handys bzw. kurzes Stocken des ioBroker-Rechners, RAM 76 %). 0.4.97: Verbindungsaufbau-Timeout 5 statt 8 s, ein fehlgeschlagener Aufbau wird nach 0,5 s still wiederholt; erst der zweite Fehlschlag wird gemeldet. 0.4.98: Profil-Editor zeigt horizontale Luftführung und 3D-Automatik immer an, wenn das Modell sie nicht ausdrücklich ausschließt (vorher in „Klimaprofile verwalten“ ohne Live-Zustand ausgeblendet mit Hinweis „Modell noch nicht ausgelesen“); Hinweistexte im Editor entfernt. 0.4.99: Profil-Editor neu gestaltet wie der Zeitplan-Editor (eigene Datei `ClimateProfileEditor.kt`, Vollbild mit ✕/Sichern, Name in der Hero-Karte mit Vorschau, je Einstellung ein Schalter „Profil setzt diesen Wert“ statt Haken, ausgeschaltet = „unverändert“, Modus als Chips, Temperatur mit −/+, Lüfter als Schieberegler, Luftführung als Auswahlmenü, Erklärung hinter ⓘ). UI-Bausteine aus `ClimateSchedules.kt` sind dafür `internal`. 0.5.0: Zeitplan-Editor liest die Klimaprofile bei jedem Öffnen neu (vorher erst nach bis zu 5 min, dadurch war „Profil“ nach dem Anlegen eines ersten Profils nicht wählbar); „Profil“ ist auch ohne Profile wählbar und sagt dann, dass es noch keines gibt. Schieberegler und Schalter in beiden Editoren durchgehend blau statt Lila. 0.5.1: „Klimaprofile verwalten“ übersichtlicher: je Anlage eine weiße Karte mit Raumnamen als Überschrift, Profile als Zeilen (Symbol im farbigen Kreis, Name, Hauptwerte, Luftführung leiser darunter), Antippen öffnet den Editor, Löschen über Papierkorb, „Neues Profil“ direkt in der Karte. 0.5.2 (Nutzerwunsch): Profile nach Jahreszeit. Jedes Profil ist Winter, Sommer oder Ganzjährig (neues Feld `season`, in Speicher, ioBroker-Ereignissen und Handy-Sync); ohne Zuordnung folgt es dem Modus (Heizen = Winter, Kühlen = Sommer, sonst ganzjährig). Im Profil-Auswahlmenü oben Umschalter ☼ Winter / ❄ Sommer, darunter nur passende und ganzjährige Profile (das aktive bleibt immer sichtbar). Gewählte Jahreszeit gilt für den Haushalt und wird per Handy-zu-Handy-Synchronisierung abgeglichen; Vorgabe vor der ersten Wahl nach Kalender (Mai–Sep Sommer). Kein automatischer Wechsel. Zeitpläne und „Klimaprofile verwalten“ zeigen alle Profile, dort mit kleinem Jahreszeit-Hinweis. Profil-Editor hat oben die Wahl Winter/Sommer/Ganzjährig. 0.5.3: Kopfzeile (✕, Titel, Sichern) in Profil- und Zeitplan-Editor fest, nur der Inhalt darunter scrollt. 0.5.4: Energie-Stundenwerte geglättet. Der Zähler meldet nur 0,25-kWh-Schritte (beim Heizen im Büro ca. alle 1,5 h), dadurch zeigte die Tagesansicht einzelne Balken mit Lücken. Das Skript zählt jetzt je Stunde die Ablesungen mit eingeschalteter Anlage (`pendingSlots`) und verteilt jeden Schritt anteilig darauf (höchstens 12 h zurück, bei neuem Lauf verworfen); Tagessumme unverändert. Wirkt für Daten ab der Skript-Aktualisierung (läuft automatisch über die App). 0.5.5: Raum- bzw. Anlagenwechsel springt immer an den Seitenanfang (vorher blieb die Scrollposition hängen); 3D-Auto-Schalter auf der Hauptseite blau statt Lila. 0.5.6: Energiediagramm seitlich wischbar (HorizontalPager je Zeitraum, letzte Seite = aktueller Zeitraum, keine Zukunft); ‹ ›, Titel-Tippen und Antippen eines Balkens bleiben und bewegen den Pager mit. 0.5.7: Zurück-Geste im Energiedialog geht nach Antippen eines Balkens wieder eine Ebene höher (Stapel der vorherigen Zeiträume); erst danach schließt sie den Dialog. 0.5.8: Knopf „↑ <Ebene>“ rechts über dem Energiediagramm: geht zurück zur Ebene, von der ein Balken-Tipp kam, sonst zur nächstgröberen (Tag → Woche → Monat → Jahr → Gesamt). 0.5.9: „↑“-Knopf kleiner (Pfeil-Symbol 20 dp, Text 12 sp, Pillenform); Vollbild-Dialoge (Energie, Profil- und Zeitplan-Editor) setzen die Android-Navigationsleiste auf die Seitenfarbe mit dunklen Symbolen (`DialogNavigationBar`). 0.5.10: Kachel „Verbrauch heute“ bleibt sichtbar, wenn die Anlage lokal nicht erreichbar ist (Daten kommen aus ioBroker). 21.11.2026: Schlafzimmer ab ca. 07:10 lokal „connection refused“ (Port 51443), obwohl Smart M-Air nicht offen war und die Original-App die Anlage online zeigt; Ursache noch offen. Gegen 12:25 wieder erreichbar (von selbst). 0.5.11: Diagnose, ein- und ausschaltbar unter Einstellungen → Diagnose (Schalter in ioBroker `0_userdata.0.KlimaPilot.diagnostics.config`, beide Handys übernehmen ihn beim App-Start). Eingeschaltet prüft das Skript jede Minute die lokale Erreichbarkeit jeder Anlage und schreibt Wechsel (nicht erreichbar mit Fehler, wieder erreichbar mit Dauer, Fehlerwechsel) nach `…diagnostics.log` (14 Tage, max. 400). Die App protokolliert lokal (`AppDiagnostics`) Erreichbarkeitswechsel aus ihrer Statusabfrage, fehlgeschlagene Befehle und ioBroker-Verbindungsfehler. Ansicht mit Aktualisieren, Kopieren (Zwischenablage) und Leeren. 0.5.12: Fehlalarm „ioBroker nicht erreichbar (Client disconnect)“ behoben – das eigene Schließen einer Sitzung wurde als Ausfall protokolliert. 0.5.13: Gemeinsame ioBroker-Verbindung (`IoBrokerPool`/`IoBrokerLease` in IoBrokerSocket.kt): Energie, Zeitpläne, Profilsync, Diagnose und Skriptprüfung teilen sich eine WebSocket-Verbindung (`name=klimapilot.app`) statt je einer eigenen; sie schließt nach 2 min ohne Aufgabe bzw. sofort nach einem Fehler ohne Wartende. Ziel: weniger Connected/Disconnect-Zeilen im ws.0-Log (vorher ~8 Verbindungen beim App-Start), weniger Last. `IoBrokerClient` (Einrichtung/Test) bleibt separat. Empfehlung an den Nutzer: ws.0-Log-Stufe auf „warn“. Nutzer-Test 0.5.13: alles funktioniert. 0.5.14: „Leeren“ in der Diagnose aktualisiert die Liste sofort (Handy-Einträge waren kein Compose-Zustand, daher blieb die Anzeige stehen) und meldet „Protokoll geleert.“ 0.5.15: Temperatur-Schieberegler (eigener `TemperatureSlider`) bewegt sich nur, wenn der runde Knopf selbst gegriffen wird (±26 dp); Wischen anderswo auf der Leiste wechselt den Raum, Tippen auf die Leiste ändert nichts. Vorher wurde beim Raum-Wischen oft versehentlich die Temperatur verstellt. 0.5.16: Zeitpläne in fester Reihenfolge (wie in ioBroker gespeichert, neue ans Ende) statt nach nächstem Termin sortiert; Reihenfolge durch Halten und Ziehen änderbar (Vibration beim Greifen), wird in ioBroker gespeichert und gilt damit auf beiden Handys. 0.5.17: Schutz gegen gegenseitiges Überschreiben von `schedules.config`: Feld `revision` (bei jedem Schreiben +1); direkt vor dem Schreiben wird die Revision erneut gelesen, hat das andere Handy dazwischen geschrieben, wird die Änderung auf dessen frischen Stand neu angewendet (bis 3 Versuche). Zusätzlich prüft das Speichern aus dem Editor, ob der Zeitplan seit dem Öffnen woanders geändert oder gelöscht wurde (`updatedAt`), und fragt dann „Überschreiben / Verwerfen“. Nur ioBroker-Zugriffe, keine zusätzliche Last auf den Klimaanlagen; das Skript ignoriert das neue Feld. Skript mit simuliertem Modul geprüft; am realen Gerät noch unbestätigt. Hinweis: Timer der Original-App nicht parallel verwenden.
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
