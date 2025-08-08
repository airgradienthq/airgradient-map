<template>
  <div class="markers-info">
    <div class="markers-container">
      <div
        v-for="marker in markers"
        :key="marker.type"
        :class="['marker-item', marker.class + '-item']"
      >
        <div :class="['marker-preview', marker.class]">9</div>
        <span class="marker-label">{{ $t(marker.label) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  const markers = [
    {
      type: 'cluster',
      label: 'Cluster',
      class: 'marker-cluster'
    },
    {
      type: 'reference',
      label: 'Reference',
      class: 'marker-reference'
    },
    {
      type: 'simple',
      label: 'Small Sensor',
      class: 'marker-simple'
    }
  ];
</script>

<style lang="scss" scoped>
  .markers-info {
    display: flex;
    flex-direction: column;
    gap: 8px;
    font-weight: var(--font-weight-medium);
  }

  .markers-container {
    display: flex;
    flex-direction: row;
    gap: 15px;
  }

  .marker-item {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .marker-preview {
    width: 20px;
    height: 20px;
    border-radius: 4px;
    background-color: var(--airGreen);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 10px;
    font-weight: 600;
    flex-shrink: 0;
  }

  .marker-cluster {
    border-radius: 50%;
    width: 25px;
    height: 25px;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
  }

  .marker-reference {
    border: 2px solid white;
    box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.2);
  }

  @media (max-width: 779px) {
    .markers-info {
      gap: 6px;
      font-size: var(--font-size-sm);
    }

    .marker-item {
      gap: 8px;
    }

    .marker-preview {
      width: 18px;
      height: 18px;
      font-size: 9px;
    }

    .marker-label {
      white-space: nowrap;
    }
  }
</style>
