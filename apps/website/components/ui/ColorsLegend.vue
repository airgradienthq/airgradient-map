<template>
  <div
    :class="['colors-info', { 'white-mode': isWhiteMode, small: size === ColorsLegendSize.SMALL }]"
  >
    <div class="color-labels mobile-legend">
      <div v-for="(label, i) in labels" class="label-item top">
        {{ i % 2 !== 0 ? label : '' }}
      </div>
    </div>

    <div class="color-scala"></div>

    <div class="color-labels mobile-legend">
      <div v-for="(label, i) in labels" class="label-item">
        {{ i % 2 === 0 ? label : '' }}
      </div>
    </div>

    <div class="color-labels desktop-legend">
      <div v-for="label in labels" class="label-item">
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
    width: 100%;
    display: flex;
    flex-direction: column;
    gap: 5px;
    font-weight: var(--font-weight-medium);
  }

  .colors-info.small {
    font-weight: var(--font-weight-light);
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
    font-weight: 600;
  }

  .color-scala {
    width: 100%;
    height: 5px;
    background-color: var(--main-white-color);
    border-radius: 1px;
    background-image: linear-gradient(
      90deg,
      var(--aq-green-500) 14%,
      var(--aq-yellow-500) 17%,
      var(--aq-yellow-500) 31%,
      var(--aq-orange-500) 34%,
      var(--aq-orange-500) 48%,
      var(--aq-red-500) 51%,
      var(--aq-red-500) 65%,
      var(--aq-violet-500) 68%,
      var(--aq-violet-500) 82%,
      var(--aq-purple-500) 85%,
      var(--aq-purple-500) 100%
    );
  }

  .color-labels {
    width: 100%;
    display: flex;
    justify-content: space-around;
    align-items: center;
  }

  .label-item {
    width: 17%;
    text-align: center;
    align-self: flex-start;
    line-height: 1.2;
  }

  .label-item.top {
    align-self: flex-end;
  }

  @media (max-width: 779px) {
    .desktop-legend {
      display: none;
    }
    .mobile-legend {
      font-size: var(--font-size-sm);
    }
  }

  @media (min-width: 780px) {
    .mobile-legend {
      display: none;
    }
  }
</style>
