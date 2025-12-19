import maplibregl from 'maplibre-gl';
import { defineNuxtPlugin } from '#imports';

const RTL_TEXT_PLUGIN_URL = '/vendor/mapbox-gl-rtl-text.min.js';

let rtlPluginRegistered = false;

type MapLibreWithRTL = typeof maplibregl & {
  setRTLTextPlugin(
    pluginURL: string,
    callback?: (error?: Error | null) => void,
    lazy?: boolean
  ): void;
};

export default defineNuxtPlugin(() => {
  if (!import.meta.client || rtlPluginRegistered) {
    return;
  }

  if (typeof maplibregl?.setRTLTextPlugin !== 'function') {
    return;
  }

  try {
    (maplibregl as MapLibreWithRTL).setRTLTextPlugin(
      RTL_TEXT_PLUGIN_URL,
      error => {
        if (error) {
          console.error('Failed to load MapLibre RTL text plugin:', error);
        }
      },
      true
    );
    rtlPluginRegistered = true;
  } catch (error) {
    console.error('Failed to initialize MapLibre RTL text plugin:', error);
  }
});
