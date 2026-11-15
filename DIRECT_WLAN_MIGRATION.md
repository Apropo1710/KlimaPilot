# KlimaPilot – Direkte WLAN-Kommunikation

Stand: 15.11.2026
Produktionsbranch: `main`
Aktueller Entwicklungsstand: 0.4.44 / versionCode 74 (Build und Veröffentlichung noch ausstehend)

## Ziel
KlimaPilot kommuniziert direkt und lokal mit Mitsubishi-Heavy-WF-RAC-WLAN-Modulen. Eine dauerhafte ioBroker-Verbindung ist nicht mehr Teil des App-Betriebs. Eine einmalige ioBroker-Leseverbindung bleibt ausschließlich für die Übernahme älterer Benutzerkonten aus `0_userdata.0.KlimaPilot.accounts` erhalten.

## Umgesetzte Schritte
- **0.4.30 / 60:** Manuelle und automatische mDNS-Suche nach `_beaver._tcp`, Port 51443; manuelle IP bleibt als Fallback.
- **0.4.31 / 61:** Benutzerkonten, Passwort-Hashes und persönliche Schriftgrößen lokal gespeichert. Bei Upgrades erfolgt einmalig ein Import der bisherigen ioBroker-Konten, ohne Gesamtabonnierung. Nach erfolgreicher Übernahme ist ioBroker für Anmeldung und Benutzerverwaltung nicht mehr erforderlich. Das Wolkensymbol zeigt den direkten Klimaanlagen-Verbindungsstatus.
- **0.4.32 / 62:** Geräteidentität und Transport-Sicherheitsbindung werden dauerhaft pro WF-RAC-Modul gespeichert. Die App verbindet sich nach Neustart automatisch mit der zuletzt bekannten Adresse. Falls diese nicht erreichbar ist, wird lokal per mDNS gesucht und ein gefundenes Modul nur dann übernommen, wenn Identität und gespeicherter Fingerprint übereinstimmen.
- **0.4.33 / 63:** Die erstmalige Vertrauensstellung per TOFU wird nur noch durch einen bewusst gestarteten Verbindungstest ausgelöst. Beim App-Start werden ausschließlich bereits gespeicherte Identitäten wiederverbunden.
- **0.4.44 / 74 (in Arbeit):** Pro Gerät kann ein WF-RAC-Modul explizit gekoppelt werden. Mehrere physische Klimaanlagen erhalten getrennte Geräte-IDs und Verbindungen. Die Kopplung prüft airconId, Transport und HTTPS-Fingerprint; weitere Geräte dürfen nur durch eine bewusste Kopplungsaktion hinzugefügt werden. Die Bedienoberfläche liest den echten Gerätestatus und steuert Ein/Aus, Betriebsmodus, Solltemperatur, Lüfterstufe, vertikale Luftführung sowie – soweit vom Modell gemeldet – horizontale Luftführung und 3D-Automatik. Gespeicherte Profile können jetzt angewendet werden. Ein isolierter Demo-Modus simuliert eine Anlage und verwendet denselben WF-RAC-Frame-Encoder/Decoder wie die echte Steuerung. Der Produktionsbuild steht noch aus; ein Hardwaretest ist mangels installierter Anlage nicht möglich.

## Geräteidentität und TLS-Sicherheit
- Gespeichert werden die vom Modul gelieferte `airconId`, Host/IP, HTTP- oder HTTPS-Verfahren, letzter erfolgreicher Prüfzeitpunkt und – bei HTTPS – der SHA-256-Fingerprint des Serverzertifikats.
- Beim bekannten Host wird der gespeicherte HTTPS-Pin bereits beim TLS-Handshake geprüft.
- Bei einer geänderten IP wird zunächst ausschließlich `getDeviceInfo` gelesen. Die zurückgelieferte `airconId`, das Transportverfahren und der beobachtete Zertifikat-Fingerprint werden mit dem gespeicherten Datensatz verglichen.
- Erst nach erfolgreichem Identitäts- und Fingerprintvergleich wird `getAirconStat` aufgerufen.
- Ein abweichendes Zertifikat, ein geändertes Transportverfahren, eine andere Geräte-ID an einer bekannten IP oder ein unbekanntes Modul bei bereits vorhandener Vertrauensbindung führen zum Abbruch. Der gespeicherte Fingerprint wird niemals automatisch überschrieben.
- Bei einem ersten Modul ohne vorhandene Vertrauensbindung wird TOFU (Trust on First Use) nur nach einer ausdrücklich vom Nutzer gestarteten Kopplung verwendet. Nach erfolgreichem Identitäts- und Statusabruf wird die Bindung dauerhaft gespeichert.
- Die Installationskennung `deviceId` bleibt ebenfalls lokal dauerhaft gespeichert.
- Normale Statusabfragen bleiben read-only. Für Steuerbefehle wird die gemeinsame AirLAN-Operator-ID verwendet und vor dem ersten Schreiben auf dem Modul registriert. Jeder Schreibvorgang liest zuerst den aktuellen Zustand und verändert nur die gewünschten Felder; unbekannte Modell-/Firmwareflags bleiben erhalten. Eine erkannte Schreibsperre eines anderen Clients wird nicht übergangen.

## ioBroker-Abgrenzung
- Keine dauerhafte ioBroker-Verbindung, keine Datenpunktabonnements und keine ioBroker-Konfiguration in der Bedienoberfläche.
- Die alte Adapter-/Datenpunktzuordnung in der Gerätekonfiguration wurde aus der Oberfläche entfernt. Bestehende lokale Raum- und Geräteeinträge bleiben erhalten; ältere Adapterfelder werden nur aus Kompatibilitätsgründen beim Lesen toleriert.
- Die lokale Profil-Synchronisierung über den getrennten ioBroker-WebSocket-Pfad ist implementiert, aber noch nicht mit zwei Smartphones und realem ioBroker praktisch bestätigt. Sie ist kein Steuerungsweg zur Klimaanlage.

## Hardware und Grenzen
- Zielgerät: Mitsubishi Heavy Industries SRK15ZS-WF.
- WLAN-/MCU-Firmware ist noch auszulesen; die Klimaanlage ist noch nicht für einen Hardwaretest verfügbar.
- mDNS-Wiedererkennung nach IP-Wechsel und Zertifikat-Pinning müssen nach Installation am echten Modul geprüft werden.
- WireGuard-Zugriff ist noch nicht praktisch geprüft.
- Steuerbefehle sind im Code integriert, aber noch nicht am konkreten SRK15ZS-WF und dessen Firmware getestet. Die App darf den Entwicklungsstand nicht als hardwarebestätigt kennzeichnen.

## Vor dem Hardwaretest noch umzusetzen
- Ausschalt-Timer und wiederkehrende Zeitpläne analog zum Funktionsumfang von AirLAN.
- Automatische Wiedererkennung eines bereits gekoppelten Moduls nach DHCP-IP-Wechsel; die aktuelle Kopplung nutzt mDNS bei der bewussten Geräteeinrichtung.
- Weitere bestätigte WF-RAC-Status-/Sonderfunktionen wie Vacant und Self Clean samt Profilunterstützung, sofern sie zum Modell passen.
- Demo-Modus und alle Steuerungsaktionen automatisiert gegen den produktiven Frame-Encoder/Decoder testen.

## Updater
- Bestehende Application-ID: `de.klimapilot.app`.
- Bestehender Produktionskanal: Release-Tag `latest`, Asset `KlimaPilot.apk`.
- Der funktionierende FileProvider-/`ACTION_VIEW`-Installationsablauf bleibt unverändert.
- Keine separate Test-App und kein zweiter Updatekanal.
