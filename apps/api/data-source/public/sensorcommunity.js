/**
 * @typedef {import('../../src/types/tasks/plugin-data-source.types').PluginDataSource} PluginDataSource
 * @typedef {import('../../src/types/tasks/plugin-data-source.types').PluginDataSourceLatestOutput} PluginDataSourceLatestOutput
 * @typedef {import('../../src/types/tasks/plugin-data-source.types').PluginDataSourceLocationOutput} PluginDataSourceLocationOutput
 * @typedef {import('../../src/types/shared/sensor-type').SensorType} SensorType
 */

const tzLookup = require('@photostructure/tz-lookup');

const LOCATION_ID_AVAILABLE = false; // we know InsertLatestMeasuresInput.locationId or not
const ALLOW_API_ACCESS = true;
const DATA_SOURCE_URL = 'https://sensor.community/';
const LICENSES = ['DbCL v1.0'];

const URL = 'https://data.sensor.community/static/v2/data.json';

function mergeSensorValues(records) {
  const merged = {};

  for (const r of records) {
    const key = `${r.location.id}`;

    if (!merged[key]) {
      merged[key] = {
        timestamp: r.timestamp,
        location: r.location,
        sensordatavalues: [...r.sensordatavalues]
      };
    } else {
      merged[key].sensordatavalues.push(...r.sensordatavalues);
    }
  }

  return Object.values(merged);
}

/**
 * @type {PluginDataSource['latest']}
 */
async function latest(args) {
  /** @type {PluginDataSourceLatestOutput} */
  const output = {
    success: false,
    count: 0,
    data: [],
    metadata: { locationIdAvailable: LOCATION_ID_AVAILABLE },
    error: null,
  };

  try {
    // NOTE: Do things here
    const response = await fetch(URL, {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' },
    });
    if (!response.ok) {
      output.error = `${response.status}: ${response.statusText}`;
      return output;
    }

    // Map data to expected structure
    const data = await response.json();
    const merged = mergeSensorValues(data);

    // Filter location.indoor = 0 and contain dust sensor
    const filtered = merged.filter(raw =>
      raw.location.indoor === 0 &&
      raw.sensordatavalues.some(v =>
        v.value_type === "P1" || v.value_type === "P2"
      )
    );

    const latestMeasuresInput = filtered.map(raw => ({
      locationReferenceId: raw.location.id,
      pm25: ((v) => v !== undefined ? Number(v) : null)(raw.sensordatavalues.find(d => d.value_type === 'P2')?.value),
      pm10: ((v) => v !== undefined ? Number(v) : null)(raw.sensordatavalues.find(d => d.value_type === 'P1')?.value),
      atmp: ((v) => v !== undefined ? Number(v) : null)(raw.sensordatavalues.find(d => d.value_type === 'temperature')?.value),
      rhum: ((v) => v !== undefined ? Number(v) : null)(raw.sensordatavalues.find(d => d.value_type === 'humidity')?.value),
      rco2: null,
      o3: null,
      no2: null,
      measuredAt: raw.timestamp,
    }));
    
    output.data = latestMeasuresInput;
    output.success = true;
    output.count = output.data.length || 0;
    return output;
  } catch (err) {
    output.error = err.message || String(err);
    return output;
  }
}

/**
 * @type {PluginDataSource['location']}
 */
async function location(args) {
  /** @type {PluginDataSourceLocationOutput} */
  const output = {
    success: false,
    count: 0,
    data: [],
    metadata: { 
      allowApiAccess: ALLOW_API_ACCESS,
      dataSourceUrl: DATA_SOURCE_URL
    },
    error: null,
  };

  try {
    // NOTE: Do things here
    const response = await fetch(URL, {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' },
    });
    if (!response.ok) {
      output.error = `${response.status}: ${response.statusText}`;
      return output;
    }

    // Map data to expected structure
    const data = await response.json();
    const merged = mergeSensorValues(data);

    const locationOwnerInput = merged.map(raw => ({
      ownerReferenceId: raw.location.id, // use location id instead
      locationReferenceId: raw.location.id,
      locationName: null,
      /** @type {SensorType} */
      sensorType: 'Small Sensor',
      timezone: tzLookup(raw.location.latitude, raw.location.longitude),
      coordinateLatitude: Number(raw.location.latitude),
      coordinateLongitude: Number(raw.location.longitude),
      licenses: LICENSES,
      provider: 'SensorCommunity',
    }));

    output.data = locationOwnerInput;
    output.success = true;
    output.count = output.data.length || 0;
    return output;
  } catch (err) {
    output.error = err.message || String(err);
    return output;
  }
}

module.exports = { latest, location };
