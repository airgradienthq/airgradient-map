<template>
  <div class="wildfire-legend">
    <h4>Fire Intensity</h4>
    <div class="legend-items">
      <div v-for="level in intensityLevels" :key="level.name" class="legend-item">
        <div
          class="legend-circle"
          :style="{
            backgroundColor: level.color,
            width: level.size + 'px',
            height: level.size + 'px'
          }"
        ></div>
        <span>{{ level.label }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  const intensityLevels = [
    { name: 'low', label: 'Low', color: '#FFA500', size: 8 },
    { name: 'medium', label: 'Medium', color: '#FF4500', size: 10 },
    { name: 'high', label: 'High', color: '#FF0000', size: 12 },
    { name: 'extreme', label: 'Extreme', color: '#8B0000', size: 14 }
  ];
</script>

<style scoped>
  .wildfire-legend {
    background: rgba(255, 255, 255, 0.9);
    border-radius: 8px;
    padding: 12px;
    backdrop-filter: blur(4px);
    border: 1px solid rgba(0, 0, 0, 0.1);
  }

  .wildfire-legend h4 {
    margin: 0 0 8px 0;
    font-size: 14px;
    font-weight: 600;
  }

  .legend-items {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .legend-item {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
  }

  .legend-circle {
    border-radius: 50%;
    flex-shrink: 0;
  }
</style>
