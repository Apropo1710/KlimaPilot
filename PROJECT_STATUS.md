<!-- AUTO-PRODUCTION-STATUS:START -->
## Automatisch gepflegter Produktionsstatus

- Quellcommit des APK-Builds: d47d0cc332cd3095456ae321ae1b28549526ffda
- App-Version / versionCode: **0.5.75 / 205**
- Build-Zeit: 2026-11-25 23:55:22 UTC
- APK-Dateiname: **KlimaPilot.apk**
- APK-Signaturprüfung: erfolgreich (apksigner verify --print-certs)
- Build-Artefakt: kein Actions-Artefakt; APK wird im GitHub-Release latest veröffentlicht
- APK-SHA-256: a9ae411b38fc4c51b4e8123c3737f8a82481ab15a1195c65a38074502a4af5bc
- GitHub-Release: Veröffentlichung wird in diesem Workflow nach dem Statuscommit abgeschlossen.
- Smartphone-Test: **nicht durch CI geprüft**; nur separat bestätigte Nutzerberichte gelten als Gerätetest.
- Updater: abgeschlossen und vom Nutzer bestätigt; Installer-Code wird durch diese Dokumentationspflege nicht verändert.
- ChatGPT-Bibliotheksakte: separate Langzeit-/Historienakte; wird durch diesen Workflow nicht verändert.

<!-- AUTO-PRODUCTION-STATUS:END -->


# KlimaPilot – verbindlicher Projektstatus

**Letzter Abgleich:** 22.11.2026, 17:30 Uhr MEZ (0.5.30)
**Repository:** https://github.com/Apropo1710/KlimaPilot

Diese Datei ist die **einzige maßgebliche Quelle für den aktuellen Projekt-, Branch-, PR-, Build- und Validierungsstand**. Die README dient nur als Einstieg. Die technischen Fachkonzepte enthalten Architektur und Entscheidungen, aber keine konkurrierenden Live-Statusübersichten.

## 1. Produktivstand – `main`

- Version, Quellcommit, APK-Hash und Release-Stand stehen ausschließlich im automatisch gepflegten Block am Dateianfang (wird vom Produktionsbuild geschrieben) und werden hier nicht wiederholt.
- Funktionsumfang: siehe Abschnitt 5 (Klimaanlagen, Profile, Zeitpläne, Energie, Filter, Diagnose, Benachrichtigungen) und Abschnitt 4 (Kopplung und Synchronisation).
- Play Protect: Seit 22.11.2026 werden Updates ohne Warnung installiert (Einspruch vom 20.11.2026 hat gewirkt, siehe 2h).
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

### Anlagen und Netz
- Seit 19.11.2026 in Betrieb: zwei Mitsubishi Heavy SRK15ZS-WF als **Multisplit an einem gemeinsamen Außengerät** (Nutzerangabe 25.11.2026), Schlafzimmer (192.168.178.197, airconId `1cd1d717d1e2`) und Büro (192.168.178.176, airconId `1cd1d717ebe8`); IPs fest in der Fritzbox. ioBroker 192.168.178.54, `ws.0` Port 8084.
- WF-RAC lokal: Port 51443, `/beaver/command/…`; Schreiben nur mit registriertem Konto, Schreibsperre ca. 60 s für den letzten Schreiber; rund 1 s Abstand zwischen Anfragen nötig, ein Befehl dauert typischerweise 2–3 s.
- Bekannte Eigenheiten: Modul startet stündlich neu (ca. xx:11–xx:17, je ca. 1 min nicht erreichbar – normal); eine offene Smart-M-Air-App blockiert den lokalen Zugriff (20.11.2026), Original-App daher nur bei Bedarf öffnen, ihre Timer nicht parallel nutzen. Am 21.11.2026 war das Schlafzimmer ca. 07:10–12:25 lokal nicht erreichbar (ECONNREFUSED, Cloud online), Ursache offen – seitdem nicht wieder aufgetreten (Diagnose läuft).
- Selbstreinigung: Anlagen melden Modellkennung 0, Self Clean ist darüber nicht per WLAN schaltbar (auch nicht in der Original-App); nur über die Fernbedienung.

### Bedienung (App)
- Kopplung robust gegen Doppelzuordnung und Neukopplung; „nicht erreichbar“ statt Kopplungsaufforderung bei gekoppelter Anlage.
- Hauptseite: Kopfzeile und Raum-Knöpfe fest; Raumreihenfolge per Halten und Ziehen, je Handy gespeichert (`room_order`), erster Raum öffnet beim Start, einmaliger Tipp dazu. Raumwechsel per Wischen, springt an den Seitenanfang. Reihenfolge der Anlagenseite: Klimaprofil, Temperatur, Modus, Lüfter, Luftführung, 3D-Auto, Zeitpläne, Verbrauch heute, Kompressor/Verbindung.
- Temperatur: Klicks werden gesammelt (1,2 s); der Schieberegler bewegt sich nur am Knopf, damit Raum-Wischen nichts verstellt.
- Einstellungen (0.5.34): ioBroker-Verbindung, ioBroker-Skript, Diagnose und Geräte koppeln stecken in der aufklappbaren Kachel „Systemeinstellungen“ direkt über „App-Version“ (Nicht-Admins sehen darin nur „Geräte koppeln“). „Zeitpläne verwenden“, Filter-Erinnerung und Benachrichtigungen bleiben einzeln. Seit 0.5.40 passen die blauen Knöpfe (14 sp, 46 dp), die Kachel „App-Version“ (Titel 14 sp, Texte 12 sp, Knöpfe 13 sp) und „Zurück zur Klimaübersicht“ zur Schrift der übrigen Kacheln. In `GithubUpdateCard.kt` wurden auf ausdrücklichen Nutzerwunsch nur Schriftgrößen und eine Knopfhöhe geändert; Ablauf, Texte und Logik des Updaters sind unverändert.
- Schriftgröße gilt seit 0.5.35 auch in Vollbild-Fenstern und Popups (`ScaledDialog` gibt die App-Dichte an das Dialogfenster weiter; vorher nutzten Dialoge die Systemschriftgröße).
- Zurück auf der Hauptseite (0.5.69, Nutzerwunsch 25.11.2026): statt die App sofort zu schließen, fragt „KlimaPilot beenden?“ (Beenden/Abbrechen); in Einstellungen und Unterseiten führt Zurück wie bisher eine Ebene höher. Nur CI, nicht am Gerät bestätigt.
- Einheitliche Gestaltung: blaue Bedienelemente (kein Lila), Popups über `KlimaPilotAlertDialog`, Hinweise hinter ⓘ, Vollbild-Editoren mit fester Kopfzeile und lesbarer Navigationsleiste.

