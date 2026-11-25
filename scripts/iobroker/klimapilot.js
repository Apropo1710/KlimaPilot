/*
 * KlimaPilot – ioBroker-Skript (JavaScript-Adapter)
 *
 * Wird von KlimaPilot eingerichtet und aktualisiert (Einstellungen → ioBroker-Skript).
 * Es erledigt folgende Dinge, auch wenn kein Smartphone zu Hause oder die App geschlossen ist:
 *
 * 1. Energieaufzeichnung: liest alle 5 Minuten den Energiezähler jeder Klimaanlage und schreibt
 *    Stunden- und Tageswerte nach 0_userdata.0.KlimaPilot.energy.units.<Geräte-ID>.
 *    Der Zähler meldet den Verbrauch des aktuellen Laufs in 0,25-kWh-Schritten (Segment 0x94/0x10)
 *    und beginnt beim Einschalten wieder bei 0; fällt der Wert, hat ein neuer Lauf begonnen.
 *    Weil ein Schritt oft über eine Stunde dauert, wird jeder Schritt auf die Ablesungen verteilt,
 *    bei denen die Anlage seit dem letzten Schritt eingeschaltet war (gleichmäßige Stundenwerte).
 *
 * 2. Zeitpläne: prüft jede Minute die Zeitpläne aus 0_userdata.0.KlimaPilot.schedules.config
 *    und schaltet fällige Anlagen. Ergebnisse stehen in 0_userdata.0.KlimaPilot.schedules.status.
 *    Es laufen nur Zeitpläne der gewählten Jahreszeit (Winter, Übergangszeit, Sommer).
 *
 * 3. Diagnose (nur wenn in KlimaPilot eingeschaltet): prüft jede Minute die lokale Erreichbarkeit
 *    und protokolliert Ausfälle in 0_userdata.0.KlimaPilot.diagnostics.log.
 *
 * 4. Raumtemperatur lernen: Der Fühler im Innengerät misst je nach Lage zu viel. Zu jedem in der App
 *    eingetragenen Thermometerwert merkt sich das Skript den Zustand der Anlage (Modus, Verdichter
 *    und wie lange er schon läuft bzw. steht, Fühlertrend, Lüfter, Außentemperatur …) und lernt
 *    daraus je Anlage die Abweichung. Nutzt nur die ohnehin laufenden Abfragen.
 *
 * 5. Ereignisprotokoll (immer aktiv, 48 h, 0_userdata.0.KlimaPilot.events.log): jede Änderung an einer
 *    Anlage zwischen zwei Abfragen (alle 5 min), jedes Schalten durch das Skript (Zeitplan,
 *    Betriebsdaten-Prüfung) und Wartezeiten wegen einer Sperre durch ein anderes Gerät. Zeigt auch,
 *    was passiert, während keine App offen ist. KlimaPilot zeigt es in der Diagnose zusammen mit dem
 *    Protokoll des Handys.
 *
 * Anlagen (IP, Geräte-ID), Strompreis und Zeitpläne schreibt KlimaPilot selbst.
 * Anfragen an dieselbe Anlage laufen nacheinander, weil das WLAN-Modul nur eine Verbindung
 * gleichzeitig verträgt.
 */

const http = require('http');
const https = require('https');

const ENERGY_ROOT = '0_userdata.0.KlimaPilot.energy';
const ENERGY_CONFIG_ID = `${ENERGY_ROOT}.config`;
const SCHEDULE_ROOT = '0_userdata.0.KlimaPilot.schedules';
const SCHEDULE_CONFIG_ID = `${SCHEDULE_ROOT}.config`;
const SCHEDULE_STATUS_ID = `${SCHEDULE_ROOT}.status`;
const DIAG_ROOT = '0_userdata.0.KlimaPilot.diagnostics';
const DIAG_CONFIG_ID = `${DIAG_ROOT}.config`;
const DIAG_LOG_ID = `${DIAG_ROOT}.log`;
const DIAG_MAX_ENTRIES = 400;
const DIAG_KEEP_DAYS = 14;
const OWNER_ID = '0_userdata.0.KlimaPilot.script.owner';
const EVENTS_ID = '0_userdata.0.KlimaPilot.events.log';
const EVENTS_KEEP_MS = 48 * 3600 * 1000;
const EVENTS_MAX = 1500;
const POLL_MINUTES = 5;
const HOURLY_DAYS = 31;          // Stundenwerte werden 31 Tage aufbewahrt, Tageswerte unbegrenzt.
const SPREAD_MAX_SLOTS = 12 * 60 / 5; // Ein Zählerschritt wird auf höchstens 12 h Laufzeit verteilt.
const ONCE_GRACE_MS = 6 * 3600 * 1000; // Einmalige Timer werden bis 6 h nach ihrem Termin nachgeholt.
const RETRY_WINDOW_MS = 10 * 60 * 1000; // Fehlgeschlagene Termine werden bis 10 min lang jede Minute wiederholt
                                        // (die Module starten stündlich kurz neu).
const PORT = 51443;
const TIMEOUT_MS = 10000;
const OPERATOR_ID = '98c7ec9c-1dac-4e5f-8d16-a145f58a38db';
const DEVICE_ID = 'klimapilot-iobroker';

// ---------------------------------------------------------------------------------------------
// Verbindung zum WF-RAC-Modul
// ---------------------------------------------------------------------------------------------

function post(scheme, host, command, contents) {
    const body = JSON.stringify({
        apiVer: '1.0',
        command,
        deviceId: DEVICE_ID,
        operatorId: OPERATOR_ID,
        timestamp: Math.floor(Date.now() / 1000),
        contents,
    });
    const lib = scheme === 'HTTPS' ? https : http;
    return new Promise((resolve, reject) => {
        const req = lib.request({
            host, port: PORT, method: 'POST', path: `/beaver/command/${command}`,
            headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body), Connection: 'close' },
            timeout: TIMEOUT_MS,
            rejectUnauthorized: false, // Das Modul hat ein selbst ausgestelltes Zertifikat.
        }, res => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                let json;
                try { json = JSON.parse(data); } catch (e) { reject(new Error(`Ungültige Antwort: ${e.message}`)); return; }
                if (json.result !== 0) {
                    const err = new Error(`Modul antwortet mit result ${json.result}`);
                    err.refused = true;
                    reject(err);
                } else resolve(json.contents || {});
            });
        });
        req.on('timeout', () => req.destroy(new Error('Zeitüberschreitung')));
        req.on('error', reject);
        req.end(body);
    });
}

const schemes = {}; // zuletzt funktionierendes Verfahren je Anlage

async function request(unit, command, contents) {
    const first = schemes[unit.airconId] || unit.scheme || 'HTTP';
    const order = first === 'HTTPS' ? ['HTTPS', 'HTTP'] : ['HTTP', 'HTTPS'];
    let lastError;
    for (const scheme of order) {
        try {
            const result = await post(scheme, unit.host, command, contents);
            schemes[unit.airconId] = scheme;
            return result;
        } catch (e) {
            if (e.refused) { schemes[unit.airconId] = scheme; throw e; } // Verfahren stimmt, das Modul lehnt ab.
            lastError = e;
        }
    }
    throw lastError;
}

// Anfragen je Anlage nacheinander ausführen.
const queues = {};
function serial(unit, task) {
    const key = unit.host;
    const run = (queues[key] || Promise.resolve()).catch(() => {}).then(task);
    queues[key] = run.catch(() => {});
    return run;
}

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

async function readStat(unit) {
    const contents = await request(unit, 'getAirconStat', { airconId: unit.airconId });
    if (contents.airconId && contents.airconId !== unit.airconId) {
        throw new Error(`An ${unit.host} antwortet eine andere Anlage (${contents.airconId})`);
    }
    return contents;
}

// ---------------------------------------------------------------------------------------------
// airconStat: Lesen und Schreiben (Übertragung von Codec.kt aus KlimaPilot)
// ---------------------------------------------------------------------------------------------

