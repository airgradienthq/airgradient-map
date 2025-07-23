<template>
  <div class="markers-info">
    <div class="markers-container desktop-legend">
      <div
        v-for="marker in markers"
        :key="marker.type"
        :class="['marker-item', marker.class + '-item']"
      >
        <div :class="['marker-preview', marker.class]">9</div>
        <span class="marker-label">{{ marker.label }}</span>
      </div>
    </div>
    <div class="markers-container mobile-legend">
      <div
        v-for="marker in markers"
        :key="marker.type"
        :class="['marker-item', marker.class + '-item']"
      >
        <div :class="['marker-preview', marker.class]">9</div>
        <span class="marker-label">{{ marker.label }}</span>
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
    width: 660px;
    height: 85px;
    border: 2px solid var(--grayColor400);
    border-radius: 20px;
    background-color: var(--main-white-color);
    display: flex;
    flex-direction: column;
    gap: 8px;
    font-weight: var(--font-weight-bold);
    font-size: 18px;
    align-items: center;
    justify-content: center;
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
    text-shadow: none;
    font-family: var(--secondary-font);
    // add more margin
    margin: 0px 10px;
  }

  .marker-preview {
    width: 51px;
    height: 51px;
    border-radius: 4px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 18px;
    margin-right: 5px;
    font-weight: var(--font-weight-bold);
    flex-shrink: 0;
    color: var(--main-white-color);
    border: 2px solid var(--main-white-color);
  }

  .marker-cluster {
    border-radius: 50%;
    width: 51px;
    height: 51px;
    background-color: var(--airPurple);
    box-shadow: var(--shadow-primary);
  }

  .marker-reference {
    border-radius: 15px;
    background-color: var(--airOrange);
    box-shadow: var(--shadow-primary);
  }

  .marker-simple {
    background-color: var(--airGreen);
    box-shadow: var(--shadow-primary);
  }

  @media (max-width: 779px) {
    .desktop-legend {
      display: none;
      justify-content: space-evenly;
    }
    .mobile-legend {
      font-size: var(--font-size-sm);
      gap: 12px 16px;
    }
    .markers-info {
      width: 326px;
      height: 116px;
      border-radius: 10px;
      gap: 6px;
      font-size: var(--font-size-base);
    }
    .marker-item {
      width: 80px;
      font-size: var(--font-size-ms);
      gap: 8px;
      flex-direction: column;
      margin: 0;
    }
    .marker-label {
      white-space: nowrap;
    }
    .marker-preview {
      margin: 0;
    }
  }

  @media (min-width: 780px) {
    .mobile-legend {
      display: none;
    }
  }
</style>
