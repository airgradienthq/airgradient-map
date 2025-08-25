import { HistoricalDataTimeZone, HistoryPeriodConfig, MeasureNames } from '../shared';

export type GeneralConfigStoreState = {
  selectedMeasure: MeasureNames;
  selectedHistoryPeriod: HistoryPeriodConfig;
  selectedHistoricalDataTimeZoneConfig: HistoricalDataTimeZone;
  headless: boolean;
};