const MODES = { AUTO: [0x20, 0x00], COOL: [0x28, 0x08], HEAT: [0x30, 0x10], FAN: [0x2c, 0x0c], DRY: [0x24, 0x04] };
const CMD_AIRFLOW = [0x0f, 0x08, 0x09, 0x0a, 0x0e];
const RCV_AIRFLOW = [0x07, 0x00, 0x01, 0x02, 0x06];
const CMD_WIND_UD = [[192, 128], [128, 128], [128, 144], [128, 160], [128, 176]];
const CMD_WIND_LR = [[3, 16], [2, 16], [2, 17], [2, 18], [2, 19], [2, 20], [2, 21], [2, 22]];
const NO_SEGMENTS = [1, 255, 255, 255, 255];
// Thermistortabellen Innen- und Außentemperatur des WF-RAC-Moduls (Index = Rohwert, aus pywfrac, MIT; wie TempTables.kt).
const INDOOR_TEMPS = [
    -30.0, -30.0, -30.0, -30.0, -30.0, -30.0, -30.0, -30.0, -30.0, -30.0, -30.0, -30.0, -30.0, -30.0, -30.0, -30.0,
    -29.0, -28.0, -27.0, -26.0, -25.0, -24.0, -23.0, -22.5, -22.0, -21.0, -20.0, -19.5, -19.0, -18.0, -17.5, -17.0,
    -16.5, -16.0, -15.0, -14.5, -14.0, -13.5, -13.0, -12.5, -12.0, -11.5, -11.0, -10.5, -10.0, -9.5, -9.0, -8.6,
    -8.3, -8.0, -7.5, -7.0, -6.5, -6.0, -5.6, -5.3, -5.0, -4.5, -4.0, -3.6, -3.3, -3.0, -2.6, -2.3,
    -2.0, -1.6, -1.3, -1.0, -0.5, 0.0, 0.3, 0.6, 1.0, 1.3, 1.6, 2.0, 2.3, 2.6, 3.0, 3.2,
    3.5, 3.7, 4.0, 4.3, 4.6, 5.0, 5.3, 5.6, 6.0, 6.3, 6.6, 7.0, 7.2, 7.5, 7.7, 8.0,
    8.3, 8.6, 9.0, 9.2, 9.5, 9.7, 10.0, 10.3, 10.6, 11.0, 11.2, 11.5, 11.7, 12.0, 12.3, 12.6,
    13.0, 13.2, 13.5, 13.7, 14.0, 14.2, 14.5, 14.7, 15.0, 15.3, 15.6, 16.0, 16.2, 16.5, 16.7, 17.0,
    17.2, 17.5, 17.7, 18.0, 18.2, 18.5, 18.7, 19.0, 19.2, 19.5, 19.7, 20.0, 20.2, 20.5, 20.7, 21.0,
    21.2, 21.5, 21.7, 22.0, 22.2, 22.5, 22.7, 23.0, 23.2, 23.5, 23.7, 24.0, 24.2, 24.5, 24.7, 25.0,
    25.2, 25.5, 25.7, 26.0, 26.2, 26.5, 26.7, 27.0, 27.2, 27.5, 27.7, 28.0, 28.2, 28.5, 28.7, 29.0,
    29.2, 29.5, 29.7, 30.0, 30.2, 30.5, 30.7, 31.0, 31.3, 31.6, 32.0, 32.2, 32.5, 32.7, 33.0, 33.2,
    33.5, 33.7, 34.0, 34.2, 34.5, 34.7, 35.0, 35.3, 35.6, 36.0, 36.2, 36.5, 36.7, 37.0, 37.2, 37.5,
    37.7, 38.0, 38.3, 38.6, 39.0, 39.2, 39.5, 39.7, 40.0, 40.3, 40.6, 41.0, 41.2, 41.5, 41.7, 42.0,
    42.3, 42.6, 43.0, 43.2, 43.5, 43.7, 44.0, 44.3, 44.6, 45.0, 45.3, 45.6, 46.0, 46.2, 46.5, 46.7,
    47.0, 47.3, 47.6, 48.0, 48.3, 48.6, 49.0, 49.3, 49.6, 50.0, 50.3, 50.6, 51.0, 51.3, 51.6, 52.0
];

const OUTDOOR_TEMPS = [
    -50.0, -50.0, -50.0, -50.0, -50.0, -48.9, -46.0, -44.0, -42.0, -41.0, -39.0, -38.0, -37.0, -36.0, -35.0, -34.0,
    -33.0, -32.0, -31.0, -30.0, -29.0, -28.5, -28.0, -27.0, -26.0, -25.5, -25.0, -24.0, -23.5, -23.0, -22.5, -22.0,
    -21.5, -21.0, -20.5, -20.0, -19.5, -19.0, -18.5, -18.0, -17.5, -17.0, -16.5, -16.0, -15.5, -15.0, -14.6, -14.3,
    -14.0, -13.5, -13.0, -12.6, -12.3, -12.0, -11.5, -11.0, -10.6, -10.3, -10.0, -9.6, -9.3, -9.0, -8.6, -8.3,
    -8.0, -7.6, -7.3, -7.0, -6.6, -6.3, -6.0, -5.6, -5.3, -5.0, -4.6, -4.3, -4.0, -3.7, -3.5, -3.2,
    -3.0, -2.6, -2.3, -2.0, -1.7, -1.5, -1.2, -1.0, -0.6, -0.3, 0.0, 0.2, 0.5, 0.7, 1.0, 1.3,
    1.6, 2.0, 2.2, 2.5, 2.7, 3.0, 3.2, 3.5, 3.7, 4.0, 4.2, 4.5, 4.7, 5.0, 5.2, 5.5,
    5.7, 6.0, 6.2, 6.5, 6.7, 7.0, 7.2, 7.5, 7.7, 8.0, 8.2, 8.5, 8.7, 9.0, 9.2, 9.5,
    9.7, 10.0, 10.2, 10.5, 10.7, 11.0, 11.2, 11.5, 11.7, 12.0, 12.2, 12.5, 12.7, 13.0, 13.2, 13.5,
    13.7, 14.0, 14.2, 14.4, 14.6, 14.8, 15.0, 15.2, 15.5, 15.7, 16.0, 16.2, 16.5, 16.7, 17.0, 17.2,
    17.5, 17.7, 18.0, 18.2, 18.5, 18.7, 19.0, 19.2, 19.4, 19.6, 19.8, 20.0, 20.2, 20.5, 20.7, 21.0,
    21.2, 21.5, 21.7, 22.0, 22.2, 22.5, 22.7, 23.0, 23.2, 23.5, 23.7, 24.0, 24.2, 24.5, 24.7, 25.0,
    25.2, 25.5, 25.7, 26.0, 26.2, 26.5, 26.7, 27.0, 27.2, 27.5, 27.7, 28.0, 28.2, 28.5, 28.7, 29.0,
    29.2, 29.5, 29.7, 30.0, 30.2, 30.5, 30.7, 31.0, 31.3, 31.6, 32.0, 32.2, 32.5, 32.7, 33.0, 33.2,
    33.5, 33.7, 34.0, 34.3, 34.6, 35.0, 35.2, 35.5, 35.7, 36.0, 36.3, 36.6, 37.0, 37.2, 37.5, 37.7,
    38.0, 38.3, 38.6, 39.0, 39.3, 39.6, 40.0, 40.3, 40.6, 41.0, 41.3, 41.6, 42.0, 42.3, 42.6, 43.0,
];
const emptyBlock = () => [0, 0, 0, 0, 0, 0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];

function crc16(bytes) {
    let crc = 0xffff;
    for (const b of bytes) {
        crc ^= (b << 8);
        for (let i = 0; i < 8; i++) crc = (crc & 0x8000) ? ((crc << 1) ^ 0x1021) & 0xffff : (crc << 1) & 0xffff;
    }
    return crc;
}
const withCrc = bytes => { const crc = crc16(bytes); return bytes.concat([crc & 0xff, crc >> 8]); };

function decodeStat(base64) {
    const d = [...Buffer.from(base64, 'base64')];
    const start = d[18] * 4 + 21;
    const c = d.slice(start, start + 18);
    const segments = d.slice(start + 19, d.length - 2);
    let energy = null;
    let outdoor = null;
    let indoor = null;
    for (let i = 0; i + 3 < segments.length; i += 4) {
        if (segments[i] === 0x80 && segments[i + 1] === 0x20 && segments[i + 2] < INDOOR_TEMPS.length) indoor = INDOOR_TEMPS[segments[i + 2]];
        if (segments[i] === 0x94 && segments[i + 1] === 0x10) energy = ((segments[i + 3] << 8) | segments[i + 2]) * 0.25;
        if (segments[i] === 0x80 && segments[i + 1] === 0x10 && segments[i + 2] < OUTDOOR_TEMPS.length) outdoor = OUTDOOR_TEMPS[segments[i + 2]];
    }
    const modelNrRaw = c[0] & 0x7f;
    const modelNr = modelNrRaw === 3 ? 2 : [0, 1, 2].indexOf(modelNrRaw);
    const mode = Object.keys(MODES).find(m => MODES[m][1] === (c[2] & 0x3c)) || null;
    return {
        power: (c[2] & 0x03) === 1,
        mode,
        presetTemp: c[4] / 2,
        airFlow: RCV_AIRFLOW.indexOf(c[3] & 0x0f),
        windUD: (c[2] & 0xc0) === 0x40 ? 0 : [0, 16, 32, 48].indexOf(c[3] & 0xf0) + 1,
        windLR: (c[12] & 0x03) === 1 ? 0 : [0, 1, 2, 3, 4, 5, 6].indexOf(c[11] & 0x1f) + 1,
        entrust: (c[12] & 0x0c) === 4,
        coolHotJudge: (c[8] & 0x08) === 0,
        modelNr,
        modelNrRaw,
        vacant: (c[10] & 1) !== 0,
        selfClean: (modelNr === 1 || modelNr === 2) ? (c[15] & 1) !== 0 : false,
        runEnergyKwh: energy,
        outdoorTemp: outdoor,
        indoorTemp: indoor,
        compressorRunning: (c[9] & 0x02) !== 0,
    };
}

