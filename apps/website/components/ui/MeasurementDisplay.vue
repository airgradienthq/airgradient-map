<template>
  <div class="measurement-display-card">
    <div class="measurement-contributor">
      <p>Contributor:<br />{{ props.contributorName }}</p>
    </div>

    <div class="measurement-logo">
      <img
        width="106"
        height="106"
        :src="`/images/aq-icons/${measurementDisplayConfig.iconPath}`"
        :alt="textLabel"
        :class="['AQ-icon', AQLevel === MeasurementLevels.INCORRECT ? 'image-gray' : '']"
      />
    </div>

    <div class="measurement-message">
      <h6>THE AIR QUALITY IS</h6>
      <h3 :style="{ color: colorConfig.bgColor }">{{ textLabel }}</h3>
    </div>

    <div class="measurement-index-container" :style="{ backgroundColor: colorConfig.bgColor }">
      <h3 :class="colorConfig.textColorClass">
        <span>{{ props.value }}</span>
        {{ selectedMeasure !== MeasureNames.RCO2 ? 'U.S. AQI' : 'ppm' }}
      </h3>
      <h5 :class="colorConfig.textColorClass">
        {{ selectedMeasure !== MeasureNames.RCO2 ? 'Air Quality Index' : 'Carbon Dioxide' }}
      </h5>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed, ComputedRef } from 'vue';
  import { useLegendLabels } from '~/composables/shared/ui/useLegendLabels';
  import { getColorForMeasure, getMeasurementLevel } from '~/utils';

  import { useGeneralConfigStore } from '~/store/general-config-store';
  import { MeasurementLevels, MeasureNames } from '~/types';
  import {
    MEASUREMENT_DISPLAY_ICON_CONFIG_BY_LEVELS,
    MeasurementDisplayIconConfig
  } from '~/constants';

  const props = defineProps({
    contributorName: {
      type: String,
      default: 'Contributor Names Placeholder'
    },
    value: {
      type: Number,
      default: 0
    }
  });

  const generalConfigStore = useGeneralConfigStore();
  const { labels } = useLegendLabels();
  const selectedMeasure = generalConfigStore.selectedMeasure;

  const AQLevel: ComputedRef<MeasurementLevels> = computed(() => {
    return getMeasurementLevel(selectedMeasure, props.value);
  });

  const measurementDisplayConfig: ComputedRef<MeasurementDisplayIconConfig> = computed(() => {
    return MEASUREMENT_DISPLAY_ICON_CONFIG_BY_LEVELS[AQLevel.value];
  });

  const textLabel: ComputedRef<string> = computed(() => {
    return labels.value[measurementDisplayConfig.value.textLabelIndex];
  });

  const colorConfig: ComputedRef<{ bgColor: string; textColorClass: string }> = computed(() => {
    return getColorForMeasure(selectedMeasure, props.value);
  });
</script>

<style lang="scss" scoped>
  .measurement-display-card {
    display: flex;
    justify-content: center;
    align-items: center;
    flex-direction: column;
    width: 266px;
    height: 433px;
    border: 2px solid var(--grayColor400);
    border-radius: 10px;
    background-color: var(--main-white-color);
  }

  .measurement-contributor {
    margin-top: 2px;
    margin-bottom: 17px;
  }

  .measurement-contributor p {
    font-family: var(--primary-font);
    font-weight: var(--font-weight-light);
    font-size: var(--font-size-sm);
    color: var(--grayColor700);
    line-height: 16px;
    display: flex;
    text-align: center;
    justify-content: center;
    flex-direction: column;
    margin: 0;
  }

  .measurement-message {
    display: flex;
    text-align: center;
    justify-content: center;
    flex-direction: column;
    font-family: var(--secondary-font);
    margin-top: 32px;
    margin-bottom: 32px;
  }

  .measurement-message h3 {
    font-weight: var(--font-weight-bold);
    font-size: var(--font-size-lg);
    margin: 0;
    line-height: 31px;
  }

  .measurement-message h6 {
    font-weight: var(--font-weight-light);
    font-size: 10px;
    color: var(--grayColor700);
    margin-bottom: 8px;
    letter-spacing: 0.05em;
  }

  .measurement-index-container {
    width: 216px;
    height: 81px;
    border-radius: 10px;
    display: flex;
    text-align: center;
    justify-content: center;
    flex-direction: column;
  }

  .measurement-index-container h3 {
    font-family: var(--secondary-font);
    font-size: var(--font-size-lg);
    font-weight: var(--font-weight-bold);
    margin: 0;
  }

  .image-gray {
    filter: grayscale(100%);
  }

  .measurement-index-container h5 {
    font-family: var(--secondary-font);
    font-size: var(--font-size-base);
    font-weight: var(--font-weight-light);
    padding-top: 2px;
    margin: 0;
  }
</style>