### Klimaprofile
- Profile je Anlage; Abweichung zeigt „(geändert)“ mit ✓, „Individuell“ speichert sofort, + legt neu an.
- Editor (`ClimateProfileEditor.kt`): je Einstellung Schalter „Profil setzt diesen Wert“ (aus = unverändert); horizontale Luftführung und 3D-Auto immer verfügbar.
- Jahreszeit (seit 0.5.72, Nutzerwunsch 25.11.2026): Haushalts-Jahreszeit Winter/Übergangszeit/Sommer, nur der Admin stellt sie in den Einstellungen (Kachel „Zeitpläne verwenden“) um; gespeichert in `schedules.config` (`season`, `seasonUpdatedAt`, vom Skript ausgewertet) und je Handy zwischengespeichert, zusätzlich per Handy-Sync; die neuere Wahl gewinnt. Kein automatischer Wechsel. Profile: Winter/Übergang/Sommer/Ganzjährig (`season`, ohne Zuordnung nach Modus); in der Übergangszeit werden alle Profile angeboten. Der frühere Winter/Sommer-Umschalter im Profilmenü ist entfallen. Jahreszeit-Auswahl (Einstellungen, Profil-Editor) seit 0.5.73 als kompakte Variante von `Segmented` (`compact = true`): kleinere Schrift, die weiße Markierung umschließt nur die Beschriftung (Nutzerwunsch 25.11.2026); im Zeitplan-Editor bleiben es die kleinen Auswahl-Chips (Mehrfachauswahl). Gerätetest 25.11.2026: Jahreszeit-Umschaltung funktioniert.
- Profilanzeige folgt Zeitplänen (0.5.36): Zeitpläne setzen nur die Werte, nicht das aktive Profil. Weicht die Anlage vom gewählten Profil ab und passt genau ein anderes Profil des Raums, wird dieses als aktiv übernommen (Anlass 22.11.2026: Zeitplan 20:00 „Nacht“ lief, Anzeige blieb „Tag (geändert)“).
- Schnell-Timer „⏱ Aus in …“ (0.5.43): bei ausgeschalteter Anlage ausgegraut und ohne Funktion statt ausgeblendet.
- 0.5.55: Schalter in Blau; lange Texte in `KlimaPilotAlertDialog` scrollen innerhalb des Dialogs, Dialog hält Abstand zu Status- und Navigationsleiste.
- 0.5.62: Sperrt das WLAN-Modul die Steuerung nach einem Befehl eines anderen Geräts (Handy, Fernbedienung, Skript) für bis zu 10 s, wartet die App die Sperre ab und sendet den Befehl einmal erneut, statt sofort „wird gerade von einem anderen Gerät gesteuert“ zu melden (Anlass: Diagnose 24.11. 00:45 und 06:35 Schlafzimmer; vorher Wartezeit 0). Längere Sperren melden weiterhin den Fehler. Nicht am Gerät bestätigt.
- 0.5.57: Entfernt als tote Reste: `WfRacConnectionCard` (alte Verbindungskachel mit Geräteinfo, nirgends mehr angezeigt) und die nur dort genutzte `WfRacConnectionRepository`.
- 0.5.74 (Gegenprobe 26.11. bestätigt, Nutzer-OK): Kachel „Kompressor“ heißt „Raum“ und zeigt Heizt gerade / Kühlt gerade / Entfeuchtet / Pause / Taut ab / Aus, weil das Statusbit je Innengerät gilt (Raum fordert an), nicht für das gemeinsame Außengerät. Lagen im Raumtemperatur-Dialog „Verdichter startet/läuft“ → „aktiv seit kurzem/länger“; das Skript benennt gespeicherte Messwerte beim Lesen um (`roomScenarioRename`), Zählung bleibt erhalten. Nur CI, nicht am Gerät bestätigt.
- 0.5.71: Raumtemperatur-Dialog schließt nach „Speichern“ die Tastatur (Nutzerwunsch 25.11.2026), damit Ergebnis und Zählung je Lage sichtbar sind. Nur CI, nicht am Gerät bestätigt.
- 0.5.70: Abtau-Anzeige ohne Zusatzabfrage: Statusbyte content[9] Bit 0x01 beim Heizen (Referenz pywfrac `OPERATION_STATUS_DEFROST_BIT`, dort auf beiden Firmware-Zweigen bestätigt; Bit 0x02 = Verdichter, wie bisher). Anlagenkarte zeigt „Eingeschaltet · taut ab“, Kachel Kompressor „Taut ab“ / „Heizen pausiert kurz“. Unit-Tests `CodecDefrostTest`. Nur CI, beim nächsten Abtauen am Gerät bestätigen. Offen: Skript-Lernen soll Abtau-Minuten ausklammern (Skript liest das Bit noch nicht).
- 0.5.69: Raumtemperatur-Dialog fragt beim Öffnen sofort bei ioBroker nach (vorher galt das Ergebnis der minütlichen Abfrage, „Speichern“ blieb nach einem Fehlschlag bis zu 1 min gesperrt; Anlass 25.11. 07:51); solange die Antwort aussteht, steht dort „Verbindung zu ioBroker wird geprüft …“. Nur CI, nicht am Gerät bestätigt.
- 0.5.68: Betriebsdaten-Prüfung per Button in der App (Systemeinstellungen → Diagnose → „Betriebsdaten der Anlagen prüfen“): Bestätigungsdialog mit Hinweis auf die 1-min-Sperre, danach fragt die App bis 90 s nach dem Ergebnis und zeigt es je Anlage an (kopierbar).
- 0.5.67: Einmalige, von Hand ausgelöste Betriebsdaten-Prüfung im Skript (`roomTemp.probe` → `roomTemp.probeResult`): Verdichterfrequenz, Strom, Wärmetauscher-Temperaturen, interner Sollwert über den Betriebsdatenkanal des Moduls (leerer Befehlsblock + Anfragesegmente, Referenz: docs/wf-rac-module-reference.md der HA-Integration jeatheak/Mitsubishi-WF-RAC-Integration). Ziel: klären, ob das Modul diese Werte liefert, bevor sie fürs Raumtemperatur-Lernen genutzt werden. Sicherheitsnetz: schaltet sich eine Anlage dabei aus, wird sie sofort wieder eingeschaltet (von der Referenz für ein Modell berichtet). Wichtiger Befund aus der Referenz: jedes angewendete `setAirconStat` (auch eine reine Betriebsdaten-Anfrage) gibt dem Absender 60 s alleiniges Schreibrecht – regelmäßiges Abfragen würde die Handys aussperren; die Wartezeit der App bei Sperre (10 s, 0.5.62) ist dafür zu kurz. Nutzer erlaubt zusätzliche Abfragen, solange die Anlage stabil bleibt. Erste Ausführung siehe nächster Punkt.
- Betriebsdaten-Prüfungen 25.11.2026 (am Gerät ausgeführt, beide Anlagen blieben unverändert):
  - 07:20 – Schlafzimmer aus (seit 07:00, vorher Heizen), Büro an mit Verdichter in Pause: beide 0,0 Hz / 0,0 A; Wärmetauscher Schlafzimmer 26,7 / 27,3 °C, Büro 19,5 / 20,9 °C; Sollwert intern beide 20,0 °C.
  - 07:39 – Büro heizt („Verdichter läuft“, schwach blasend), Schlafzimmer aus: **beide** melden ~50,5 Hz / 1,9 A und Wärmetauscher 25,4 / 25,4 °C; Sollwert intern Büro 23,0 °C, Schlafzimmer 20,0 °C. Rohdaten Schlafzimmer `80 20 98 ff | 80 10 4c ff | 94 10 16 00 | 11 11 f9 ff | 90 10 07 ff | 81 20 64 ff | 87 10 64 ff | 05 13 28 ff`, Büro `80 20 8d ff | 80 10 4c ff | 94 10 01 00 | 11 11 fb ff | 90 10 07 ff | 81 20 64 ff | 87 10 64 ff | 05 13 2e ff`.
  - 07:57 – Büro an, Verdichter in Pause (Soll 20,5 °C), Schlafzimmer aus: beide 0,0 Hz / 0,0 A; Wärmetauscher Schlafzimmer 23,2 / 22,9 °C, Büro 22,2 / 24,2 °C; Sollwert intern beide 20,0 °C. Rohdaten Schlafzimmer `80 20 9d ff | 80 10 4e ff | 94 10 16 00 | 11 10 00 ff | 90 10 00 ff | 81 20 5d ff | 87 10 5c ff | 05 13 28 ff`, Büro `80 20 93 ff | 80 10 4e ff | 94 10 02 00 | 11 10 00 ff | 90 10 00 ff | 81 20 5a ff | 87 10 60 ff | 05 13 28 ff`.
  - 08:10 – während hörbarem Abtauen (beide an): Verdichter 10,0 Hz, Strom Schlafzimmer 0,6 A / Büro 0,3 A; Wärmetauscher Schlafzimmer −5,5 / −6,3 °C, Büro −4,2 / −4,8 °C; Sollwert intern Schlafzimmer 22,0 °C, Büro 20,0 °C; Status beider „steht“. Rohdaten Schlafzimmer `80 20 91 ff | 80 10 4c ff | 94 10 00 00 | 11 10 64 ff | 90 10 02 ff | 81 20 1c ff | 87 10 1b ff | 05 13 2c ff`, Büro `80 20 94 ff | 80 10 4c ff | 94 10 02 00 | 11 10 64 ff | 90 10 01 ff | 81 20 1e ff | 87 10 1d ff | 05 13 28 ff`. Schlafzimmer per Zeitplan an, Soll 20,0 °C → intern 22,0 °C (+2,0, während des Abtauens; Büro hatte +2,5 – weiter beobachten). Abtauen erkennbar: beim Heizen Wärmetauscher unter 0 °C in beiden Innengeräten (Kreislauf umgekehrt), Verdichter niedrig, Status „steht“.
  - 26.11. 00:28 (Gegenprobe, per VPN ausgelöst) – Schlafzimmer heizt („läuft“), Büro aus („steht“): beide ~35 Hz / 2,2 A; Wärmetauscher Schlafzimmer 40,1 / 40,7 °C, **ausgeschaltetes Büro 41,5 / 41,5 °C**; Sollwert intern Schlafzimmer 23,0 °C (Soll zuletzt 20,0 °C, Profil „Nacht“ – nicht neu abgelesen), Büro 18,0 °C. Rohdaten Schlafzimmer `80 20 9c ff | 80 10 4d ff | 94 10 0c 00 | 11 11 5e ff | 90 10 08 ff | 81 20 96 ff | 87 10 98 ff | 05 13 2e ff`, Büro `80 20 a6 ff | 80 10 4d ff | 94 10 0a 00 | 11 11 5b ff | 90 10 08 ff | 81 20 9b ff | 87 10 9b ff | 05 13 24 ff`. Bestätigt: Frequenz/Strom gehören zum gemeinsamen Außengerät, das Statusbit „Verdichter läuft/steht“ ist je Innengerät (= Raum fordert Wärme an), und das ausgeschaltete Innengerät wird voll mit durchströmt (Wärmetauscher so heiß wie beim heizenden).
  - Deutung: Verdichterfrequenz und Strom gehören zum gemeinsamen Außengerät und werden von beiden Modulen gleich gemeldet, auch vom ausgeschalteten – die Werte sind echt, aber nicht raumbezogen. Das Statusbit „Verdichter läuft/steht“ ist dagegen je Innengerät. Ein ausgeschaltetes Innengerät wird bei Multisplit vermutlich mit durchströmt (warmer Wärmetauscher) – mögliche Erklärung, warum auch der Fühler einer ausgeschalteten Anlage zu viel misst, wenn die andere heizt. Sollwert intern: Büro 07:39 eingestellt 20,5 °C, intern 23,0 °C → Heizoffset der Anlage +2,5 °C belegt (Anzeige App 19,6 °C, außen −4,0 °C). In der Pause (07:20 und 07:57, Soll 20,5 °C) stand intern 20,0 °C, beim ausgeschalteten Büro am 26.11. 00:28 18,0 °C – ohne Wärmeanforderung also kein verlässlicher Sollwert. Offset bei Wärmeanforderung bisher +2,5 (Büro), +2,0 (Schlafzimmer beim Abtauen) und vermutlich +3,0 (Schlafzimmer 00:28, Soll nicht abgelesen) – nicht konstant, weiter beobachten. Wärmetauscher-Werte unterscheiden sich je Innengerät (07:57), sind also raumbezogen; das ausgeschaltete Schlafzimmer hatte 57 min nach dem Ausschalten noch ~23 °C am Wärmetauscher.
