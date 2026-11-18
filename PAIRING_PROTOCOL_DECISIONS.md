# KlimaPilot – Pairing- und Protokollentscheidungen

**Fachlicher Stand:** 18.11.2026  
**Branch-/PR-/CI-Status:** ausschließlich in `PROJECT_STATUS.md`.

## 1. Verbindliche Architekturgrenzen

- Pairing verbindet zwei KlimaPilot-Installationen; es ist unabhängig vom WF-RAC-Pairing zur Klimaanlage.
- Discovery ist kein Vertrauensnachweis.
- QR-Pairing, TCP-Authentifizierung und Nutzdatentransfer sind getrennte Protokollstufen.
- Nutzdaten fließen erst nach erfolgreicher gegenseitiger Authentifizierung über dieselbe Verbindung (siehe `LOCAL_WIFI_SYNC_DESIGN.md`, Abschnitt 6).
- PR #6 ist seit 18.11.2026 in `main` enthalten.

## 2. Identität

- ECDSA P-256-Schlüsselpaar im Android Keystore.
- Privater Schlüssel bleibt nicht exportierbar.
- Peer-ID ist SHA-256-abgeleitet aus dem öffentlichen Schlüssel.
- Fingerprint wird aus demselben Schlüsselmaterial abgeleitet.
- Identität ist installationsbezogen, nicht an IP-Adresse, MAC-Adresse oder Kontonamen gebunden.

## 3. QR-Pairing

- Einladung ist signiert und 120 Sekunden gültig.
- Enthält Protokollversion, Sitzungs-ID, Erstellungs-/Ablaufzeit, Peer-ID, öffentlichen Schlüssel, Fingerprint, Nonce und Signatur.
- Antwort ist ebenfalls signiert und an Sitzungs-ID sowie Einladungs-Nonce gebunden.
- Parser prüfen Payloadgröße, Version, Zeitfenster, Nonces, Schlüssel-/Fingerprint-Konsistenz und Signatur.
- Ein-Scan-Ablauf: Einladender startet einen zeitlich begrenzten TCP-Rückkanal; das zweite Gerät bestätigt „Koppeln“, sendet die signierte Antwort, das Einladungsgerät speichert und schickt ein signiertes ACK; erst danach speichert das zweite Gerät. Fingerprints werden im Hintergrund geprüft, aber nicht angezeigt.
- Erst nach der lokalen Bestätigung darf der Peer gespeichert werden.
- Wiederverwendung bereits konsumierter Sitzungen wird abgelehnt.
- Pairing-UI ist seit 0.4.76 auch in der Produktions-App vorhanden (ausklappbare Kachel „Geräte koppeln“).

## 4. Trusted-Peer-Speicher

- Enthält nur explizit bestätigte Peers.
- Discovery-Ergebnisse dürfen niemals direkt als vertraut gespeichert werden.
- Öffentlicher Schlüssel und Peer-ID müssen konsistent sein.
- Anzahl und Anzeigenamen gespeicherter Peers sind begrenzt.
- Peer kann widerrufen werden.

## 5. Lokale Discovery

- DNS-SD-Diensttyp `_klimapilot-sync._tcp.`.
- TXT-Felder: Protokollversion und zufällige Discovery-ID; keine Profil-, Konto- oder Schlüsselmaterialien.
- Entdeckte Geräte bleiben `UnverifiedClimatePeer`.
- Lokale Listener-Adresse: private IPv4 bevorzugt, IPv4-Link-Local Fallback, danach IPv6-ULA oder scoped IPv6-Link-Local. Öffentliche Adressen sind ausgeschlossen.
- WLAN-Callback beobachtet Verlust und Linkänderungen des ausgewählten WLANs.
- Default-Network-Callback erkennt den Wechsel zu einem anderen WLAN, auch wenn dieses bereits verfügbar ist. Mobilfunk als Defaultnetz ist zulässig, solange das ausgewählte WLAN bestehen bleibt.
- Beide Callbacks werden beim Stop/Cleanup entfernt.
- WLAN-/Defaultnetz-Lifecycle-Fix: Commit `bba8969ba4306d159ba388753f85c6638bf32f7f`.
- Socket-Shutdown-Race-Fix: Commit `334308e40ea17b2e57a6c640ca49c20e83d22e8b`. Socket-Registrierung und Manager-Cleanup sind synchronisiert; ein nach Shutdown zurückgegebener Socket wird sofort geschlossen und nicht verbunden. Der gezielte Instrumentierungstest liegt in Commit `63d53bb07a55bfb0207659749f8e6624d78215a7`, seine Ausführung ist noch nicht bestätigt.

