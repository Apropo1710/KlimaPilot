# KlimaPilot – aktueller Projekt- und Übergabestatus

**Abgleich:** 18.11.2026, 08:16 Uhr MEZ  
**Repository:** https://github.com/Apropo1710/KlimaPilot

## Produktiv

- `main`: `b82433038a3ced3de3ff908788fde4ef36d58f66`
- Version: **0.4.64 / versionCode 94**
- Letzter erfolgreicher Produktionsbuild: #233 auf `b88ff7e9709bc1ea5aafd673e26bb1d4efee9c16`
- Release `latest` enthält die signierte APK.
- APK: https://github.com/Apropo1710/KlimaPilot/releases/download/latest/KlimaPilot.apk

## Entwicklung

- `test` ist der einzige Arbeitsbranch für WLAN-Pairing.
- PR #6 offen, Draft, nicht gemergt: https://github.com/Apropo1710/KlimaPilot/pull/6
- Letzter App-Code-Commit: `bba8969ba4306d159ba388753f85c6638bf32f7f`
- Letzter Doku-Abgleich wird als eigener Commit direkt auf `test` abgelegt.
- Branches `main` und `test` sind divergent; vor dem Doku-Commit 181 ahead / 10 behind. Kein Rebase oder Merge.

## Zuletzt geändert

In `ClimateProfilePeerDiscovery.kt` wurde ein zusätzlicher Android-Default-Network-Callback ergänzt. Damit wird ein Wechsel des Standardnetzes zu einem bereits verfügbaren anderen WLAN erkannt. Zusätzliche WLANs allein lösen keinen Abbruch aus; Mobilfunk als Defaultnetz bleibt erlaubt, solange das ausgewählte WLAN intakt ist. Beide Callbacks werden beim Cleanup abgemeldet.

## Pairing-Status

- QR-Einladung und signierte Antwort, ECDSA-Identität im Android Keystore, beidseitige Bestätigung und Trusted-Peer-Speicher sind implementiert.
- TCP-Handshake mit gegenseitiger Peer-Authentifizierung ist separat implementiert.
- TLS/mTLS wurde verworfen; TCP ist unverschlüsselt.
- **Discovery ist weiterhin reject-only.** Handshake nicht in den Listener integriert.
- Kein Profil-/Outbox-Transfer über lokale Peer-Verbindung.
- Der bestehende ioBroker-Sync-Pfad verwendet weiterhin die gemeinsame Event-Engine.

## Tests / Builds

- WLAN-Run #202: erfolgreich auf älterem Code `ae1bdc03...`, nicht auf dem aktuellen Code.
- TCP-Emulator-Run #201: erfolgreich auf älterem Code `41b9a3ab...`, API 26.
- Aktueller Netzwerk-Code `bba8969...`: kein zugehöriger bestätigter Android-Build oder Testlauf.
- Sieben JVM-Adress-/IPv6-Tests vorhanden, aktueller Lauf nicht bestätigt.
- Kein Zwei-Smartphone-Test.
- API 35 Emulatorprüfung nicht bestätigt.

## Nächster sinnvoller Schritt

Netzwerk-Lifecycle-Änderung unabhängig reviewen und den WLAN-PR-Workflow auf genau diesem Stand abwarten. Erst nach erfolgreicher CI-Validierung weiter zur nächsten Integrationsstufe. Keinen Workflow manuell vom Nutzer starten lassen, solange ein automatischer PR-Lauf aussteht oder kein konkreter Startbedarf geklärt ist.

## Feste Regeln

- `main` und Release bleiben unangetastet.
- PR #6 bleibt Draft und wird nicht gemergt.
- Updater niemals ändern.
- Keine Nutzdatenübertragung, bevor Handshake-Integration, Framing, Größen-/Schema-/Wertebereichsprüfung, Replay-Schutz und Zwei-Smartphone-Test abgeschlossen sind.
- Keine nicht verifizierten ioBroker-Datenpunkte oder Hardwarefunktionen behaupten.
- SENEC/`energyfluss` nicht einplanen; `mhi-wfrac` erst nach Installation der Klimaanlage.
