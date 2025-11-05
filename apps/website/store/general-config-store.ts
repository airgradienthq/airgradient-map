import { defineStore } from 'pinia';
import { HISTORY_PERIODS } from '~/constants';
import { HistoricalDataTimeZone, HistoryPeriodConfig } from '~/types';

import { MeasureNames } from '~/types';
import { GeneralConfigStoreState } from '~/types';

export const useGeneralConfigStore = defineStore('generalConfig', {
  state: (): GeneralConfigStoreState => ({
    selectedMeasure: MeasureNames.PM25,
    selectedHistoryPeriod: HISTORY_PERIODS[0],
    selectedHistoricalDataTimeZoneConfig: HistoricalDataTimeZone.LOCAL,
    headless: false,
    embedded: false
  }),
  actions: {
    setSelectedMeasure(measure: MeasureNames) {
      this.selectedMeasure = measure;
    },
    setSelectedHistoryPeriod(period: HistoryPeriodConfig) {
      this.selectedHistoryPeriod = period;
    },
    setSelectedHistoricalDataTimeZoneConfig(timezone: HistoricalDataTimeZone) {
      this.selectedHistoricalDataTimeZoneConfig = timezone;
    },
    setHeadless(headless: boolean) {
      this.headless = headless;
    },
    setEmbedded(embedded: boolean) {
      this.embedded = embedded;
    }
  }
});
