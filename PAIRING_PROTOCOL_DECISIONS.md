# KlimaPilot – Pairing- und Protokollentscheidungen

**Fachlicher Stand:** 18.11.2026  
**Branch-/PR-/CI-Status:** ausschließlich in `PROJECT_STATUS.md`.

## 1. Verbindliche Architekturgrenzen

- Pairing verbindet zwei KlimaPilot-Installationen; es ist unabhängig vom WF-RAC-Pairing zur Klimaanlage.
- Discovery ist kein Vertrauensnachweis.
- QR-Pairing, TCP-Authentifizierung und Nutzdatentransfer sind getrennte Protokollstufen.
- Kein Profil-/Outbox-Transfer über Peer-TCP, solange Handshake-Integration, Nachrichtenhärtung und Zwei-Smartphone-Test fehlen.
- PR #6 bleibt Draft; kein Merge ohne ausdrückliche Freigabe.

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
- Beide Seiten bestätigen den Peer und Fingerprint ausdrücklich.
- Erst nach der lokalen Bestätigung darf der Peer gespeichert werden.
- Wiederverwendung bereits konsumierter Sitzungen wird abgelehnt.
- Pairing-UI ist auf die Debug-App `de.klimapilot.app.debug` begrenzt.

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
- Aktueller Lifecycle-Fix: Commit `bba8969ba4306d159ba388753f85c6638bf32f7f`.

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
| Gegenseitige Nutzerbestätigung | Implementiert |
| Trusted-Peer-Speicher | Implementiert |
| DNS-SD-Discovery | Implementiert |
| WLAN-Interface-Bindung | Implementiert |
| WLAN-/Defaultnetz-Lifecycle-Überwachung | Implementiert, aktuelle CI-Prüfung offen |
| TCP-Peer-Handshake-Klasse | Implementiert, separat getestet auf älteren Ständen |
| Handshake in Discovery-Listener integriert | Implementiert, CI-/Geräteprüfung offen |
| Profil-/Outbox-Transfer über Peer-TCP | **Nicht umgesetzt** |
| Zwei-Smartphone-Praxistest | **Nicht durchgeführt** |

Discovery-Metadaten bleiben unverifiziert. Der angekündigte TCP-Port führt bei eingehenden Verbindungen den gegenseitigen Handshake aus; ein ausgehender Handshake erfolgt nur nach ausdrücklicher Nutzeraktion. Unbekannte/nicht vertraute Peers werden abgewiesen. Jede Verbindung wird nach dem Handshake geschlossen; es werden keine Nutzdaten übertragen.

## 8. Offene technische Anforderungen vor Nutzdaten

- Die integrierte Handshake-Stufe durch CI und Zwei-Smartphone-Test absichern; unautorisierte Verbindungen müssen weiterhin abgewiesen werden.
- Framing mit Protokollversion, Nachrichtentyp und festen Größenobergrenzen definieren.
- Schema-/Typ-/Wertebereichs- und Gerätezuordnungsvalidierung auf Empfängerseite.
- Replay-/Duplikaterkennung und idempotente Wiederaufnahme.
- Empfangsbestätigung erst nach dauerhafter Speicherung.
- Fehler-/Timeout-/Abbruchpfade, parallele Verbindungen und Ressourcen-Cleanup durch CI und reale Geräte testen.
- Bestehenden ioBroker-Transport und dessen Event-Engine regressionsprüfen.
- Zwei reale Smartphones testen.

## 9. Validierung

Build-, CI- und Geräte-Testnachweise für den aktuellen Stand werden ausschließlich in `PROJECT_STATUS.md` geführt. Ältere erfolgreiche Testläufe dürfen nicht als Validierung des neuesten Codes ausgegeben werden.

## 10. Historische Entscheidung

Am 17.11.2026 wurde TLS/mTLS nach wiederholten Keystore-/Conscrypt-/Emulatorproblemen verworfen und der Nutzer stimmte einem einfacheren, signaturbasierten TCP-Handshake ohne Transportverschlüsselung zu. Diese Entscheidung gilt weiterhin. Eine spätere Änderung der Transport-Sicherheitsentscheidung benötigt eine ausdrückliche neue Abstimmung.