- 0.5.66: Eingabe „Thermometer“ nur mit Ziffern (Zahlenblock ohne Komma-Suche): „175“ wird als 17,5 angezeigt und gespeichert, „17“ als 17,0.
- 0.5.65: Messwerte der Raumtemperatur verfallen nie; statt 400 Werten je Anlage (älteste zuerst raus) höchstens 100 je Anlage und Lage, ein neuer Wert ersetzt nur den ältesten derselben Lage – Winterwerte bleiben fürs nächste Jahr erhalten. Skript hält die Messwerte im Speicher statt sie jede Minute neu zu lesen.
- 0.5.64: Rückmeldung beim Eintragen eines Thermometerwerts deutlicher: „Wird übertragen …“, dann „Warte auf Bestätigung …“ (grau); die App fragt danach alle 3 s bis 45 s nach dem Ergebnis des Skripts und zeigt „✓ Messwert … übernommen (n gesamt)“ grün bzw. „✗ …“ rot, ohne Bestätigung einen roten Hinweis; Status verschwindet beim Schließen. Skript: Eingabe-Ergebnis erscheint auch für Anlagen ohne aktuelle Beobachtung (vorher fehlte die Anlage dann in `roomTemp.model`). Erster Eintrag am Gerät 24.11. 18:31 blieb ohne sichtbare Bestätigung.
- Raumtemperatur lernen (0.5.63): Antippen von „Innen“ öffnet den Dialog „Raumtemperatur“ (Fühlerwert, Herkunft des angezeigten Werts, Eingabe „Thermometer zeigt“, Messwerte je Lage, letzten Wert löschen; für alle Benutzer). Das ioBroker-Skript speichert jeden Thermometerwert mit dem Zustand der Anlage (Merkmale: ein, Heizen, Verdichter, Minuten seit Verdichter-Start/-Stopp und Ausschalten als abklingende Werte, Fühlertrend, Außentemperatur, Lüfter, Soll − Fühler) und lernt je Anlage per Ridge-Regression mit Kreuzprüfung (Glättung aus 0,3–30 gewählt); das Modell gilt ab 8 Werten nur, wenn es besser trifft als der Mittelwert (ab 3 Werten). Die App zieht die aktuelle gelernte Abweichung (`roomTemp.model`, höchstens 12 min alt, jede Minute geladen) vom Live-Fühlerwert ab, sonst die feste Korrektur `heatOffset` (Editor: ein Wert „fest, bis genug Messwerte gelernt sind“, nur Admin). Beobachtung nur aus den ohnehin laufenden Abfragen: jede Minute mit Diagnose, sonst alle 5 min. Simulation im Node-Test: nach 10 Werten Modellfehler ~0,15 °C gegen ~0,4 °C beim Mittelwert. Nicht am Gerät bestätigt. Entfernt: gleitender Heiz-Zuschlag (`heatExtra`, `HeatingTracker`, 0.5.60–0.5.62) und die 5-s-Fühleranzeige beim Antippen. Anlass: Messungen 24.11. (Büro Fühler 23,0/Thermometer 18,2 bei stehendem, 21,5/18,2 bei laufendem Verdichter) – der Fühler misst in Pausen mehr zu viel, keine feste Regel passte. **Schnittstelle geändert:** neue Datenpunkte `roomTemp.input`, `roomTemp.samples`, `roomTemp.model`, `roomTemp.track` (README ergänzt). Nutzer trägt regelmäßig Werte ein; Verbesserungen des Lernens prüfen, wenn Daten vorliegen.
- Zeitplan-Rhythmus (0.5.50): wöchentliche Zeitpläne optional „alle 2/3/4 Wochen“ mit wählbarer Startwoche (seit 0.5.51 als kompakte Segmentschalter) (für Schichtarbeit, z. B. zusätzlicher Einschalt-Zeitplan nur in der Nachtschicht-Woche). **Schnittstelle geändert:** neue optionale Felder `everyWeeks`/`weekAnchor` in `schedules.config` (README ergänzt); das Skript prüft sie in `dueKey`. Alle Handys aktualisieren – ältere App-Versionen würden die Felder beim Speichern verwerfen.
- Raum-/Anlagenauswahl (0.5.43): ausgewählter Chip kräftig blau mit weißer Schrift, die übrigen hellblau ohne Rahmen (vorher war der ausgewählte hellblau und der nicht ausgewählte weiß umrandet – leicht zu verwechseln). Offen, auf Wunsch des Nutzers zurückgestellt: Die Wahl eines Betriebsmodus schaltet die Anlage mit ein (`onMode` setzt `power = true`); der Nutzer möchte, dass Verstellen Ein/Aus nie ändert – erst nach seiner Beobachtung umsetzen.
- Aktives Profil (0.5.42): Eine ausgeschaltete Anlage gilt nicht als „(geändert)“, nur die übrigen Werte werden verglichen. Wird das aktive Profil bearbeitet (Hauptseite, „Klimaprofile verwalten“ oder anderes Handy) und läuft die Anlage, übernimmt sie die neuen Werte sofort; ausgeschaltet bleibt sie aus. Erkannt über die zuletzt gesehene Profilfassung je Raum (nur solange die App läuft).
- „Klimaprofile verwalten“: je Anlage eine Karte mit Profilzeilen, Papierkorb und „Neues Profil“.

