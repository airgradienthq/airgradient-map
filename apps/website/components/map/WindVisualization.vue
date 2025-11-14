<template>
  <div></div>
</template>

<script setup lang="ts">
  import { watch, onUnmounted, onMounted, ref, nextTick } from 'vue';
  import L from 'leaflet';
  import { useRuntimeConfig } from 'nuxt/app';

  import { VELOCITY_COLOR_SCALE } from '~/constants/map/wind-layer';
  import { transformToLeafletVelocityFormat } from '~/utils/wind-data-transformer';

  interface Props {
    map: L.Map | null;
    enabled: boolean;
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
    console.log('loadWindData');
    console.log('windData', windData);
    console.log('props.map', props.map);
    if (windData) {
      return;
    }

    if (!props.map) {
      return;
    }

    isLoadingWindData.value = true;

    try {
      // Get map bounds
      const bounds = props.map.getBounds();
      const xmin = bounds.getWest();
      const xmax = bounds.getEast();
      const ymin = bounds.getSouth();
      const ymax = bounds.getNorth();

      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 50000); // 10 second timeout

      const apiUrl = config.public.apiUrl as string;

      const url = `${apiUrl}/wind-data/current?xmin=${xmin}&xmax=${xmax}&ymin=${ymin}&ymax=${ymax}`;
      console.log('url', url);

      const res = await fetch(url, {
        signal: controller.signal
      });

      clearTimeout(timeoutId);

      if (!res.ok) {
        throw new Error(`HTTP ${res.status}: ${res.statusText}`);
      }

      const apiData = await res.json();

      console.log('apiData', apiData);
      if (!apiData || !apiData.header || !apiData.data) {
        throw new Error('Invalid wind data format');
      }

      // Transform API format to leaflet-velocity format
      windData = transformToLeafletVelocityFormat(apiData);
      console.log('windData transformed:', {
        uHeader: windData[0].header,
        vHeader: windData[1].header,
        uDataLength: windData[0].data.length,
        vDataLength: windData[1].data.length,
        firstUValues: windData[0].data.slice(0, 5),
        firstVValues: windData[1].data.slice(0, 5)
      });

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

      console.log('Wind data validation passed:', {
        uComponent: {
          headerKeys: Object.keys(windData[0].header),
          dataLength: windData[0].data.length
        },
        vComponent: {
          headerKeys: Object.keys(windData[1].header),
          dataLength: windData[1].data.length
        }
      });
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

      // Final validation before passing to leaflet-velocity
      if (!Array.isArray(windData) || windData.length !== 2) {
        throw new Error(
          `Wind data must be array of 2 components, got: ${Array.isArray(windData) ? windData.length : typeof windData}`
        );
      }

      if (!windData[0] || !windData[0].data || !windData[1] || !windData[1].data) {
        throw new Error('Wind data components must have data arrays');
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
