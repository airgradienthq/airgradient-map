<template>
  <div></div>
</template>

<script setup lang="ts">
  import { watch, onUnmounted, onMounted, ref, nextTick } from 'vue';
  import L from 'leaflet';
  import { useRuntimeConfig } from 'nuxt/app';

  import {
    VELOCITY_COLOR_SCALE_DARK,
    VELOCITY_COLOR_SCALE_LIGHT
  } from '~/constants/map/wind-layer';
  import { transformToLeafletVelocityFormat } from '~/utils/wind-data-transformer';
  import { createVueDebounce } from '~/utils/debounce';
  import type { MapTheme } from '~/constants';

  interface Props {
    map: L.Map | null;
    enabled: boolean;
    mapTheme: MapTheme;
  }

  const props = defineProps<Props>();
  const config = useRuntimeConfig();

  const emit = defineEmits<{
    loadingChange: [loading: boolean];
  }>();

  let velocityLayer: any = null;
  let windData: any = null;
  let libraryLoaded = false;
  let isInitializing = false;
  const isLoadingWindData = ref(false);

  // Store handler references for proper cleanup
  let moveendHandler: (() => void) | null = null;
  let zoomendHandler: (() => void) | null = null;

  // Debounced reload to prevent multiple API calls on zoom/pan
  const reloadWindLayerDebounced = createVueDebounce(reloadWindLayerForCurrentView, 500);
  const normalizeLongitude = (lon: number): number => {
    let normalized = ((lon + 180) % 360) - 180;
    if (normalized < -180) {
      normalized += 360;
    } else if (normalized > 180) {
      normalized -= 360;
    }
    return normalized;
  };
  const getColorScaleForTheme = () =>
    props.mapTheme === 'dark' ? VELOCITY_COLOR_SCALE_DARK : VELOCITY_COLOR_SCALE_LIGHT;

  onMounted(async () => {
    if (process.client) {
      try {
        await import('leaflet-velocity');
        libraryLoaded = true;

        await nextTick();

        if (props.enabled && props.map) {
          await loadAndShowWindLayer();
          setupMapEventListeners();
        }
      } catch (error) {
        console.error('Failed to load leaflet-velocity library:', error);
      }
    }
  });

  watch(
    () => props.enabled,
    async enabled => {
      if (!libraryLoaded || isInitializing) {
        return;
      }

      if (enabled && props.map) {
        await loadAndShowWindLayer();
        setupMapEventListeners();
      } else {
        removeWindLayer();
        removeMapEventListeners();
      }
    },
    { immediate: false }
  );

  watch(
    () => props.map,
    async (newMap, oldMap) => {
      if (!libraryLoaded) return;

      if (oldMap) {
        removeMapEventListeners();
        if (velocityLayer) {
          removeWindLayer();
        }
      }

      if (newMap && props.enabled) {
        setTimeout(async () => {
          await loadAndShowWindLayer();
          setupMapEventListeners();
        }, 100);
      }
    }
  );

  watch(
    () => props.mapTheme,
    () => {
      if (!libraryLoaded || !props.enabled) return;
      if (!props.map) return;
      if (!windData) return;

      removeWindLayer();
      addVelocityLayerToMap();
    }
  );

  watch(isLoadingWindData, loading => {
    emit('loadingChange', loading);
  });

  function removeWindLayer() {
    if (velocityLayer && props.map) {
      props.map.removeLayer(velocityLayer);
      velocityLayer = null;
    }
  }

  function hideWindLayer() {
    if (velocityLayer && props.map) {
      const canvas = velocityLayer?._canvasLayer?._canvas;
      if (canvas) {
        canvas.style.opacity = '0';
      }
    }
  }

  async function reloadWindLayerForCurrentView() {
    if (!props.map || !props.enabled) return;

    try {
      // Reload wind data for current bounds
      await loadWindData(true);

      if (!windData) return;

      // Remove old layer
      removeWindLayer();

      await nextTick();

      addVelocityLayerToMap();
    } catch (error) {
      console.error('Error reloading wind layer:', error);
    }
  }

  function addVelocityLayerToMap() {
    velocityLayer = (L as any).velocityLayer({
      displayValues: true,
      displayOptions: {
        velocityType: 'Wind',
        position: 'bottomleft',
        emptyString: 'No wind data',
        showCardinal: true,
        speedUnit: 'm/s',
        directionString: 'Direction',
        speedString: 'Speed',
        angleConvention: 'bearingCW'
      },
      data: windData,
      minVelocity: 0,
      maxVelocity: 15,
      velocityScale: 0.015,
      opacity: 0.85,
      colorScale: getColorScaleForTheme()
    });

    if (velocityLayer && props.map && props.enabled) {
      velocityLayer.addTo(props.map);
    }
  }

  function setupMapEventListeners() {
    if (!props.map) return;

    // Remove any existing listeners first to prevent duplicates
    removeMapEventListeners();

    // Hide wind layer when map starts moving to avoid visual lag
    props.map.on('movestart', hideWindLayer);
    props.map.on('zoomstart', hideWindLayer);

    // Store handler references so we can remove only our specific listeners
    // Reload wind data when map stops moving (debounced to prevent multiple requests)
    moveendHandler = () => {
      reloadWindLayerDebounced();
    };
    zoomendHandler = () => {
      reloadWindLayerDebounced();
    };

    props.map.on('moveend', moveendHandler);
    props.map.on('zoomend', zoomendHandler);
  }

  function removeMapEventListeners() {
    if (!props.map) return;

    props.map.off('movestart', hideWindLayer);
    props.map.off('zoomstart', hideWindLayer);

    // Remove only the specific handlers we added, preserving other listeners
    if (moveendHandler) {
      props.map.off('moveend', moveendHandler);
      moveendHandler = null;
    }
    if (zoomendHandler) {
      props.map.off('zoomend', zoomendHandler);
      zoomendHandler = null;
    }
  }

  async function loadWindData(forceReload = false) {
    if (windData && !forceReload) {
      return;
    }

    if (!props.map) {
      return;
    }

    isLoadingWindData.value = true;

    try {
      // Get map bounds
      const bounds = props.map.getBounds();
      const rawXmin = bounds.getWest();
      const rawXmax = bounds.getEast();
      const ymin = bounds.getSouth();
      const ymax = bounds.getNorth();

      let xmin = normalizeLongitude(rawXmin);
      let xmax = normalizeLongitude(rawXmax);

      /**
       * Detect antimeridian issues in two scenarios:
       * 1. Leaflet reports west > east (viewport already wrapped).
       * 2. The view is close to +/-180° even if still monotonic. Leaflet will
       *    clamp bounds to [-180, 180], so when the seam is visible at either
       *    edge we proactively fetch the global slice to avoid partial grids.
       */
      const crossesAntimeridian = xmin > xmax || rawXmax > 180 || rawXmin < -180;
      const seamMargin = 150; // degrees from +/-180 at which we switch to global
      const nearWorldEdge = rawXmax > seamMargin || rawXmin < -seamMargin;

      if (crossesAntimeridian || nearWorldEdge) {
        console.debug('Wind layer: viewport near antimeridian, requesting global data', {
          rawXmin,
          rawXmax,
          normalizedXmin: xmin,
          normalizedXmax: xmax
        });
        xmin = -180;
        xmax = 180;
      }

      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 50000);

      const apiUrl = config.public.apiUrl as string;

      const url = `${apiUrl}/wind-data/current?xmin=${xmin}&xmax=${xmax}&ymin=${ymin}&ymax=${ymax}`;

      const res = await fetch(url, {
        signal: controller.signal
      });

      clearTimeout(timeoutId);

      if (!res.ok) {
        throw new Error(`HTTP ${res.status}: ${res.statusText}`);
      }

      const apiData = await res.json();

      if (!apiData || !apiData.header || !apiData.data) {
        throw new Error('Invalid wind data format');
      }

      // Transform API format to leaflet-velocity format
      windData = transformToLeafletVelocityFormat(apiData);

      // Validate transformed data structure matches what leaflet-velocity expects
      if (!Array.isArray(windData) || windData.length !== 2) {
        throw new Error(
          `Invalid wind data: expected array of 2 components, got ${Array.isArray(windData) ? windData.length : typeof windData}`
        );
      }

      if (!windData[0] || !windData[0].header || !windData[0].data) {
        throw new Error('Invalid wind data: U-component missing header or data');
      }

      if (!windData[1] || !windData[1].header || !windData[1].data) {
        throw new Error('Invalid wind data: V-component missing header or data');
      }

      if (!Array.isArray(windData[0].data) || !Array.isArray(windData[1].data)) {
        throw new Error('Invalid wind data: data must be arrays');
      }
    } catch (error) {
      console.error('Wind data load failed:', error);
      windData = null;
      throw error;
    } finally {
      isLoadingWindData.value = false;
    }
  }

  async function loadAndShowWindLayer() {
    if (isInitializing) {
      return;
    }

    if (!props.map) {
      return;
    }

    if (!libraryLoaded || !(L as any).velocityLayer) {
      return;
    }

    if (!props.map.getContainer()) {
      return;
    }

    isInitializing = true;

    try {
      removeWindLayer();

      // Force reload to ensure wind data matches current map bounds
      // This is especially important when the layer was disabled and map was moved
      await loadWindData(true);

      if (!windData) {
        return;
      }

      await nextTick();

      if (!props.map || !props.enabled) {
        return;
      }

      // Final validation before passing to leaflet-velocity
      if (!Array.isArray(windData) || windData.length !== 2) {
        throw new Error(
          `Wind data must be array of 2 components, got: ${Array.isArray(windData) ? windData.length : typeof windData}`
        );
      }

      if (!windData[0] || !windData[0].data || !windData[1] || !windData[1].data) {
        throw new Error('Wind data components must have data arrays');
      }

      addVelocityLayerToMap();
    } catch (error) {
      console.error('Error creating/adding velocity layer:', error);
      velocityLayer = null;
    } finally {
      isInitializing = false;
    }
  }

  onUnmounted(() => {
    removeMapEventListeners();
    removeWindLayer();
    windData = null;
    libraryLoaded = false;
    isInitializing = false;
    moveendHandler = null;
    zoomendHandler = null;
  });
</script>
