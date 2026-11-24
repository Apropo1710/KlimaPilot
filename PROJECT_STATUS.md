<!-- AUTO-PRODUCTION-STATUS:START -->
## Automatisch gepflegter Produktionsstatus

- Quellcommit des APK-Builds: 05691d7a7ddad83f6199a7f61137956166995978
- App-Version / versionCode: **0.5.57 / 187**
- Build-Zeit: 2026-11-24 02:23:47 UTC
- APK-Dateiname: **KlimaPilot.apk**
- APK-Signaturprüfung: erfolgreich (apksigner verify --print-certs)
- Build-Artefakt: kein Actions-Artefakt; APK wird im GitHub-Release latest veröffentlicht
- APK-SHA-256: 0fe0f2968dccb3232ea1b46edb38d1c1b2dd61574e8227023777bedcc1ce2de6
- GitHub-Release: **erfolgreich veröffentlicht**; Asset KlimaPilot.apk wurde aktualisiert.
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
- Seit 19.11.2026 in Betrieb: zwei Mitsubishi Heavy SRK15ZS-WF, Schlafzimmer (192.168.178.197, airconId `1cd1d717d1e2`) und Büro (192.168.178.176, airconId `1cd1d717ebe8`); IPs fest in der Fritzbox. ioBroker 192.168.178.54, `ws.0` Port 8084.
- WF-RAC lokal: Port 51443, `/beaver/command/…`; Schreiben nur mit registriertem Konto, Schreibsperre ca. 60 s für den letzten Schreiber; rund 1 s Abstand zwischen Anfragen nötig, ein Befehl dauert typischerweise 2–3 s.
- Bekannte Eigenheiten: Modul startet stündlich neu (ca. xx:11–xx:17, je ca. 1 min nicht erreichbar – normal); eine offene Smart-M-Air-App blockiert den lokalen Zugriff (20.11.2026), Original-App daher nur bei Bedarf öffnen, ihre Timer nicht parallel nutzen. Am 21.11.2026 war das Schlafzimmer ca. 07:10–12:25 lokal nicht erreichbar (ECONNREFUSED, Cloud online), Ursache offen – seitdem nicht wieder aufgetreten (Diagnose läuft).
- Selbstreinigung: Anlagen melden Modellkennung 0, Self Clean ist darüber nicht per WLAN schaltbar (auch nicht in der Original-App); nur über die Fernbedienung.

### Bedienung (App)
- Kopplung robust gegen Doppelzuordnung und Neukopplung; „nicht erreichbar“ statt Kopplungsaufforderung bei gekoppelter Anlage.
- Hauptseite: Kopfzeile und Raum-Knöpfe fest; Raumreihenfolge per Halten und Ziehen, je Handy gespeichert (`room_order`), erster Raum öffnet beim Start, einmaliger Tipp dazu. Raumwechsel per Wischen, springt an den Seitenanfang. Reihenfolge der Anlagenseite: Klimaprofil, Temperatur, Modus, Lüfter, Luftführung, 3D-Auto, Zeitpläne, Verbrauch heute, Kompressor/Verbindung.
- Temperatur: Klicks werden gesammelt (1,2 s); der Schieberegler bewegt sich nur am Knopf, damit Raum-Wischen nichts verstellt.
- Einstellungen (0.5.34): ioBroker-Verbindung, ioBroker-Skript, Diagnose und Geräte koppeln stecken in der aufklappbaren Kachel „Systemeinstellungen“ direkt über „App-Version“ (Nicht-Admins sehen darin nur „Geräte koppeln“). „Zeitpläne verwenden“, Filter-Erinnerung und Benachrichtigungen bleiben einzeln. Seit 0.5.40 passen die blauen Knöpfe (14 sp, 46 dp), die Kachel „App-Version“ (Titel 14 sp, Texte 12 sp, Knöpfe 13 sp) und „Zurück zur Klimaübersicht“ zur Schrift der übrigen Kacheln. In `GithubUpdateCard.kt` wurden auf ausdrücklichen Nutzerwunsch nur Schriftgrößen und eine Knopfhöhe geändert; Ablauf, Texte und Logik des Updaters sind unverändert.
- Schriftgröße gilt seit 0.5.35 auch in Vollbild-Fenstern und Popups (`ScaledDialog` gibt die App-Dichte an das Dialogfenster weiter; vorher nutzten Dialoge die Systemschriftgröße).
- Einheitliche Gestaltung: blaue Bedienelemente (kein Lila), Popups über `KlimaPilotAlertDialog`, Hinweise hinter ⓘ, Vollbild-Editoren mit fester Kopfzeile und lesbarer Navigationsleiste.

