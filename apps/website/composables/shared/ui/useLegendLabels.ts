import { computed } from 'vue';
import { storeToRefs } from 'pinia';

import { useGeneralConfigStore } from '~/store/general-config-store';
import { PM25_LEGEND_LABELS, RCO2_LEGEND_LABELS } from '~/constants';
import { MeasureNames } from '~/types';

/**
 * Composable that provides reactive legend labels based on the currently selected measure.
 *
 * @returns {{
 *   labels: string[] - Array of legend labels for the current measure
 * }}
 */
export function useLegendLabels() {
  const { selectedMeasure } = storeToRefs(useGeneralConfigStore());

  const labels = computed(() => getLegendLabels(selectedMeasure.value));

  function getLegendLabels(measure: MeasureNames) {
    switch (measure) {
      case MeasureNames.PM25:
      case MeasureNames.PM_AQI:
        return PM25_LEGEND_LABELS;
      case MeasureNames.RCO2:
        return RCO2_LEGEND_LABELS;
      default:
        return [];
    }
  }

  return {
    labels
  };
}
