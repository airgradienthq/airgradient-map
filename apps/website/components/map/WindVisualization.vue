<template>
  <div></div>
</template>

<script setup lang="ts">
  import { watch, onUnmounted, onMounted, ref, nextTick } from 'vue';
  import L from 'leaflet';
  import { VELOCITY_COLOR_SCALE } from '~/constants/map/wind-layer';

  interface Props {
    map: L.Map | null;
    enabled: boolean;
    windDataUrl: string;
  }

  const props = defineProps<Props>();

  const emit = defineEmits<{
    loadingChange: [loading: boolean];
  }>();

  let velocityLayer: any = null;
  let windData: any = null;
  let libraryLoaded = false;
  let isInitializing = false;
  const isLoadingWindData = ref(false);

  onMounted(async () => {
    if (process.client) {
      try {
        await import('leaflet-velocity');
        libraryLoaded = true;

        await nextTick();

        if (props.enabled && props.map) {
          await loadAndShowWindLayer();
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
      } else {
        removeWindLayer();
      }
    },
    { immediate: false }
  );

  watch(
    () => props.map,
    async (newMap, oldMap) => {
      if (!libraryLoaded) return;

      if (oldMap && velocityLayer) {
        removeWindLayer();
      }

      if (newMap && props.enabled) {
        setTimeout(async () => {
          await loadAndShowWindLayer();
        }, 100);
      }
    }
  );

  watch(isLoadingWindData, loading => {
    emit('loadingChange', loading);
  });

  function removeWindLayer() {
    if (velocityLayer && props.map) {
      try {
        props.map.removeLayer(velocityLayer);
        velocityLayer = null;
      } catch (e) {
        console.warn('Error removing wind layer:', e);
        velocityLayer = null;
      }
    }
  }

  async function loadWindData() {
    if (windData) {
      return;
    }

    isLoadingWindData.value = true;

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 10000); // 10 second timeout

      const res = await fetch(`${props.windDataUrl}?t=${Date.now()}`, {
        signal: controller.signal
      });

      clearTimeout(timeoutId);

      if (!res.ok) {
        throw new Error(`HTTP ${res.status}: ${res.statusText}`);
      }

      windData = await res.json();

      if (!windData || (!Array.isArray(windData) && !windData.features)) {
        throw new Error('Invalid wind data format');
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

      await loadWindData();

      if (!windData) {
        return;
      }

      await nextTick();

      if (!props.map || !props.enabled) {
        return;
      }

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

      if (velocityLayer && props.map && props.enabled) {
        velocityLayer.addTo(props.map);
      }
    } catch (error) {
      console.error('Error creating/adding velocity layer:', error);
      velocityLayer = null;
    } finally {
      isInitializing = false;
    }
  }

  onUnmounted(() => {
    removeWindLayer();
    windData = null;
    libraryLoaded = false;
    isInitializing = false;
  });
</script>
