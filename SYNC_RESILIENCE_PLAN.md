# KlimaPilot – Ausfallsichere, unabhängige Synchronisation

**Status:** Verbindliches, zeitnah umzusetzendes Architekturvorhaben  
**Priorität:** Hoch – soll in einem der nächsten sinnvollen Entwicklungspakete begonnen werden  
**Erfasst:** 16.11.2026  
**Produktionsbranch:** `main`

## 1. Ziel und Grundsatz

KlimaPilot soll seine Daten und Funktionen auch dann zuverlässig nutzen können, wenn ioBroker vorübergehend oder dauerhaft nicht verfügbar ist. Ein Ausfall des bisherigen Synchronisationsservers darf weder zum Verlust gespeicherter Daten noch zu einer Einschränkung der lokalen Klimaanlagensteuerung führen.

**Grundsatz: Local-first.** Jedes Smartphone besitzt einen vollständigen lokalen Datenbestand der für die App benötigten Benutzer-, Geräte-, Profil- und Einstellungsdaten. Synchronisation ist ein zusätzlicher Dienst und keine Voraussetzung für die lokale Nutzung.

Eine absolute Garantie, dass niemals irgendein Ausfall auftritt, ist technisch nicht möglich. Ziel ist deshalb eine ausfallsichere Architektur: Ein Ausfall einzelner Verbindungen oder Synchronisationsdienste darf nicht zum Ausfall der App oder zum Verlust bereits gespeicherter Änderungen führen.

## 2. Bereits vorhandene Architektur

- Die Klimaanlagensteuerung erfolgt direkt und lokal über das Mitsubishi-Heavy-WF-RAC-WLAN-Modul.
- ioBroker ist nicht der direkte Steuerungsweg zur Klimaanlage.
- Die optionale ioBroker-WebSocket-Verbindung wird für die Synchronisation von Klimaprofilen zwischen Smartphones verwendet.
- Die lokale Datenhaltung muss unabhängig von dieser Verbindung funktionieren.
- Die bestehende ioBroker-Synchronisation funktioniert als bisheriger Synchronisationsweg und soll nicht unnötig ersetzt oder destabilisiert werden.

## 3. Vorgeschlagene Synchronisationsmöglichkeiten

### 3.1 Direkte Synchronisation zwischen Smartphones

Smartphones können Daten direkt miteinander austauschen.

Möglicher Ablauf:
- Smartphone A ändert ein Klimaprofil.
- Smartphone B erhält die Änderung direkt.
- Beide Geräte speichern ihre Daten lokal.
- Eine direkte Verbindung über das lokale Netzwerk ist möglich; eine Verbindung über das Internet wäre zusätzlich zu prüfen.

Vorteil:
- Kein zentraler Synchronisationsserver ist für den direkten Austausch erforderlich.

Nachteile und Grenzen:
- Beide Geräte müssen erreichbar sein.
- Sind beide Geräte gleichzeitig offline oder nicht erreichbar, können sie keine Änderungen austauschen.
- Direkte Verbindungen über das Internet sind wegen NAT, Firewalls und wechselnden Netzen technisch aufwendiger.

Direkte Smartphone-zu-Smartphone-Synchronisation ist daher eine mögliche Ergänzung, aber nicht automatisch die beste alleinige Lösung.

### 3.2 Cloud-Synchronisation

Ein unabhängiger Cloud-Dienst kann als alternativer Synchronisationsweg dienen.

Mögliche Daten:
- Klimaprofile
- Aktuell ausgewähltes Profil
- Individuelle Einstellungen
- Benutzereinstellungen
- Später gegebenenfalls weitere App-Einstellungen

Beide Smartphones greifen auf denselben synchronisierten Datenbestand zu.

Vorteil:
- Synchronisation bleibt auch dann möglich, wenn der ioBroker-Server ausgeschaltet oder defekt ist.

Nachteile:
- Es wird ein Cloud-Anbieter oder ein eigener, unabhängig betriebener Server benötigt.
- Datenschutz, Authentifizierung, Kosten, Verfügbarkeit und Betrieb müssen vor der Auswahl geklärt werden.

