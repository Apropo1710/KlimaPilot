## Entwicklungsstand – 14.11.2026, 23:07 MEZ
- Maßgeblicher Branch: `main`; aktueller Head nach Umsetzung der Schriftgrößenfunktion: `3c55b7c28cdc5455fafc8b386b5494b55567e20a`.
- Produktionsversion im Quellcode: **0.4.28 / versionCode 58**.
- Neue Funktion: Schriftgröße in fünf Stufen (85 %, 100 %, 115 %, 130 %, 150 %) einstellbar; wird als `fontScale` im jeweiligen KlimaPilot-Benutzerkonto im ioBroker-Kontenregister gespeichert.
- Die Schriftgröße wird beim Anmelden aus dem Konto geladen und über Compose-`LocalDensity` auf die gesamte App angewendet. Neue Konten starten mit 100 %.
- Bestehende Konten bleiben kompatibel: fehlendes `fontScale` wird als 100 % interpretiert. Passwortzurücksetzungen erhalten die gespeicherte Schriftgröße.
- Die Auswahl wird über die vorhandene revisionsgesicherte Konten-Repository-Funktion gespeichert; kein lokaler Gerätewert.
- Klimaprofile sind als nächster Teil des vereinbarten Pakets vorgemerkt. Die eigentliche Profilbelegung und Anwendung erfolgt erst anhand der nach Installation eingerichteten und geprüften Mitsubishi-/ioBroker-Datenpunkte; aktuell sind die Klima-Steuerfelder noch nicht freigegeben.
- GitHub-Actions-Build für Version 0.4.28: **noch nicht geprüft**. APK, Signatur und Release `latest` sind daher noch nicht bestätigt.
- Keine Änderung an Benutzerrollen, ioBroker-Verbindung oder Updater.

- Maßgeblicher Branch: `main`; Zuletzt vor dieser Statuspflege geprüfter Branch-Head: `1a7c5d7460d9cdf2241027eb7de3f90a6b92ebb8` (reiner Dokumentationscommit); der Laufzeitcode blieb unverändert.
- App-Code-Commit: `56b49749a77f07b50f6c2bac2085edd250e56116`; Produktionsversion **0.4.27 / versionCode 57**.
- APK-Build-Quellcommit: `7984def11f7da78407f24697ab4d6128ab700706` (Dokumentationscommit auf demselben App-Code-Stand); GitHub Actions Run `43405429051` / #103: **completed / success**.
- Release `latest`: erfolgreich aktualisiert; Asset `KlimaPilot.apk` vorhanden. APK-SHA-256: `537ea7f086b88a1fecc7106dc8733d20c860c2deabfca666b39fdc11fdbe705d`; Signaturprüfung laut Produktionsnachweis erfolgreich.
- Der Nutzer hat die scrollbaren Dialoge der Benutzerverwaltung auf dem Gerät bestätigt: **Gerätetest erfolgreich**.
- Der Updater bleibt abgeschlossen und vom Nutzer bestätigt; keine Änderung daran.
- Ältere „Build ausstehend“- und „vorgesehene Version“-Einträge weiter unten sind historische Zwischenstände.
- Offen bleiben ausschließlich hardwareabhängige Arbeiten nach Installation der Mitsubishi-Klimaanlage.

Zuletzt aktualisiert: 14.11.2026, 02:57 MEZ
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


## Änderung 13.11.2026 – Update-Hinweis beim App-Start

- Die Startprüfung entspricht dem bereits in HomePilot eingesetzten Ablauf.
- Nur mit bereits gespeicherter GitHub-Anmeldung wird beim App-Start das private Release `latest` geprüft.
- Bei fehlender Anmeldung, Netzwerkfehlern oder nicht erreichbarem GitHub erscheint kein Popup.
- Bei einer neueren Release-Version erscheint ein Dialog mit **„Zu Einstellungen“** und **„Später“**.
- „Zu Einstellungen“ öffnet die vorhandene App-Einstellungsseite mit der manuellen Updatekarte; Download und APK-Installation bleiben unverändert.
- Die Prüfung läuft einmal pro App-Start. Der Dialog kann bis zum nächsten App-Start geschlossen werden.
- App-Version: 0.4.24 / versionCode 54.
- Build und Smartphone-Test stehen nach dem Commit noch aus.


## Änderung 13.11.2026 – Benutzerkonten und Rollen

- Eigenständige KlimaPilot-Konten nach dem bereits in HomePilot erprobten Konzept ergänzt.
- Kein Gastmodus: Die App bleibt bis zur erfolgreichen Kontoanmeldung gesperrt. Beim ersten Start wird nach erfolgreicher ioBroker-Verbindung das erste Administratorkonto eingerichtet (inklusive Passwortbestätigung).
- Konten liegen getrennt von HomePilot im ioBroker-Datenpunkt `0_userdata.0.KlimaPilot.accounts`; Konten und Anzeigenamen sind dadurch geräteübergreifend verfügbar.
- Passwörter werden mit PBKDF2-HMAC-SHA256, individuellen Zufallssalzen und 120.000 Iterationen gespeichert, nicht im Klartext.
- Administratoren können Benutzer und weitere Administratoren anlegen, Passwörter zurücksetzen und andere Konten löschen. Das letzte Administratorkonto und das aktuell angemeldete Konto können nicht gelöscht werden.
- Jeder Benutzer kann sein eigenes Passwort ändern und den Benutzer wechseln. Der Wechsel beendet nicht die ioBroker-WebSocket-Verbindung.
- Raum-/Geräteverwaltung und ioBroker-Verbindungseinstellungen sind auf Administratoren beschränkt; normale Benutzer können die Klimaübersicht bedienen und ihr Konto verwalten.
- Die bestehende ioBroker-Verbindung, der Updatehinweis und der APK-Installer bleiben erhalten.
- Vorgesehene Version: **0.4.26 / versionCode 56**. Build, APK-Signatur, Release und Smartphone-Test werden separat geprüft.


## Änderung 13.11.2026 – Dialoge der Benutzerverwaltung scrollbar

- Das Dialogfenster „KlimaPilot-Benutzer anlegen“ begrenzt den Eingabebereich auf 280 dp und macht ihn separat scrollbar. Dadurch bleiben Titel und Aktionen erreichbar, auch wenn die Bildschirmtastatur geöffnet ist.
- Auch der Dialog zum Zurücksetzen eines Passworts erhält einen begrenzten, scrollbareren Eingabebereich.
- Keine Änderung an Kontenmodell, Passwortspeicherung, Rollen oder ioBroker-Synchronisation.
- Vorgesehene Version: **0.4.27 / versionCode 57**. Build und Release separat prüfen.
