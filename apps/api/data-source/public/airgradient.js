const URL = 'https://api.airgradient.com/public/api/v1/world/locations/measures/current';
const AG_DEFAULT_LICENSE = 'CC BY-SA 4.0';

async function latest() {
  try {
    const response = await fetch(URL, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        Origin: 'https://airgradient.com',
      },
    });
    if (!response.ok) {
      return {
        success: false,
        count: 0,
        data: [],
        error: `${response.status}: ${response.statusText}`,
      };
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

    const metadata = {
      locationIdAvailable: false,
    };

    return {
      success: true,
      count: latestMeasuresInput.length ? latestMeasuresInput.length : 0,
      data: latestMeasuresInput,
      metadata: metadata,
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

async function location() {
  try {
    const response = await fetch(URL, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        Origin: 'https://airgradient.com',
      },
    });
    if (!response.ok) {
      return {
        success: false,
        count: 0,
        data: [],
        error: `${response.status}: ${response.statusText}`,
      };
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

    return {
      success: true,
      count: locationOwnerInput.length ? locationOwnerInput.length : 0,
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