### 3.3 Lokale Speicherung und spätere Synchronisation (Offline-Synchronisation)

Diese Funktion ist unabhängig davon wichtig, welcher Synchronisationsdienst verwendet wird.

- Jede Änderung wird sofort lokal dauerhaft gespeichert.
- Die App bleibt bei einem Server- oder Internetausfall normal bedienbar.
- Nicht übertragene Änderungen werden lokal vorgemerkt.
- Sobald eine Synchronisationsverbindung wieder verfügbar ist, werden die Änderungen automatisch übertragen.
- Bereits lokal gespeicherte Daten dürfen durch einen vorübergehenden Verbindungsverlust nicht verschwinden.

## 4. Empfohlene Zielarchitektur

Die Synchronisation wird von der eigentlichen Klimasteuerung und von der lokalen Datenhaltung getrennt.

**KlimaPilot auf Smartphone A und Smartphone B**
- Jedes Gerät besitzt einen lokalen Datenbestand.
- Die App kann Profile und Einstellungen ohne Synchronisationsverbindung lesen und verwenden.

**Synchronisationsschicht**
- Einheitliche Schnittstelle zwischen App und Synchronisationsdiensten.
- ioBroker bleibt zunächst als bestehender Synchronisationsanbieter erhalten.
- Ein weiterer Anbieter, beispielsweise eine Cloud, kann später ergänzt werden.
- Direkte Geräte-Synchronisation kann bei Bedarf als weiterer Weg hinzukommen.

**Lokaler Datenspeicher**
- Änderungen werden zuerst lokal gespeichert.
- Eine dauerhafte Warteschlange (Outbox) hält noch nicht übertragene Änderungen vor.
- Nach Wiederherstellung einer Verbindung werden sie zuverlässig nachgeliefert.

Schematisch:

```
KlimaPilot Smartphone A       KlimaPilot Smartphone B
   lokale Daten                   lokale Daten
         |                              |
         +------ Synchronisationsschicht ------+
                        |
              +---------+---------+
              |                   |
           ioBroker             Cloud
        (bestehend)          (optional)
              |
       weitere Wege:
       direkte Geräte-Sync

Jedes Smartphone behält seinen lokalen Datenbestand
und arbeitet auch ohne Synchronisationsverbindung weiter.
```

## 5. Verbindliche Architekturentscheidungen

1. **Local-first:** Lokale Datenhaltung ist maßgeblich für die unmittelbare Bedienbarkeit. Die App darf für das Anzeigen und Anwenden bereits gespeicherter Profile keine Serververbindung benötigen.
2. **Synchronisation entkoppeln:** Die App-Funktionen dürfen nicht direkt von einem bestimmten Synchronisationsanbieter abhängig sein.
3. **ioBroker zunächst beibehalten:** Der vorhandene Weg bleibt erhalten, bis eine Alternative vollständig implementiert und getestet ist.
4. **Kein vorschneller Cloud-Zwang:** Anbieter, Kosten und Datenschutz werden vor einer Cloud-Entscheidung geprüft.
5. **Offline-Outbox:** Änderungen müssen bei fehlender Verbindung dauerhaft vorgemerkt und später nachgeliefert werden.
6. **Keine Datenverluste:** Ein Serverausfall, App-Neustart oder Gerätewechsel darf bereits lokal gespeicherte Änderungen nicht verwerfen.
7. **Updater bleibt unangetastet:** Dieses Architekturvorhaben ist kein Anlass, den funktionierenden Updater zu verändern.

## 6. Geplanter Umsetzungsfahrplan

### Phase 1 – Bestandsaufnahme und Absicherung der lokalen Datenhaltung
- Aktuelle Profil-, Auswahl- und Individualprofil-Speicherung vollständig prüfen.
- Prüfen, welche Daten heute ausschließlich über ioBroker verfügbar sind.
- Sicherstellen, dass alle benötigten Daten lokal dauerhaft vorhanden sind.
- Verhalten bei deaktiviertem, nicht erreichbarem oder fehlerhaftem ioBroker untersuchen.
- Keine bestehende Funktion entfernen.

