# KlimaPilot – Direkte WLAN-Kommunikation

Stand: 15.11.2026
Produktionsbranch: `main`
Produktivversion vor diesem Arbeitsschritt: 0.4.31 / versionCode 61

## Ziel
KlimaPilot kommuniziert direkt und lokal mit Mitsubishi-Heavy-WF-RAC-WLAN-Modulen. Eine dauerhafte ioBroker-Verbindung ist nicht mehr Teil des App-Betriebs. Eine einmalige ioBroker-Leseverbindung bleibt ausschließlich für die Übernahme älterer Benutzerkonten aus `0_userdata.0.KlimaPilot.accounts` erhalten.

## Umgesetzte Schritte
- **0.4.30 / 60:** Manuelle und automatische mDNS-Suche nach `_beaver._tcp`, Port 51443; manuelle IP bleibt als Fallback.
- **0.4.31 / 61:** Benutzerkonten, Passwort-Hashes und persönliche Schriftgrößen lokal gespeichert. Bei Upgrades erfolgt einmalig ein Import der bisherigen ioBroker-Konten, ohne Gesamtabonnierung. Nach erfolgreicher Übernahme ist ioBroker für Anmeldung und Benutzerverwaltung nicht mehr erforderlich. Das Wolkensymbol zeigt den direkten Klimaanlagen-Verbindungsstatus.
- **0.4.32 / 62:** Geräteidentität und Transport-Sicherheitsbindung werden dauerhaft pro WF-RAC-Modul gespeichert. Die App verbindet sich nach Neustart automatisch mit der zuletzt bekannten Adresse. Falls diese nicht erreichbar ist, wird lokal per mDNS gesucht und ein gefundenes Modul nur dann übernommen, wenn Identität und gespeicherter Fingerprint übereinstimmen.

## Geräteidentität und TLS-Sicherheit
- Gespeichert werden die vom Modul gelieferte `airconId`, Host/IP, HTTP- oder HTTPS-Verfahren, letzter erfolgreicher Prüfzeitpunkt und – bei HTTPS – der SHA-256-Fingerprint des Serverzertifikats.
- Beim bekannten Host wird der gespeicherte HTTPS-Pin bereits beim TLS-Handshake geprüft.
- Bei einer geänderten IP wird zunächst ausschließlich `getDeviceInfo` gelesen. Die zurückgelieferte `airconId`, das Transportverfahren und der beobachtete Zertifikat-Fingerprint werden mit dem gespeicherten Datensatz verglichen.
- Erst nach erfolgreichem Identitäts- und Fingerprintvergleich wird `getAirconStat` aufgerufen.
- Ein abweichendes Zertifikat, ein geändertes Transportverfahren, eine andere Geräte-ID an einer bekannten IP oder ein unbekanntes Modul bei bereits vorhandener Vertrauensbindung führen zum Abbruch. Der gespeicherte Fingerprint wird niemals automatisch überschrieben.
- Bei einem ersten Modul ohne vorhandene Vertrauensbindung wird TOFU (Trust on First Use) verwendet und nach erfolgreichem read-only Statusabruf dauerhaft gespeichert.
- Die Installationskennung `deviceId` bleibt ebenfalls lokal dauerhaft gespeichert.
- Die Diagnose bleibt read-only: keine Operator-Registrierung und keine `setAirconStat`-Schreibbefehle.

## ioBroker-Abgrenzung
- Keine dauerhafte ioBroker-Verbindung, keine Datenpunktabonnements und keine ioBroker-Konfiguration in der Bedienoberfläche.
- Die alte Adapter-/Datenpunktzuordnung in der Gerätekonfiguration wurde aus der Oberfläche entfernt. Bestehende lokale Raum- und Geräteeinträge bleiben erhalten; ältere Adapterfelder werden nur aus Kompatibilitätsgründen beim Lesen toleriert.
- Eine spätere optionale Sicherung/Synchronisierung lokaler Profile kann getrennt entworfen werden; sie ist derzeit nicht aktiv.

## Hardware und Grenzen
- Zielgerät: Mitsubishi Heavy Industries SRK15ZS-WF.
- WLAN-/MCU-Firmware ist noch auszulesen; die Klimaanlage ist noch nicht für einen Hardwaretest verfügbar.
- mDNS-Wiedererkennung nach IP-Wechsel und Zertifikat-Pinning müssen nach Installation am echten Modul geprüft werden.
- WireGuard-Zugriff ist noch nicht praktisch geprüft.
- Keine Steuerbefehle, bis Firmware, Datenmodell und Schreibverhalten am Gerät verifiziert sind.

## Updater
- Bestehende Application-ID: `de.klimapilot.app`.
- Bestehender Produktionskanal: Release-Tag `latest`, Asset `KlimaPilot.apk`.
- Der funktionierende FileProvider-/`ACTION_VIEW`-Installationsablauf bleibt unverändert.
- Keine separate Test-App und kein zweiter Updatekanal.
