import { MeasureNames } from '~/types';

export const MEASURE_UNITS: Record<MeasureNames, string> = {
  [MeasureNames.PM25]: 'μg/m³',
  [MeasureNames.PM_AQI]: 'US AQI',
  [MeasureNames.RCO2]: 'ppm'
};