### ioBroker-Skript (`scripts/iobroker/klimapilot.js` → `script.js.common.KlimaPilot`)
- Wird von der App als Asset eingerichtet und bei jedem Start aktualisiert; alte Kopien (u. a. „KlimaPilot Energie“) werden gelöscht, das Löschen geprüft, sonst angehalten. Läuft das Skript doch doppelt, arbeitet nur die zuletzt gestartete Kopie (`script.owner`).
- 22.11.2026: Das von Hand angelegte alte Skript „KlimaPilot Energie“ lief noch und doppelte alle Einträge; vom Nutzer gelöscht.
- Schnittstelle (alle Datenpunkte, JSON, Revisionsverfahren): README-Abschnitt „ioBroker-Schnittstelle“; jede Änderung dort nachtragen und hier vermerken.

### Zeitpläne (seit 0.4.92)
- Je Anlage wöchentlich oder einmalig, Ein (Profil oder eigene Werte) bzw. Aus; Ausführung durch das Skript (minütlich, Wiederholung bis 10 min, einmalige bis 6 h nachgeholt); ausgeführte einmalige werden deaktiviert.
- Zeitplan-Karte ohne Zeile „Nächster: …“ (Nutzerwunsch 22.11.2026, spart Platz); eine Zeile erscheint nur noch bei „ioBroker nicht erreichbar“ oder „Noch keine Zeitpläne“.
- Feste, per Halten und Ziehen änderbare Reihenfolge (in ioBroker, gilt auf beiden Handys); Schnell-Timer „Aus in 30 min / 1 / 2 / 3 h“ (`quick`).
- Schutz gegen gegenseitiges Überschreiben: `revision` mit erneutem Lesen vor dem Schreiben, Rückfrage im Editor bei Konflikt (`updatedAt`).
- Jahreszeiten (0.5.72): Jeder Zeitplan gehört zu einer oder mehreren Jahreszeiten (`seasons`); angezeigt und vom Skript ausgeführt werden nur die der Haushalts-Jahreszeit (Schnell-Timer immer). Neue Zeitpläne haben keine Vorauswahl und lassen sich ohne Jahreszeit nicht sichern; alle bestehenden gelten als Winter. Leere Liste zeigt „Keine Zeitpläne für den Sommer“ usw. Schnittstellenänderung: README „ioBroker-Schnittstelle“ → `schedules.config`. Nur CI, nicht am Gerät bestätigt.
- Globaler Schalter „Zeitpläne verwenden“ (Admin), auch per Handy-Sync. Am 20.11.2026 einmaliger Zeitplan am Gerät bestätigt; wöchentliche noch nicht ausdrücklich bestätigt.