### Klimaprofile
- Profile je Anlage; Abweichung zeigt „(geändert)“ mit ✓, „Individuell“ speichert sofort, + legt neu an.
- Editor (`ClimateProfileEditor.kt`): je Einstellung Schalter „Profil setzt diesen Wert“ (aus = unverändert); horizontale Luftführung und 3D-Auto immer verfügbar.
- Jahreszeit: Profile Winter/Sommer/Ganzjährig (`season`, ohne Zuordnung nach Modus); Umschalter im Profilmenü, gilt für den Haushalt (Handy-Sync), kein automatischer Wechsel.
- Profilanzeige folgt Zeitplänen (0.5.36): Zeitpläne setzen nur die Werte, nicht das aktive Profil. Weicht die Anlage vom gewählten Profil ab und passt genau ein anderes Profil des Raums, wird dieses als aktiv übernommen (Anlass 22.11.2026: Zeitplan 20:00 „Nacht“ lief, Anzeige blieb „Tag (geändert)“).
- Schnell-Timer „⏱ Aus in …“ (0.5.43): bei ausgeschalteter Anlage ausgegraut und ohne Funktion statt ausgeblendet.
- 0.5.55: Schalter in Blau; lange Texte in `KlimaPilotAlertDialog` scrollen innerhalb des Dialogs, Dialog hält Abstand zu Status- und Navigationsleiste; Info-Text zur Heiz-Korrektur gekürzt.
- 0.5.58: Heiz-Korrektur beim ersten Wert nach App-Start voll, sobald die Anlage im Modus Heizen eingeschaltet ist – auch wenn der Verdichter gerade pausiert (Beobachtung 24.11. 03:26 Büro: Fühler 23 °C, Raum geschätzt ~18 °C, Verdichter aus; vorher wurde dann nicht korrigiert). Nur bei ausgeschalteter Anlage startet sie bei 0.
- 0.5.57: Antippen von „Innen“ zeigt 5 s lang den rohen Fühlerwert („FÜHLER“, grau) – zum Vergleich mit einem Thermometer. Entfernt als tote Reste: `WfRacConnectionCard` (alte Verbindungskachel mit Geräteinfo, nirgends mehr angezeigt) und die nur dort genutzte `WfRacConnectionRepository`.
- Heiz-Korrektur folgt dem Fühler (0.5.56): Statt fester 10-min-Rücknahme wächst die Korrektur bei laufendem Verdichter um den Anstieg des Fühlers (bis zum eingestellten Wert) und schrumpft im Ruhezustand um dessen Abfall; beim ersten Wert gilt: Verdichter läuft → voll, sonst 0. Das Lernen in ioBroker (0.5.53–0.5.55, Datenpunkt `heatOffset`) und der Schalter „automatisch verwenden“ sind wieder entfernt: Die Methode unterschätzte (erste Messung 0,5 °C statt ~3 °C), weil die warme Luft am Fühler unter der Decke hängen bleibt. Das Skript löscht den alten Datenpunkt. **Schnittstelle geändert:** `heatOffset` entfällt (README angepasst).
- Heiz-Korrektur der Innentemperatur (0.5.47): je Anlage in „Räume & Geräte verwalten“ (nur Admin) einstellbar, 0–8 °C in 0,5er-Schritten (`heatOffset` im synchronisierten Gerätedatensatz). Im Modus Heizen zeigt die Hauptseite Fühlerwert minus Korrektur; nur Anzeige, keine Regelung. Grundsatz: Admin-Funktionen sind für normale Benutzer ausgeblendet, ohne Ersatztext (Hinweis in den Systemeinstellungen entfernt).
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
- Globaler Schalter „Zeitpläne verwenden“ (Admin), auch per Handy-Sync. Am 20.11.2026 einmaliger Zeitplan am Gerät bestätigt; wöchentliche noch nicht ausdrücklich bestätigt.

### Energie
- Laufzähler der Anlage (0,25-kWh-Schritte, Rücksetzung beim Einschalten); Skript liest alle 5 min, verteilt jeden Schritt auf die Laufzeit davor, speichert Stunden (31 Tage) und Tage (dauerhaft) sowie Außentemperatur (Mittel je Stunde/Tag).
- Hauptseite „Verbrauch heute“ (auch ohne lokale Verbindung). Energiefenster: Räume/Alle, Tag–Gesamt, kWh/€, seitlich wischbar, Balken öffnet feinere Ebene, „↑“ und Zurück-Geste gehen höher; Skalen links (Wert) und rechts (Außentemperatur), Werte an Balken bis 12 Stück, Tagesansicht zeigt Werte per Tipp. Fester Strompreis ohne Grundgebühr. Kopfzeile „Energie / Schließen“ seit 0.5.48 fest, nur der Inhalt scrollt. Seit 0.5.45 beim Wischen vergleichbar: Skalen links/rechts stehen fest (außerhalb des Pagers), und eine Seite nutzt dieselbe Skala wie ihre beiden Nachbarzeiträume (Maximum über alle drei); Legende steht seit 0.5.46 wieder nebeneinander, aber fest über dem Pager (scrollt nicht mehr mit). Unter „Alle“ seit 0.5.44 gestapelte Balken je Anlage (Blautöne unterschiedlicher Helligkeit, Orange bleibt der Außentemperatur), Einzelwerte im Balkenteil, wenn Platz ist, Legende mit Summe je Anlage im Zeitraum. Diagrammschrift seit 0.5.35 größer (Achsen 12 sp, Werte 13 sp, Diagramm 280 dp hoch), Achsenbreiten nach gemessener Schrift; Werte, die nicht über ihren Balken passen, entfallen. Achsenbeschriftung unter den Balken wird zentriert gezeichnet und nicht mehr abgeschnitten (Monatsansicht zeigte „1 5 9 1 1 2“ statt „1 5 9 13 17 21“).

### Filter-Erinnerung
- Abstände wie im Handbuch, gezählt in Betriebstagen (Tage mit Betrieb, `runDays`): Luftfilter 1–4 Wochen (Vorgabe 2), Allergen- und Geruchsfilter 1–6 Monate (Vorgabe 3). Hinweis auf der Anlagenseite mit „Erledigt“, Einstellungskachel für alle Konten. Zugeklappt zeigt die Kachel „Nach Betriebstagen der Anlage“ (vorher noch „Betriebsstunden“). Zählung seit 21.11.2026.

### Diagnose und Benachrichtigungen
- Diagnose (Einstellungen, an/aus über ioBroker): Skript prüft minütlich die Erreichbarkeit und protokolliert Wechsel; das Handy ergänzt nur echte Wechsel aus eigener Sicht (keine Prüfpausen, keine abgebrochenen Abrufe). Auswertung 21./22.11.2026: keine echten Ausfälle; Aussetzer vom Handy lagen am WLAN des Handys.
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
