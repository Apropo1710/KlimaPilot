# KlimaPilot – Direkte WLAN-Kommunikation

Stand: 15.11.2026 (MEZ)
Produktivbasis: `main` nach Release 0.4.29 / versionCode 59
Entwicklungsbranch: `feature/direct-wf-rac-wlan`
Basis von `main`: `c2f92a4bd73ad0b59b6cbace713177d6b458353e`
Produktionsstand auf Basisbranch: 0.4.28 / versionCode 58
Gemeinsame App-Version: 0.4.29 / versionCode 59 (bestehende Paketkennung und Updater)

## Ziel
Die bestehende KlimaPilot-App wird schrittweise um direkte lokale Kommunikation mit dem Mitsubishi-Heavy-WF-RAC-Modul ergänzt. `main` bleibt während der Entwicklung unverändert. Die ioBroker-Funktionen bleiben vorerst bestehen.

## Erster Entwicklungsschritt
- Version 0.4.30 / versionCode 60: manuelle und automatische mDNS-Gerätesuche über `_beaver._tcp`; Auswahl eines gefundenen Moduls übernimmt dessen IP-Adresse in das vorhandene Feld.
- Die mDNS-Suche ist bewusst manuell auslösbar, läuft maximal 10 Sekunden und beendet Discovery sowie Multicast-Lock anschließend. Die manuelle IP-Eingabe bleibt als Rückfallebene erhalten.
- Keine Änderungen an Updater, ioBroker-Verbindung, Benutzerkonten oder vorhandenen Klimafunktionen.
- WF-RAC-Protokollkomponenten aus AirLAN in das KlimaPilot-Paket übernommen.
- Quelle: https://github.com/erdtsieck/airlan
- Lizenz: MIT; vollständige Lizenz und Herkunftshinweis in `NOTICE-AIRLAN.md`.
- Enthalten: HTTP/HTTPS-Transport, TLS-Zertifikat-Pinning (Trust-on-first-use), JSON-Request-Writer, Statuscodec, Temperaturtabellen, Request-Queue und Protokolltrace.
- Erste UI-Diagnosefunktion in den Administratoreinstellungen: manuelle Modul-IP, `getDeviceInfo` und `getAirconStat`, Anzeige von Firmware- und Statusinformationen.
- Diagnose bleibt zunächst read-only: keine Registrierung eines Operator-Kontos und keine `setAirconStat`-Schreibbefehle.
- Pro Modul maximal vier Operator-Konten und 60-Sekunden-Schreibsperre anderer Clients beachten.

## Sicherheit und Netzwerk
- HTTPS-Zertifikat wird beim Erstkontakt per SHA-256-Pinning gebunden; bei späterer Abweichung muss der Zugriff fehlschlagen.
- HTTP/HTTPS wird anhand der tatsächlichen Modulantwort ermittelt.
- Manuelle IP-Eingabe bleibt als Fallback verfügbar. Die App kann den Dienst `_beaver._tcp` jetzt per Android-NSD/mDNS suchen und gefundene Modul-IP-Adressen zur Auswahl anbieten. Die Suche läuft nur auf ausdrückliche Betätigung und endet nach 10 Sekunden.
- WireGuard-Zugriff ist noch nicht praktisch geprüft.
- Es werden keine Cloud-Dienste benötigt.

## Noch nicht umgesetzt
- Persistenz der Geräteidentität und des HTTPS-Pins je Klimaanlage.
- Steuerbefehle und sichere Operator-Registrierung.
- UI-Anbindung des echten Gerätestatus an die bisherige Klimaübersicht.
- Lokale Profile/Datenbank und ioBroker-Backup-Synchronisierung.
- WireGuard- und Stabilitätstests.

## Gemeinsame App und Updateweg
- Es gibt nur die bestehende App mit Application-ID `de.klimapilot.app`; keine parallele Test-App und keinen zweiten Updatekanal.
- Die WLAN-Funktionen werden in dieselbe App integriert, die bereits installiert ist.
- Der bestehende Updater bleibt unverändert auf dem privaten GitHub-Release-Tag `latest` und dem Asset `KlimaPilot.apk`.
- Signiert wird mit dem bereits verwendeten stabilen KlimaPilot-Release-Keystore aus den vorhandenen GitHub Actions Secrets. Der Schlüssel liegt nicht im Repository.
- Der Branch-Workflow baut und prüft einen signierten Release-Kandidaten mit der echten Paketkennung, veröffentlicht ihn aber nicht selbst. Nach Review und Merge in `main` übernimmt der bestehende Produktionsworkflow den normalen Build und aktualisiert `latest`.
- Die Installationsübergabe bleibt beim bewährten FileProvider-/`ACTION_VIEW`-Ablauf mit APK-MIME-Typ und URI-Berechtigung.

## Hardwaretest
Zielgerät: Mitsubishi Heavy Industries SRK15ZS-WF. WLAN-/MCU-Firmware noch auszulesen. Beim ersten Test ausschließlich IP eingeben und Status lesen. Keine Operator-Registrierung, keine Steuerbefehle und keine konkurrierenden App-Schreibtests, bis Firmware und Verhalten verifiziert sind.