function commandBlock(s) {
    const b = emptyBlock();
    b[2] |= (s.power ? 3 : 2) | MODES[s.mode][0];
    b[3] |= CMD_AIRFLOW[s.airFlow];
    const ud = CMD_WIND_UD[s.windUD]; if (ud) { b[2] |= ud[0]; b[3] |= ud[1]; }
    const lr = CMD_WIND_LR[s.windLR]; if (lr) { b[12] |= lr[0]; b[11] |= lr[1]; }
    b[4] |= Math.trunc(s.presetTemp / 0.5) + 128;
    b[12] |= s.entrust ? 12 : 8;
    if (!s.coolHotJudge) b[8] |= 8;
    if (s.modelNr === 1 && s.vacant) b[10] |= 1;
    if (s.modelNr === 1 || s.modelNr === 2) b[12] |= s.selfClean ? 144 : 128;
    return b;
}

function receiveBlock(s) {
    const b = emptyBlock();
    if (s.power) b[2] |= 1;
    b[2] |= MODES[s.mode][1];
    b[3] |= RCV_AIRFLOW[s.airFlow];
    if (s.windUD === 0) b[2] |= 64; else if (s.windUD >= 2 && s.windUD <= 4) b[3] |= (s.windUD - 1) * 16;
    if (s.windLR === 0) b[12] |= 1; else if (s.windLR >= 1 && s.windLR <= 7) b[11] |= s.windLR - 1;
    b[4] |= Math.trunc(s.presetTemp / 0.5);
    if (s.entrust) b[12] |= 4;
    if (!s.coolHotJudge) b[8] |= 8;
    b[0] |= s.modelNrRaw;
    if (s.modelNr === 1 && s.vacant) b[10] |= 1;
    if (s.modelNr === 1 || s.modelNr === 2) b[12] |= s.selfClean ? 144 : 128;
    return b;
}

function encodeStat(s) {
    if (!MODES[s.mode]) throw new Error('Unbekannter Betriebsmodus der Anlage');
    if (!(s.airFlow >= 0 && s.airFlow < CMD_AIRFLOW.length)) throw new Error(`Unbekannte Lüfterstufe ${s.airFlow}`);
    const bytes = withCrc(commandBlock(s).concat(NO_SEGMENTS)).concat(withCrc(receiveBlock(s).concat(NO_SEGMENTS)));
    return Buffer.from(bytes).toString('base64');
}

const modelHasVaneLR = raw => [0, 1, 2, 3].includes(raw);

// ---------------------------------------------------------------------------------------------
// Hilfsfunktionen für Datenpunkte
// ---------------------------------------------------------------------------------------------

const pad = n => String(n).padStart(2, '0');
const dayKey = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const hourKey = d => `${dayKey(d)}T${pad(d.getHours())}`;
const round = v => Math.round(v * 1000) / 1000;

async function ensureJsonState(id, name, initial) {
    if (!(await existsStateAsync(id))) {
        await createStateAsync(id, JSON.stringify(initial), { name, type: 'string', role: 'json', read: true, write: true });
    }
}

async function readJson(id) {
    const state = await getStateAsync(id);
    if (!state || !state.val) return null;
    try { return JSON.parse(state.val); } catch (e) { return null; }
}

async function units() {
    await ensureJsonState(ENERGY_CONFIG_ID, 'KlimaPilot – Anlagen und Strompreis (wird von KlimaPilot geschrieben)', { units: [] });
    const config = await readJson(ENERGY_CONFIG_ID) || {};
    return (config.units || []).filter(u => u.airconId && u.host);
}

// ---------------------------------------------------------------------------------------------
// 1. Energieaufzeichnung
// ---------------------------------------------------------------------------------------------

// Aufräumen: Datenpunkt der wieder entfernten Heiz-Korrektur-Lernfunktion (0.5.53–0.5.55).
try { if (existsObject('0_userdata.0.KlimaPilot.heatOffset')) deleteState('0_userdata.0.KlimaPilot.heatOffset'); } catch (e) { /* nichts zu tun */ }

async function pollEnergy(unit) {
    const id = `${ENERGY_ROOT}.units.${unit.airconId}`;
    await ensureJsonState(id, `KlimaPilot Energie ${unit.name || unit.airconId}`, {});
    const data = Object.assign({ hourly: {}, daily: {}, totalKwh: 0 }, await readJson(id) || {});
    data.airconId = unit.airconId;
    data.name = unit.name || data.name || '';
    data.room = unit.room || data.room || '';
    const now = new Date();
    try {
        const contents = await serial(unit, () => readStat(unit));
        const stat = decodeStat(contents.airconStat);
        roomObserve(unit, stat, now);
        eventObserve(unit, stat, now);
        const value = stat.runEnergyKwh;
        if (value === null) throw new Error('Anlage meldet keinen Energiezähler');
        // Betriebszeit (Betriebstage für die Filter-Erinnerung in der App) und Außentemperatur (Mittel je Stunde und Tag).
        if (stat.power) {
            data.runMinutes = (data.runMinutes || 0) + POLL_MINUTES;
            // Betriebstage: jeder Kalendertag, an dem die Anlage mindestens einmal lief, zählt einmal.
            if (data.lastRunDay !== dayKey(now)) {
                data.runDays = (data.runDays || 0) + 1;
                data.lastRunDay = dayKey(now);
            }
        }
        if (typeof stat.outdoorTemp === 'number') {
            for (const [map, count, key] of [['outdoorHourly', 'outdoorHourlyN', hourKey(now)], ['outdoorDaily', 'outdoorDailyN', dayKey(now)]]) {
                data[map] = data[map] || {};
                data[count] = data[count] || {};
                const n = data[count][key] || 0;
                data[map][key] = Math.round(((data[map][key] || 0) * n + stat.outdoorTemp) / (n + 1) * 10) / 10;
                data[count][key] = n + 1;
            }
            // Zähler werden nur für die laufende Stunde bzw. den laufenden Tag gebraucht.
            for (const [count, key] of [['outdoorHourlyN', hourKey(now)], ['outdoorDailyN', dayKey(now)]]) {
                for (const k of Object.keys(data[count])) if (k !== key) delete data[count][k];
            }
        }
        // Laufzeit seit dem letzten Zählerschritt, als Anzahl eingeschalteter Ablesungen je Stunde.
        const slots = data.pendingSlots && typeof data.pendingSlots === 'object' ? data.pendingSlots : {};
        // Neuer Lauf: Laufzeit des vorigen Laufs gehört zu keinem kommenden Schritt mehr.
        if (typeof data.lastRunKwh === 'number' && value < data.lastRunKwh) for (const key of Object.keys(slots)) delete slots[key];
        if (stat.power) slots[hourKey(now)] = (slots[hourKey(now)] || 0) + 1;
        if (typeof data.lastRunKwh === 'number') {
            // Fällt der Zähler, hat ein neuer Lauf begonnen: dann zählt der neue Wert ganz.
            const delta = value >= data.lastRunKwh ? value - data.lastRunKwh : value;
            if (delta > 0) {
                book(data, delta, slots, now);
                for (const key of Object.keys(slots)) delete slots[key];
            }
        } else {
            // Erste Ablesung: Ausgangswert merken. Was davor verbraucht wurde, ist zeitlich nicht zuzuordnen.
            data.recordingSince = now.toISOString();
        }
        // Nur die jüngste Laufzeit behalten, damit ein Schritt nicht über Tage verschmiert wird.
        const keys = Object.keys(slots).sort();
        let total = keys.reduce((sum, key) => sum + slots[key], 0);
        for (const key of keys) {
            if (total <= SPREAD_MAX_SLOTS) break;
            const drop = Math.min(slots[key], total - SPREAD_MAX_SLOTS);
            slots[key] -= drop; total -= drop;
            if (slots[key] <= 0) delete slots[key];
        }
        data.pendingSlots = slots;
        data.lastRunKwh = value;
        data.lastPollAt = now.toISOString();
        delete data.lastError;
        await diagReach(unit, true);
    } catch (e) {
        data.lastError = `${now.toISOString()} ${e.message}`;
        await diagReach(unit, !!e.refused, e.message);
    }
    const oldest = hourKey(new Date(now.getTime() - HOURLY_DAYS * 24 * 3600 * 1000));
    for (const key of Object.keys(data.hourly)) if (key < oldest) delete data.hourly[key];
    if (data.outdoorHourly) for (const key of Object.keys(data.outdoorHourly)) if (key < oldest) delete data.outdoorHourly[key];
    await setStateAsync(id, JSON.stringify(data), true);
}

/** Verteilt einen Zählerschritt anteilig auf die Stunden, in denen die Anlage lief. */
function book(data, delta, slots, now) {
    const entries = Object.entries(slots).filter(([, count]) => count > 0);
    const total = entries.reduce((sum, [, count]) => sum + count, 0);
    const parts = total > 0 ? entries.map(([hour, count]) => [hour, delta * count / total]) : [[hourKey(now), delta]];
    for (const [hour, kwh] of parts) {
        const day = hour.slice(0, 10);
        data.hourly[hour] = round((data.hourly[hour] || 0) + kwh);
        data.daily[day] = round((data.daily[day] || 0) + kwh);
    }
    data.totalKwh = round((data.totalKwh || 0) + delta);
}

