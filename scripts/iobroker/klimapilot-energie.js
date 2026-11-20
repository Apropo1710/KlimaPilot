/*
 * KlimaPilot – Energieaufzeichnung für ioBroker (JavaScript-Adapter)
 *
 * Liest alle 5 Minuten den Energiezähler jeder Klimaanlage direkt am WF-RAC-Modul und
 * schreibt daraus Stunden- und Tageswerte fort. KlimaPilot zeigt sie in der Energieansicht an.
 *
 * Die Liste der Anlagen (IP, Geräte-ID) und den Strompreis schreibt KlimaPilot selbst nach
 *   0_userdata.0.KlimaPilot.energy.config
 * Die Messwerte stehen je Anlage in
 *   0_userdata.0.KlimaPilot.energy.units.<Geräte-ID>
 *
 * Hintergrund zum Zähler: Das Modul meldet den Verbrauch des aktuellen Laufs in 0,25-kWh-Schritten
 * (Segment 0x94/0x10). Er zählt, solange das Gerät läuft, hält den Wert im Aus-Zustand und beginnt
 * beim nächsten Einschalten wieder bei 0. Fällt der Wert, hat ein neuer Lauf begonnen.
 * Verloren geht dabei höchstens, was zwischen der letzten Abfrage und dem Ausschalten dazukam.
 *
 * Das Skript liest nur (getAirconStat); es schaltet nichts und braucht keinen Kontoplatz am Modul.
 */

const http = require('http');
const https = require('https');

const ROOT = '0_userdata.0.KlimaPilot.energy';
const CONFIG_ID = `${ROOT}.config`;
const POLL_MINUTES = 5;
const HOURLY_DAYS = 31;          // Stundenwerte werden 31 Tage aufbewahrt, Tageswerte unbegrenzt.
const PORT = 51443;
const TIMEOUT_MS = 10000;
const OPERATOR_ID = '98c7ec9c-1dac-4e5f-8d16-a145f58a38db';
const DEVICE_ID = 'klimapilot-iobroker';

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
                try {
                    const json = JSON.parse(data);
                    if (json.result !== 0) reject(new Error(`Modul antwortet mit result ${json.result}`));
                    else resolve(json.contents || {});
                } catch (e) { reject(new Error(`Ungültige Antwort: ${e.message}`)); }
            });
        });
        req.on('timeout', () => req.destroy(new Error('Zeitüberschreitung')));
        req.on('error', reject);
        req.end(body);
    });
}

/** Energie des aktuellen Laufs in kWh aus dem airconStat-Blob, oder null wenn nicht gemeldet. */
function runEnergyKwh(airconStat) {
    const d = Buffer.from(airconStat, 'base64');
    const start = d[18] * 4 + 21;
    const segStart = start + 19;
    const segEnd = d.length - 2;
    for (let i = segStart; i + 3 < segEnd; i += 4) {
        if (d[i] === 0x94 && d[i + 1] === 0x10) return ((d[i + 3] << 8) | d[i + 2]) * 0.25;
    }
    return null;
}

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

const schemes = {}; // zuletzt funktionierendes Verfahren je Anlage

async function readUnit(unit) {
    const first = schemes[unit.airconId] || unit.scheme || 'HTTP';
    const order = first === 'HTTPS' ? ['HTTPS', 'HTTP'] : ['HTTP', 'HTTPS'];
    let lastError;
    for (const scheme of order) {
        try {
            const contents = await post(scheme, unit.host, 'getAirconStat', { airconId: unit.airconId });
            schemes[unit.airconId] = scheme;
            if (contents.airconId && contents.airconId !== unit.airconId) {
                throw new Error(`An ${unit.host} antwortet eine andere Anlage (${contents.airconId})`);
            }
            return runEnergyKwh(contents.airconStat);
        } catch (e) { lastError = e; }
    }
    throw lastError;
}

async function pollUnit(unit) {
    const id = `${ROOT}.units.${unit.airconId}`;
    await ensureJsonState(id, `KlimaPilot Energie ${unit.name || unit.airconId}`, {});
    const data = Object.assign({ hourly: {}, daily: {}, totalKwh: 0 }, await readJson(id) || {});
    data.airconId = unit.airconId;
    data.name = unit.name || data.name || '';
    data.room = unit.room || data.room || '';
    const now = new Date();
    try {
        const value = await readUnit(unit);
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

let running = false;
async function pollAll() {
    if (running) return;
    running = true;
    try {
        await ensureJsonState(CONFIG_ID, 'KlimaPilot Energie – Konfiguration (wird von KlimaPilot geschrieben)', { units: [] });
        const config = await readJson(CONFIG_ID) || {};
        for (const unit of config.units || []) {
            if (!unit.airconId || !unit.host) continue;
            await pollUnit(unit);
        }
    } catch (e) {
        log(`KlimaPilot Energie: ${e.message}`, 'warn');
    } finally {
        running = false;
    }
}

schedule(`*/${POLL_MINUTES} * * * *`, pollAll);
pollAll();
