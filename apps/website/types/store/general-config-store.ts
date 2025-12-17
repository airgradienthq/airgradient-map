import { HistoricalDataTimeZone, HistoryPeriodConfig, MeasureNames } from '../shared';

export type GeneralConfigStoreState = {
  selectedMeasure: MeasureNames;
  selectedHistoryPeriod: HistoryPeriodConfig;
  selectedHistoricalDataTimeZoneConfig: HistoricalDataTimeZone;
  headless: boolean;
  embedded: boolean;
  excludeOutliers: boolean;
  outlierRadiusKm: number;
  outlierWindowHours: number;
};