let energyRunning = false;
async function pollAllEnergy() {
    if (energyRunning) return;
    energyRunning = true;
    try {
        for (const unit of await units()) await pollEnergy(unit);
        await roomPublish();
    } catch (e) {
        log(`KlimaPilot Energie: ${e.message}`, 'warn');
    } finally {
        energyRunning = false;
    }
}

// ---------------------------------------------------------------------------------------------
// 5. Ereignisprotokoll (immer aktiv, 48 h)
// ---------------------------------------------------------------------------------------------

let eventsQueue = Promise.resolve();
const lastSeen = {};  // airconId → { stat, at } der letzten Abfrage
const ownWrite = {};  // airconId → Zeitpunkt, zu dem das Skript zuletzt geschaltet hat

/** Hängt einen Eintrag an; Schreibvorgänge laufen nacheinander, damit keiner verloren geht. */
function eventLog(subject, text) {
    eventsQueue = eventsQueue.then(async () => {
        await ensureJsonState(EVENTS_ID, 'KlimaPilot – Ereignisprotokoll des Skripts (48 h)', { entries: [] });
        const data = await readJson(EVENTS_ID) || {};
        const oldest = Date.now() - EVENTS_KEEP_MS;
        const entries = (Array.isArray(data.entries) ? data.entries : []).filter(e => Date.parse(e.at) >= oldest);
        entries.push({ at: new Date().toISOString(), subject: subject || '', text });
        await setStateAsync(EVENTS_ID, JSON.stringify({ entries: entries.slice(-EVENTS_MAX) }), true);
    }).catch(e => log(`KlimaPilot Ereignisprotokoll: ${e.message}`, 'warn'));
    return eventsQueue;
}

const MODE_LABEL = { HEAT: 'Heizen', COOL: 'Kühlen', DRY: 'Trocknen', FAN: 'Lüfter', AUTO: 'Auto' };
const FAN_LABEL = ['Auto', 'Leise', 'Niedrig', 'Mittel', 'Hoch'];
const fmtTemp = v => typeof v === 'number' ? v.toFixed(1).replace('.', ',') : '?';

/** Lesbarer Unterschied der einstellbaren Werte, z. B. „Soll 20,5 → 21,0 °C, Lüfter Auto → Hoch“. */
function statDiff(a, b) {
    const parts = [];
    if (a.power !== b.power) parts.push(b.power ? 'eingeschaltet' : 'ausgeschaltet');
    if (a.mode !== b.mode) parts.push(`Modus ${MODE_LABEL[a.mode] || a.mode} → ${MODE_LABEL[b.mode] || b.mode}`);
    if (a.presetTemp !== b.presetTemp) parts.push(`Soll ${fmtTemp(a.presetTemp)} → ${fmtTemp(b.presetTemp)} °C`);
    if (a.airFlow !== b.airFlow) parts.push(`Lüfter ${FAN_LABEL[a.airFlow] || a.airFlow} → ${FAN_LABEL[b.airFlow] || b.airFlow}`);
    if (a.windUD !== b.windUD) parts.push(`Lamelle oben/unten ${a.windUD} → ${b.windUD}`);
    if (a.windLR !== b.windLR) parts.push(`Lamelle links/rechts ${a.windLR} → ${b.windLR}`);
    if (a.entrust !== b.entrust) parts.push(`3D-Auto ${b.entrust ? 'an' : 'aus'}`);
    return parts.join(', ');
}

/** Vergleicht mit der vorigen Abfrage und protokolliert Änderungen samt möglicher Quelle. */
function eventObserve(unit, stat, now) {
    const id = unit.airconId;
    const prev = lastSeen[id];
    lastSeen[id] = { stat, at: now.getTime() };
    if (!prev) return;
    const diff = statDiff(prev.stat, stat);
    if (!diff) return;
    const byScript = ownWrite[id] && ownWrite[id] > prev.at;
    eventLog(unit.name || id, byScript
        ? `geändert seit ${new Date(prev.at).toTimeString().slice(0, 5)} (das Skript hat dazwischen geschaltet): ${diff}`
        : `geändert seit ${new Date(prev.at).toTimeString().slice(0, 5)} – nicht vom Skript (Handy, Fernbedienung oder anderes Gerät): ${diff}`);
}

// ---------------------------------------------------------------------------------------------
// 3. Diagnose (in KlimaPilot unter Einstellungen → Diagnose ein- und ausschaltbar)
// ---------------------------------------------------------------------------------------------
// Bei eingeschalteter Diagnose prüft das Skript jede Minute, ob die Anlagen lokal erreichbar sind,
// und schreibt jeden Wechsel (nicht erreichbar / wieder erreichbar, mit Dauer und Fehler) ins Protokoll.

const reach = {}; // airconId → { ok, since, error }

async function diagEnabled() {
    await ensureJsonState(DIAG_CONFIG_ID, 'KlimaPilot – Diagnose an/aus (wird von KlimaPilot geschrieben)', { enabled: false });
    const config = await readJson(DIAG_CONFIG_ID) || {};
    return config.enabled === true;
}

async function diagLog(subject, event, detail) {
    await ensureJsonState(DIAG_LOG_ID, 'KlimaPilot – Diagnoseprotokoll', { entries: [] });
    const logData = await readJson(DIAG_LOG_ID) || {};
    const oldest = Date.now() - DIAG_KEEP_DAYS * 24 * 3600 * 1000;
    const entries = (Array.isArray(logData.entries) ? logData.entries : []).filter(e => Date.parse(e.at) >= oldest);
    // Gleicher Eintrag binnen 3 min (z. B. Energieablesung und Minutenprüfung oder Neustart-Überlappung)
    // nur einmal aufnehmen.
    const recent = entries.slice(-10).some(e => e.source === 'ioBroker' && e.subject === subject && e.event === event &&
        (e.detail || '') === (detail || '') && Date.now() - Date.parse(e.at) < 3 * 60000);
    if (recent) return;
    entries.push({ at: new Date().toISOString(), source: 'ioBroker', subject, event, detail: detail || '' });
    await setStateAsync(DIAG_LOG_ID, JSON.stringify({ entries: entries.slice(-DIAG_MAX_ENTRIES) }), true);
}

const minutesSince = since => Math.max(1, Math.round((Date.now() - since) / 60000));

/**
 * Merkt sich die Erreichbarkeit einer Anlage und protokolliert Ausfälle, wenn die Diagnose an ist.
 * Erst ab DIAG_MIN_OUTAGE_MS Dauer wird ein Ausfall eingetragen (die stündlichen Neustarts des
 * WLAN-Moduls dauern rund 1 min und gehören nicht ins Protokoll); das Ende nur, wenn der Ausfall
 * eingetragen wurde.
 */
const DIAG_MIN_OUTAGE_MS = 3 * 60000;
async function diagReach(unit, ok, error) {
    const previous = reach[unit.airconId];
    const now = Date.now();
    if (ok) {
        reach[unit.airconId] = { ok: true, since: now };
        if (!previous || previous.ok || !previous.logged) return;
        await diagWrite(unit, 'wieder erreichbar', `nach ${minutesSince(previous.since)} min`);
        return;
    }
    const down = previous && !previous.ok ? previous : { ok: false, since: now, logged: false };
    down.error = error;
    reach[unit.airconId] = down;
    if (down.logged || now - down.since < DIAG_MIN_OUTAGE_MS) return;
    down.logged = true;
    const since = new Date(down.since).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
    await diagWrite(unit, 'nicht erreichbar', `seit ${since} – ${error || ''}`);
}

async function diagWrite(unit, event, detail) {
    try {
        if (await diagEnabled()) await diagLog(unit.name || unit.airconId, event, detail);
    } catch (e) {
        log(`KlimaPilot Diagnose: ${e.message}`, 'warn');
    }
}

let probing = false;
async function probeAll() {
    if (probing || new Date().getMinutes() % POLL_MINUTES === 0) return; // Zur Energieablesung prüft schon pollEnergy.
    probing = true;
    try {
        if (!(await diagEnabled())) return;
        for (const unit of await units()) {
            try {
                const contents = await serial(unit, () => readStat(unit));
                try { roomObserve(unit, decodeStat(contents.airconStat), new Date()); } catch (e) { /* nur Lernen */ }
                await diagReach(unit, true);
            } catch (e) {
                await diagReach(unit, !!e.refused, e.message);
            }
        }
        await roomPublish();
    } catch (e) {
        log(`KlimaPilot Diagnose: ${e.message}`, 'warn');
    } finally {
        probing = false;
    }
}

let diagWasEnabled = null;
let watching = false;
async function watchDiagnostics() {
    if (watching) return; // Start- und Minutenaufruf dürfen sich nicht überschneiden.
    watching = true;
    try {
        const enabled = await diagEnabled();
        if (diagWasEnabled !== null && enabled !== diagWasEnabled) {
            await diagLog('ioBroker-Skript', enabled ? 'Diagnose eingeschaltet' : 'Diagnose ausgeschaltet');
            if (enabled) for (const key of Object.keys(reach)) delete reach[key]; // Ausgangslage neu erfassen
        } else if (diagWasEnabled === null && enabled) {
            await diagLog('ioBroker-Skript', 'Skript gestartet');
        }
        diagWasEnabled = enabled;
    } catch (e) {
        log(`KlimaPilot Diagnose: ${e.message}`, 'warn');
    } finally {
        watching = false;
    }
}

