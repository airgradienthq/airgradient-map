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
    clusterMinPoints: 2,
    clusterRadius: 80,
    clusterMaxZoom: 8,
    headless: false
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
    setClusterMinPoints(minPoints: number) {
      this.clusterMinPoints = minPoints;
    },
    setClusterRadius(radius: number) {
      this.clusterRadius = radius;
    },
    setClusterMaxZoom(maxZoom: number) {
      this.clusterMaxZoom = maxZoom;
    },
    setHeadless(headless: boolean) {
      this.headless = headless;
    }
  }
});
