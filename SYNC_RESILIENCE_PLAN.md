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

**Stand: 17.11.2026 – in Arbeit, noch nicht für Produktion freigegeben.**

Auf dem separaten Branch `fix/durable-profile-change-journal` wurde der erste Teil der Offline-Absicherung umgesetzt:

- Profil-Upserts und Profil-Löschungen werden im selben synchronen SharedPreferences-Commit wie der lokale Profilbestand in ein dauerhaftes Journal geschrieben.
- Jedes Journal-Ereignis erhält bereits beim lokalen Commit eine stabile UUID-basierte Ereignis-ID.
- Die Übernahme in die vorhandene Outbox ist idempotent. Eine dauerhaft gespeicherte Import-ID verhindert auch dann eine erneute Einreihung, wenn die Outbox das Ereignis zwischenzeitlich bereits übertragen und bestätigt hat.
- Journal-Einträge werden erst nach erfolgreicher Übernahme in die Outbox entfernt. Scheitert die Bereinigung, kann der nächste Lauf denselben Eintrag erneut sicher übernehmen.
- Die Profil-UI verwendet für Änderungen und Löschungen mit gemeinsamer Gerätekennung diesen atomaren Speicherweg. Ohne Gerätekennung bleibt das bisherige rein lokale Speichern erhalten.
- Das Journal wird beim nächsten Profil-Synchronisationslauf vor dem Upload in die Outbox überführt.
- Vor einer Änderung der automatisch erzeugten WF-RAC-Gerätekennung werden noch offene Journal-Einträge zunächst vollständig in die Outbox übernommen. Erst danach werden die ausstehenden Ereignisse gemeinsam auf die neue Kennung umgestellt; bei unvollständiger Übernahme wird die Kennungsänderung abgebrochen.
- Aktive Profilauswahl und individuelle Einstellungen werden bei aktivierter Synchronisierung nun jeweils zusammen mit ihrem Outbox-Ereignis in einem einzigen synchronen SharedPreferences-Commit gespeichert. Ohne gemeinsame Gerätekennung bleibt der lokale Speicherweg erhalten.

**Remote-Wiederanlauf:** Bei eingehenden Remote-Ereignissen werden lokale Nutzdaten zunächst gespeichert und der zugehörige Versionsmarker anschließend in einem separaten Commit festgehalten. Ein Absturz dazwischen kann zur erneuten Verarbeitung führen. Die derzeitigen Operationen (Profil-Upsert/Löschung, Auswahl setzen/zurücksetzen und Snapshot überschreiben) sind auf denselben Zielzustand ausgelegt und damit wiederholbar; eine gemeinsame Transaktion über beide Speicherbereiche besteht jedoch nicht. Dieser Wiederanlaufpfad bleibt vor Freigabe gezielt zu prüfen.

**Importmarker-Bereinigung:** Die dauerhaften Journal-Import-IDs werden jetzt entfernt, sobald der Repository-Journal-Commit die entsprechenden Einträge erfolgreich gelöscht hat. Die Reihenfolge ist bewusst: erst Outbox + Importmarker committen, dann Repository-Journal bereinigen, danach Importmarker bereinigen. Ein Absturz vor der Journal-Bereinigung lässt den Marker bestehen und verhindert eine doppelte Einreihung; ein Absturz nach der Journal-Bereinigung kann höchstens einen verwaisten Marker hinterlassen, der beim nächsten Lauf entfernt wird. Damit wächst die Liste im Normalbetrieb nicht mehr dauerhaft mit allen jemals synchronisierten Profiländerungen.

**Gemeinsame Prozesssperre – umgesetzt auf dem Arbeitsbranch:**
- Repository- und Store-Zugriffe verwenden jetzt dasselbe prozessweite, reentrante Monitor-Objekt statt instanzlokaler `@Synchronized`-Sperren. Damit werden auch getrennt erzeugte Repository-/Store-Instanzen serialisiert.
- Journal-Flush und Outbox-Übernahme laufen unter derselben Sperre. Die Gerätekennungs-Migration umfasst Journal-Flush und anschließendes Outbox-Rekey in einer gemeinsamen kritischen Sektion.
- Die Prüfung eines Remote-Ereignisses, seine lokale Anwendung einschließlich einer gegebenenfalls nötigen Auswahlbereinigung sowie das Speichern des Versionsmarkers sind gemeinsam geschützt.
- Netzwerk-I/O bleibt außerhalb der Sperre. Eine während des Uploads umgeschlüsselte Outbox-Änderung wird bei der Bestätigung nicht versehentlich entfernt: Bestätigt wird nur noch exakt die hochgeladene Ereignisrevision (Event-ID, Gerätekennung, Installation und Sequenz). Eine zwischenzeitlich umgeschlüsselte Revision bleibt zur erneuten Übertragung erhalten.