// ---------------------------------------------------------------------------------------------
// 4. Raumtemperatur lernen
// ---------------------------------------------------------------------------------------------
// Die App schreibt einen Thermometerwert nach roomTemp.input. Das Skript speichert ihn mit dem
// Zustand der Anlage in roomTemp.samples und lernt je Anlage, wie weit der Fühler vom Raum abweicht:
// Ridge-Regression über standardisierte Merkmale, Stärke der Glättung per Kreuzprüfung gewählt.
// Das Modell gilt nur, wenn es in der Kreuzprüfung besser trifft als der einfache Mittelwert.
// Ergebnis (aktuelle Abweichung je Anlage) steht in roomTemp.model; die App zieht sie vom Fühler ab.

const ROOM_ROOT = '0_userdata.0.KlimaPilot.roomTemp';
const ROOM_INPUT_ID = `${ROOM_ROOT}.input`;
const ROOM_SAMPLES_ID = `${ROOM_ROOT}.samples`;
const ROOM_MODEL_ID = `${ROOM_ROOT}.model`;
const ROOM_TRACK_ID = `${ROOM_ROOT}.track`;
// Messwerte verfallen nie mit der Zeit. Nur wenn eine Lage (z. B. „Heizen, Pause lang“) mehr als
// ROOM_MAX_PER_SCENARIO Werte hat, ersetzt ein neuer Wert den ältesten derselben Lage – so verdrängen
// Sommerwerte (Kühlen) nie die Winterwerte (Heizen) und umgekehrt.
const ROOM_MAX_PER_SCENARIO = 100;
const ROOM_MIN_MEAN = 3;    // ab 3 Messwerten: Mittelwert
const ROOM_MIN_MODEL = 8;   // ab 8 Messwerten: Modell, wenn es in der Kreuzprüfung besser trifft
const ROOM_FEATURES = ['on', 'heat', 'comp', 'runFresh', 'restFresh', 'offFresh', 'trend', 'outdoor', 'fan', 'setDiff'];
const ROOM_LAMBDAS = [0.3, 1, 3, 10, 30];
const MODE_NAMES = { HEAT: 'Heizen', COOL: 'Kühlen', DRY: 'Trocknen', FAN: 'Lüfter', AUTO: 'Auto' };

const roomTrack = {};   // airconId → { power, comp, powerSince, compSince, raws, raw, stat, at }
const roomFits = {};    // airconId → gelerntes Modell
const roomResults = {}; // airconId → Ergebnis der letzten Eingabe
let roomTrackLoaded = false;

const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
const roomMinutes = (since, t) => since == null ? 240 : Math.min(240, Math.max(0, (t - since) / 60000));

/** Merkt sich je Minute (bzw. 5 min ohne Diagnose) den Zustand; Wechsel von Ein/Aus und Verdichter mit Zeitpunkt. */
function roomObserve(unit, stat, now) {
    if (typeof stat.indoorTemp !== 'number') return;
    const t = now.getTime();
    const tr = roomTrack[unit.airconId] = roomTrack[unit.airconId] || { raws: [] };
    const comp = !!(stat.power && stat.compressorRunning);
    let changed = false;
    // Unbekannter Vorzustand (erster Lauf): Dauer unbekannt, gilt als „lange“.
    if (tr.power !== stat.power) { tr.powerSince = tr.power === undefined ? null : t; tr.power = stat.power; changed = true; }
    if (tr.comp !== comp) { tr.compSince = tr.comp === undefined ? null : t; tr.comp = comp; changed = true; }
    tr.raws = (tr.raws || []).filter(([at]) => t - at <= 20 * 60000);
    tr.raws.push([t, stat.indoorTemp]);
    tr.raw = stat.indoorTemp;
    tr.stat = { power: stat.power, mode: stat.mode, airFlow: stat.airFlow, presetTemp: stat.presetTemp, outdoorTemp: stat.outdoorTemp };
    tr.at = t;
    if (changed) roomSaveTrack();
}

function roomSaveTrack() {
    const data = {};
    for (const [id, tr] of Object.entries(roomTrack)) data[id] = { power: tr.power, comp: tr.comp, powerSince: tr.powerSince, compSince: tr.compSince };
    ensureJsonState(ROOM_TRACK_ID, 'KlimaPilot – Raumtemperatur: Zustandswechsel (intern)', {})
        .then(() => setStateAsync(ROOM_TRACK_ID, JSON.stringify(data), true))
        .catch(e => log(`KlimaPilot Raumtemperatur: ${e.message}`, 'warn'));
}

async function roomLoadTrack() {
    if (roomTrackLoaded) return;
    roomTrackLoaded = true;
    await ensureJsonState(ROOM_TRACK_ID, 'KlimaPilot – Raumtemperatur: Zustandswechsel (intern)', {});
    const data = await readJson(ROOM_TRACK_ID) || {};
    for (const [id, saved] of Object.entries(data)) {
        if (!roomTrack[id]) roomTrack[id] = Object.assign({ raws: [] }, saved);
    }
}

function roomFeatures(tr, t) {
    const s = tr.stat;
    const on = s.power ? 1 : 0;
    const comp = tr.comp ? 1 : 0;
    const old = tr.raws.find(([at]) => t - at <= 12 * 60000) || tr.raws[0];
    return {
        on,
        heat: on && s.mode === 'HEAT' ? 1 : 0,
        comp,
        runFresh: comp ? Math.exp(-roomMinutes(tr.compSince, t) / 15) : 0,      // 1 = Verdichter gerade gestartet
        restFresh: on && !comp ? Math.exp(-roomMinutes(tr.compSince, t) / 15) : 0, // 1 = Verdichter gerade gestoppt
        offFresh: !on ? Math.exp(-roomMinutes(tr.powerSince, t) / 30) : 0,        // 1 = Anlage gerade ausgeschaltet
        trend: old ? clamp(tr.raw - old[1], -3, 3) : 0,                            // Fühleränderung der letzten ~10 min
        outdoor: typeof s.outdoorTemp === 'number' ? s.outdoorTemp : 5,
        fan: on ? (s.airFlow > 0 ? s.airFlow : 2) : 0,                             // Auto zählt als mittlere Stufe
        setDiff: on && typeof s.presetTemp === 'number' ? clamp(s.presetTemp - tr.raw, -10, 10) : 0,
    };
}

/**
 * „aktiv“ = dieser Raum fordert Wärme/Kälte an (Statusbit je Innengerät). Am gemeinsamen
 * Außengerät kann der Verdichter auch für den anderen Raum laufen, daher nicht „Verdichter“.
 */
function roomScenarioRename(label) {
    return label.replace(', Verdichter startet', ', aktiv seit kurzem').replace(', Verdichter läuft', ', aktiv länger');
}

function roomScenario(tr, t) {
    const s = tr.stat;
    if (!s.power) return roomMinutes(tr.powerSince, t) < 30 ? 'Aus, kürzlich abgeschaltet' : 'Aus';
    const mode = MODE_NAMES[s.mode] || 'An';
    if (s.mode === 'FAN') return mode;
    if (tr.comp) return `${mode}, aktiv ${roomMinutes(tr.compSince, t) < 10 ? 'seit kurzem' : 'länger'}`;
    return `${mode}, Pause ${roomMinutes(tr.compSince, t) < 10 ? 'kurz' : 'lang'}`;
}

function roomScenarioList(tr) {
    const mode = tr && tr.stat && tr.stat.power && MODE_NAMES[tr.stat.mode] && tr.stat.mode !== 'FAN' ? MODE_NAMES[tr.stat.mode] : 'Heizen';
    return ['Aus', 'Aus, kürzlich abgeschaltet', `${mode}, aktiv seit kurzem`, `${mode}, aktiv länger`, `${mode}, Pause kurz`, `${mode}, Pause lang`];
}

/** Löst A·x = b (A symmetrisch positiv definit) per Gauß-Elimination mit Pivotsuche. */
function solve(A, b) {
    const n = b.length;
    const M = A.map((row, i) => [...row, b[i]]);
    for (let c = 0; c < n; c++) {
        let p = c;
        for (let r = c + 1; r < n; r++) if (Math.abs(M[r][c]) > Math.abs(M[p][c])) p = r;
        [M[c], M[p]] = [M[p], M[c]];
        const d = M[c][c] || 1e-9;
        for (let r = 0; r < n; r++) {
            if (r === c) continue;
            const f = M[r][c] / d;
            if (f) for (let k = c; k <= n; k++) M[r][k] -= f * M[c][k];
        }
    }
    return M.map((row, i) => row[n] / (row[i] || 1e-9));
}

