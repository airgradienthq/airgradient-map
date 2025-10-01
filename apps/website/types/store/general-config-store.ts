import { HistoricalDataTimeZone, HistoryPeriodConfig, MeasureNames } from '../shared';

export type GeneralConfigStoreState = {
  selectedMeasure: MeasureNames;
  selectedHistoryPeriod: HistoryPeriodConfig;
  selectedHistoricalDataTimeZoneConfig: HistoricalDataTimeZone;
  clusterMinPoints: number;
  clusterRadius: number;
  clusterMaxZoom: number;
  headless: boolean;
};
