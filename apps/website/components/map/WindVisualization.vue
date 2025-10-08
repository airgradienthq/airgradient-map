<template>
  <div></div>
</template>

<script setup lang="ts">
  import { ref, watch, onUnmounted, onMounted } from 'vue';
  import L from 'leaflet';

  interface Props {
    map: L.Map | null;
    enabled: boolean;
    windDataUrl: string;
  }

  const props = defineProps<Props>();

  let velocityLayer: any = null;
  let windData: any = null;
  let loadingData = false;
  let leafletVelocity: any = null;

  onMounted(async () => {
    // Dynamic import for client-side only
    if (process.client) {
      try {
        await import('leaflet-velocity-ts');
        leafletVelocity = (L as any).velocityLayer;
      } catch (error) {
        console.error('Failed to load leaflet-velocity-ts:', error);
      }
    }
  });

  watch(
    () => props.enabled,
    async enabled => {
      if (enabled && props.map && leafletVelocity) {
        await loadAndShowWindLayer();
      } else if (velocityLayer && props.map) {
        props.map.removeLayer(velocityLayer);
        velocityLayer = null;
      }
    },
    { immediate: true }
  );

  watch(
    () => props.map,
    async newMap => {
      if (newMap && props.enabled && leafletVelocity) {
        await loadAndShowWindLayer();
      }
    }
  );

  async function loadWindData() {
    if (loadingData || windData) return;

    loadingData = true;
    try {
      const res = await fetch(`${props.windDataUrl}?t=${Date.now()}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);

      const raw = await res.json();

      // Handle different data formats
      if (Array.isArray(raw) && raw[0]?.header && raw[1]?.data) {
        windData = [
          { header: raw[0].header, data: raw[0].data },
          { header: raw[1].header, data: raw[1].data }
        ];
      } else if (raw?.header?.[0]) {
        windData = raw;
      } else {
        createMockData();
      }
    } catch (error) {
      console.error('Wind data load failed:', error);
      createMockData();
    } finally {
      loadingData = false;
    }
  }

  function createMockData() {
    const nx = 360,
      ny = 181;
    const u = [],
      v = [];

    for (let j = 0; j < ny; j++) {
      for (let i = 0; i < nx; i++) {
        const lat = 90 - j,
          lon = i;
        u.push(Math.sin((lon * Math.PI) / 180) * Math.cos((lat * Math.PI) / 180) * 10);
        v.push(Math.cos((lon * Math.PI) / 180) * Math.sin((lat * Math.PI) / 180) * 5);
      }
    }

    windData = [
      {
        header: {
          nx,
          ny,
          lo1: 0,
          la1: 90,
          dx: 1,
          dy: 1,
          refTime: new Date().toISOString(),
          parameterUnit: 'm/s',
          parameterNumberName: 'U component of wind',
          parameterNumber: 2
        },
        data: u
      },
      {
        header: {
          nx,
          ny,
          lo1: 0,
          la1: 90,
          dx: 1,
          dy: 1,
          refTime: new Date().toISOString(),
          parameterUnit: 'm/s',
          parameterNumberName: 'V component of wind',
          parameterNumber: 3
        },
        data: v
      }
    ];
  }

  async function loadAndShowWindLayer() {
    if (!props.map || !leafletVelocity) return;

    // Remove existing layer
    if (velocityLayer) {
      props.map.removeLayer(velocityLayer);
      velocityLayer = null;
    }

    // Load data if not already loaded
    if (!windData) {
      await loadWindData();
    }

    if (!windData) return;

    // Create velocity layer
    velocityLayer = leafletVelocity({
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
      velocityScale: 0.005,
      particleAge: 64,
      particleMultiplier: 0.004,
      frameRate: 15,
      opacity: 0.97
    });

    velocityLayer.addTo(props.map);
  }

  onUnmounted(() => {
    if (velocityLayer && props.map) {
      props.map.removeLayer(velocityLayer);
      velocityLayer = null;
    }
  });
</script>