### Energie
- Laufzähler der Anlage (0,25-kWh-Schritte, Rücksetzung beim Einschalten); Skript liest alle 5 min, verteilt jeden Schritt auf die Laufzeit davor, speichert Stunden (31 Tage) und Tage (dauerhaft) sowie Außentemperatur (Mittel je Stunde/Tag).
- Hauptseite „Verbrauch heute“ (auch ohne lokale Verbindung). Energiefenster: Räume/Alle, Tag–Gesamt, kWh/€, seitlich wischbar, Balken öffnet feinere Ebene, „↑“ und Zurück-Geste gehen höher; Skalen links (Wert) und rechts (Außentemperatur), Werte an Balken bis 12 Stück, Tagesansicht zeigt Werte per Tipp. Fester Strompreis ohne Grundgebühr. Kopfzeile „Energie / Schließen“ seit 0.5.48 fest, nur der Inhalt scrollt. Seit 0.5.45 beim Wischen vergleichbar: Skalen links/rechts stehen fest (außerhalb des Pagers), und eine Seite nutzt dieselbe Skala wie ihre beiden Nachbarzeiträume (Maximum über alle drei); Legende steht seit 0.5.46 wieder nebeneinander, aber fest über dem Pager (scrollt nicht mehr mit). Unter „Alle“ seit 0.5.44 gestapelte Balken je Anlage (Blautöne unterschiedlicher Helligkeit, Orange bleibt der Außentemperatur), Einzelwerte im Balkenteil, wenn Platz ist, Legende mit Summe je Anlage im Zeitraum. Diagrammschrift seit 0.5.35 größer (Achsen 12 sp, Werte 13 sp, Diagramm 280 dp hoch), Achsenbreiten nach gemessener Schrift; Werte, die nicht über ihren Balken passen, entfallen. Achsenbeschriftung unter den Balken wird zentriert gezeichnet und nicht mehr abgeschnitten (Monatsansicht zeigte „1 5 9 1 1 2“ statt „1 5 9 13 17 21“).

### Filter-Erinnerung
- Abstände wie im Handbuch, gezählt in Betriebstagen (Tage mit Betrieb, `runDays`): Luftfilter 1–4 Wochen (Vorgabe 2), Allergen- und Geruchsfilter 1–6 Monate (Vorgabe 3). Hinweis auf der Anlagenseite mit „Erledigt“, Einstellungskachel für alle Konten. Zugeklappt zeigt die Kachel „Nach Betriebstagen der Anlage“ (vorher noch „Betriebsstunden“). Zählung seit 21.11.2026.

