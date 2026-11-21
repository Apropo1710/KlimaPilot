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
 *
 * 3. Diagnose (nur wenn in KlimaPilot eingeschaltet): prüft jede Minute die lokale Erreichbarkeit
 *    und protokolliert Ausfälle in 0_userdata.0.KlimaPilot.diagnostics.log.
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
// Thermistortabelle Außentemperatur des WF-RAC-Moduls (Index = Rohwert, aus pywfrac, MIT; wie TempTables.kt).
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
    for (let i = 0; i + 3 < segments.length; i += 4) {
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
        const value = stat.runEnergyKwh;
        if (value === null) throw new Error('Anlage meldet keinen Energiezähler');
        // Betriebsstunden (für die Filter-Erinnerung in der App) und Außentemperatur (Mittel je Stunde und Tag).
        if (stat.power) data.runMinutes = (data.runMinutes || 0) + POLL_MINUTES;
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
    } catch (e) {
        log(`KlimaPilot Energie: ${e.message}`, 'warn');
    } finally {
        energyRunning = false;
    }
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
    entries.push({ at: new Date().toISOString(), source: 'ioBroker', subject, event, detail: detail || '' });
    await setStateAsync(DIAG_LOG_ID, JSON.stringify({ entries: entries.slice(-DIAG_MAX_ENTRIES) }), true);
}

const minutesSince = since => Math.max(1, Math.round((Date.now() - since) / 60000));

/** Merkt sich die Erreichbarkeit einer Anlage und protokolliert Wechsel, wenn die Diagnose an ist. */
async function diagReach(unit, ok, error) {
    const previous = reach[unit.airconId];
    const now = Date.now();
    if (previous && previous.ok === ok && (ok || previous.error === error)) return;
    reach[unit.airconId] = { ok, since: previous && previous.ok === ok ? previous.since : now, error };
    try {
        if (!(await diagEnabled())) return;
        const name = unit.name || unit.airconId;
        if (!previous) {
            if (!ok) await diagLog(name, 'nicht erreichbar', error);
        } else if (previous.ok !== ok) {
            if (ok) await diagLog(name, 'wieder erreichbar', `nach ${minutesSince(previous.since)} min`);
            else await diagLog(name, 'nicht erreichbar', error);
        } else if (!ok) {
            await diagLog(name, 'anderer Fehler', error);
        }
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
                await serial(unit, () => readStat(unit));
                await diagReach(unit, true);
            } catch (e) {
                await diagReach(unit, !!e.refused, e.message);
            }
        }
    } catch (e) {
        log(`KlimaPilot Diagnose: ${e.message}`, 'warn');
    } finally {
        probing = false;
    }
}

let diagWasEnabled = null;
async function watchDiagnostics() {
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
                return;
            } catch (e) {
                if (!e.refused || attempt === 2) throw e;
                // Abgelehnt: entweder sperrt gerade ein anderes Gerät (bis zu 60 s nach seinem
                // letzten Befehl) oder das Modul kennt unser Konto nicht mehr.
                const after = await readStat(unit);
                const lockedFor = (after.expires || 0) - Math.floor(Date.now() / 1000);
                if (lockedFor > 0 && lockedFor <= 70) {
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
 * Liefert den Schlüssel des fälligen Termins oder null. Ein wöchentlicher Termin ist ab seiner
 * Uhrzeit bis RETRY_WINDOW_MS danach fällig, ein einmaliger bis ONCE_GRACE_MS danach.
 */
function dueKey(item, now) {
    if (!item.enabled) return null;
    if (item.kind === 'weekly') {
        const m = /^(\d{2}):(\d{2})$/.exec(item.time || '');
        if (!m || !(item.days || []).includes(isoDay(now))) return null;
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
        let changed = false;
        for (const item of config.items || []) {
            const key = dueKey(item, now);
            const previous = status[item.id];
            if (!key || (previous && previous.key === key && previous.done)) continue;
            const unit = all.find(u => u.airconId === item.airconId);
            const entry = { key, at: now.toISOString(), done: false, attempts: (previous && previous.key === key ? previous.attempts || 0 : 0) + 1 };
            if (!unit) {
                entry.result = 'Anlage ist ioBroker nicht bekannt';
            } else {
                try {
                    await execute(unit, item);
                    entry.result = 'ok';
                    entry.done = true;
                } catch (e) {
                    entry.result = e.message;
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

schedule(`*/${POLL_MINUTES} * * * *`, pollAllEnergy);
schedule('* * * * *', runSchedules);
schedule('* * * * *', async () => { await watchDiagnostics(); await probeAll(); });
watchDiagnostics();
pollAllEnergy();
runSchedules();
