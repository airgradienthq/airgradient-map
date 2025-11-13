/**
 * @typedef {import('../../src/types/tasks/plugin-data-source.types').PluginDataSource} PluginDataSource
 * @typedef {import('../../src/types/tasks/plugin-data-source.types').PluginDataSourceOutput} PluginDataSourceOutput
 */

const URL = 'https://api.airgradient.com/public/api/v1/world/locations/measures/current';
const AG_DEFAULT_LICENSE = 'CC BY-SA 4.0';

/**
 * @type {PluginDataSource['latest']}
 */
async function latest(args) {
  /** @type {PluginDataSourceOutput} */
  let output = {
    success: false,
    count: 0,
    data: [],
    metadata: null,
    error: null,
  };

  try {
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

    output.success = true;
    output.count = latestMeasuresInput.length ? latestMeasuresInput.length : 0;
    output.data = latestMeasuresInput;
    output.metadata = {
      locationIdAvailable: false,
    };
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
  /** @type {PluginDataSourceOutput} */
  let output = {
    success: false,
    count: 0,
    data: [],
    metadata: null,
    error: null,
  };

  try {
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
      sensorType: 'Small Sensor',
      timezone: raw.timezone,
      coordinateLatitude: raw.latitude,
      coordinateLongitude: raw.longitude,
      licenses: [AG_DEFAULT_LICENSE],
      provider: 'AirGradient',
    }));

    output.success = true;
    output.count = locationOwnerInput.length ? locationOwnerInput.length : 0;
    output.data = locationOwnerInput;
    return output;
  } catch (err) {
    output.error = err.message || String(err);
    return output;
  }
}

module.exports = { latest, location };
