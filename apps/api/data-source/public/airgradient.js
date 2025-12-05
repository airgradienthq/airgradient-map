/**
 * @typedef {import('../../src/types/tasks/plugin-data-source.types').PluginDataSource} PluginDataSource
 * @typedef {import('../../src/types/tasks/plugin-data-source.types').PluginDataSourceLatestOutput} PluginDataSourceLatestOutput
 * @typedef {import('../../src/types/tasks/plugin-data-source.types').PluginDataSourceLocationOutput} PluginDataSourceLocationOutput
 * @typedef {import('../../src/types/shared/sensor-type').SensorType} SensorType
 */

const LOCATION_ID_AVAILABLE = false; // we know InsertLatestMeasuresInput.locationId or not
const ALLOW_API_ACCESS = true;
const DATA_SOURCE_URL = 'https://www.airgradient.com';

const URL = 'https://api.airgradient.com/public/api/v1/world/locations/measures/current';
const AG_DEFAULT_LICENSE = 'CC BY-SA 4.0';

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
      headers: {
        'Content-Type': 'application/json',
        Origin: 'https://airgradient.com',
      },
    });
    if (!response.ok) {
      output.error = `${response.status}: ${response.statusText}`;
      return output;
    }

    // Map data to expected structure
    const data = await response.json();
    const latestMeasuresInput = data.map(raw => ({
      locationReferenceId: raw.locationId,
      pm25: raw.pm02,
      pm10: raw.pm10,
      atmp: raw.atmp,
      rhum: raw.rhum,
      rco2: raw.rco2,
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
      headers: {
        'Content-Type': 'application/json',
        Origin: 'https://airgradient.com',
      },
    });
    if (!response.ok) {
      output.error = `${response.status}: ${response.statusText}`;
      return output;
    }

    // Map data to expected structure
    const data = await response.json();
    const locationOwnerInput = data.map(raw => ({
      ownerReferenceId: raw.placeId,
      ownerName: raw.publicContributorName,
      ownerUrl: raw.publicPlaceUrl,
      locationReferenceId: raw.locationId,
      locationName: raw.publicLocationName,
      /** @type {SensorType} */
      sensorType: 'Small Sensor',
      timezone: raw.timezone,
      coordinateLatitude: raw.latitude,
      coordinateLongitude: raw.longitude,
      licenses: [AG_DEFAULT_LICENSE],
      provider: 'AirGradient',
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