### Phase 2 – Zuverlässige Offline-Synchronisation
- Outbox für noch nicht übertragene Änderungen einführen beziehungsweise vorhandene Mechanismen vereinheitlichen.
- Änderungen eindeutig identifizierbar und wiederholbar übertragen (Idempotenz).
- Bestätigungen erst nach erfolgreicher Verarbeitung als erledigt markieren.
- Wiederholungsversuche bei Verbindungsfehlern mit kontrollierter Wartezeit.
- Neustartfestigkeit der Warteschlange sicherstellen.

### Phase 3 – Konfliktbehandlung und Wiederanlauf
- Gleichzeitige Änderungen auf mehreren Smartphones berücksichtigen.
- Eine nachvollziehbare Konfliktstrategie festlegen; nicht ungeprüft den zuletzt eingetroffenen Serverwert übernehmen.
- Gerätekennung, Änderungszeitpunkt beziehungsweise Revision und eindeutige Event-ID berücksichtigen.
- Löschungen so abbilden, dass gelöschte Einträge nicht durch alte Offline-Daten ungewollt wieder auftauchen.
- Nach einem längeren Ausfall einen kontrollierten Abgleich durchführen.

### Phase 4 – Synchronisationsanbieter entkoppeln
- Einheitliche interne Schnittstelle für Synchronisationsanbieter definieren.
- Bestehenden ioBroker-Weg hinter dieser Schnittstelle weiterverwenden.
- Keine Änderung am funktionierenden Klimasteuerungsweg.
- Keine unnötige Änderung am bestehenden ioBroker-Protokoll, solange die Entkopplung dies nicht erfordert.

### Phase 5 – Alternative ergänzen
- Cloud-Anbieter oder eigenen unabhängigen Dienst anhand Kosten, Datenschutz, Sicherheit und Verfügbarkeit bewerten.
- Alternative als zusätzlichen Anbieter implementieren.
- Direkte Smartphone-Synchronisation als optionale Ergänzung bewerten.
- Anbieterwechsel und Wiederherstellung ohne Datenverlust ermöglichen.

### Phase 6 – Ausfall- und Wiederherstellungstests
Mindestens folgende Szenarien müssen praktisch geprüft werden:
- ioBroker ausgeschaltet, Profile weiterhin anzeigen und anwenden.
- Während des Ausfalls Profil ändern und App schließen beziehungsweise Smartphone neu starten.
- ioBroker wieder starten und vorgemerkte Änderungen vollständig übertragen.
- Beide Smartphones zeitweise offline und anschließend wieder online.
- Änderungen auf beiden Smartphones während einer Trennung.
- Doppelte Zustellung eines Events.
- Unterbrochene Übertragung und erneuter Versuch.
- Löschen und anschließender Abgleich.
- Optionaler alternativer Synchronisationsdienst nicht erreichbar.
- Wechsel zwischen Synchronisationsanbietern ohne Verlust lokaler Daten.

## 7. Erfolgskriterien

Das Vorhaben gilt erst dann als erfolgreich umgesetzt, wenn:
- KlimaPilot Profile und Einstellungen ohne ioBroker-Verbindung lokal anzeigen und verwenden kann.
- Änderungen während eines Ausfalls dauerhaft lokal erhalten bleiben.
- Änderungen nach Wiederherstellung der Verbindung zuverlässig übertragen werden.
- Doppelte Übertragungen keine doppelten oder widersprüchlichen Datensätze erzeugen.
- Konflikte zwischen mehreren Smartphones kontrolliert behandelt werden.
- Ein Ausfall eines Synchronisationsanbieters die lokale Klimaanlagensteuerung nicht beeinträchtigt.
- Die Tests mit mindestens zwei Smartphones und realen Verbindungsunterbrechungen bestanden wurden.

## 8. Priorität und Arbeitsweise

