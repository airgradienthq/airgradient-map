/**
 * @typedef {import('../../src/types/tasks/plugin-data-source.types').PluginDataSource} PluginDataSource
 * @typedef {import('../../src/types/tasks/plugin-data-source.types').PluginDataSourceOutput} PluginDataSourceOutput
 */

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
    // NOTE: Do things here

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
    // NOTE: Do things here

    return output;
  } catch (err) {
    output.error = err.message || String(err);
    return output;
  }
}

module.exports = { latest, location };
