/**
 * @typedef {import('../../src/types/tasks/plugin-data-source.types').PluginDataSource} PluginDataSource
 * @typedef {import('../../src/types/tasks/plugin-data-source.types').PluginDataSourceLatestOutput} PluginDataSourceLatestOutput
 * @typedef {import('../../src/types/tasks/plugin-data-source.types').PluginDataSourceLocationOutput} PluginDataSourceLocationOutput
 * @typedef {import('../../src/types/shared/sensor-type').SensorType} SensorType
 */

const LOCATION_ID_AVAILABLE = true; // we know InsertLatestMeasuresInput.locationId or not
const ALLOW_API_ACCESS = true;
const DATA_SOURCE_URL = '';

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
    
    // output.data = ?;
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

    // output.data = ?;
    output.success = true;
    output.count = output.data.length || 0;
    return output;
  } catch (err) {
    output.error = err.message || String(err);
    return output;
  }
}

module.exports = { latest, location };
