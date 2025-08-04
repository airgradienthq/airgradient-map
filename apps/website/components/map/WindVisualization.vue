<template>
  <div class="wind-container">
    <canvas
      ref="windCanvas"
      class="wind-visualization"
      :width="canvasWidth"
      :height="canvasHeight"
      @mousemove="onMouseMove"
      @mouseleave="hideWindInfo"
    />
    <div v-if="windInfo && showTooltip" class="wind-tooltip" :style="tooltipStyle">
      <div class="wind-speed">{{ windInfo.speed.toFixed(1) }} m/s</div>
      <div class="wind-direction">{{ windInfo.direction.toFixed(0) }}°</div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
  import { useIntervalRefresh } from '~/composables/shared/useIntervalRefresh';

  interface Props {
    width?: number;
    height?: number;
    bounds?: { north: number; south: number; east: number; west: number };
    particleCount?: number;
    maxParticleAge?: number;
    frameRate?: number;
    velocityScale?: number;
    windDataUrl?: string;
    showTooltip?: boolean;
    isMoving?: boolean;
  }

  const props = withDefaults(defineProps<Props>(), {
    width: 800,
    height: 600,
    bounds: () => ({ north: 90, south: -90, east: 180, west: -180 }),
    particleCount: 2000,
    maxParticleAge: 100,
    frameRate: 40,
    velocityScale: 0.8,
    windDataUrl: `http://localhost:3001/wind-data/file?t=${Date.now()}`,
    showTooltip: true,
    isMoving: false
  });

  const windCanvas = ref<HTMLCanvasElement>();
  const windInfo = ref<{ speed: number; direction: number } | null>(null);
  const tooltipPosition = ref({ x: 0, y: 0 });
  const canvasWidth = ref(props.width);
  const canvasHeight = ref(props.height);
  const tooltipStyle = computed(() => ({
    left: `${tooltipPosition.value.x + 10}px`,
    top: `${tooltipPosition.value.y - 10}px`
  }));

  const { startRefreshInterval, stopRefreshInterval } = useIntervalRefresh(
    loadWind,
    6 * 60 * 60 * 1000
  );

  watch([() => props.width, () => props.height, () => props.bounds], () => {
    canvasWidth.value = props.width;
    canvasHeight.value = props.height;
    initParticles();
  });

  watch(
    () => props.isMoving,
    moving => {
      if (moving) {
        stopAnimation();
      } else {
        animate();
      }
    }
  );

  const PARTICLE_LINE_WIDTH = 1.4;
  const MAX_AGE = props.maxParticleAge;
  const FRAME = props.frameRate;

  let ctx: CanvasRenderingContext2D | null = null;
  let windData: any = null;
  let animationId: any = null;
  let particles: any[] = [];
  let colorStyles: string[] = [];
  let buckets: any[][] = [];

  onMounted(async () => {
    if (!windCanvas.value) return;
    ctx = windCanvas.value.getContext('2d');
    if (!ctx) return;

    await loadWind();
    startRefreshInterval();
    initParticles();
    initColors();
    if (!props.isMoving) animate();
  });

  onUnmounted(() => {
    stopAnimation();
    stopRefreshInterval();
  });

  function onMouseMove(e: MouseEvent) {
    if (!props.showTooltip) return;
    const rect = windCanvas.value?.getBoundingClientRect();
    if (!rect) return;
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    tooltipPosition.value = { x, y };
    const { lat, lng } = pixelToLatLng(x, y);
    const wind = getWind(lat, lng);
    if (wind && wind.magnitude) {
      const direction = ((Math.atan2(-wind.v, wind.u) * 180) / Math.PI + 360) % 360;
      windInfo.value = { speed: wind.magnitude, direction };
    } else {
      windInfo.value = null;
    }
  }

  function hideWindInfo() {
    windInfo.value = null;
  }

  async function loadWind() {
    try {
      const url = `${props.windDataUrl.split('?')[0]}?t=${Date.now()}`;
      const res = await fetch(url);
      const raw = await res.json();
      if (Array.isArray(raw) && raw[0]?.header && raw[1]?.data) {
        windData = { header: [raw[0].header], data: [raw[0].data, raw[1].data] };
      } else if (raw?.header?.[0]) {
        windData = raw;
      } else {
        createMockData();
      }
    } catch {
      createMockData();
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
    windData = {
      header: [
        {
          nx,
          ny,
          lo1: 0,
          la1: 90,
          dx: 1,
          dy: 1,
          refTime: new Date().toISOString(),
          parameterUnit: 'm/s'
        }
      ],
      data: [u, v]
    };
  }

  function initColors() {
    colorStyles = [
      'rgba(200, 200, 240, 0.9)',
      'rgba(255, 230, 200, 0.9)',
      'rgba(255, 200, 150, 0.9)',
      'rgba(255, 160, 100, 0.85)',
      'rgba(255, 120, 70, 0.85)',
      'rgba(255, 80, 40, 0.85)',
      'rgba(255, 40, 20, 0.85)',
      'rgba(220, 0, 0, 0.85)'
    ];
    buckets = Array.from({ length: colorStyles.length }, () => []);
  }

  function initParticles() {
    const count = Math.min(
      Math.round(((canvasWidth.value * canvasHeight.value) / 10000) * 7),
      props.particleCount
    );
    particles = Array.from({ length: count }, () => ({
      x: Math.random() * canvasWidth.value,
      y: Math.random() * canvasHeight.value,
      age: Math.random() * MAX_AGE,
      maxAge: MAX_AGE
    }));
  }

  function animate() {
    if (props.isMoving) return;
    try {
      evolve();
      draw();
      animationId = setTimeout(animate, FRAME);
    } catch {
      stopAnimation();
    }
  }

  function stopAnimation() {
    if (animationId) {
      clearTimeout(animationId);
      animationId = null;
    }

    if (ctx) {
      ctx.save();
      ctx.globalCompositeOperation = 'source-over';
      ctx.clearRect(0, 0, canvasWidth.value, canvasHeight.value);
      ctx.restore();
    }

    particles = [];
    buckets.forEach(b => (b.length = 0));
  }

  function pixelToLatLng(x: number, y: number) {
    const lat =
      props.bounds.north - (y / canvasHeight.value) * (props.bounds.north - props.bounds.south);
    const lng =
      props.bounds.west + (x / canvasWidth.value) * (props.bounds.east - props.bounds.west);
    return { lat, lng };
  }

  function getWind(lat: number, lng: number) {
    if (!windData || windData.data.length < 2) return null;
    const h = windData.header[0];
    let lon = lng < 0 ? lng + 360 : lng;
    const i = Math.floor((lon - h.lo1) / h.dx);
    const j = Math.floor((h.la1 - lat) / h.dy);
    const fi = (lon - h.lo1) / h.dx - i;
    const fj = (h.la1 - lat) / h.dy - j;
    const u = bilinear(windData.data[0], i, j, fi, fj, h.nx);
    const v = bilinear(windData.data[1], i, j, fi, fj, h.nx);
    if (u === null || v === null) return null;
    return { u, v, magnitude: Math.sqrt(u * u + v * v) };
  }

  function bilinear(data: number[], i: number, j: number, fi: number, fj: number, nx: number) {
    const idx = (x: number, y: number) => y * nx + x;
    const v00 = data[idx(i, j)] ?? 0;
    const v10 = data[idx(i + 1, j)] ?? 0;
    const v01 = data[idx(i, j + 1)] ?? 0;
    const v11 = data[idx(i + 1, j + 1)] ?? 0;
    return [v00, v10, v01, v11].every(Number.isFinite)
      ? (v00 * (1 - fi) + v10 * fi) * (1 - fj) + (v01 * (1 - fi) + v11 * fi) * fj
      : null;
  }

  function evolve() {
    buckets.forEach(b => (b.length = 0));
    particles.forEach(p => {
      if (p.age > MAX_AGE) Object.assign(p, randomizeParticle());
      const { lat, lng } = pixelToLatLng(p.x, p.y);
      const wind = getWind(lat, lng);
      if (!wind || !wind.magnitude) {
        p.age = MAX_AGE;
      } else {
        const scale = props.velocityScale * (canvasHeight.value / 1000);
        const xt = p.x + wind.u * scale;
        const yt = p.y - wind.v * scale;
        if (xt >= 0 && xt < canvasWidth.value && yt >= 0 && yt < canvasHeight.value) {
          p.xt = xt;
          p.yt = yt;
          const idx = Math.min(
            Math.floor((wind.magnitude / 30) * (colorStyles.length - 1)),
            colorStyles.length - 1
          );
          buckets[idx].push(p);
        } else {
          p.age = MAX_AGE;
        }
      }
      p.age++;
    });
  }

  function randomizeParticle() {
    return {
      x: Math.random() * canvasWidth.value,
      y: Math.random() * canvasHeight.value,
      age: Math.random() * MAX_AGE,
      maxAge: MAX_AGE
    };
  }

  function draw() {
    if (!ctx) return;

    const prev = ctx.globalCompositeOperation;
    ctx.globalCompositeOperation = 'destination-in';
    ctx.fillStyle = 'rgba(0, 0, 0, 0.95)';
    ctx.fillRect(0, 0, canvasWidth.value, canvasHeight.value);
    ctx.globalCompositeOperation = prev;

    ctx.lineWidth = PARTICLE_LINE_WIDTH;
    ctx.lineCap = 'round';
    ctx.shadowColor = 'rgba(0, 0, 0, 0.5)';
    ctx.shadowBlur = 1;

    buckets.forEach((bucket, i) => {
      if (!bucket.length) return;
      ctx.beginPath();
      ctx.strokeStyle = colorStyles[i];
      bucket.forEach(p => {
        if (p.xt !== undefined && p.yt !== undefined) {
          ctx.moveTo(p.x, p.y);
          ctx.lineTo(p.xt, p.yt);
          p.x = p.xt;
          p.y = p.yt;
        }
      });
      ctx.stroke();
    });

    ctx.shadowBlur = 0;
  }
</script>

<style scoped>
  .wind-container {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    z-index: 450;
    pointer-events: none;
  }

  .wind-visualization {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: transparent;
    pointer-events: none;
  }

  .wind-tooltip {
    position: fixed;
    background: rgba(0, 0, 0, 0.8);
    color: white;
    padding: 8px 12px;
    border-radius: 4px;
    font-size: 12px;
    pointer-events: none;
    z-index: 1000;
    white-space: nowrap;
    backdrop-filter: blur(4px);
  }

  .wind-speed {
    font-weight: 600;
    margin-bottom: 2px;
  }

  .wind-direction {
    font-size: 11px;
    opacity: 0.9;
  }
</style>