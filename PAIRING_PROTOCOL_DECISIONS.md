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

## 5. Lokaler TCP-Transport ohne Transportverschlüsselung

TLS und mTLS werden nicht weiterverfolgt. Die Emulatorläufe zeigten wiederholt Keystore-/Conscrypt-Probleme oder Emulatorabbrüche; für die lokale Profil-Synchronisation wird deshalb bewusst auf Transportverschlüsselung verzichtet. Das ist eine bewusste Abwägung zugunsten einer einfachen, kompatiblen WLAN-Verbindung und keine Hochsicherheitslösung.

Verbindliche Schutzmaßnahmen auf Anwendungsebene:

- Der Transport verwendet einen normalen TCP-Socket im lokalen WLAN. Der Datenverkehr ist **nicht verschlüsselt** und kann von anderen Teilnehmern desselben Netzwerks mitgelesen werden.
- Vor jeder späteren Nutzdatenübertragung müssen sich beide Geräte gegenseitig authentifizieren. Beide Seiten beweisen den Besitz ihres nicht exportierbaren Android-Keystore-Schlüssels mit ECDSA auf secp256r1.
- Jede Verbindung verwendet zwei frische zufällige 32-Byte-Nonces. Die Signaturen binden eine Protokollkennung, die jeweilige Rolle, beide Nonces sowie Client- und Server-Peer-ID ein; aufgezeichnete Handshakes können nicht unverändert wiederverwendet werden.
- Jede Seite prüft Peer-ID und Public Key gegen ihre eigene lokale Trusted-Peer-Liste. Eine einseitige Freigabe reicht nicht: Der Server bestätigt die Verbindung erst nach erfolgreicher Prüfung des Clients; der Client wartet auf diese Bestätigung.
- Unbekannte, widerrufene oder nicht gegenseitig bestätigte Geräte erhalten keine Nutzdaten.
- Handshake-Felder sind längenbegrenzt. Der Client-Verbindungsaufbau hat ein Connect-Timeout von fünf Sekunden; der gesamte Handshake ist pro Verbindung auf zehn Sekunden begrenzt (nicht zehn Sekunden je Lesevorgang).
- Pairing/QR, Peer-ID, Fingerprint und bestehende Trusted-Peer-Speicherung bleiben unverändert.

Implementiert ist zunächst die eigenständige Klasse ClimatePeerTcp mit Instrumentierungstests für erfolgreiche gegenseitige Authentifizierung sowie Ablehnung eines nicht vertrauten Clients und Servers. Sie ist noch nicht in den Discovery-Socket oder einen Profil-Datentransfer eingebunden. **Es werden derzeit noch keine Profile über TCP übertragen.**

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
3. TCP-Handshake auf API 26 und 35 in CI prüfen und anschließend mit zwei realen Smartphones testen.
4. Erst nach erfolgreichem gegenseitigem Pairing und TCP-Handshake den Discovery-Socket anbinden; Profilübertragung bleibt bis zum Praxistest gesperrt.

**Freigabegrenze:** Bis Pairing und gegenseitige TCP-Authentifizierung auf zwei realen Geräten erfolgreich geprüft sind, werden über den Peer-Kanal keine Profilinhalte übertragen.


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


## 11. Wechsel auf TCP ohne Transportverschlüsselung (17.11.2026)

- Entscheidung nach ausdrücklicher Nutzerfreigabe: TLS/mTLS vollständig verwerfen; lokaler TCP-Transport ohne Transportverschlüsselung.
- ClimatePeerTls.kt und der TLS-Instrumentierungstest wurden entfernt. Die Keystore-Identität benötigt nur noch SHA-256 für Anwendungssignaturen; die TLS-spezifische Freigabe von DIGEST_NONE ist zurückgenommen.
- Neue Klasse ClimatePeerTcp.kt implementiert beidseitige Challenge-Response-Authentifizierung mit zwei frischen 32-Byte-Nonces, Rollenbindung, ECDSA-Signaturen, lokalen Trusted-Peer-Prüfungen, begrenzten Feldern und Socket-Timeout.
- Der Server sendet eine explizite Annahme/Ablehnung zurück. Der Client gilt erst nach positiver Bestätigung als authentifiziert.
- Neuer API-26/API-35-Instrumentierungstest prüft gegenseitig vertraute Peers sowie die Ablehnung eines nicht vertrauten Clients.
- Noch nicht umgesetzt: Anbindung des Handshakes an den Discovery-Server/Client und Profilübertragung. Bis zum Zwei-Smartphone-Test bleibt Nutzdatentransfer gesperrt.
- Sicherheitsgrenze: TCP-Daten sind unverschlüsselt und im lokalen Netz mitlesbar. Authentifizierung verhindert nicht das passive Mitlesen durch andere WLAN-Teilnehmer.
- main, Updater, WF-RAC-Steuerung und bestehender ioBroker-Sync bleiben unangetastet.


## 12. TCP-Härtung und CI-Ergebnis (17.11.2026, 23:xx MEZ)

- Commit `cc42e9bd49e464a665557a005877eac93a13a72d` ergänzt eine feste Gesamtfrist von zehn Sekunden für den Handshake und einen Connect-Timeout von fünf Sekunden.
- Das signierte Transkript bindet zusätzlich die Protokollkennung `KlimaPilot.LocalPeerAuth.v1` sowie Client- und Server-Peer-ID ein. Die Rollenbindung und beide frischen Nonces bleiben erhalten.
- Ein zusätzlicher Instrumentierungstest prüft die Ablehnung eines Servers, der auf dem Client nicht in der lokalen Trusted-Peer-Liste steht.
- [TCP-Peer-Authentifizierung #49](https://github.com/Apropo1710/KlimaPilot/actions/runs/44191379738): API 26 und API 35 Jobs melden grün. **Die Detailprotokolle zeigen jedoch unterschiedliche Ergebnisse:** API 26 startete drei Instrumentierungstests und schloss `connectedDebugAndroidTest` erfolgreich ab. API 35 meldete weiterhin `Starting 0 tests`; der Testschritt schlug fehl, wurde im damaligen Workflow aber durch `continue-on-error` und die optionale Job-Einstellung als grüner Job dargestellt.
- [WLAN-Branch-Validierung #49](https://github.com/Apropo1710/KlimaPilot/actions/runs/44191379609): erfolgreich.
- Daher gilt derzeit nur API 26 als tatsächlich bestandener Instrumentierungstest. API 35 ist nicht bestanden und wird im PR-Workflow bis zu einem Runner mit nutzbarem KVM nicht mehr als grüner Schein-Erfolg dargestellt; es kann manuell über `workflow_dispatch` geprüft werden.
- Die API-26-Prüfung belegt den Emulator-/Keystore-Handshake auf dieser Android-Version, aber keinen Zwei-Smartphone-Praxistest.
- Weiterhin nicht umgesetzt: Integration in Discovery und Profilübertragung. TCP-Nutzdaten bleiben bis zum realen Zwei-Geräte-Test gesperrt.
- Sicherheitsgrenze: Die Authentifizierung verhindert nicht das aktive Einschleusen oder Verändern von TCP-Daten durch einen Angreifer, der den WLAN-Verkehr umleiten kann. Vor einer späteren Profilübertragung sind daher strikte Größen-, Schema- und Wertebereichsprüfungen auf Empfängerseite erforderlich. Die unverschlüsselte Übertragung bleibt zudem passiv mitlesbar.
- Updater, WF-RAC-Steuerweg, ioBroker-Synchronisation und Produktionsbranch `main` wurden nicht verändert.
