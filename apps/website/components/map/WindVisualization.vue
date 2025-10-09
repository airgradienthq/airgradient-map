<template>
  <div></div>
</template>

<script setup lang="ts">
  import { watch, onUnmounted, onMounted, nextTick } from 'vue';
  import L from 'leaflet';

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
      
      // If enabled on mount, load the layer
      if (props.enabled && props.map) {
        await nextTick();
        await loadAndShowWindLayer();
      }
    }
  });

  watch(
    () => props.enabled,
    async enabled => {
      if (!libraryLoaded) return; // Wait for library to load
      
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
      if (!libraryLoaded) return; // Wait for library to load
      
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
      console.log('Not ready:', { map: !!props.map, libraryLoaded, velocityLayer: !!(L as any).velocityLayer });
      return;
    }

    // Remove existing layer
    if (velocityLayer) {
      try {
        props.map.removeLayer(velocityLayer);
      } catch (e) {
        console.warn('Error removing layer:', e);
      }
      velocityLayer = null;
    }

    // Load data
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

    // Create velocity layer
    try {
      velocityLayer = (L as any).velocityLayer({
        displayValues: true,
        displayOptions: {
          velocityType: 'Wind',
          position: 'bottomleft',
          emptyString: 'No wind data',
          angleConvention: 'bearingCW',
          showCardinal: true,
          speedUnit: 'm/s',
          directionString: 'Direction',
          speedString: 'Speed'
        },
        data: windData,
        minVelocity: 0,
        maxVelocity: 15,
        velocityScale: 0.005,
        opacity: 0.97
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