### Diagnose und Benachrichtigungen
- Diagnose (Einstellungen, an/aus über ioBroker): Skript prüft minütlich die Erreichbarkeit und protokolliert Wechsel; das Handy ergänzt nur echte Wechsel aus eigener Sicht (keine Prüfpausen, keine abgebrochenen Abrufe). Auswertung 21./22.11.2026: keine echten Ausfälle; Aussetzer vom Handy lagen am WLAN des Handys.
- Ereignisprotokoll (0.5.75, Nutzerwunsch 26.11.2026): je Handy immer aktiv, nur lokal (`event_journal.jsonl`), 48 h; Anzeige/Kopieren/Leeren in der Diagnose-Karte. Erfasst App geöffnet/im Hintergrund, jeden Befehl an eine Anlage mit Auslöser (Bedienung, „Profil … gewählt“, „Automatik: aktives Profil … wurde geändert“, individuelle Einstellungen), Änderung und Ergebnis inkl. Sperrdauer, Änderungen an der angezeigten Anlage, die nicht von diesem Handy kamen (nur solange die App läuft), übernommene Synchronisierung (Profile, anderes Handy, Jahreszeit) und die Jahreszeit-Umstellung. Diagnose-Eintrag „Befehl fehlgeschlagen“ nennt jetzt Auslöser und Sperrdauer. Nur CI, nicht am Gerät bestätigt. Grenzen: nur das jeweilige Handy; die Anlage wird nur abgefragt, solange die App läuft – Änderungen bei geschlossener App sieht nur das Skript.
- Auswertung 22.11.2026 abends: Anlagen laut ioBroker nur bei den stündlichen Modul-Neustarts (ca. 1 min) und zweimal kurz („socket hang up“) weg; die vielen Handy-Einträge ohne Gegenstück in ioBroker stammen vom WLAN/Energiesparen des Handys. Skript seit 0.5.37: gleiche Diagnose-Einträge binnen 3 min werden nur einmal geschrieben (vorher doppelte „wieder erreichbar“ und mehrfaches „Skript gestartet“), und ein Neustart desselben Skripts meldet keine „doppelte Kopie“ mehr. Doppelte ioBroker-Einträge traten trotzdem weiter auf (vermutlich gleichzeitig schreibende Skript-Laufzeiten). Seit 0.5.39 trägt auch das Skript Ausfälle erst ab 3 min ein, die stündlichen Modul-Neustarts erscheinen also nicht mehr; die App zeigt gleiche Einträge binnen 3 min nur einmal. Schnittstelle: README „diagnostics.log“ angepasst (`detail` mit „seit HH:MM – “, kein `anderer Fehler` mehr). Am 23.11. schrieb trotzdem noch eine alte Skript-Laufzeit Einträge im alten Format (neuer Code nachweislich installiert); Abhilfe: Instanz `javascript.0` neu starten. Absicherung: Die Skript-Einrichtung hält ein laufendes Skript beim Update erst an und startet es 3 s später mit dem neuen Code; fehlt `script.owner`, übernimmt nur eine Laufzeit. Seit 0.5.49 trägt das Handy nur Einträge ein, solange es im WLAN ist (über Mobilfunk sind Anlagen und ioBroker ohnehin nicht erreichbar; Anlass: Eintrag von 192.0.0.4 am 23.11.). Seit 0.5.38 trägt das Handy Ausfälle erst ab 2 min Dauer ein („nicht erreichbar · seit HH:MM“) und nur dann auch das Ende; kurze Aussetzer des Handy-WLANs erscheinen nicht mehr.
- Rohdaten-Aufzeichnung (nur 0.5.41, Diagnose → „Rohdaten aufzeichnen“, je Handy): rein lesend; jede Änderung der Statusbytes (Kopf H, Zustandsblock K, Segmente S ohne Temperaturen/Energie) der abgefragten Anlage geht ins Handy-Protokoll, ständig schwankende Bytes nach 6 Änderungen ignoriert. Ergebnis 23.11.2026 (Büro): Hi Power und Eco haben kein eigenes Kennzeichen, die Anlage meldet nur die Lüfterstufe (Hi Power → Rohwert 2 „Mittel“, Eco → 0 „Leise“, zurück → 7 Auto); Silent ändert nichts im Status. Nicht über WLAN steuerbar; ein Versuch mit den unbenutzten Lüfterwerten 3–5 wurde auf Wunsch des Nutzers nicht gemacht. Aufzeichnung in 0.5.42 wieder entfernt.
- Gemeinsame ioBroker-Verbindung der App (`IoBrokerPool`), schließt nach 2 min Ruhe; Empfehlung ws.0-Log-Stufe „warn“.
- Android-Benachrichtigungen (standardmäßig aus, je Art einschaltbar): WorkManager-Check etwa alle 2 h im WLAN für fällige Filter, endgültig fehlgeschlagene Zeitpläne und seit über 1 h nicht erreichbare Anlagen; Dauerzustände täglich erneut, erledigte werden entfernt. Berechtigungen `POST_NOTIFICATIONS` (Abfrage beim Einschalten), `VIBRATE` (Greifen beim Sortieren; auf dem Xiaomi des Nutzers ohne spürbare Wirkung).

### Updater ohne GitHub-Anmeldung (0.5.52, 23.11.2026)
- Auf ausdrückliche Anweisung des Nutzers: Die GitHub-Anmeldung (Device-Flow mit Zugriffs-/Erneuerungsschlüssel) ist entfernt, weil das Repo öffentlich ist. Anlass: Die Frau des Nutzers war plötzlich abgemeldet und konnte kein Update laden (Erneuerungsschlüssel sind bei GitHub nur einmal gültig; jeder Fehler beim Erneuern löschte die Anmeldung).
- `GithubPrivateUpdates.kt` → `GithubUpdates.kt`: Release-Abfrage und APK-Download ohne `Authorization`, sonst unverändert (gleiche Endpunkte, Prüfungen, Größenprüfung). Alte Schlüssel und der Keystore-Eintrag werden beim ersten Abruf gelöscht.
- `GithubUpdateCard.kt`: „Mit GitHub verbinden“ und Code-Anzeige entfernt; „Nach Updates suchen“ immer sichtbar, Prüfung beim Öffnen. **Download, APK-Prüfung und Installationsablauf (`ACTION_VIEW` + FileProvider + ClipData) unverändert.** Start-Popup prüft ohne Anmeldung.
- Gerätetest 24.11.2026 00:2x (Handy des Nutzers): Update 0.5.52 → 0.5.53 ohne Anmeldung geladen und installiert – bestätigt. Zweites Handy noch offen.
- Grenze ohne Anmeldung: 60 GitHub-API-Abfragen pro Stunde je Internetadresse (alle Geräte im Haushalt zusammen); die App braucht 1 je Start, 1 je Öffnen der Einstellungen, 2 je Update. Wird das Repo wieder privat, funktionieren Updates nicht mehr.

