import type { AGMapClusterData, AGMapLocationData } from '~/types';
import type { FeatureCollection } from 'geojson';

/**
 * Converts an array of AGMapLocationData or AGMapClusterData to a GeoJSON FeatureCollection.
 *
 * @param {Array<AGMapLocationData | AGMapClusterData>} data - The array of map location or cluster data to convert.
 * @returns {FeatureCollection} A GeoJSON FeatureCollection representing the input data.
 */
export function convertToGeoJSON(
  data: Array<AGMapLocationData | AGMapClusterData>
): FeatureCollection {
  return {
    type: 'FeatureCollection',
    features: data.map((item: AGMapLocationData | AGMapClusterData) => {
      return {
        type: 'Feature',
        properties: {
          type: item.type,
          sensorsCount: 'sensorsCount' in item ? item.sensorsCount : null,
          value: item.value,
          locationId: 'locationId' in item ? item.locationId : null,
          locationName: 'locationName' in item ? item.locationName : null,
          sensorType: 'sensorType' in item ? item.sensorType : null
        },
        geometry: {
          type: 'Point',
          coordinates: [item.longitude, item.latitude]
        }
      };
    })
  };
}
