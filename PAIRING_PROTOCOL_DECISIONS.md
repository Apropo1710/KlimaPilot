# KlimaPilot – Pairing-Entscheidungen für lokale WLAN-Synchronisation

**Branch:** `feature/local-wifi-pairing`  
**Basis:** `3b0c4a8969c36dbf46de8369f9f87add3ad87b27`  
**Stand:** 17.11.2026  
**Status:** pragmatische Umsetzungsentscheidung; noch keine Pairing- oder Transportimplementierung.

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

## 5. Verschlüsselter Transport – technische Freigabe vor Umsetzung

Bevor Profilereignisse über WLAN übertragen werden, wird ein kleiner Kompatibilitätstest für gegenseitiges TLS durchgeführt:

- Bevorzugter Weg: Android-JSSE/TLS mit den vorhandenen Android-Keystore-Schlüsseln und den dazugehörigen Zertifikaten.
- Die Gegenstelle wird gegen den bereits gespeicherten öffentlichen Peer-Schlüssel geprüft (Pinning); ein beliebiges selbstsigniertes Zertifikat reicht nicht.
- Der TLS-Handshake muss auf beiden Seiten die Identität nachweisen. Die Anwendung akzeptiert nur Peers, die lokal gekoppelt und nicht widerrufen sind.
- Zielplattformen: mindestens API 26 und aktuelle Android-Versionen.
- Zuerst prüfen, ob Androids KeyManager/TrustManager mit dem nicht exportierbaren Keystore-Schlüssel zuverlässig auf Client- und Serverseite funktioniert. Keine private Schlüssel-Exportlösung und kein eigener TLS-Ersatz.
- Falls die JSSE-Integration auf minSdk 26 nicht stabil funktioniert, wird die konkrete Alternative vor Implementierung bewertet. Bis dahin bleibt Profiltransfer gesperrt.

Es werden keine eigenen Verschlüsselungs-, Zertifikats- oder Signaturformate entwickelt und keine zusätzliche Kryptobibliothek ohne konkreten Bedarf eingebaut.

## 6. Bewusste Begrenzung

Nicht Teil dieses Arbeitspakets sind:
- zusätzliche Final-Acks oder mehrstufige Challenge-Kaskaden,
- eine persistente Datenbank für kurzlebige Pairing-Sitzungen,
- MAC-Adressen oder IP-Adressen als Geräteidentität,
- Hintergrunddienst oder dauerhafter WLAN-Scan,
- Profil-/Konten-/ioBroker-Zugangsdaten im Pairing,
- Änderungen am Updater, WF-RAC-Steuerpfad oder bestehenden ioBroker-Sync.

## 7. Nächste Umsetzungsschritte

1. Kleinen Pairing-Session-Controller für den beschriebenen Ablauf ergänzen und mit Tests für Ablauf, falsche Sitzung, Wiederverwendung und beidseitige lokale Freigabe absichern.
2. QR-Anzeige/-Scan mit einer passenden Android-Lösung integrieren; Abhängigkeiten und Berechtigungen minimal halten.
3. Separaten TLS-Kompatibilitätstest für Android Keystore / API 26 durchführen.
4. Erst nach erfolgreichem Pairing und gegenseitigem TLS die Peer-Transport- und Profil-Sync-Implementierung beginnen.

**Freigabegrenze:** Bis Pairing und gegenseitiges TLS auf realen Geräten erfolgreich geprüft sind, werden über den Peer-Kanal keine Profilinhalte übertragen.
