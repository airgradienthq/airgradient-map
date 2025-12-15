import L, { LatLng, LatLngBounds } from 'leaflet';

export type MapTheme = 'light' | 'dark';

export const DEFAULT_MAP_VIEW_CONFIG = {
  zoom: 3,
  maxZoom: 18,
  minZoom: 2,
  center: <L.PointExpression>[47.21322, -1.559482],
  maxBoundsViscosity: 1,
  maxBounds: new LatLngBounds(new LatLng(-88, -180), new LatLng(88, 179)),
  wind_layer: false,
  map_theme: <MapTheme>'light',
  light_map_style_url: 'https://tiles.openfreemap.org/styles/bright',
  dark_map_style_url: 'https://tiles.openfreemap.org/styles/dark',
  embedded: false,
  debug: undefined // hide it at first
};
