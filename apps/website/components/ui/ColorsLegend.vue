<template>
  <div
    :class="['colors-info', { 'white-mode': isWhiteMode, small: size === ColorsLegendSize.SMALL }]"
  >
    <div class="color-labels mobile-legend">
      <div v-for="label in labels" :class="['label-item', `item-${label.toLowerCase()}`]">
        {{ label }}
      </div>
    </div>

    <div class="color-labels desktop-legend">
      <div v-for="label in labels" :class="['label-item', `item-${label.toLowerCase()}`]">
        {{ label }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { PropType } from 'vue';

  import { ColorsLegendSize } from '~/types';
  import { useLegendLabels } from '~/composables/shared/ui/useLegendLabels';

  defineProps({
    /**
     * Whether the legend is in white mode.
     * @type {boolean}
     * @default false
     */
    isWhiteMode: {
      type: Boolean,
      default: false
    },
    /**
     * Size of the colors legend.
     * @type {ColorsLegendSize}
     * @default ColorsLegendSize.MEDIUM
     */
    size: {
      type: String as PropType<ColorsLegendSize>,
      default: ColorsLegendSize.MEDIUM
    }
  });

  const { labels } = useLegendLabels();
</script>

<style lang="scss" scoped>
  .colors-info {
    width: 660px;
    height: 85px;
    border: 2px solid var(--grayColor400);
    border-radius: 20px;
    background-color: var(--main-white-color);
    display: flex;
    flex-direction: column;
    font-weight: var(--font-weight-bold);
    align-items: center;
    justify-content: center;
  }

  .colors-info.small {
    font-weight: var(--font-weight-bold);
    font-size: var(--font-size-sm);

    @media (max-width: 768px) {
      font-size: var(--font-size-xs);
    }
  }

  .colors-info.white-mode {
    text-shadow:
      0 0 3px var(--main-white-color),
      0 0 5px var(--main-white-color),
      0 0 4px var(--main-white-color);
    color: var(--main-text-color);
    font-weight: var(--font-weight-bold);
  }

  .color-labels {
    width: 100%;
    display: flex;
    justify-content: space-evenly;
    align-items: center;
  }

  .label-item {
    width: 111px;
    height: 34px;
    display: flex;
    text-align: center;
    justify-content: center;
    align-items: center;
    border-radius: 10px;
    color: var(--main-white-color);
    text-shadow: none;
    font-family: var(--secondary-font);
    font-size: var(--font-size-ml);
  }

  .label-item.top {
    align-self: flex-end;
  }

  .item-good {
    background-color: var(--airGreen);
  }

  .item-moderate {
    background-color: var(--airYellow);
    color: var(--main-text-color);
  }

  .item-poor {
    background-color: var(--airOrange);
  }

  .item-unhealthy {
    background-color: var(--airRed);
  }

  .item-severe {
    background-color: var(--airPurple);
  }

  @media (max-width: 779px) {
    .desktop-legend {
      display: none;
    }
    .mobile-legend {
      font-size: var(--font-size-sm);
      justify-content: center;
      flex-wrap: wrap;
      gap: 12px 16px;
    }
    .label-item {
      width: 84px;
      height: 28px;
      border-radius: 5px;
      font-size: var(--font-size-ms);
    }
    .colors-info {
      width: 326px;
      height: 104px;
      border-radius: 10px;
    }
  }

  @media (min-width: 780px) {
    .mobile-legend {
      display: none;
    }
  }
</style>