**Automatisierte Tests – ergänzt; CI-Lauf #31 für den damaligen Head `8650e6e` erfolgreich:**
- Robolectric-Testbasis für echte Android-SharedPreferences.
- Parallelzugriffe über mehrere Repository-Instanzen: Profile und Journal-Ereignisse dürfen nicht verloren gehen.
- Parallelzugriffe über mehrere Store-Instanzen: alle Outbox-Ereignisse und eindeutige Sequenzen bleiben erhalten.
- Journal-Flush parallel zu Profiländerungen.
- Outbox-Rekey während eines laufenden Uploads mit anschließender Bestätigung der alten Revision.
- Wiederholter Journal-Flush muss idempotent bleiben.
- Zusätzlich einfacher Test der Reentranz und gegenseitigen Ausschließung des gemeinsamen Monitors.

**Review und Nachbesserung – 17.11.2026:**
- Das unabhängige Claude-Review fand einen Konflikt zwischen ungeflushter lokaler Journaländerung und eingehendem Remote-Ereignis. Vor jeder Remote-Versionsprüfung wird das lokale Journal jetzt innerhalb derselben gemeinsamen Sperre geflusht.
- Die aktive Profilauswahl wird auch bei `requestSync = false` als Outbox-Ereignis vorgemerkt; das Flag steuert nur die Erhöhung von `syncRevision`.
- Ein fehlgeschlagener Journal-Flush bricht ab, ohne das Repository-Journal zu leeren.
- Beim Rekey werden offene Ereignisse, der Initialisierungsmarker und jetzt auch sämtliche bekannten Profilversionen vom alten auf den neuen Geräteschlüssel übertragen. Bei bereits vorhandener Zielversion bleibt jeweils die neuere Version erhalten. Damit bleiben auch bestätigte Ereignisse berücksichtigt, die nicht mehr in der Outbox liegen.
- Der Versionsübernahme-Fix ist durch einen gezielten Regressionstest für einen bestätigten alten Versionsstand (Clock 45) gegenüber einer vorhandenen Zielversion (Clock 40) abgedeckt.
- Die Übernahme der Versionsschlüssel sammelt zunächst alle zu migrierenden Schlüssel in einer separaten Liste, bevor neue JSON-Properties geschrieben werden. Damit wird eine Änderung des `JSONObject.keys()`-Iterators während der Iteration vermieden (Head `c8c08bf`).

**Automatisierte Tests und CI:**
- GitHub-Actions-Lauf #35 für Head `eb7ce131`: erfolgreich (Unit-Tests und Debug-Kompilierung).
- GitHub-Actions-Lauf #37 für Head `0caed4c`: erfolgreich (Unit-Tests und Debug-Kompilierung einschließlich Rekey-Versionsübernahme).
- GitHub-Actions-Lauf #38 für Head `0bdd4a5`: erfolgreich (Unit-Tests und Debug-Kompilierung; einschließlich Journal-Recovery- und Remote-Clock-Regressionstests).
- GitHub-Actions-Lauf #40 für den aktuellen Head `c8c08bf`: erfolgreich (Unit-Tests und Debug-Kompilierung einschließlich der Absicherung der JSON-Key-Iteration).
- Die Replay-Tests simulieren die jeweiligen dauerhaft gespeicherten Zwischenzustände mit SharedPreferences und neuen Repository-Instanzen. Sie sind gezielte Wiederanlauf-/Idempotenztests, aber kein echter Betriebssystem-Prozessabsturztest.
- Der PR-Workflow prüft Unit-Tests und Debug-Kompilierung. Release-Keystore, signierter Release-Build, APK-Signaturprüfung und Produktionsartefakt-Upload sind nicht Bestandteil dieses PR-Workflows.

