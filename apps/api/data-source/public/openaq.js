/**
 * @typedef {import('../../src/types/tasks/plugin-data-source.types').PluginDataSource} PluginDataSource
 * @typedef {import('../../src/types/tasks/plugin-data-source.types').PluginDataSourceOutput} PluginDataSourceOutput
 */

const OPENAQ_PROVIDERS = [
  { sourceName: 'air4thai', id: 118 },
  { sourceName: 'airnow', id: 119 },
  { sourceName: 'eea', id: 70 },
  { sourceName: 'Australia - Queensland', id: 154 },
  { sourceName: 'Australia - Tasmania', id: 156 },
  { sourceName: 'Chile - SINCA', id: 164 },
  { sourceName: 'CPCB', id: 168 },
  { sourceName: 'canterbury-nz', id: 17 },
  { sourceName: 'korea-air', id: 69 },
  { sourceName: 'japan-soramame', id: 63 },
  { sourceName: 'Sinaica Mexico', id: 223 },
  { sourceName: 'Taiwan', id: 279 },
];

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
    const referenceIdToIdMap = args.referenceIdToIdMap;
    const referenceIdToIdMapLength = args.referenceIdToIdMapLength;

    let maxPages = -1;
    let pageCounter = 1;
    let matchCounter = 0;
    const latestMeasuresInput = [];

    // Loop until receive all latest measures from recorded location
    // Or last endpoint page is reached
    while (matchCounter < referenceIdToIdMapLength) {
      // Parameters '2' is pm2.5 parameter id
      const url = `https://api.openaq.org/v3/parameters/2/latest?limit=1000&page=${pageCounter}`;
      const response = await fetch(url, {
        method: 'GET',
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/json',
          'x-api-key': args.apiKey,
        },
      });
      if (response.status == 404) {
        //console.log('Requested page already empty for parameters endpoint');
        break;
      }
      if (!response.ok) {
        // TODO: What's the best way to do here?
        continue;
      }

      // Map each latest measures value to expected structure
      const data = await response.json();
      data.results.forEach(raw => {
        // Only keep latest measures from recorded location
        const locationReferenceId = raw.locationsId.toString();
        if (locationReferenceId in referenceIdToIdMap) {
          latestMeasuresInput.push({
            locationReferenceId: Number(locationReferenceId),
            locationId: referenceIdToIdMap[locationReferenceId],
            pm25: raw.value,
            pm10: null,
            atmp: null,
            rhum: null,
            rco2: null,
            o3: null,
            no2: null,
            measuredAt: raw.datetime.utc,
          });
          matchCounter = matchCounter + 1;
        }
      });

      if (maxPages === -1) {
        const found = Number(data.meta.found);
        const limit = Number(data.meta.limit);
        if (!isNaN(found) && !isNaN(limit) && limit > 0) {
          maxPages = Math.ceil(found / limit);
        }
      }
      if (pageCounter == maxPages) {
        //console.log('Reached the last page of OpenAQ latest data.');
        break;
      }

      pageCounter = pageCounter + 1;
    }

    if (matchCounter < referenceIdToIdMapLength) {
      //console.log(
      //  `Total OpenAQ locations that not match ${referenceIdToIdMapLength - matchCounter}`,
      //);
    }

    // Return results
    output.success = true;
    output.count = latestMeasuresInput.length;
    output.data = latestMeasuresInput;
    output.metadata = {
      // Since actual locationId already available, then just re-use it for later
      locationIdAvailable: true,
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
    const locationOwnerInput = [];
    for (let i = 0; i < OPENAQ_PROVIDERS.length; i++) {
      let finish = false;
      let pageCounter = 1;
      let total = 0;
      const providerId = OPENAQ_PROVIDERS[i].id;

      // Loop every locations available for each provider
      while (finish === false) {
        // Retrieve every 1000 data maximum, so it will sync to database every 500 row
        const url = `https://api.openaq.org/v3/locations?monitor=true&page=${pageCounter}&limit=1000&providers_id=${providerId}`;
        const response = await fetch(url, {
          method: 'GET',
          headers: {
            Accept: 'application/json',
            'Content-Type': 'application/json',
            'x-api-key': args.apiKey,
          },
        });
        if (!response.ok) {
          // TODO: What's the best way to do here?
          continue;
        }

        // Map data to expected structure and append the result
        const data = await response.json();
        data.results.forEach(raw => {
          locationOwnerInput.push({
            ownerReferenceId: raw.owner.id,
            ownerName: raw.owner.name,
            locationReferenceId: raw.id,
            locationName: raw.name,
            sensorType: 'Reference',
            timezone: raw.timezone,
            coordinateLatitude: raw.coordinates.latitude,
            coordinateLongitude: raw.coordinates.longitude,
            licenses: (raw.licenses ?? []).map(license => license.name), // Check if its null first
            provider: raw.provider.name,
          });
        });

        // Sometimes `found` field is a string
        const t = typeof data.meta.found;
        if (t === 'number') {
          let foundInt = Number(data.meta.found);
          total = total + Number(data.meta.found);

          // Check if this batch is the last batch
          if (foundInt <= data.meta.limit) {
            finish = true;
            //console.log(`ProviderId ${providerId} loop finish with total page ${pageCounter}`);
          }
        } else {
          total = total + data.meta.limit;
        }

        pageCounter = pageCounter + 1;
      }
    }

    // Return results
    output.success = true;
    output.count = locationOwnerInput.length;
    output.data = locationOwnerInput;
    return output;
  } catch (err) {
    output.error = err.message || String(err);
    return output;
  }
}

module.exports = { latest, location };
