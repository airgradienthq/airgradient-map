import { MeasureNames } from '~/types';

export const MEASURE_LABELS: Record<MeasureNames, string> = {
  [MeasureNames.PM25]: 'PM2.5',
  [MeasureNames.PM_AQI]: 'US AQI (PM2.5)',
  [MeasureNames.RCO2]: 'CO2'
};

export const MEASURE_LABELS_WITH_UNITS: Record<MeasureNames, string> = {
  [MeasureNames.PM25]: 'PM2.5 (μg/m³)',
  [MeasureNames.PM_AQI]: 'US AQI (PM2.5)',
  [MeasureNames.RCO2]: 'CO2 (ppm)'
};

export const MEASURE_LABELS_SUBSCRIPTS: Record<MeasureNames, string> = {
  [MeasureNames.PM25]: 'PM<sub>2.5</sub>',
  [MeasureNames.PM_AQI]: 'US AQI (PM<sub>2.5</sub>)',
  [MeasureNames.RCO2]: 'CO<sub>2</sub>'
};
