<template>
  <div></div>
</template>

<script setup lang="ts">
  import { watch, onUnmounted, onMounted } from 'vue';
  import L from 'leaflet';
  import { VELOCITY_COLOR_SCALE } from '~/constants/map/wind-layer';

  interface Props {
    map: L.Map | null;
    enabled: boolean;
    windDataUrl: string;
  }

  const props = defineProps<Props>();

  let velocityLayer: any = null;
  let windData: any = null;
  let libraryLoaded = false;

  onMounted(async () => {
    if (process.client) {
      await import('leaflet-velocity');
      libraryLoaded = true;

      if (props.enabled && props.map) {
        await loadAndShowWindLayer();
      }
    }
  });

  watch(
    () => props.enabled,
    async enabled => {
      if (!libraryLoaded) return;

      if (enabled && props.map) {
        await loadAndShowWindLayer();
      } else if (velocityLayer && props.map) {
        props.map.removeLayer(velocityLayer);
        velocityLayer = null;
      }
    }
  );

  watch(
    () => props.map,
    async newMap => {
      if (!libraryLoaded) return;

      if (newMap && props.enabled) {
        await loadAndShowWindLayer();
      }
    }
  );

  async function loadWindData() {
    if (windData) return;

    try {
      const res = await fetch(`${props.windDataUrl}?t=${Date.now()}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);

      windData = await res.json();
      console.log('Wind data loaded:', windData);
    } catch (error) {
      console.error('Wind data load failed:', error);
      throw error;
    }
  }

  async function loadAndShowWindLayer() {
    if (!props.map || !libraryLoaded || !(L as any).velocityLayer) {
      console.log('Not ready:', {
        map: !!props.map,
        libraryLoaded,
        velocityLayer: !!(L as any).velocityLayer
      });
      return;
    }

    if (velocityLayer) {
      try {
        props.map.removeLayer(velocityLayer);
      } catch (e) {
        console.warn('Error removing layer:', e);
      }
      velocityLayer = null;
    }

    try {
      await loadWindData();
    } catch (error) {
      console.error('Failed to load wind data');
      return;
    }

    if (!windData) {
      console.error('No wind data available');
      return;
    }

    try {
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
        opacity: 0.97,
        colorScale: VELOCITY_COLOR_SCALE
      });

      velocityLayer.addTo(props.map);
      console.log('Velocity layer added successfully');
    } catch (error) {
      console.error('Error creating velocity layer:', error);
    }
  }

  onUnmounted(() => {
    if (velocityLayer && props.map) {
      try {
        props.map.removeLayer(velocityLayer);
      } catch (e) {
        console.warn('Error removing layer on unmount:', e);
      }
      velocityLayer = null;
    }
  });
</script>
