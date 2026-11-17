# KlimaPilot – Pairing-Entscheidungen für lokale WLAN-Synchronisation

**Branch:** `feature/local-wifi-pairing`  
**Basis:** `3b0c4a8969c36dbf46de8369f9f87add3ad87b27`  
**Stand:** 17.11.2026  
**Status:** Pairing-Controller und QR-Anzeige/-Scan implementiert; CI erfolgreich. Kein Netzwerktransport und keine Profilübertragung.

## 1. Ziel

Smartphones werden einmalig bewusst miteinander gekoppelt. Danach dürfen sie bekannte Geräte im selben WLAN automatisch wiederfinden und Profile synchronisieren. Der Vorgang soll verständlich bleiben und keine unnötigen Sicherheitsdialoge erzeugen.

## 2. Festgelegter Pairing-Ablauf

1. Gerät A zeigt eine kurzlebige QR-Einladung (120 Sekunden) mit seiner bereits vorhandenen signierten Installationsidentität.
2. Gerät B scannt die Einladung und prüft Signatur, Ablaufzeit und Identität. B zeigt den Gerätenamen/Fingerprint von A an.
3. Der Nutzer bestätigt A auf Gerät B. Erst danach erzeugt B die bereits vorgesehene signierte Antwort.
4. Gerät B zeigt die Antwort als QR-Code. Gerät A scannt sie und prüft Sitzungs-ID, Einladungs-Nonce, Identität und Signatur.
5. Gerät A zeigt den Fingerprint von B an. Der Nutzer bestätigt B auf Gerät A.
6. Jedes Gerät speichert den jeweils anderen Peer erst nach seiner eigenen ausdrücklichen Bestätigung.

Die beiden lokalen Bestätigungen sind unabhängig. Ein zusätzlicher finaler Bestätigungs-/ACK-Rundlauf ist nicht vorgesehen: Ein Peer darf Daten nur austauschen, wenn **beide Seiten** den jeweils anderen Schlüssel in ihrer lokalen Trusted-Peer-Liste haben. Ist nur eine Seite bestätigt, scheitert der Verbindungsaufbau ohne Datenübertragung; nach der zweiten Bestätigung funktioniert er.

## 3. Sitzungs- und Replay-Verhalten

- Es gibt jeweils nur eine aktive Pairing-Sitzung pro Gerät.
- Einladungen bleiben 120 Sekunden gültig.
- Eine Einladung wird nach lokaler Bestätigung oder bewusstem Abbruch für die laufende App-Sitzung als verbraucht behandelt.
- Der aktive Sitzungszustand bleibt nur im Arbeitsspeicher. Nach Prozessende muss eine neue Einladung erstellt werden; die kurze Gültigkeit macht eine persistente Replay-Datenbank unnötig.
- Eine Antwort ist an Sitzungs-ID und Einladungs-Nonce gebunden. Eine Antwort aus einer anderen oder bereits verbrauchten Sitzung wird abgelehnt.
- Keine dauerhafte Speicherung von QR-Payloads, Nonces oder temporären Sitzungsgeheimnissen.
- Die vorhandenen ECDSA-/Keystore-, Invitation-, Response- und TrustedPeer-Bausteine werden wiederverwendet; keine eigene Kryptografie.

## 4. Identität und Vertrauensspeicherung

- Bestehende Installationsidentität: ECDSA auf secp256r1, privater Schlüssel im Android Keystore.
- Peer-ID und Fingerprint bleiben SHA-256-basierte Kennungen des öffentlichen Schlüssels.
- Der Nutzer sieht beim Bestätigen den vollständigen gruppierten Fingerprint der Gegenstelle.
- Ein Gerätename ist nur eine Anzeigehilfe, niemals ein Identitätsnachweis.
- Unbekannte oder widerrufene Peers erhalten keine Profil- oder Kontodaten.
- Vor jedem Datenaustausch müssen beide Seiten den gegenseitigen Peer-Schlüssel lokal als vertrauenswürdig erkennen.
- Widerruf entfernt den Peer aus der lokalen Vertrauensliste. Die Gegenseite kann dann keine neue Verbindung mehr erfolgreich authentifizieren.

