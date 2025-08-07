<template>
  <div class="measurement-display-card" :class="aqiClass">
    <div class="measurement-contributor">
      <p>Contributor:<br />{{ props.contributorName }}</p>
    </div>

    <div class="measurement-logo">
      <img
        width="106"
        height="106"
        :src="`/images/aq-icons/${icon}`"
        :alt="iconAlt"
        class="AQ-icon"
      />
    </div>

    <div class="measurement-message">
      <h6>THE AIR QUALITY IS</h6>
      <h3 :style="{ color: aqiColor.bgColor }">{{ aqiLabel }}</h3>
    </div>

    <div class="measurement-index-container" :style="{ backgroundColor: aqiColor.bgColor }">
      <h3 :style="{ color: textColorClass }">
        <span>{{ props.aqiMeasurement }}</span> U.S. AQI
      </h3>
      <h5 :style="{ color: textColorClass }">Air Quality Index</h5>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import { useLegendLabels } from '~/composables/shared/ui/useLegendLabels';
  import { getAQIColor } from '~/utils';
  import { MASCOT_ICONS, MASCOT_ICONS_ALT_TEXT } from '~/constants/shared/mascot-icons';

  const props = defineProps({
    contributorName: {
      type: String,
      default: 'Contributor Names Placeholder'
    },
    aqiMeasurement: {
      type: Number,
      default: 112
    }
  });

  const aqiColor = computed(() => getAQIColor(props.aqiMeasurement, true));
  const textColorClass = computed(() => {
    if (aqiColor.value.textColorClass === 'text-light') {
      return '#ffffff';
    }
    return '#212121';
  });

  /**
   * Determine the AQI label based on measurement.
   */
  const { labels } = useLegendLabels();
  const aqiInfo = computed(() => {
    const aqi = props.aqiMeasurement;
    if (aqi <= 50) {
      return { label: labels.value[0], className: 'aqi-good', index: 0 };
    } else if (aqi <= 100) {
      return { label: labels.value[1], className: 'aqi-moderate', index: 1 };
    } else if (aqi <= 150) {
      return { label: labels.value[2], className: 'aqi-sensitive', index: 2 };
    } else if (aqi <= 200) {
      return { label: labels.value[3], className: 'aqi-unhealthy', index: 3 };
    } else if (aqi <= 300) {
      return { label: labels.value[4], className: 'aqi-very-unhealthy', index: 4 };
    } else {
      return { label: labels.value[5], className: 'aqi-hazardous', index: 5 };
    }
  });
  const aqiLabel = computed(() => aqiInfo.value.label);
  const aqiClass = computed(() => aqiInfo.value.className);

  /**
   * Path to SVG icon. Icons in public/images/aq-icons/-
   */
  const iconInfo = computed(() => {
    const iconIndex = aqiInfo.value.index;
    return { icon: MASCOT_ICONS[iconIndex], iconAlt: MASCOT_ICONS_ALT_TEXT[iconIndex] };
  });
  const icon = computed(() => iconInfo.value.icon);
  const iconAlt = computed(() => iconInfo.value.iconAlt);
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

  .measurement-index-container h5 {
    font-family: var(--secondary-font);
    font-size: var(--font-size-base);
    font-weight: var(--font-weight-light);
    padding-top: 2px;
    margin: 0;
  }
</style>
