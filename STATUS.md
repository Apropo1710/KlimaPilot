# KlimaPilot – aktueller Projektstatus

**Abgleich:** 16.11.2026, 15:24 Uhr MEZ  
**Repository:** https://github.com/Apropo1710/KlimaPilot  
**Branch:** `main`  
**App-Version:** **0.4.59 / versionCode 89**

## Verifizierter Produktionsstand

- `main`-HEAD: `3291d3786a5c0574b737c04329a98c8600a88b4c` (Dokumentationscommit).
- APK-Quellcommit: `058c3cc456beecccaab8b102e6af3d1e6a42017f`.
- GitHub Actions [#211](https://github.com/Apropo1710/KlimaPilot/actions/runs/43866655609): **completed / success**, 16.11.2026, 13:29:31 Uhr MEZ.
- APK-Paket-/Versionsprüfung und `apksigner verify --print-certs`: erfolgreich.
- Release `latest`: `KlimaPilot.apk` aktualisiert 16.11.2026, 13:29:15 Uhr MEZ; Asset-ID `754773422`, 11.370.135 Bytes.
- APK-SHA-256: `18ad8cbaf473a88e7a8ae1da7c28ff95e0fcbfa90b5cf005d40c12d3485116b3`.
- Git-Tag `latest` zeigt auf APK-Quellcommit `058c3cc456beecccaab8b102e6af3d1e6a42017f`; `main` enthält danach einen Dokumentationscommit.
- APK: https://github.com/Apropo1710/KlimaPilot/releases/download/latest/KlimaPilot.apk

## Zuletzt umgesetzt

- 0.4.59: Cloud-Symbol 12 dp weiter vom Zahnrad abgesetzt; bei Verbindung grün, offline gedimmt.
- 0.4.58: Cloud-Symbol rechts neben Zahnrad; abgeschnittenen Status-Text entfernt.
- 0.4.57: Kopfzeile bleibt beim Scrollen sichtbar; einheitliches Hellblau; Raum-Wischgeste erhalten.
- 0.4.56: Moduskacheln gleich hoch, Symbolbereiche vereinheitlicht, Kartenflächen angepasst.
- 0.4.55: Modus-/Power-Symbole, Luftführungs-Dropdowns und Update-Button überarbeitet.
- 0.4.54: Hauptansicht kompakter an MyAirco-Layout angelehnt.

## Technik und feste Entscheidungen

- Kotlin, Jetpack Compose/Material 3; Application-ID `de.klimapilot.app`; minSdk 26; compile/target SDK 35; Java 17.
- Direkte lokale Klimasteuerung über WF-RAC-WLAN-Modul; optionale ioBroker-WebSocket-Verbindung für Klimaprofil-Synchronisierung.
- Zielgerät Mitsubishi Heavy `SRK15ZS-WF`; Anlage noch nicht installiert, reale Hardware-/Firmwaretests offen.
- `mhi-wfrac` noch nicht eingerichtet und für direkten lokalen Steuerweg nicht erforderlich.
- SENEC/`energyfluss` wird nicht eingeplant.

## Offene Prüfungen

- Konten-Registry mit absichtlich beschädigten Daten (Schutz aus 0.4.50).
- Pairing-Reset bei konkurrierenden Steuerbefehlen (0.4.52).
- Modellabhängige Luftführung/3D-Automatik und Profilanwendung am echten Gerät (0.4.53 M3/M4).
- Profil-Synchronisierung mit zwei Smartphones und realem ioBroker (0.4.53 M5).
- Hardwaretests nach Installation der Klimaanlage.
- Nutzer-Endtest der installierten 0.4.59 noch nicht bestätigt.

## Updater – abgeschlossen

Der Updater funktioniert und wurde vom Nutzer erfolgreich getestet. Keine offene Updater-Aufgabe. Installationsablauf nicht verändern: `ACTION_VIEW`, MIME `application/vnd.android.package-archive`, FileProvider-URI, URI-Berechtigung und `ClipData`. `REQUEST_INSTALL_PACKAGES` nicht einführen; nicht auf `ACTION_INSTALL_PACKAGE` umstellen; keine automatische Updateprüfung ergänzen.

## Letzte Builds

| Run | Version | Ergebnis |
|---|---|---|
| #211 | 0.4.59 / 89 | success – aktueller Release |
| #210 | 0.4.58 / 88 | success |
| #209 | 0.4.57 / 87 | success – Color-Import korrigiert |
| #208 | 0.4.57 Zwischenstand | failure |
| #207 | 0.4.57 Zwischenstand | failure |
| #206 | 0.4.56 / 86 | success |

## Dokumentationsquellen

- `PROJECT_STATUS.md`: maßgebliche aktuelle technische und produktive Statusakte.
- `STATUS.md`: kompakte operative Übergabedatei.
- ChatGPT Library: `KlimaPilot_Projektstand_aktualisiert.md` ist die ausführliche Langzeitakte; `KlimaPilot_Projektstand.md` bleibt als historisches Archiv mit aktuellem Statuskopf erhalten.
- Vor jeder Entwicklungsarbeit `main` und betroffene Dateien live prüfen. CI-Erfolg nicht mit Hardwaretest oder vollständiger Fehlerfreiheit gleichsetzen.