### Updater-Beobachtung (22.11.2026)
- Gelegentlich erscheint nach „Update laden“ der Status „Android-Installationsdialog wurde angefordert“, der Dialog kommt aber nicht; ein zweites Tippen funktioniert immer. Der Code ruft `startActivity` ohne Fehler auf – das System zeigt den Dialog nur nicht (vermutlich bündelt Android/MIUI den Aufruf mit einer noch vorhandenen Installer-Aufgabe oder der Play-Protect-Prüfung). Ursache nicht sicher nachweisbar; **bewusst keine Änderung am Updater** (Nutzerpriorität: Updater darf nicht unbrauchbar werden). Umgehung: erneut tippen.

### Bestätigt / offen
- Am Gerät bestätigt: Kopplung, Status, Solltemperatur, Profilwechsel, Kompressor-Anzeige, Energiezähler und -verlauf, einmaliger Zeitplan, Profil-Zuordnung im Zeitplan, Sortieren der Räume.
- Noch nicht gezielt geprüft: Modi, Lüfterstufen und Luftführung an beiden Anlagen; wöchentliche Zeitpläne; Benachrichtigungen; neues Energiediagramm (0.5.30).

## 6. CI und Workflows

- `.github/workflows/android.yml`: Produktionsbuild bei Push auf `main` (Commits mit `[skip ci]` ausgenommen); veröffentlicht `KlimaPilot.apk` im Release `latest` und pflegt den automatischen Block oben in dieser Datei.
- `.github/workflows/wlan-validation.yml` („KlimaPilot WLAN Branch Validation“): Push auf `test` (ausgenommen `PROJECT_STATUS.md` und `app/src/debug/**`) sowie `workflow_dispatch`. Unit-Tests, Debug-APK, Release-Kotlin-Kompilierung, Prüfung von Paket, Version, Test-Zertifikat und Fehlen von `REQUEST_INSTALL_PACKAGES`; veröffentlicht die Test-APK als Pre-Release `klimapilot-test-latest`. Maven-Downloads werden bis zu fünfmal wiederholt.
- `.github/workflows/pairing-tls-validation.yml` (historischer Dateiname): API-26-Emulator-/TCP-Instrumentierungstest, nur `workflow_dispatch`.
- **API 26:** TCP-Instrumentierungstests bestanden (u. a. Lauf #63, 3/3; Lauf #69).
- **API 35:** ungeprüft. Alle Versuche vom 18.11.2026 scheiterten an der CI-Umgebung, nicht an der App: Lauf #63 mit `Can't find service: package`, die Wiederholungen (auf inzwischen gelöschten Prüfbranches) an einem Shell-Syntaxfehler im Bereitschaftsschritt, bevor ein Test lief. API 35 ist optional und kein Freigabekriterium; keine weiteren Versuche ohne konkreten neuen Ansatz.
- Status-Wächter in der WLAN-Validierung (seit 22.11.2026): schlägt fehl, wenn ein Push auf `test` App-Code (`app/src/main/**`, `app/build.gradle.kts`) oder `scripts/iobroker/**` ändert, `PROJECT_STATUS.md` aber nicht. Prüft nur das Mitpflegen, nicht den Inhalt.
- `.github/workflows/docs-guard.yml` („Docs guard“): prüft bei jedem Push, jedem PR und täglich, dass `PROJECT_STATUS.md` die einzige Statusdatei bleibt.
- Gradle-Cache auf `test` (22.11.2026): `setup-gradle` speichert den Cache sonst nur auf `main`; jeder Prüflauf lud daher alle Abhängigkeiten neu und scheiterte öfter an Maven Central (HTTP 429). `wlan-validation.yml` setzt jetzt `cache-read-only: false`.
- Actions-Versionen (22.11.2026): `actions/checkout@v5`, `actions/setup-java@v5`, `actions/upload-artifact@v5` laufen auf Node.js 24 (v4 nutzte das abgekündigte Node.js 20). `android-actions/setup-android@v4`, `gradle/actions/setup-gradle@v4` und `reactivecircus/android-emulator-runner@v2` vorerst unverändert.
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
- 20.11.2026: Vergleich mit HomePilot (gleicher Updater, gleiche Berechtigungen inkl. `REQUEST_INSTALL_PACKAGES`, gleiche Pairing-Technik; Play Protect stuft HomePilot als sicher ein) – Updater und Berechtigungen sind damit als Ursache unwahrscheinlich, die Release-Signatur ist stabil. Vermutung: Die PHA-Einstufung von 0.4.74 hängt an Paket/Signatur. Einspruch am 20.11.2026 eingereicht (APK 0.4.91, SHA256 59227cbd…e8425, VirusTotal 0/65). Google antwortet nicht; Erfolg zeigt sich nur daran, dass beim nächsten Update keine Warnung mehr erscheint. Ursprünglich geplant: Play-Protect-Einspruch über https://support.google.com/googleplay/android-developer/contact/protectappeals mit Paketname `de.klimapilot.app` und SHA256 der jeweils aktuellen Release-APK (vorher bei VirusTotal hochladen). Keine Codeänderungen auf Verdacht.
- 22.11.2026: Update auf 0.5.29 erstmals ohne Play-Protect-Warnung installiert – der Einspruch hat offenbar gewirkt. Kein neuer Signaturschlüssel nötig; Rückfalloption entfällt, solange die Warnung nicht zurückkommt.

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

1. Gerätetest: Modi, Lüfterstufen und Luftführung an beiden Anlagen; wöchentliche Zeitpläne; Energiediagramm 0.5.30.
2. Diagnose weiter beobachten (Ursache des Schlafzimmer-Ausfalls vom 21.11.2026).
3. Optional: Negativfälle der Kopplung testen; gekoppelte Smartphones in der Oberfläche entfernbar machen.
4. Später (eigenes Projekt): Desktop-Oberfläche über die ioBroker-Schnittstelle.
5. Raumtemperatur-Lernen (seit 0.5.63) auswerten, Termin Sa 28.11.2026: Nutzer schickt `roomTemp.model` (oder Dialog-Screenshots); prüfen: Messwerte je Lage, `using`, `errorModel` gegen `errorMean`. Ziel ±0,5 °C; bleibt es nach 20–30 Werten bei ±1 °C oder mehr, reicht der Fühler nicht (dann externer Raumsensor). Bisherige echte Messpaare 24.11.: Büro 07:24 Fühler 23,0/Thermometer 18,2 (Verdichter steht), 07:44 21,5/18,2 (läuft); Schlafzimmer 06:40 Fühler 19,5/Wecker 17 (ohne Heizen). 25.11. 03:16 Büro aus seit Stunden: Fühler 24,5 °C (Fußbodenheizung aus) – Thermometerwert dazu fehlt noch. 25.11. 07:51 Schlafzimmer aus seit 07:00, Büro heizt am gemeinsamen Außengerät: Fühler 25,7 °C / Thermometer 17,6 °C (+8,1 °C) – stützt die Mitdurchströmung des ausgeschalteten Geräts; Speichern zunächst gesperrt (Dialog meldete „ioBroker nicht erreichbar“ – Speichern hängt an der minütlichen ioBroker-Abfrage, nicht am Ein/Aus der Anlage), nach erneutem Öffnen gespeichert. Nur Thermometerwerte als Beleg nehmen, nie die von der App angezeigte (selbst korrigierte) Innentemperatur. Mitsubishi verrechnet beim Heizen ab Werk einen eigenen Offset in der Regelung; die Anzeige = Fühler − unsere Korrektur sagt nichts über die echte Raumtemperatur.
6. Betriebsdaten-Prüfung (Button, 0.5.68): zwei Läufe 25.11., Werte echt (Abschnitt 5); Verdichter/Strom gelten für das gemeinsame Außengerät. Heizoffset +2,5 °C belegt (Abschnitt 5). Offen: prüfen, ob die Energiezähler beider Module ebenfalls das gemeinsame Außengerät zählen (dann wäre „Alle“ im Energiediagramm doppelt) – Tag mit nur einer laufenden Anlage vergleichen. Für regelmäßige Abfragen fürs Lernen, Entscheidung über eine gemeinsame `deviceId` aller KlimaPilot-Geräte (Skript und beide Handys), weil sonst jede Betriebsdaten-Abfrage die Handys 60 s aussperrt; Vorteil auch: keine gegenseitige Sperre der Handys; Nachteil: kein Schutz gegen gleichzeitiges Steuern, Original-App bleibt ausgesperrt; Akzeptanz durch das Modul ungetestet. Nutzer erlaubt zusätzliche Abfragen, solange die Anlage stabil bleibt.
7. Ideen fürs Lernen, erst mit echten Daten prüfen: Fühler als träge Masse mit luftstromabhängiger Kopplung an die Raumluft → Merkmal Lüfter×Pause, Fühlertrend stärker; Wärmetauscher-Temperatur der eigenen Anlage als wichtigstes Merkmal (Restwärme nach Abschalten, in Pausen und durch Mitdurchströmung, wenn die andere Anlage heizt); Verdichterfrequenz nur als gemeinsames Merkmal des Außengeräts bzw. „andere Anlage heizt“; Abtauen erkennen (Merkmal: Wärmetauscher < 0 °C beim Heizen, belegt 25.11. 08:10) und solche Zeiträume beim Lernen ausklammern.
8. Abtau-Anzeige (0.5.70, über das Status-Bit) beim nächsten Abtauen am Gerät bestätigen; danach das Bit auch im ioBroker-Skript auswerten und Abtau-Minuten beim Raumtemperatur-Lernen ausklammern.
9. Wartezeit der App bei Schreibsperre ist 10 s (0.5.62), die Sperre des Moduls dauert 60 s. Diagnose zeigt die Meldung „von einem anderen Gerät gesteuert“ weiterhin (Schlafzimmer 24.11. 21:06 und 25.11. 16:55, jeweils Handy; 25.11. 16:55 war das zweite Handy nicht zuhause, kein Zeitplan fällig – andere Schreiber sind nur Skript-Zeitpläne und die Betriebsdaten-Prüfung) – Nutzer hat um 16:55 nichts bedient, die App hat also selbst geschrieben. Einziger automatischer Schreibweg in der App: Übernahme geänderter Werte des aktiven Profils (`ClimateProfiles.kt`, LaunchedEffect „The active profile was edited“). Die Meldung kommt nur, wenn das Modul ablehnt und eine echte Sperre meldet (`lockedForS` > 10 s) – wer sie ausgelöst hat, ist unbekannt. Seit 0.5.75 nennt der Diagnose-Eintrag Auslöser und Sperrdauer, und das Ereignisprotokoll (48 h) zeigt den Ablauf – beim nächsten Auftreten auswerten. Ursache offen; spricht für den Test der gemeinsamen Geräte-ID (Punkt 6). Außerdem 25.11. Büro 01:26–02:26 nicht erreichbar (ECONNREFUSED, 60 min – deutlich länger als der stündliche Modul-Neustart); einmalig, beobachten.

## 11. Dokumentationsstruktur und Pflege

### Regeln für Menschen und KI-Werkzeuge
- `AGENTS.md`: verbindliche Arbeitsregeln, wird von KI-Werkzeugen beim Start gelesen; `CLAUDE.md` verweist darauf.

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
