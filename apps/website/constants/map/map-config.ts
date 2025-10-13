import L, { LatLng, LatLngBounds } from 'leaflet';

export const DEFAULT_MAP_VIEW_CONFIG = {
  zoom: 3,
  maxZoom: 18,
  minZoom: 2,
  center: <L.PointExpression>[47.21322, -1.559482],
  maxBoundsViscosity: 1,
  maxBounds: new LatLngBounds(new LatLng(-88, -230), new LatLng(88, 230)),
  wind_layer: false,
  light_map_style_url: 'https://tiles.openfreemap.org/styles/liberty',
  dark_map_style_url: 'https://tiles.openfreemap.org/styles/positron'
};
