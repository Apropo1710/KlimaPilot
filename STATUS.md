# Projektstatus – KlimaPilot

Zuletzt aktualisiert: 11.11.2026  
Repository: https://github.com/Apropo1710/KlimaPilot  
Produktions-/Hauptbranch: `main`

> Diese Datei ist die zentrale Übergabe für neue Chats. Vor jeder Arbeit zuerst lesen und nach jeder zusammenhängenden Änderung aktualisieren.

## Projektziel
Eigenständige Android-App zur Steuerung von Klimaanlagen über ioBroker.

## Bekannter Projektstand
- Das Repository ist eigenständig und von HomePilot getrennt.
- Die README beschreibt das Projekt derzeit nur knapp als Android-App zur Klimaanlagensteuerung über ioBroker.
- Die Klimaanlagen wurden noch nicht installiert (Stand der letzten bekannten Rückmeldung: 09.11.2026); der Nutzer erwartet die Installation in den nächsten Wochen.
- Der ioBroker-Adapter `mhi-wfrac` ist noch nicht eingerichtet und soll erst mit Installation der Mitsubishi-Heavy-Klimaanlage genutzt werden.
- Konkrete Datenpunkte, Zeitsteuerung, Timer und Wochenplan sollen nach Installation anhand der real verfügbaren Adapterdatenpunkte festgelegt werden. Nicht vorab Funktionen als verifiziert dokumentieren.

## Nächste Schritte nach Geräteinstallation
1. Adapter `mhi-wfrac` einrichten und reale Datenpunktstruktur erfassen.
2. Verfügbare Istwerte, Sollwerte, Betriebsmodi und Schreibmöglichkeiten dokumentieren.
3. Prüfen, welche Datenpunkte für Zeitsteuerung, Timer und Wochenplan geeignet sind.
4. Bedienkonzept und Automatik erst auf Basis der tatsächlichen Datenpunkte implementieren.
5. Tests mit realer Klimaanlage durchführen und bestätigte Funktionen hier dokumentieren.

## Branch-Wechsel und Übernahme der Statusdatei

- Die `STATUS.md` gehört zum jeweiligen Projektstand und wird auf jedem aktiv bearbeiteten Branch mitgeführt.
- Vor einem Branch-Wechsel zuerst den aktuellen Stand dieser Datei sichern und im Zielbranch übernehmen bzw. mit dessen Stand zusammenführen.
- Wird ein Feature-/Fix-Branch in `main` gemergt, muss die aktuelle `STATUS.md` Bestandteil desselben Merge-Ergebnisses sein. Danach wird sie auf `main` weitergeführt.
- Wird ein neuer Branch von einem älteren Stand erstellt, die aktuelle Statusdatei aus dem zuletzt maßgeblichen Branch nachziehen, bevor dort weitergearbeitet wird.
- Nach dem Merge ist `main` die maßgebliche Statusquelle für den übernommenen Projektstand; bei neuer Arbeit auf einem Feature-Branch wird sie dort fortgeschrieben und beim nächsten Merge wieder nach `main` übertragen.
- Keine Statusdatei durch eine ältere Kopie überschreiben: Änderungen aus beiden Branches anhand der Commit-Historie zusammenführen.

## Arbeitsregeln
- Vor Änderungen STATUS.md und relevante Quelldateien lesen.
- Nach jeder zusammenhängenden Änderung STATUS.md im selben Commit aktualisieren.
- Builds/Tests für den exakten Commit prüfen und dokumentieren.
- Keine Funktionen oder Datenpunkte als verfügbar behaupten, bevor sie am installierten Adapter/Gerät verifiziert wurden.
- Bei Chatwechsel hier beginnen; keine bereits geklärten Projektentscheidungen erneut abfragen.
