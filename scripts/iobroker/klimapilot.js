/*
 * KlimaPilot – ioBroker-Skript (JavaScript-Adapter)
 *
 * Wird von KlimaPilot eingerichtet und aktualisiert (Einstellungen → ioBroker-Skript).
 * Es erledigt zwei Dinge, auch wenn kein Smartphone zu Hause oder die App geschlossen ist:
 *
 * 1. Energieaufzeichnung: liest alle 5 Minuten den Energiezähler jeder Klimaanlage und schreibt
 *    Stunden- und Tageswerte nach 0_userdata.0.KlimaPilot.energy.units.<Geräte-ID>.
 *    Der Zähler meldet den Verbrauch des aktuellen Laufs in 0,25-kWh-Schritten (Segment 0x94/0x10)
 *    und beginnt beim Einschalten wieder bei 0; fällt der Wert, hat ein neuer Lauf begonnen.
 *
 * 2. Zeitpläne: prüft jede Minute die Zeitpläne aus 0_userdata.0.KlimaPilot.schedules.config
 *    und schaltet fällige Anlagen. Ergebnisse stehen in 0_userdata.0.KlimaPilot.schedules.status.
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
const POLL_MINUTES = 5;
const HOURLY_DAYS = 31;          // Stundenwerte werden 31 Tage aufbewahrt, Tageswerte unbegrenzt.
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
    for (let i = 0; i + 3 < segments.length; i += 4) {
        if (segments[i] === 0x94 && segments[i + 1] === 0x10) energy = ((segments[i + 3] << 8) | segments[i + 2]) * 0.25;
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
        const value = decodeStat(contents.airconStat).runEnergyKwh;
        if (value === null) throw new Error('Anlage meldet keinen Energiezähler');
        if (typeof data.lastRunKwh === 'number') {
            // Fällt der Zähler, hat ein neuer Lauf begonnen: dann zählt der neue Wert ganz.
            const delta = value >= data.lastRunKwh ? value - data.lastRunKwh : value;
            if (delta > 0) {
                const h = hourKey(now), d = dayKey(now);
                data.hourly[h] = round((data.hourly[h] || 0) + delta);
                data.daily[d] = round((data.daily[d] || 0) + delta);
                data.totalKwh = round((data.totalKwh || 0) + delta);
            }
        } else {
            // Erste Ablesung: Ausgangswert merken. Was davor verbraucht wurde, ist zeitlich nicht zuzuordnen.
            data.recordingSince = now.toISOString();
        }
        data.lastRunKwh = value;
        data.lastPollAt = now.toISOString();
        delete data.lastError;
    } catch (e) {
        data.lastError = `${now.toISOString()} ${e.message}`;
    }
    const oldest = hourKey(new Date(now.getTime() - HOURLY_DAYS * 24 * 3600 * 1000));
    for (const key of Object.keys(data.hourly)) if (key < oldest) delete data.hourly[key];
    await setStateAsync(id, JSON.stringify(data), true);
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
pollAllEnergy();
runSchedules();