## 5. Verschlüsselter Transport – vereinfachter TLS-Ansatz

Die ursprüngliche gegenseitige TLS-Client-Zertifikatsauthentifizierung (mTLS) wird nicht weiterverfolgt. Der Android-Emulatornachweis war auf API 26 erfolgreich, API 35 scheiterte wiederholt bereits vor Testausführung durch einen Emulator-/ADB-Absturz. Deshalb wird keine mTLS-Clientzertifikatsanforderung mehr verwendet.

Neuer Ansatz:

- Normales TLS 1.2 verschlüsselt jede Verbindung. Der TLS-Server präsentiert das vorhandene Android-Keystore-Identitätszertifikat.
- Der TLS-Client pinnt den Zertifikat-Public-Key gegen den beim QR-Pairing gespeicherten öffentlichen Schlüssel des erwarteten Peers. Es gibt keinen Trust-all-Modus.
- Der TLS-Server fordert kein Clientzertifikat an. Stattdessen sendet er nach erfolgreichem TLS-Handshake eine frische zufällige 256-Bit-Challenge.
- Der Client signiert diese Challenge mit seinem vorhandenen nicht exportierbaren Android-Keystore-Schlüssel und sendet Peer-ID, öffentlichen Schlüssel und Signatur.
- Der Server akzeptiert den Client nur, wenn Peer-ID und öffentlicher Schlüssel in der lokalen Trusted-Peer-Liste exakt zusammenpassen und die Signatur gültig ist.
- Die Challenge ist pro Verbindung neu und wird nicht wiederverwendet. Alle Handshake-Felder sind längenbegrenzt.
- Beide Geräte benötigen weiterhin die gegenseitige lokale Freigabe aus dem bestehenden QR-Pairing. Unbekannte oder widerrufene Peers erhalten keine Profildaten.
- Zielplattformen bleiben API 26 und aktuelle Android-Versionen.

Damit authentifiziert der Client den Server über TLS-Zertifikat-Pinning; der Server authentifiziert den Client auf Anwendungsebene über eine frische Keystore-Signatur. Die TLS-Verschlüsselung bleibt Standard-JSSE/TLS; es wird kein eigener Verschlüsselungsalgorithmus eingeführt.

Die neue Hilfsklasse `ClimatePeerTls` stellt TLS-Kontexte und den Challenge-Ablauf bereit. Sie ist noch nicht in einen produktiven Peer-Socket/Profiltransport eingebunden. Profilübertragung bleibt gesperrt, bis die API-26/API-35-Instrumentierung und anschließend der Zwei-Smartphone-Test erfolgreich sind.

## 6. Bewusste Begrenzung

Nicht Teil dieses Arbeitspakets sind:
- zusätzliche Final-Acks oder mehrstufige Challenge-Kaskaden,
- eine persistente Datenbank für kurzlebige Pairing-Sitzungen,
- MAC-Adressen oder IP-Adressen als Geräteidentität,
- Hintergrunddienst oder dauerhafter WLAN-Scan,
- Profil-/Konten-/ioBroker-Zugangsdaten im Pairing,
- Änderungen am Updater, WF-RAC-Steuerpfad oder bestehenden ioBroker-Sync.

## 7. Nächste Umsetzungsschritte

1. Pairing-Session-Controller für den beschriebenen Ablauf ergänzen und mit Tests für Ablauf, falsche Sitzung, Wiederverwendung und beidseitige lokale Freigabe absichern: **implementiert**; Controller- und Session-Tests sind in CI erfolgreich.
2. QR-Anzeige/-Scan: **implementiert** mit ZXing Core für QR-Erzeugung und Google Code Scanner über Google Play Services für den Scan. CI-Unit-Test für QR-Payload-Roundtrip erfolgreich.
3. Separaten TLS-Kompatibilitätstest für Android Keystore / API 26+ durchführen.
4. Erst nach erfolgreichem Pairing und gegenseitigem TLS die Peer-Transport- und Profil-Sync-Implementierung beginnen.