function ridgeFit(X, y, lambda) {
    const n = X.length, k = X[0].length;
    const mean = Array(k).fill(0), sd = Array(k).fill(0);
    for (const x of X) x.forEach((v, j) => { mean[j] += v / n; });
    for (const x of X) x.forEach((v, j) => { sd[j] += (v - mean[j]) ** 2 / n; });
    for (let j = 0; j < k; j++) sd[j] = Math.sqrt(sd[j]) > 1e-6 ? Math.sqrt(sd[j]) : 1;
    const ym = y.reduce((a, v) => a + v, 0) / n;
    const Z = X.map(x => x.map((v, j) => (v - mean[j]) / sd[j]));
    const A = Array.from({ length: k }, (_, i) => Array.from({ length: k }, (_, j) => (i === j ? lambda : 0)));
    const b = Array(k).fill(0);
    Z.forEach((z, r) => {
        for (let i = 0; i < k; i++) {
            b[i] += z[i] * (y[r] - ym);
            for (let j = 0; j < k; j++) A[i][j] += z[i] * z[j];
        }
    });
    return { mean, sd, w: solve(A, b), ym };
}

const ridgePredict = (m, x) => m.ym + x.reduce((a, v, j) => a + m.w[j] * (v - m.mean[j]) / m.sd[j], 0);

/** Mittlerer Fehler in der Kreuzprüfung (bis 60 Werte: jeder einzeln weggelassen, sonst 10 Teile). */
function crossValidate(X, y, lambda) {
    const n = y.length, folds = n <= 60 ? n : 10;
    let errModel = 0, errMean = 0;
    for (let f = 0; f < folds; f++) {
        const trainX = [], trainY = [], testI = [];
        for (let i = 0; i < n; i++) {
            if (i % folds === f) testI.push(i);
            else { trainX.push(X[i]); trainY.push(y[i]); }
        }
        if (!testI.length || trainY.length < 2) continue;
        const m = lambda === null ? null : ridgeFit(trainX, trainY, lambda);
        const avg = trainY.reduce((a, v) => a + v, 0) / trainY.length;
        for (const i of testI) {
            if (m) errModel += Math.abs(ridgePredict(m, X[i]) - y[i]);
            errMean += Math.abs(avg - y[i]);
        }
    }
    return { model: errModel / n, mean: errMean / n };
}

function roomFit(airconId, samples) {
    const usable = samples.filter(s => s && s.f && typeof s.offset === 'number');
    const n = usable.length;
    const y = usable.map(s => s.offset);
    const fit = { n, using: 'none', min: n ? Math.min(...y) : 0, max: n ? Math.max(...y) : 0 };
    if (n >= ROOM_MIN_MEAN) {
        fit.using = 'mean';
        fit.mean = y.reduce((a, v) => a + v, 0) / n;
        fit.cvMean = crossValidate(usable.map(() => [0]), y, null).mean;
    }
    if (n >= ROOM_MIN_MODEL) {
        const X = usable.map(s => ROOM_FEATURES.map(name => Number(s.f[name]) || 0));
        let best = null;
        for (const lambda of ROOM_LAMBDAS) {
            const cv = crossValidate(X, y, lambda);
            if (!best || cv.model < best.cv) best = { lambda, cv: cv.model };
        }
        fit.cvModel = best.cv;
        fit.lambda = best.lambda;
        // Nur übernehmen, wenn das Modell spürbar besser trifft als der einfache Mittelwert.
        if (best.cv < fit.cvMean - 0.05) {
            fit.using = 'model';
            fit.ridge = ridgeFit(X, y, best.lambda);
            fit.weights = Object.fromEntries(ROOM_FEATURES.map((name, j) => [name, Math.round(fit.ridge.w[j] / fit.ridge.sd[j] * 1000) / 1000]));
        }
    }
    roomFits[airconId] = fit;
}

function roomOffset(fit, f) {
    if (!fit || fit.using === 'none') return null;
    const raw = fit.using === 'model' ? ridgePredict(fit.ridge, ROOM_FEATURES.map(name => f[name])) : fit.mean;
    // Nie weiter hinaus raten als 1 °C über die bisher gemessenen Abweichungen.
    return Math.round(clamp(raw, fit.min - 1, fit.max + 1) * 10) / 10;
}

let roomSamplesCache = null;

/** Messwerte einmal laden und im Speicher halten (die Veröffentlichung läuft jede Minute). */
async function roomReadSamples() {
    if (roomSamplesCache) return roomSamplesCache;
    await ensureJsonState(ROOM_SAMPLES_ID, 'KlimaPilot – Raumtemperatur: Thermometerwerte mit Anlagenzustand', { units: {} });
    const data = await readJson(ROOM_SAMPLES_ID) || {};
    data.units = data.units && typeof data.units === 'object' ? data.units : {};
    // Lagen hießen bis 0.5.74 „Verdichter startet/läuft“; das Statusbit gilt aber je Raum (Multisplit).
    for (const list of Object.values(data.units)) {
        if (Array.isArray(list)) for (const sample of list) if (sample && typeof sample.scenario === 'string') sample.scenario = roomScenarioRename(sample.scenario);
    }
    roomSamplesCache = data;
    return data;
}

async function roomInput(text) {
    let input;
    try { input = JSON.parse(text); } catch (e) { return; }
    if (!input || !input.airconId) return;
    const data = await roomReadSamples();
    const list = data.units[input.airconId] = Array.isArray(data.units[input.airconId]) ? data.units[input.airconId] : [];
    const result = { id: input.id || '', at: new Date().toISOString(), ok: false, message: '' };
    if (input.delete) {
        const before = list.length;
        data.units[input.airconId] = list.filter(s => s.id !== input.delete);
        result.ok = data.units[input.airconId].length < before;
        result.message = result.ok ? 'Messwert gelöscht.' : 'Messwert nicht gefunden.';
    } else {
        const tr = roomTrack[input.airconId];
        const thermo = Number(input.thermometer);
        const raw = typeof input.raw === 'number' ? input.raw : tr && tr.raw;
        const t = Date.now();
        if (!tr || !tr.stat || t - tr.at > 10 * 60000) result.message = 'Keine aktuellen Daten der Anlage im Skript – später noch einmal versuchen.';
        else if (!(thermo >= 5 && thermo <= 40)) result.message = 'Thermometerwert unplausibel.';
        else if (typeof raw !== 'number' || raw - thermo < -5 || raw - thermo > 12) result.message = 'Abweichung zum Fühler unplausibel – Wert nicht übernommen.';
        else {
            const f = roomFeatures(tr, t);
            for (const k of Object.keys(f)) f[k] = Math.round(f[k] * 1000) / 1000;
            list.push({ id: input.id || String(t), at: new Date(t).toISOString(), thermo, raw, offset: Math.round((raw - thermo) * 10) / 10, scenario: roomScenario(tr, t), f });
            const same = list.filter(s => s.scenario === list[list.length - 1].scenario);
            if (same.length > ROOM_MAX_PER_SCENARIO) list.splice(list.indexOf(same[0]), 1);
            result.ok = true;
            result.message = `Messwert ${String(thermo).replace('.', ',')} °C übernommen (${list.length} gesamt).`;
        }
    }
    if (result.ok) {
        await setStateAsync(ROOM_SAMPLES_ID, JSON.stringify(data), true);
        roomFit(input.airconId, data.units[input.airconId]);
    }
    roomResults[input.airconId] = result;
    await setStateAsync(ROOM_INPUT_ID, text, true);
    await roomPublish();
}

/** Schreibt je Anlage die aktuelle Abweichung, den Lernstand und die Abdeckung der Lagen. */
async function roomPublish() {
    try {
        const data = await roomReadSamples();
        const out = { updatedAt: new Date().toISOString(), units: {} };
        const t = Date.now();
        // Auch Anlagen ohne aktuelle Beobachtung, damit die App das Ergebnis jeder Eingabe sieht.
        const ids = new Set([...Object.keys(roomTrack), ...Object.keys(data.units), ...Object.keys(roomResults)]);
        for (const id of ids) {
            const list = data.units[id] || [];
            if (!roomFits[id]) roomFit(id, list);
            const fit = roomFits[id];
            const tr = roomTrack[id];
            const fresh = tr && tr.stat && t - tr.at <= 10 * 60000;
            const coverage = {};
            for (const s of list) coverage[s.scenario] = (coverage[s.scenario] || 0) + 1;
            const last = list[list.length - 1];
            out.units[id] = {
                using: fit.using,
                samples: fit.n,
                offset: fresh ? roomOffset(fit, roomFeatures(tr, t)) : null,
                scenario: fresh ? roomScenario(tr, t) : '',
                scenarios: roomScenarioList(tr),
                coverage,
                errorModel: typeof fit.cvModel === 'number' ? Math.round(fit.cvModel * 100) / 100 : null,
                errorMean: typeof fit.cvMean === 'number' ? Math.round(fit.cvMean * 100) / 100 : null,
                minForModel: ROOM_MIN_MODEL,
                weights: fit.weights || null,
                last: last ? { id: last.id, at: last.at, thermo: last.thermo, raw: last.raw } : null,
                input: roomResults[id] || null,
            };
        }
        await ensureJsonState(ROOM_MODEL_ID, 'KlimaPilot – Raumtemperatur: gelernte Abweichung je Anlage (nur lesen)', { units: {} });
        await setStateAsync(ROOM_MODEL_ID, JSON.stringify(out), true);
    } catch (e) {
        log(`KlimaPilot Raumtemperatur: ${e.message}`, 'warn');
    }
}

