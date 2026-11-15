# KlimaPilot – Direkte WLAN-Kommunikation

Stand: 15.11.2026 (MEZ)
Entwicklungsbranch: `feature/direct-wf-rac-wlan`
Basis von `main`: `c2f92a4bd73ad0b59b6cbace713177d6b458353e`
Produktionsstand auf Basisbranch: 0.4.28 / versionCode 58
WLAN-Testbuild: 0.4.29 / versionCode 59 (Build und Release-Prüfung noch ausstehend)

## Ziel
Die bestehende KlimaPilot-App wird schrittweise um direkte lokale Kommunikation mit dem Mitsubishi-Heavy-WF-RAC-Modul ergänzt. `main` bleibt während der Entwicklung unverändert. Die ioBroker-Funktionen bleiben vorerst bestehen.

## Erster Entwicklungsschritt
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
- Manuelle IP-Eingabe ist zunächst die vorgesehene Diagnosemethode. mDNS-Suche folgt nach dem ersten Lesezugriff.
- WireGuard-Zugriff ist noch nicht praktisch geprüft.
- Es werden keine Cloud-Dienste benötigt.

## Noch nicht umgesetzt
- Automatische mDNS-Erkennung und Netzwerksuche.
- Persistenz der Geräteidentität und des HTTPS-Pins je Klimaanlage.
- Steuerbefehle und sichere Operator-Registrierung.
- UI-Anbindung des echten Gerätestatus an die bisherige Klimaübersicht.
- Lokale Profile/Datenbank und ioBroker-Backup-Synchronisierung.
- WireGuard- und Stabilitätstests.

## Teststrategie und eigener Updater
- Eigener Buildtyp `wlanTest` mit Application-ID `de.klimapilot.app.debug`; die produktive App `de.klimapilot.app` bleibt parallel installiert.
- Der WLAN-Testbuild wird dauerhaft mit dem bestehenden, stabilen KlimaPilot-Signaturschlüssel signiert. Dadurch bleiben Folgeupdates installierbar; es wird kein kurzlebiger CI-Debug-Schlüssel verwendet.
- Der Signaturschlüssel wird ausschließlich aus den bereits vorhandenen GitHub Actions Secrets geladen und nicht im Repository abgelegt.
- Der Test-Updater erkennt die getrennte Paketkennung und verwendet ausschließlich das private GitHub-Release-Tag `wlan-test-latest` mit dem Asset `KlimaPilot-WLAN-debug.apk`.
- Die Produktivversion verwendet weiterhin ausschließlich `latest` und `KlimaPilot.apk`. Die beiden Updatekanäle sind im App-Code getrennt.
- Der WLAN-Workflow baut und prüft den signierten Testbuild, kontrolliert Paketkennung, Versionscode, Versionsname und APK-Signatur und veröffentlicht anschließend das separate Test-Release. Pull Requests führen nur die Build-/Unit-Test-Prüfung aus.
- Die Installationsübergabe bleibt beim bewährten FileProvider-/`ACTION_VIEW`-Ablauf mit APK-MIME-Typ und URI-Berechtigung.
- Der Produktivworkflow und `main` werden nicht verändert. Erst nach erfolgreichem Hardwaretest und Review wird eine Integration in `main` vorbereitet.

## Hardwaretest
Zielgerät: Mitsubishi Heavy Industries SRK15ZS-WF. WLAN-/MCU-Firmware noch auszulesen. Beim ersten Test ausschließlich IP eingeben und Status lesen. Keine Operator-Registrierung, keine Steuerbefehle und keine konkurrierenden App-Schreibtests, bis Firmware und Verhalten verifiziert sind.