**Noch offen vor Freigabe:**
- Unabhängiges Re-Review des aktuellen Heads `c8c08bf`, insbesondere Rekey-Versionsübernahme, JSON-Key-Iteration und Replay-/Clock-Tests.
- Den PR-Branch kontrolliert mit dem aktuellen `main` abgleichen; der Branch ist gegenüber `main` divergent, die acht Rückstände betreffen nach dem zuletzt geprüften Compare Status-/Projektdokumentation.
- Die Konfliktregeln für bereits geflushte lokale Änderungen (Last-Writer-Wins nach Logical Clock) und lokale Bearbeitung gegen Remote-Löschung sind bewusste Verhaltensentscheidungen und sollten vor Freigabe dokumentiert beziehungsweise bestätigt werden.
- Ein gezielter Commit-Fehler im Outbox-Flush wird jetzt per injizierbarem Commit-Ergebnis getestet; ein echter OS-Prozessabsturz bleibt davon getrennt.
- Die getrennten SharedPreferences-Commits sind nicht übergreifend crash-atomar. Die gemeinsame Prozesssperre verhindert konkurrierende Zugriffe innerhalb des Prozesses, ersetzt aber keine Transaktion über mehrere Speicherbereiche.
- Hauptthread-Performance bleibt ein separater Beobachtungspunkt. Ungültige Remote-Ereignisse und der UI-nahe Auswahl-Speicherpfad werden jetzt gezielt regressionsgetestet.

Der PR ist weiterhin nicht für Produktion freigegeben. Produktionsversion bleibt `0.4.63` / versionCode `93`. Es wurde kein Produktionsbuild oder Release erstellt.

Der Updater, dessen Berechtigungen und Installationsablauf sowie der direkte WF-RAC-Steuerungsweg wurden in diesem Änderungspaket nicht bearbeitet.


## 10. Review-Nacharbeit – Fehlerisolation und bekannte Grenzen (17.11.2026)

- Ungültige Remote-Ereignisse werden einzeln abgelehnt. Die Verarbeitung fährt mit späteren Ereignissen fort; Anzahl und erster Validierungsgrund erscheinen im Synchronisierungsstatus. Nur `IllegalArgumentException` aus der Ereignisvalidierung wird isoliert. Speicher-/Persistenzfehler (insbesondere `IllegalStateException`) brechen den Lauf weiterhin ab.
- Ein abgelehntes Remote-Ereignis wird nicht auf dem ioBroker verändert oder gelöscht. Es kann daher bei späteren Synchronisierungsläufen erneut als ungültig gemeldet werden; gültige Ereignisse dahinter werden trotzdem verarbeitet.
- **Rekey-Grenze (M2):** Beim Wechsel der Gerätekennung werden bestätigte Profilversionen mit übertragen, damit ein bereits bestätigter Stand nicht durch die Initialisierungsmarkierung unsichtbar wird. Hat das Zielgerät bereits eine neuere Version, bleibt diese maßgeblich. Ein bereits bestätigter Quellstand wird nicht künstlich erneut hochgeladen: ein Upload mit neuer Clock könnte einen tatsächlich neueren Zielstand überschreiben. Deshalb kann ein anderer Teilnehmer einen älteren Stand behalten, bis eine spätere echte Profiländerung erfolgt.
- **Konfliktregel (N2):** Bei unterschiedlichen Logical Clocks gewinnt die höhere Clock. Ungeflushte lokale Journaländerungen werden vor der Remote-Versionsprüfung in die Outbox übernommen und erhalten dadurch eine Clock oberhalb der zuvor beobachteten Remote-Clock. Eine bereits geflushte lokale Änderung kann dagegen von einem späteren Remote-Ereignis mit höherer Clock überholt werden. Eine lokale Bearbeitung gegen eine Remote-Löschung gewinnt nur dann, wenn ihre lokale Version nach dieser Regel neuer ist; andernfalls gilt die Remote-Löschung.
- **Rekey-Key-Iteration:** Der Code erstellt vor dem Schreiben eine Momentaufnahme der zu migrierenden JSON-Schlüssel. Der vorhandene Versionsvergleichstest prüft die Zusammenführung alter/neuer Versionen, nicht isoliert die Iterator-Invalidierung; die frühere Aussage, CI-Lauf #40 sichere genau diesen Iteratorfehler regressionsseitig ab, war zu weitgehend.
- **Wiederherstellungstests:** Commit-Fehler im Outbox-Flush, Replay nach Upload-Bestätigung, Rekey ohne Zielversion und der aus der UI verwendete Auswahl-Speicherpfad werden separat geprüft. Robolectric bleibt eine Simulation mit SharedPreferences und ersetzt keine echten Prozess-Kill- oder Zwei-Smartphone-Tests.
- **Rekey ohne initialisierten Quellschlüssel:** War der alte Geräteschlüssel noch nicht als initialisiert markiert, wird dieser Marker beim Rekey nicht übertragen. Der neue Schlüssel kann deshalb beim nächsten Initialisierungslauf einen vollständigen Initial-Upload auslösen. Dabei besteht das dokumentierte Risiko, dass lokal vorhandene ältere Profile neuere Remote-Stände überschreiben können; dieser Sonderfall ist vor einer späteren Erweiterung der Rekey-Logik gezielt zu bewerten.
- **Remote-Clock abgelehnter Ereignisse:** `syncNow` beobachtet derzeit die höchste Clock aller eingelesenen Remote-Ereignisse, bevor die Einzelvalidierung und Anwendung beginnt. Dadurch kann auch ein später abgelehntes Ereignis die lokale Logical Clock erhöhen. Ein sehr hoher, aber syntaktisch lesbarer Clock-Wert kann den lokalen Zähler entsprechend weit anheben; die Ereignisvalidierung begrenzt diesen Wert derzeit nicht.

