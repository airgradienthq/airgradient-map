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

async function latest() {
  return {
    success: false,
    count: 0,
    data: null,
    error: '',
  };
}

async function location(apiKey) {
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
            'x-api-key': apiKey,
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
            console.log(`ProviderId ${providerId} loop finish with total page ${pageCounter}`);
          }
        } else {
          total = total + data.meta.limit;
        }

        pageCounter = pageCounter + 1;
      }
    }

    return {
      success: true,
      count: locationOwnerInput.length,
      data: locationOwnerInput,
      error: null,
    };
  } catch (err) {
    return {
      success: false,
      count: 0,
      data: [],
      error: err.message || String(err),
    };
  }
}

module.exports = { latest, location };
