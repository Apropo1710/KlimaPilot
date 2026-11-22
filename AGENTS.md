# Regeln für die Arbeit an diesem Repository

Diese Datei gilt für jeden, der hier Änderungen macht – Menschen und KI-Werkzeuge (Claude, ChatGPT/Codex, Copilot …). Sie wird bei jeder Sitzung zuerst gelesen und hat Vorrang vor eigenen Gewohnheiten.

## Projekt in einem Satz

KlimaPilot ist eine Android-App (Kotlin, Jetpack Compose) zur Steuerung von Mitsubishi-Heavy-Klimaanlagen direkt über das WF-RAC-Modul, mit ioBroker und gekoppelten Smartphones als Synchronisationswegen.

## Vor jeder Änderung lesen

1. [`PROJECT_STATUS.md`](PROJECT_STATUS.md) – einzige Statusquelle, insbesondere **„Verbindliche Regeln“**, **„CI und Workflows“** und **„Dokumentationsstruktur und Pflege“**. Diese Regeln gelten vollständig; diese Datei wiederholt sie nicht.
2. Die betroffenen Quelldateien und, bei Architekturfragen, das passende Fachkonzept.

## Unverhandelbar

- **Updater und Installationsablauf niemals ändern** – auch nicht nebenbei oder beim Aufräumen. Nur eine ausdrückliche, konkrete Anweisung des Nutzers hebt das auf.
- **`PROJECT_STATUS.md` ist die einzige Statusdatei.** Keine weiteren Status-, Plan-, Übergabe-, TODO- oder Notizdateien anlegen, auch nicht „vorübergehend“. Neue Stände in die passenden Abschnitte einarbeiten und überholte Aussagen ersetzen, nicht daneben stellen. Der Workflow „Docs guard“ schlägt fehl, wenn eine weitere solche Datei auftaucht.
- **Den automatisch gepflegten Block** am Anfang von `PROJECT_STATUS.md` (Version, Build, Release) nie von Hand ändern.
- **Ablauf:** App-Änderungen zuerst auf `test`, erst bei grüner Validierung dieselbe Änderung nach `main`. Reine Dokumentationscommits auf `main` mit `[skip ci]` kennzeichnen.
- **Nichts als getestet darstellen, was nicht getestet ist.** CI-Ergebnis, lokaler Test und Gerätetest klar trennen; „bestätigt“ gilt erst nach einem Test am Gerät.

## Dokumentation – feste Plätze

- `README.md` – Einstieg, keine zweite Statusübersicht
- `PROJECT_STATUS.md` – Stand, Branches, CI, Regeln, nächste Schritte
- Fachkonzepte: `DIRECT_WLAN_MIGRATION.md`, `LOCAL_WIFI_SYNC_DESIGN.md`, `PAIRING_PROTOCOL_DECISIONS.md`, `SYNC_RESILIENCE_PLAN.md` – nur bei Änderungen an Architektur, Entscheidungen oder Anforderungen anpassen
- `NOTICE-AIRLAN.md` – Lizenzhinweis, unverändert lassen

Neue Fachkonzepte nur nach Rücksprache mit dem Nutzer und mit Eintrag in `PROJECT_STATUS.md` → „Dokumentationsstruktur“.

## Keine Reste

Veraltete Branches, Releases, Workflows, Dateien und Code werden entfernt, nicht liegen gelassen oder „als veraltet markiert“.

## Sicherheit

Keystore, Signaturschlüssel, Passwörter, Tokens und persönliche Daten gehören nie in Git, Logs, Issues oder Screenshots.