## 9. Verbindliche Entscheidung: Synchronisation ohne Cloud- und Dienstbindung (17.11.2026)

Die Zielarchitektur wird ausdrücklich auf Kostenkontrolle, lokale Nutzbarkeit und Unabhängigkeit von externen Anbietern ausgerichtet.

### Festgelegte Leitlinien

1. **Kein Cloud-Dienst und kein zusätzlicher Vermittlungsserver:** KlimaPilot soll für die Smartphone-Synchronisation keinen kostenpflichtigen oder fremdbetriebenen Cloud-/Relay-Dienst voraussetzen. Es entstehen dadurch keine zusätzlichen laufenden Dienstkosten und keine Bindung an einen externen Synchronisationsanbieter.
2. **ioBroker bleibt zunächst der vorhandene zentrale Austauschpunkt:** Der bereits genutzte ioBroker-Weg wird weiterverwendet. Es wird kein zusätzlicher Server nur für KlimaPilot-Synchronisation eingerichtet.
3. **Local-first bleibt maßgeblich:** Jedes Smartphone hält seine benötigten Profile und Einstellungen lokal vor. Die App muss diese Daten ohne ioBroker-Verbindung anzeigen und lokal verwenden können. Die lokale Klimaanlagensteuerung bleibt davon vollständig unabhängig.
4. **Offline-Outbox und spätere Nachlieferung:** Änderungen werden zuerst dauerhaft lokal gespeichert. Ist ioBroker nicht erreichbar, bleiben sie in der lokalen Outbox und werden übertragen, sobald der bestehende Austauschweg wieder verfügbar ist.
5. **Direkte Geräte-Synchronisation nur optional im selben lokalen Netzwerk:** Eine direkte Smartphone-zu-Smartphone-Verbindung, beispielsweise im selben WLAN, kann später als zusätzliche Möglichkeit geprüft werden. Sie ist keine Voraussetzung für die Grundfunktion und wird erst nach separater technischer Bewertung umgesetzt.
6. **Keine garantierte Sofortsynchronisation über getrennte Netze:** Sind beide Smartphones unterwegs, in unterschiedlichen WLANs oder nur über Mobilfunk erreichbar und ist ioBroker von dort nicht erreichbar, können sie ohne Vermittlungsdienst nicht zuverlässig sofort miteinander synchronisieren. Änderungen bleiben lokal vorgemerkt und werden beim nächsten erreichbaren gemeinsamen Austauschweg nachgeliefert. Das ist ein bewusster Kompromiss zugunsten von Kosten- und Dienstunabhängigkeit.
7. **Keine Einschränkung der Klimasteuerung:** Der direkte WF-RAC-Steuerungsweg bleibt unabhängig von ioBroker und jeder Synchronisationsverbindung.

### Daraus folgende Umsetzungsreihenfolge

- Zuerst Local-first-Datenhaltung, dauerhafte Outbox, Wiederanlauf und Konfliktbehandlung zuverlässig absichern.
- ioBroker als einzigen zunächst vorgesehenen geräteübergreifenden Austauschpunkt beibehalten.
- Danach optional prüfen, ob eine direkte Synchronisation im selben WLAN einen echten Mehrwert bietet und ohne zusätzliche Infrastruktur zuverlässig umgesetzt werden kann.
- Cloud-, Relay- oder sonstige externe Vermittlungsdienste sind nicht Bestandteil der aktuellen Planung. Eine spätere Änderung dieser Grundentscheidung erfordert eine ausdrückliche neue Entscheidung; sie wird nicht stillschweigend vorausgesetzt.

### Bewusste Einschränkung

Ohne erreichbaren gemeinsamen Austauschpunkt gibt es keine sofortige Synchronisation zwischen räumlich getrennten Smartphones. KlimaPilot bleibt auf beiden Geräten lokal nutzbar; die Synchronisation wird nachgeholt, sobald ioBroker oder ein später ausdrücklich freigegebener direkter lokaler Weg verfügbar ist.
