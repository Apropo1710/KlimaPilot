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

Implementiert auf `test`:
- Discovery per DNS-SD.
- ECDSA-Installationsidentität im Android Keystore.
- signierte QR-Einladung und Antwort, gegenseitige Bestätigung und Trusted-Peer-Speicher.
- separater TCP-Handshake zur gegenseitigen Peer-Authentifizierung.
- lokale WLAN-Adressauswahl und Lifecycle-Überwachung.

Noch nicht umgesetzt:
- Integration des TCP-Handshakes in den Discovery-Listener.
- Nachrichtenframing und Nutzdatenprotokoll.
- Peer-Transportanbindung an Outbox und `ClimateProfileSyncEngine`.
- dauerhafte Empfangsbestätigung, Replay-/Duplikatbehandlung und Wiederaufnahme.
- Zwei-Smartphone-Test.

Discovery bleibt reject-only; es werden keine Profile über Peer-TCP übertragen.

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

Alle lokalen Peer-Arbeiten bleiben auf `test`. PR #6 bleibt Draft. Kein Merge, keine Produktionsversionserhöhung und kein Release ohne ausdrückliche Freigabe. Updater und bestehende ioBroker-/WF-RAC-Funktionen bleiben unangetastet.
