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

## 9. Verbindliche Entscheidung: Synchronisation ohne Cloud- und Dienstbindung (17.11.2026)

Die Zielarchitektur wird ausdrücklich auf Kostenkontrolle, lokale Nutzbarkeit und Unabhängigkeit von externen Anbietern ausgerichtet.

### Festgelegte Leitlinien

1. **Kein Cloud-Dienst und kein zusätzlicher Vermittlungsserver:** KlimaPilot soll für die Smartphone-Synchronisation keinen kostenpflichtigen oder fremdbetriebenen Cloud-/Relay-Dienst voraussetzen. Es entstehen dadurch keine zusätzlichen laufenden Dienstkosten und keine Bindung an einen externen Synchronisationsanbieter.
2. **ioBroker bleibt zunächst der vorhandene zentrale Austauschpunkt:** Der bereits genutzte ioBroker-Weg wird weiterverwendet. Es wird kein zusätzlicher Server nur für KlimaPilot-Synchronisation eingerichtet.
3. **Local-first bleibt maßgeblich:** Jedes Smartphone hält seine benötigten Profile und Einstellungen lokal vor. Die App muss diese Daten ohne ioBroker-Verbindung anzeigen und lokal verwenden können. Die lokale Klimaanlagensteuerung bleibt davon vollständig unabhängig.
4. **Offline-Outbox und spätere Nachlieferung:** Änderungen werden zuerst dauerhaft lokal gespeichert. Ist ioBroker nicht erreichbar, bleiben sie in der lokalen Outbox und werden übertragen, sobald der bestehende Austauschweg wieder verfügbar ist.
5. **Direkte Geräte-Synchronisation als lokaler Fallback im selben WLAN:** Nach Abschluss und Stabilisierung der laufenden Profil-Synchronisation soll eine direkte Smartphone-zu-Smartphone-Verbindung als zusätzlicher Synchronisationsweg geprüft und anschließend separat umgesetzt werden. Sie funktioniert ausschließlich, wenn beide Smartphones gleichzeitig im selben lokalen WLAN erreichbar sind. Ist ioBroker nicht erreichbar, bleiben Änderungen auf jedem Smartphone lokal gespeichert. Sobald beide Geräte wieder im selben WLAN sind, gleichen sie die bis dahin aufgelaufenen Änderungen automatisch nachträglich in beide Richtungen ab. Die App und die lokale Klimasteuerung bleiben währenddessen vollständig nutzbar. Die vorhandene Outbox-, Ereignis- und Konfliktlogik wird weiterverwendet; es entsteht kein zweites unabhängiges Synchronisationssystem.
6. **Keine garantierte Sofortsynchronisation über getrennte Netze:** Sind beide Smartphones unterwegs, in unterschiedlichen WLANs oder nur über Mobilfunk erreichbar und ist ioBroker von dort nicht erreichbar, können sie ohne Vermittlungsdienst nicht zuverlässig sofort miteinander synchronisieren. Änderungen bleiben lokal vorgemerkt und werden beim nächsten erreichbaren gemeinsamen Austauschweg nachgeliefert. Das ist ein bewusster Kompromiss zugunsten von Kosten- und Dienstunabhängigkeit.
7. **Keine Einschränkung der Klimasteuerung:** Der direkte WF-RAC-Steuerungsweg bleibt unabhängig von ioBroker und jeder Synchronisationsverbindung.

### Daraus folgende Umsetzungsreihenfolge

- Zuerst Local-first-Datenhaltung, dauerhafte Outbox, Wiederanlauf und Konfliktbehandlung zuverlässig absichern.
- ioBroker als einzigen zunächst vorgesehenen geräteübergreifenden Austauschpunkt beibehalten.
- Nach Abschluss und Stabilisierung von PR #3 die direkte WLAN-Synchronisation als separates Entwicklungspaket umsetzen: Geräteerkennung und sichere Verbindung im selben lokalen Netzwerk, danach automatischer beidseitiger Nachholabgleich über die vorhandene Outbox- und Konfliktlogik.
- Cloud-, Relay- oder sonstige externe Vermittlungsdienste sind nicht Bestandteil der aktuellen Planung. Eine spätere Änderung dieser Grundentscheidung erfordert eine ausdrückliche neue Entscheidung; sie wird nicht stillschweigend vorausgesetzt.

### Bewusste Einschränkung

Ohne erreichbaren gemeinsamen Austauschpunkt gibt es keine sofortige Synchronisation zwischen räumlich getrennten Smartphones. KlimaPilot bleibt auf beiden Geräten lokal nutzbar. Sobald beide Smartphones wieder gleichzeitig im selben WLAN sind, soll der direkte lokale Weg die während der Trennung gespeicherten Änderungen automatisch nachträglich in beide Richtungen abgleichen. Sind die Geräte in unterschiedlichen WLANs oder nur über Mobilfunk erreichbar und ioBroker ist nicht verfügbar, bleiben Änderungen bis zum nächsten gemeinsamen Austauschweg lokal vorgemerkt.
