// IMPORTANT: Enum Values must match the column name in the database

import { MeasureType } from 'src/types';

export const OUTLIER_COLUMN_NAME: Partial<Record<MeasureType, string>> = {
  [MeasureType.PM25]: 'is_pm25_outlier',
  [MeasureType.RCO2]: 'is_rco2_outlier',
};

export type MeasureTypeWithOutlier = keyof typeof OUTLIER_COLUMN_NAME;

export const MEASURE_TYPES_WITH_OUTLIER: MeasureTypeWithOutlier[] = Object.keys(
  OUTLIER_COLUMN_NAME,
) as MeasureTypeWithOutlier[];
