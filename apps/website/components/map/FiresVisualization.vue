<template>
  <div></div>
</template>

<script setup lang="ts">
  import { watch, onUnmounted, onMounted, ref, nextTick } from 'vue';
  import L from 'leaflet';
  import { useRuntimeConfig } from 'nuxt/app';

  import {
    FIRE_CONFIDENCE_COLORS,
    calculateFireMarkerSize,
    FIRES_QUERY_DEFAULTS
  } from '~/constants/map/fires-layer';
  import { createVueDebounce } from '~/utils/debounce';

  interface Props {
    map: L.Map | null;
    enabled: boolean;
  }

  const props = defineProps<Props>();
  const config = useRuntimeConfig();

  const emit = defineEmits<{
    loadingChange: [loading: boolean];
  }>();

  let firesLayer: L.GeoJSON | null = null;
  let firesData: any = null;
  let isInitializing = false;
  const isLoadingFiresData = ref(false);

  // Store handler references for proper cleanup
  let moveendHandler: (() => void) | null = null;
  let zoomendHandler: (() => void) | null = null;

  // Debounced reload to prevent multiple API calls on zoom/pan
  const reloadFiresLayerDebounced = createVueDebounce(reloadFiresLayerForCurrentView, 500);

  onMounted(async () => {
    if (process.client) {
      await nextTick();

      if (props.enabled && props.map) {
        await loadAndShowFiresLayer();
        setupMapEventListeners();
      }
    }
  });

  watch(
    () => props.enabled,
    async enabled => {
      if (isInitializing) {
        return;
      }

      if (enabled && props.map) {
        await loadAndShowFiresLayer();
        setupMapEventListeners();
      } else {
        removeFiresLayer();
        removeMapEventListeners();
      }
    },
    { immediate: false }
  );

  watch(
    () => props.map,
    async (newMap, oldMap) => {
      if (oldMap) {
        removeMapEventListeners();
        if (firesLayer) {
          removeFiresLayer();
        }
      }

      if (newMap && props.enabled) {
        setTimeout(async () => {
          await loadAndShowFiresLayer();
          setupMapEventListeners();
        }, 100);
      }
    }
  );

  watch(isLoadingFiresData, loading => {
    emit('loadingChange', loading);
  });

  function removeFiresLayer() {
    if (firesLayer && props.map) {
      props.map.removeLayer(firesLayer);
      firesLayer = null;
    }
  }

  function hideFiresLayer() {
    if (firesLayer && props.map) {
      props.map.removeLayer(firesLayer);
    }
  }

  async function reloadFiresLayerForCurrentView() {
    if (!props.map || !props.enabled) return;

    try {
      // Reload fires data for current bounds
      await loadFiresData(true);

      if (!firesData) return;

      // Remove old layer
      removeFiresLayer();

      await nextTick();

      addFiresLayerToMap();
    } catch (error) {
      console.error('Error reloading fires layer:', error);
    }
  }

  function createFireMarker(feature: any, latlng: L.LatLng): L.CircleMarker {
    const props = feature.properties;
    const confidence = props.confidence as 'low' | 'nominal' | 'high';
    const frp = props.frp || 0;

    // Calculate marker size based on FRP
    const radius = calculateFireMarkerSize(frp);

    // Get color based on confidence level
    const fillColor = FIRE_CONFIDENCE_COLORS[confidence] || FIRE_CONFIDENCE_COLORS.nominal;

    const marker = L.circleMarker(latlng, {
      radius,
      fillColor,
      color: '#fff',
      weight: 1,
      opacity: 0.9,
      fillOpacity: 0.7,
    });

    // Create popup with fire details
    const popupContent = `
      <div style="font-size: 12px; line-height: 1.4;">
        <strong>Fire Detection</strong><br/>
        <strong>Date:</strong> ${props.acq_date} ${props.acq_time}<br/>
        <strong>Confidence:</strong> ${props.confidence}<br/>
        <strong>FRP:</strong> ${frp.toFixed(1)} MW<br/>
        <strong>Satellite:</strong> ${props.satellite}<br/>
        <strong>Time:</strong> ${props.daynight === 'D' ? 'Day' : 'Night'}<br/>
        <strong>Coordinates:</strong> ${props.latitude.toFixed(4)}, ${props.longitude.toFixed(4)}
      </div>
    `;

    marker.bindPopup(popupContent);

    return marker;
  }

  function addFiresLayerToMap() {
    if (!firesData || !props.map || !props.enabled) {
      return;
    }

    firesLayer = L.geoJSON(firesData, {
      pointToLayer: createFireMarker,
    });

    firesLayer.addTo(props.map);
  }

  function setupMapEventListeners() {
    if (!props.map) return;

    // Remove any existing listeners first to prevent duplicates
    removeMapEventListeners();

    // Hide fires layer when map starts moving to avoid visual lag
    props.map.on('movestart', hideFiresLayer);
    props.map.on('zoomstart', hideFiresLayer);

    // Store handler references so we can remove only our specific listeners
    // Reload fires data when map stops moving (debounced to prevent multiple requests)
    moveendHandler = () => {
      reloadFiresLayerDebounced();
    };
    zoomendHandler = () => {
      reloadFiresLayerDebounced();
    };

    props.map.on('moveend', moveendHandler);
    props.map.on('zoomend', zoomendHandler);
  }

  function removeMapEventListeners() {
    if (!props.map) return;

    props.map.off('movestart', hideFiresLayer);
    props.map.off('zoomstart', hideFiresLayer);

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

  async function loadFiresData(forceReload = false) {
    if (firesData && !forceReload) {
      return;
    }

    if (!props.map) {
      return;
    }

    isLoadingFiresData.value = true;

    try {
      // Get map bounds
      const bounds = props.map.getBounds();
      const xmin = bounds.getWest();
      const xmax = bounds.getEast();
      const ymin = bounds.getSouth();
      const ymax = bounds.getNorth();

      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), FIRES_QUERY_DEFAULTS.timeout);

      const apiUrl = config.public.apiUrl as string;

      const url = `${apiUrl}/fires-data/current?xmin=${xmin}&xmax=${xmax}&ymin=${ymin}&ymax=${ymax}&hours=${FIRES_QUERY_DEFAULTS.hours}`;

      const res = await fetch(url, {
        signal: controller.signal
      });

      clearTimeout(timeoutId);

      if (!res.ok) {
        throw new Error(`HTTP ${res.status}: ${res.statusText}`);
      }

      const apiData = await res.json();

      if (!apiData || apiData.type !== 'FeatureCollection') {
        throw new Error('Invalid fires data format');
      }

      firesData = apiData;

      console.log(`Loaded ${apiData.count} fire detections`);
    } catch (error) {
      console.error('Fires data load failed:', error);
      firesData = null;
      throw error;
    } finally {
      isLoadingFiresData.value = false;
    }
  }

  async function loadAndShowFiresLayer() {
    if (isInitializing) {
      return;
    }

    if (!props.map) {
      return;
    }

    if (!props.map.getContainer()) {
      return;
    }

    isInitializing = true;

    try {
      removeFiresLayer();

      // Force reload to ensure fires data matches current map bounds
      await loadFiresData(true);

      if (!firesData) {
        return;
      }

      await nextTick();

      if (!props.map || !props.enabled) {
        return;
      }

      addFiresLayerToMap();
    } catch (error) {
      console.error('Error creating/adding fires layer:', error);
      firesLayer = null;
    } finally {
      isInitializing = false;
    }
  }

  onUnmounted(() => {
    removeMapEventListeners();
    removeFiresLayer();
    firesData = null;
    isInitializing = false;
    moveendHandler = null;
    zoomendHandler = null;
  });
</script>