// ---------------------------------------------------------------------------------------------
// Einmalige Prüfung: liefert das Modul Betriebsdaten (Verdichterfrequenz, Strom, Wärmetauscher)?
// ---------------------------------------------------------------------------------------------
// Ausgelöst von Hand: in ioBroker 0_userdata.0.KlimaPilot.roomTemp.probe auf einen beliebigen Wert setzen.
// Protokoll nach der WF-RAC-Referenz der Home-Assistant-Integration jeatheak/Mitsubishi-WF-RAC-Integration
// (docs/wf-rac-module-reference.md, §5.3/5.4): setAirconStat mit leerem Befehlsblock (keine Set-Bits,
// ändert nichts) und angehängten Anfragen; die Antworten stehen in derselben Antwort.
// Hinweise der Referenz: (1) jede solche Anfrage gibt dem Skript 60 s Schreibrecht – Handys können in
// der Zeit nicht steuern; (2) eine einzelne gemeldete Anlage schaltete sich bei leerem Befehlsblock aus –
// deshalb wird der Ein/Aus-Zustand danach geprüft und notfalls wiederhergestellt.

const ROOM_PROBE_ID = `${ROOM_ROOT}.probe`;
const ROOM_PROBE_RESULT_ID = `${ROOM_ROOT}.probeResult`;
const PROBE_CODES = [0x11, 0x90, 0x81, 0x87, 0x05];

/** Thermistorkurve der Wärmetauscher-Fühler (Referenz §5.4): byte = G·Rs/(Rs+R(T)). */
function coilTemp(byte) {
    if (!(byte > 0 && byte < 367)) return null;
    const r = 1912 * (367 / byte - 1);
    return Math.round((1 / (1 / 298.15 + Math.log(r / 5200) / 3900) - 273.15) * 10) / 10;
}

function receiveSegments(base64) {
    const d = [...Buffer.from(base64, 'base64')];
    const start = d[18] * 4 + 21;
    const segments = d.slice(start + 19, d.length - 2);
    const out = [];
    for (let i = 0; i + 3 < segments.length; i += 4) out.push(segments.slice(i, i + 4));
    return out;
}

async function probeUnit(unit) {
    const before = decodeStat((await serial(unit, () => readStat(unit))).airconStat);
    const block = emptyBlock();
    if (!before.coolHotJudge) block[8] |= 8; // wie bei einem echten Befehl mitführen (Referenz §5.3)
    const trailer = [PROBE_CODES.length];
    for (const code of PROBE_CODES) trailer.push(code, 0xff, 0xff, 0xff);
    const bytes = withCrc(block.concat(trailer)).concat(withCrc(receiveBlock(before).concat(NO_SEGMENTS)));
    const result = { at: new Date().toISOString(), powerBefore: before.power, compressorRunning: before.compressorRunning };
    try {
        const contents = await serial(unit, () => request(unit, 'setAirconStat', { airconId: unit.airconId, airconStat: Buffer.from(bytes).toString('base64') }));
        ownWrite[unit.airconId] = Date.now();
        eventLog(unit.name || unit.airconId, 'Betriebsdaten-Prüfung (sperrt das Modul für die Handys rund 60 s)');
        const segs = receiveSegments(contents.airconStat);
        result.ok = true;
        result.raw = segs.map(s => s.map(b => b.toString(16).padStart(2, '0')).join(' '));
        for (const [code, sel, op2] of segs) {
            if (code === 0x11) result.compressorHz = Math.round(((sel - 0x10) * 25.6 + 0.1 * op2) * 10) / 10;
            if (code === 0x90) result.currentA = Math.round(op2 * 14 / 51 * 100) / 100;
            if (code === 0x81) result.coilC = coilTemp(op2);
            if (code === 0x87) result.coilGasC = coilTemp(op2);
            if (code === 0x05) result.setpointC = op2 / 2;
        }
    } catch (e) {
        result.ok = false;
        result.error = e.message;
    }
    // Sicherheitsnetz: Hat sich die Anlage dadurch ausgeschaltet, sofort wieder einschalten.
    await sleep(3000);
    try {
        const after = decodeStat((await serial(unit, () => readStat(unit))).airconStat);
        result.powerAfter = after.power;
        if (before.power && !after.power) {
            result.switchedOff = true;
            await serial(unit, () => request(unit, 'setAirconStat', { airconId: unit.airconId, airconStat: encodeStat(before) }));
            ownWrite[unit.airconId] = Date.now();
            eventLog(unit.name || unit.airconId, 'Betriebsdaten-Prüfung hatte die Anlage ausgeschaltet – wieder eingeschaltet');
            result.restored = true;
            await diagLog(unit.name || unit.airconId, 'Betriebsdaten-Prüfung hat die Anlage ausgeschaltet – wieder eingeschaltet', 'Betriebsdaten bei dieser Anlage nicht abfragen');
        }
    } catch (e) {
        result.afterError = e.message;
    }
    return result;
}

async function roomProbe() {
    const out = { at: new Date().toISOString(), units: {} };
    for (const unit of await units()) {
        try { out.units[unit.name || unit.airconId] = await probeUnit(unit); } catch (e) { out.units[unit.name || unit.airconId] = { ok: false, error: e.message }; }
    }
    await ensureJsonState(ROOM_PROBE_RESULT_ID, 'KlimaPilot – Ergebnis der Prüfung der Betriebsdaten (nur lesen)', {});
    await setStateAsync(ROOM_PROBE_RESULT_ID, JSON.stringify(out), true);
    log(`KlimaPilot Betriebsdaten-Prüfung: ${JSON.stringify(out)}`);
}

async function roomStart() {
    await roomLoadTrack();
    await ensureJsonState(ROOM_INPUT_ID, 'KlimaPilot – Raumtemperatur: Thermometerwert aus der App (wird von KlimaPilot geschrieben)', {});
    on({ id: ROOM_INPUT_ID, change: 'any', ack: false }, async obj => {
        try { if (await isOwner()) await roomInput(String(obj.state.val || '')); } catch (e) { log(`KlimaPilot Raumtemperatur: ${e.message}`, 'warn'); }
    });
    await ensureJsonState(ROOM_PROBE_ID, 'KlimaPilot – Betriebsdaten einmal prüfen: beliebigen Wert setzen', '');
    on({ id: ROOM_PROBE_ID, change: 'any', ack: false }, async () => {
        try { if (await isOwner()) { await setStateAsync(ROOM_PROBE_ID, 'läuft …', true); await roomProbe(); await setStateAsync(ROOM_PROBE_ID, 'fertig', true); } }
        catch (e) { log(`KlimaPilot Betriebsdaten-Prüfung: ${e.message}`, 'warn'); }
    });
    // Eine Eingabe, die während eines Neustarts kam, noch übernehmen (bis 10 min alt).
    const pending = await getStateAsync(ROOM_INPUT_ID);
    if (pending && pending.ack === false && pending.val && Date.now() - pending.ts < 10 * 60000) {
        setTimeout(() => { isOwner().then(own => own && roomInput(String(pending.val))).catch(() => {}); }, 90 * 1000);
    }
}

// ---------------------------------------------------------------------------------------------
// 2. Zeitpläne
// ---------------------------------------------------------------------------------------------

/** Wendet die Aktion eines Zeitplans auf den aktuellen Zustand an. */
function applyAction(current, item) {
    const next = Object.assign({}, current);
    if (item.action === 'off') {
        next.power = false;
        return next;
    }
    next.power = true;
    const s = item.settings || {};
    const vaneLR = modelHasVaneLR(current.modelNrRaw);
    if (s.mode && MODES[s.mode]) next.mode = s.mode;
    if (typeof s.targetTemp === 'number') next.presetTemp = Math.min(30, Math.max(16, s.targetTemp));
    if (Number.isInteger(s.fanSpeed) && s.fanSpeed >= 0 && s.fanSpeed < CMD_AIRFLOW.length) next.airFlow = s.fanSpeed;
    if (Number.isInteger(s.verticalSwing) && s.verticalSwing >= 0 && s.verticalSwing < CMD_WIND_UD.length) next.windUD = s.verticalSwing;
    if (vaneLR && Number.isInteger(s.horizontalSwing) && s.horizontalSwing >= 0 && s.horizontalSwing < CMD_WIND_LR.length) next.windLR = s.horizontalSwing;
    if (vaneLR && typeof s.threeDAuto === 'boolean') next.entrust = s.threeDAuto;
    return next;
}

async function execute(unit, item) {
    return serial(unit, async () => {
        for (let attempt = 0; attempt < 3; attempt++) {
            const contents = await readStat(unit);
            const current = decodeStat(contents.airconStat);
            const next = applyAction(current, item);
            try {
                await request(unit, 'setAirconStat', { airconId: unit.airconId, airconStat: encodeStat(next) });
                ownWrite[unit.airconId] = Date.now();
                return;
            } catch (e) {
                if (!e.refused || attempt === 2) throw e;
                // Abgelehnt: entweder sperrt gerade ein anderes Gerät (bis zu 60 s nach seinem
                // letzten Befehl) oder das Modul kennt unser Konto nicht mehr.
                const after = await readStat(unit);
                const lockedFor = (after.expires || 0) - Math.floor(Date.now() / 1000);
                if (lockedFor > 0 && lockedFor <= 70) {
                    eventLog(unit.name || unit.airconId, `Zeitplan wartet: Modul von einem anderen Gerät gesperrt (noch ${lockedFor} s)`);
                    await sleep((lockedFor + 1) * 1000);
                } else {
                    await request(unit, 'updateAccountInfo', { accountId: OPERATOR_ID, airconId: unit.airconId, remote: 0, timezone: 'Europe/Berlin' });
                }
            }
        }
    });
}