## 6. TCP-Authentifizierung – bewusst ohne TLS

TLS/mTLS wurde nach ausdrücklicher Nutzerentscheidung verworfen. Der Transport ist unverschlüsseltes TCP im lokalen WLAN.

Handshake-Merkmale:
- 32-Byte-Client- und Server-Nonce,
- Rollenbindung im signierten Transkript,
- Peer-ID/Public-Key-Konsistenzprüfung,
- Prüfung des Gegenübers im lokalen Trusted-Peer-Speicher,
- gegenseitige ECDSA-Signaturprüfung,
- Connect-Timeout 5 Sekunden, Handshake-Deadline 10 Sekunden,
- begrenzte Handshake-Feldgrößen.

**Sicherheitsgrenze:** Andere Teilnehmer desselben WLANs können den TCP-Verkehr passiv mitlesen. Die Signaturen authentifizieren die Geräte und erschweren aktive Manipulation, bieten aber keine Vertraulichkeit.

## 7. Implementierungsstand – strikt getrennte Stufen

| Stufe | Status |
|---|---|
| Keystore-Identität | Implementiert |
| Signierte QR-Einladung | Implementiert |
| Signierte QR-Antwort | Implementiert |
| Ein-Scan-Kopplung mit signiertem ACK | Implementiert, Gerätetest bestanden |
| Trusted-Peer-Speicher | Implementiert |
| DNS-SD-Discovery | Implementiert |
| WLAN-Interface-Bindung | Implementiert |
| WLAN-/Defaultnetz-Lifecycle-Überwachung | Implementiert |
| TCP-Peer-Handshake-Klasse | Implementiert, separat getestet auf älteren Ständen |
| Handshake in Discovery-Listener integriert | Implementiert, automatisch |
| Konten-/Profil-/Konfigurationsabgleich über Peer-TCP | Implementiert (0.4.76/0.4.77) |
| Zwei-Geräte-Praxistest | Kopplung und Abgleich bestanden (18.11.2026); Negativfälle offen |

Discovery-Metadaten bleiben unverifiziert. Eingehende und automatisch ausgehende Verbindungen durchlaufen den gegenseitigen Handshake; unbekannte/nicht vertraute Peers werden abgewiesen. Nach erfolgreicher Authentifizierung folgt der Datenabgleich, danach wird die Verbindung geschlossen.

## 8. Offene Punkte

- Praxistests der Negativfälle (Abbruch, Timeout, Verbindungsverlust während Antwort/ACK, wiederverwendeter oder manipulierter QR-Code).
- Entfernen gekoppelter Geräte über die Oberfläche (Widerruf ist im Speicher vorhanden, aber nicht in der UI).

## 9. Validierung

Build-, CI- und Geräte-Testnachweise für den aktuellen Stand werden ausschließlich in `PROJECT_STATUS.md` geführt. Ältere erfolgreiche Testläufe dürfen nicht als Validierung des neuesten Codes ausgegeben werden.

## 10. Historische Entscheidung

Am 17.11.2026 wurde TLS/mTLS nach wiederholten Keystore-/Conscrypt-/Emulatorproblemen verworfen und der Nutzer stimmte einem einfacheren, signaturbasierten TCP-Handshake ohne Transportverschlüsselung zu. Diese Entscheidung gilt weiterhin. Eine spätere Änderung der Transport-Sicherheitsentscheidung benötigt eine ausdrückliche neue Abstimmung.