**Freigabegrenze:** Bis Pairing und gegenseitiges TLS auf realen Geräten erfolgreich geprüft sind, werden über den Peer-Kanal keine Profilinhalte übertragen.


## 8. Implementierungsstand QR-Integration (17.11.2026)

- `ClimatePairingQrCode` erzeugt QR-Matrizen aus den bereits signierten Pairing-Payloads; ZXing Core 3.5.4 wird verwendet.
- `ClimatePairingSettingsCard` stellt Einladung/Antwort dar, startet den QR-Scan und zeigt vor der lokalen Bestätigung den vollständigen Fingerprint der Gegenstelle an.
- Der Scan läuft über Google Code Scanner / Google Play Services 16.1.0; keine eigene Kamera-Berechtigung wird durch diese Integration angefordert.
- `ClimatePairingQrCodeTest` dekodiert einen erzeugten QR-Code wieder und vergleicht den UTF-8-Payload exakt.
- GitHub Actions #122 (Push) und #123 (PR) für Head `2657dbb99e65cbaff107737ecd040edcf401bbd4`: beide erfolgreich; Unit-Tests und Debug-Kompilierung bestanden.
- Noch kein Zwei-Smartphone-Praxistest, kein TLS, kein Netzwerktransport und keine Profilübertragung. Pairing-UI/QR-Codes allein geben keine Synchronisierung frei.

## 9. TLS-Testdiagnose (17.11.2026)

- Der erste Emulatorlauf [#2](https://github.com/Apropo1710/KlimaPilot/actions/runs/44131206603) schlug auf API 26 und API 35 fehl.
- API 26 führte den Test aus, meldete aber `SSLHandshakeException: Connection closed by peer`. Der Test wurde angepasst, damit ein Fehler des Server-Endpunkts künftig zusammen mit dem Clientfehler ausgegeben wird; ein Socket-Timeout verhindert unbegrenztes Warten.
- API 35 startete zeitweise mit einer `offline`-ADB-Verbindung; anschließend stürzte der Instrumentierungsprozess ab, bevor ein Test ausgeführt wurde. Der Workflow sichert deshalb nun nach dem Testlauf die Emulator-Logcat-Ausgabe als Actions-Artefakt.
- Diese Anpassungen verbessern ausschließlich die Diagnose. Sie sind kein Nachweis, dass mTLS bereits funktioniert.
- Nächster Schritt: neuen automatischen CI-Lauf vollständig auswerten und anhand der Server-Ausnahme bzw. Logcat gezielt korrigieren. Bis dahin bleiben Transport und Profilübertragung gesperrt.
- Claude-Review wird nach Wiederverfügbarkeit des Nutzerlimits vor Beginn weiterer Transportarbeiten nachgeholt.


## 10. Vereinfachte TLS-/Peer-Authentifizierung (17.11.2026)

- Neue Klasse `ClimatePeerTls`: TLS-Serverkontext aus dem Android Keystore; TLS-Clientkontext mit Pinning des erwarteten secp256r1-Public-Keys.
- Der TLS-Server verlangt kein Clientzertifikat. Nach TLS-Handshake sendet er eine frische 32-Byte-Challenge; der Client antwortet mit Peer-ID, Public Key und ECDSA-Signatur aus der bestehenden Installationidentität.
- Serverseitig werden Peer-ID/Public-Key gegen `ClimateTrustedPeerStore.isTrusted` geprüft und die Signatur verifiziert. Längenfelder sind begrenzt.
- Instrumentierungstest ersetzt den bisherigen mTLS-Test: TLS-Pinning plus signierte Client-Challenge und Ablehnung eines falschen Server-Pins.
- API-26/API-35-CI muss den neuen Ansatz noch ausführen. Ein bestandener Unit-/Kompilierungslauf ist kein Nachweis des Android-Keystore-TLS-Verhaltens.
- Noch kein produktiver Peer-Transport und keine Profilübertragung. Der Discovery-Socket bleibt weiterhin reject-only.
- Kein Updater-, Manifest-, Berechtigungs-, ioBroker- oder WF-RAC-Eingriff.