Dieses Vorhaben ist ausdrücklich **zeitnah** einzuplanen und nicht nur eine unverbindliche Zukunftsidee. Es soll nach Abschluss beziehungsweise sinnvoller Absicherung der unmittelbar laufenden Arbeiten in einem gebündelten Entwicklungspaket begonnen werden.

Die Umsetzung erfolgt schrittweise auf dem produktiven Branch `main`. Vor jeder Codeänderung werden der aktuelle GitHub-Stand und alle betroffenen Dateien geprüft. Bestehende funktionierende Synchronisations- und Steuerungsfunktionen bleiben erhalten. Jede Phase wird gebaut und gezielt getestet, bevor die nächste größere Änderung erfolgt.

**Wichtig:** Eine Cloud oder direkte Geräteverbindung ist noch nicht ausgewählt oder implementiert. Festgelegt ist zunächst das Ziel: lokale Nutzbarkeit, dauerhafte Speicherung, zuverlässige Nachlieferung und Austauschbarkeit der Synchronisationsanbieter.


## 9. Umsetzungsstand – dauerhafte Profiländerungen (Arbeitsbranch)

**Stand: 16.11.2026 – in Arbeit, noch nicht für Produktion freigegeben.**

Auf dem separaten Branch `fix/durable-profile-change-journal` wurde der erste Teil der Offline-Absicherung umgesetzt:

- Profil-Upserts und Profil-Löschungen werden im selben synchronen SharedPreferences-Commit wie der lokale Profilbestand in ein dauerhaftes Journal geschrieben.
- Jedes Journal-Ereignis erhält bereits beim lokalen Commit eine stabile UUID-basierte Ereignis-ID.
- Die Übernahme in die vorhandene Outbox ist idempotent. Eine dauerhaft gespeicherte Import-ID verhindert auch dann eine erneute Einreihung, wenn die Outbox das Ereignis zwischenzeitlich bereits übertragen und bestätigt hat.
- Journal-Einträge werden erst nach erfolgreicher Übernahme in die Outbox entfernt. Scheitert die Bereinigung, kann der nächste Lauf denselben Eintrag erneut sicher übernehmen.
- Die Profil-UI verwendet für Änderungen und Löschungen mit gemeinsamer Gerätekennung diesen atomaren Speicherweg. Ohne Gerätekennung bleibt das bisherige rein lokale Speichern erhalten.
- Das Journal wird beim nächsten Profil-Synchronisationslauf vor dem Upload in die Outbox überführt.
- Vor einer Änderung der automatisch erzeugten WF-RAC-Gerätekennung werden noch offene Journal-Einträge zunächst vollständig in die Outbox übernommen. Erst danach werden die ausstehenden Ereignisse gemeinsam auf die neue Kennung umgestellt; bei unvollständiger Übernahme wird die Kennungsänderung abgebrochen.
- Aktive Profilauswahl und individuelle Einstellungen werden bei aktivierter Synchronisierung nun jeweils zusammen mit ihrem Outbox-Ereignis in einem einzigen synchronen SharedPreferences-Commit gespeichert. Ohne gemeinsame Gerätekennung bleibt der lokale Speicherweg erhalten.

**Noch nicht abgesichert:** aktive Profilauswahl und individuelle Einstellungen besitzen weiterhin getrennte lokale Speicherung und Outbox-Vormerkung. Auch die Remote-Übernahme und der zugehörige Versionsmarker sind noch nicht atomar gekoppelt. Diese Punkte bleiben ausdrücklich offen.

**Prüfstatus:** Noch kein Android-Build und keine automatisierten Laufzeittests für diesen Arbeitsbranch bestätigt. Im bisherigen Repository-Baum ist kein `app/src/test`-Testbestand vorhanden. Vor Freigabe sind mindestens Kotlin-/Gradle-Build, gezielte Tests der Journal-Wiederanläufe, Code-Review (einschließlich des vereinbarten unabhängigen Claude-Reviews) und eine erneute Prüfung des vollständigen Diffs erforderlich.

Der Updater, dessen Berechtigungen und Installationsablauf sowie der direkte WF-RAC-Steuerungsweg wurden in diesem Änderungspaket nicht bearbeitet.
