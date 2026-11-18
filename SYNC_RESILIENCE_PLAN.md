# KlimaPilot – ausfallsichere Synchronisation

**Fachlicher Stand:** 18.11.2026  
**Produktions-/Branch-/PR-Status:** ausschließlich in `PROJECT_STATUS.md`.

## 1. Ziel

Profiländerungen sollen dauerhaft gespeichert, bei Verbindungsunterbrechungen nachgeliefert und über mehrere Transportwege zuverlässig abgeglichen werden. Lokale Daten dürfen bei Ausfällen nicht verloren gehen. Cloud-Abhängigkeit ist nicht vorgesehen.

## 2. Produktiver Synchronisationspfad

- Der bestehende ioBroker-WebSocket-Sync ist vom Haupt-WebSocket der App getrennt.
- Vor Synchronisierung werden Verbindungsauthentifizierung und `system.adapter.admin.0.alive` geprüft.
- Nicht bestätigte Änderungen bleiben in der lokalen Outbox.
- Ereignisse werden mit Installations-ID, Sequenz und Logical Clock versioniert.
- Remote-Ereignisse durchlaufen `ClimateProfileSyncEngine`; bekannte/ältere Events werden übersprungen, ungültige Events einzeln abgelehnt.
- Bestätigungen erfolgen erst nach erfolgreicher Übertragung und lokaler Speicherung des Upload-Acknowledgements.
- Die gemeinsame Engine wird bereits vom ioBroker-Pfad verwendet.

## 3. Bereits erledigte Arbeiten

- Dauerhaftes Änderungsjournal/Outbox und Wiederanlaufmechanismen wurden im früheren Synchronisations-PR #3 umgesetzt und in `main` integriert.
- Rekey- und Versionsübernahme sowie Fehlerisolation wurden durch Review und Regressionstests nachgebessert.
- Gemeinsame Remote-Event-Anwendung ist in `ClimateProfileSyncEngine` ausgelagert.
- Der bestehende ioBroker-Pfad nutzt diese Engine.
- Die lokale Smartphone-Synchronisation ist als zusätzlicher Transportweg vorgesehen, nicht als Ersatz des ioBroker-Pfads.

## 4. Lokale Smartphone-Synchronisation – aktueller Stand

Umgesetzt und seit 0.4.77 in Produktion:
- Discovery per DNS-SD, ECDSA-Identität im Android Keystore, Ein-Scan-Kopplung mit signiertem ACK, Trusted-Peer-Speicher.
- Gegenseitiger TCP-Handshake, danach Datenabgleich (`ClimatePeerDataSync`): Konten, Profilereignisse über `ClimateProfileSyncEngine`, Admin-Konfiguration, ioBroker-Adresse.
- Peer-Ereignisprotokoll (`ClimatePeerEventLog`) hält das neueste Ereignis je Profil, auch aus ioBroker, damit Geräte ohne ioBroker vollständig abgleichen.
- Automatischer Abgleich bei geöffneter App (beim Finden und alle 30 s).
- Zwei-Geräte-Test am 18.11.2026 bestanden.

Noch offen: Praxistest der Fehlerfälle; Zusammenführung einzelner Konten bei gleichzeitigen Änderungen (derzeit gewinnt ein vollständiger Kontenstand).

## 5. Verbindliche Konflikt- und Wiederanlaufgrundsätze

- Keine pauschale Priorität für Smartphone oder ioBroker.
- Logical Clock, Installation-ID und Sequenz bestimmen die Ereignisordnung gemäß bestehender Implementierung.
- Ungeflushte lokale Journaländerungen müssen vor einer Remote-Versionsentscheidung dauerhaft berücksichtigt werden.
- Wiederholte Übertragung muss idempotent sein.
- Ein Remote-Event darf erst als übernommen bestätigt werden, wenn Nutzdaten und Versionsmarker dauerhaft gesichert sind.
- Fehler bei einzelnen ungültigen Events dürfen nicht unbemerkt gültige Events verwerfen.
- Änderungen an Rekey, Outbox oder Konfliktlogik benötigen gezielte Regressionstests.

## 6. Sicherheits- und Betriebsgrenzen

- Peer-TCP ist unverschlüsselt; Teilnehmer desselben WLANs können Daten passiv mitlesen.
- Gegenseitige Signaturprüfung authentifiziert Peers, ist aber kein Ersatz für Transportverschlüsselung.
- Vor Nutzdaten sind Größen-, Schema-, Wertebereichs-, Gerätezuordnungs-, Replay- und Zeitlimits zwingend.
- Keine Synchronisation über SENEC/`energyfluss`.
- Keine Änderungen am WF-RAC-Steuerpfad oder Updater für dieses Vorhaben.

## 7. Validierung

Build-, CI- und Geräte-Testnachweise für den aktuellen Stand werden ausschließlich in `PROJECT_STATUS.md` geführt. Ältere erfolgreiche Testläufe sind kein Nachweis für den jeweils neuesten Codecommit.

## 8. Weiteres Vorgehen

Die nächsten Arbeitsschritte und ihre Freigabestatus werden ausschließlich in `PROJECT_STATUS.md` gepflegt.

## 9. Produktionsschutz

Neue Peer-Funktionen werden auf `test` gebaut und mit der Testversion geprüft, bevor sie nach `main` gehen. Updater und bestehende ioBroker-/WF-RAC-Steuerung bleiben unangetastet.
