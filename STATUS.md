## Geprüfter Gesamtstand – 15.11.2026, 03:12 MEZ

## Verifizierter Abgleich – 16.11.2026, 03:02 MEZ

- Maßgeblicher Branch: `main`, geprüfter Head `060dde5f5b94104b63b0427810fc94f72f9cf9e5` (Dokumentationscommit vom 15.11.2026, 21:45:38 UTC).
- App-Version laut `app/build.gradle.kts`: **0.4.53 / versionCode 83**. Letzter bestätigter App-Code-Commit: `8eefbf5233784ba250b6f84940ccdd54fbae1ac7` (15.11.2026, 21:39:26 UTC; Korrektur des asynchronen Profil-Anwendungsaufrufs).
- PR #1 zur direkten WF-RAC-WLAN-Anbindung wurde am 15.11.2026 um 06:31:55 UTC nach `main` gemergt. Die direkte lokale Gerätekommunikation ist damit Bestandteil von `main`; ioBroker bleibt für die optionale Profilsynchronisierung relevant. Der Adapter `mhi-wfrac` ist für den direkten WLAN-Steuerweg nicht erforderlich.
- Der Remote-Branch `feature/direct-wf-rac-wlan` besteht noch (Head `9705e55b0c6a5a948a513b3950906b93016f0b01`, Version 0.4.29 / 59), sein PR ist aber gemergt. Der Branch ist nur noch historisch; neue Arbeit ausschließlich auf `main`.
- **APK-Nachweis nicht widerspruchsfrei:** Der automatisch gepflegte Produktionsblock in `PROJECT_STATUS.md` nennt Build-Quellcommit `51945f6670383977cb48f7926b75ec9392edac57` und SHA-256 `0c5721ec4c5b25ca963e0fcc59bbbd6ed6661337055bcd42ee60ebea46ff052b`. Der manuelle Abschnitt nennt dagegen Run #198, Quellcommit `8eefbf5233784ba250b6f84940ccdd54fbae1ac7` und SHA-256 `39c3eba2aba7e4fd6d20e66f8ef64a5738247eab0a750aece7b6081c87a71bdd`. Die Release-Asset-Prüfung war über die verfügbare GitHub-Schnittstelle nicht abrufbar; deshalb wird keiner der beiden Hashes hier als abschließend verifiziert festgelegt.
- Hardwaretest der direkten WLAN-Steuerung bleibt offen, bis die Klimaanlage installiert und erreichbar ist. Der Updater bleibt unverändert und vom Nutzer bestätigt.

- Maßgeblicher Branch: `main`; vor dieser Statusaktualisierung geprüfter Head `d714516abe7cf5b1f27533cdd81c332e2dd9989d` (Dokumentationscommit nach erfolgreichem APK-Release-Workflow).
- Letzter App-Code-Commit: `485d274fbb5e10d321f76a72033d6e108ce1a2c5` – Schriftgröße je Benutzerkonto gespeichert; Version **0.4.28 / versionCode 58**.
- APK-Build-Quellcommit: `bd20181e86972f390901d65c442996bc3cf109d1`; GitHub Actions Run `43666033340`: **completed / success**. Der vorherige Dokumentationsbuild `43666030742` war ebenfalls erfolgreich.
- Schriftgröße: fünf Stufen von 85 % bis 150 %, Standard 100 %. Speicherung als `fontScale` im jeweiligen ioBroker-Benutzerkonto; bestehende Konten ohne Feld bleiben kompatibel.
- Release `latest`: Asset `KlimaPilot.apk` vorhanden. Aktueller SHA-256: `0d3f44fda8d003a92268644048db1a851f7fbd7ab8c30468f4a7d6b6f55b31c2`. Signaturprüfung laut automatisch gepflegtem Produktionsnachweis erfolgreich.
- Die durch Dokumentationscommits ausgelösten APK-Builds änderten keinen App-Laufzeitcode; die Produktionsversion blieb 0.4.28 / 58.
- Ein separater Smartphone-Test der neuen Schriftgrößeneinstellung ist noch nicht bestätigt.
- Klimaprofile sind weiterhin vorgemerkt, aber noch nicht implementiert. Die Profilbelegung muss nach Installation anhand der echten Mitsubishi-/ioBroker-Datenpunkte festgelegt werden.
- Der Updater bleibt unverändert und vom Nutzer bestätigt. Hardwareabhängige Klimaanlagenarbeiten bleiben bis zur Installation offen.

Zuletzt aktualisiert: 15.11.2026, 03:12 MEZ
Repository: https://github.com/Apropo1710/KlimaPilot
Produktions-/Hauptbranch: `main`

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
