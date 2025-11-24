const path = require('path');

/**
 * Generic worker that can execute any plugin method
 * @param {Object} params
 * @param {string} params.folder - Plugin folder ('public' or 'private')
 * @param {string} params.fileName - Plugin filename (e.g., 'openaq.js')
 * @param {string} params.method - Method to call ('latest' or 'location')
 * @param {Object} params.args - Arguments to pass to the method
 * @returns {Promise<PluginDataSourceOutput>}
 */
module.exports = async ({ folder, fileName, method, args }) => {
  try {
    // Construct path to plugin file
    const filePath = path.join(__dirname, folder, fileName);

    // Dynamically load the plugin
    const plugin = require(filePath);

    var result;
    if (method == 'location') {
      result = await plugin.location(args);
    } else if (method === 'latest') {
      result = await plugin.latest(args);
    } else {
      throw new Error(`Method '${method}' not found in plugin ${fileName}`);
    }

    return result;
  } catch (error) {
    // Piscina will propagate this error back to the main thread
    throw new Error(`Plugin worker error (${fileName}.${method}): ${error.message}`);
  }
};