const minuteKey = d => `${dayKey(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
const isoDay = d => ((d.getDay() + 6) % 7) + 1; // Montag = 1 … Sonntag = 7

/**
 * Wöchentliche Termine mit everyWeeks > 1 laufen nur jede n-te Woche, gezählt ab der Woche von
 * weekAnchor (Datum eines Montags, JJJJ-MM-TT). Ohne diese Angaben: jede Woche.
 */
function activeInWeek(item, now) {
    const n = Number(item.everyWeeks) || 1;
    if (n <= 1 || typeof item.weekAnchor !== 'string') return true;
    const p = /^(\d{4})-(\d{2})-(\d{2})$/.exec(item.weekAnchor);
    if (!p) return true;
    const monday = d => { const x = new Date(d.getFullYear(), d.getMonth(), d.getDate()); x.setDate(x.getDate() - (isoDay(x) - 1)); return x; };
    const anchor = monday(new Date(Number(p[1]), Number(p[2]) - 1, Number(p[3])));
    const weeks = Math.round((monday(now).getTime() - anchor.getTime()) / (7 * 86400000));
    return ((weeks % n) + n) % n === 0;
}

/**
 * Jahreszeit des Haushalts (in der App vom Admin gewählt, in schedules.config als `season`):
 * winter, transition (Übergangszeit) oder summer. Ohne Wahl nach Kalender (Mai–Sep Sommer,
 * sonst Winter) – dieselbe Regel wie in der App.
 */
const SEASONS = ['winter', 'transition', 'summer'];
function householdSeason(config, now) {
    if (SEASONS.includes(config.season)) return config.season;
    const month = now.getMonth() + 1;
    return month >= 5 && month <= 9 ? 'summer' : 'winter';
}

/**
 * Jahreszeiten eines Zeitplans (`seasons`). Einträge von vor 0.5.72 haben keine und gelten als
 * Winter. Schnell-Timer (quick) laufen in jeder Jahreszeit.
 */
function inSeason(item, season) {
    if (item.quick) return true;
    const list = Array.isArray(item.seasons) ? item.seasons.filter(s => SEASONS.includes(s)) : [];
    return (list.length ? list : ['winter']).includes(season);
}

/**
 * Liefert den Schlüssel des fälligen Termins oder null. Ein wöchentlicher Termin ist ab seiner
 * Uhrzeit bis RETRY_WINDOW_MS danach fällig, ein einmaliger bis ONCE_GRACE_MS danach.
 */
function dueKey(item, now) {
    if (!item.enabled) return null;
    if (item.kind === 'weekly') {
        const m = /^(\d{2}):(\d{2})$/.exec(item.time || '');
        if (!m || !(item.days || []).includes(isoDay(now))) return null;
        if (!activeInWeek(item, now)) return null;
        const at = new Date(now.getFullYear(), now.getMonth(), now.getDate(), Number(m[1]), Number(m[2]));
        const diff = now.getTime() - at.getTime();
        if (diff >= 0 && diff <= RETRY_WINDOW_MS) return `${dayKey(now)}T${item.time}`;
        return null;
    }
    if (item.kind === 'once' && typeof item.at === 'number') {
        const diff = now.getTime() - item.at;
        if (diff >= 0 && diff <= ONCE_GRACE_MS) return `once:${item.at}`;
    }
    return null;
}

let scheduleRunning = false;
async function runSchedules() {
    if (scheduleRunning) return;
    scheduleRunning = true;
    try {
        await ensureJsonState(SCHEDULE_CONFIG_ID, 'KlimaPilot – Zeitpläne (werden von KlimaPilot geschrieben)', { enabled: true, items: [] });
        await ensureJsonState(SCHEDULE_STATUS_ID, 'KlimaPilot – Ergebnisse der Zeitpläne', {});
        const config = await readJson(SCHEDULE_CONFIG_ID) || {};
        if (config.enabled === false) return;
        const status = await readJson(SCHEDULE_STATUS_ID) || {};
        const now = new Date();
        const all = await units();
        const season = householdSeason(config, now);
        let changed = false;
        for (const item of config.items || []) {
            // Zeitpläne anderer Jahreszeiten laufen nicht (und werden in der App ausgeblendet).
            if (!inSeason(item, season)) continue;
            const key = dueKey(item, now);
            const previous = status[item.id];
            if (!key || (previous && previous.key === key && previous.done)) continue;
            const unit = all.find(u => u.airconId === item.airconId);
            const entry = { key, at: now.toISOString(), done: false, attempts: (previous && previous.key === key ? previous.attempts || 0 : 0) + 1 };
            if (!unit) {
                entry.result = 'Anlage ist ioBroker nicht bekannt';
            } else {
                const what = item.quick ? 'Schnell-Timer „Aus in …“' : `Zeitplan ${item.kind === 'once' ? 'einmalig' : item.time}`;
                const action = item.action === 'off' ? 'ausschalten' : `einschalten${item.profileName ? ` mit Profil „${item.profileName}“` : ''}`;
                try {
                    await execute(unit, item);
                    entry.result = 'ok';
                    entry.done = true;
                    eventLog(unit.name || unit.airconId, `${what}: ${action} – ausgeführt`);
                } catch (e) {
                    entry.result = e.message;
                    eventLog(unit.name || unit.airconId, `${what}: ${action} – fehlgeschlagen (Versuch ${entry.attempts}): ${e.message}`);
                    log(`KlimaPilot Zeitplan ${item.id} (Versuch ${entry.attempts}): ${e.message}`, 'warn');
                }
            }
            status[item.id] = entry;
            changed = true;
        }
        // Ergebnisse gelöschter Zeitpläne entfernen.
        const ids = new Set((config.items || []).map(i => i.id));
        for (const id of Object.keys(status)) if (!ids.has(id)) { delete status[id]; changed = true; }
        if (changed) await setStateAsync(SCHEDULE_STATUS_ID, JSON.stringify(status), true);
    } catch (e) {
        log(`KlimaPilot Zeitpläne: ${e.message}`, 'warn');
    } finally {
        scheduleRunning = false;
    }
}

// ---------------------------------------------------------------------------------------------
// Nur eine laufende Kopie: Jede Kopie trägt sich beim Start ein; nur die zuletzt gestartete arbeitet.
// Eine zweite Kopie (z. B. versehentlich doppelt angelegt) meldet sich einmal und pausiert.
// ---------------------------------------------------------------------------------------------

const RUN_ID = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
const SELF = `${typeof scriptName !== 'undefined' ? scriptName : '?'} (javascript.${typeof instance !== 'undefined' ? instance : '?'})`;
let duplicateReported = false;

async function claimOwnership() {
    await ensureJsonState(OWNER_ID, 'KlimaPilot – aktive Skriptkopie (intern)', {});
    await setStateAsync(OWNER_ID, JSON.stringify({ id: RUN_ID, script: SELF, startedAt: new Date().toISOString() }), true);
}

async function isOwner() {
    const owner = await readJson(OWNER_ID);
    if (owner && owner.id === RUN_ID) return true;
    if (!owner || !owner.id) {
        // Eintrag fehlt (z. B. gelöscht): diese Laufzeit übernimmt, alle anderen sehen danach eine fremde Kennung.
        await claimOwnership();
        return true;
    }
    // Dasselbe Skript wurde neu gestartet und die alte Laufzeit läuft noch kurz nach: kein Duplikat.
    if (!duplicateReported && owner.script !== SELF) {
        duplicateReported = true;
        log(`KlimaPilot: Eine andere Kopie dieses Skripts ist aktiv (${owner.script}). Diese Kopie ${SELF} pausiert – bitte entfernen.`, 'warn');
        try {
            if (await diagEnabled()) await diagLog('ioBroker-Skript', 'doppelte Kopie pausiert', `${SELF}; aktiv: ${owner.script}`);
        } catch (e) { /* nur Hinweis */ }
    }
    return false;
}

const whenOwner = task => async () => { if (await isOwner()) await task(); };

schedule(`*/${POLL_MINUTES} * * * *`, whenOwner(pollAllEnergy));
schedule('* * * * *', whenOwner(runSchedules));
schedule('* * * * *', whenOwner(async () => { await watchDiagnostics(); await probeAll(); }));
claimOwnership().then(async () => {
    eventLog('ioBroker-Skript', `gestartet (${SELF})`);
    // Gespeicherte Zustandswechsel vor der ersten Ablesung laden, sonst gilt die Dauer als unbekannt.
    await roomLoadTrack().catch(e => log(`KlimaPilot Raumtemperatur: ${e.message}`, 'warn'));
    watchDiagnostics();
    pollAllEnergy();
    roomStart().catch(e => log(`KlimaPilot Raumtemperatur: ${e.message}`, 'warn'));
});
runSchedules();
