import {
  CHART_COLORS_500_CSS_VARS,
  CHART_COLORS_700_CSS_VARS,
  CHART_COLORS_100_CSS_VARS
} from '~/constants/shared/colors';
import { ChartColorsType, MeasureNames, MeasurementLevels } from '~/types';
import { getMeasurementLevel } from './get-measure-level';

/**
 * Gets the color representation for PM2.5 values.
 *
 * @param {number} pmValue - The PM2.5 value in μg/m³
 * @param {boolean} [dark=false] - Whether to use dark mode colors
 * @returns {{ bgColor: string; textColorClass: string }} Object containing background and text colors
 *   - bgColor: CSS color value for the background
 *   - textColorClass: CSS color class for the text that ensures readability
 */
export function getPM25Color(
  pmValue: number,
  shade: 500 | 700 | 300 | 100 = 500
): { bgColor: string; textColorClass: string } {
  let result = ChartColorsType.DEFAULT;

  const level = getMeasurementLevel(MeasureNames.PM25, pmValue);

  if (level === MeasurementLevels.GOOD) {
    result = ChartColorsType.GREEN;
  } else if (level === MeasurementLevels.MODERATE) {
    result = ChartColorsType.YELLOW;
  } else if (level === MeasurementLevels.UNHEALTHY_SENSITIVE_GROUPS) {
    result = ChartColorsType.ORANGE;
  } else if (level === MeasurementLevels.UNHEALTHY) {
    result = ChartColorsType.RED;
  } else if (level === MeasurementLevels.VERY_UNHEALTHY) {
    result = ChartColorsType.VIOLET;
  } else if (level === MeasurementLevels.HAZARDOUS) {
    result = ChartColorsType.PURPLE;
  }

  return {
    bgColor: getAQColorByShade(result, shade),
    textColorClass: 'text-light'
  };
}

function getAQColorByShade(color: ChartColorsType, shade: 500 | 700 | 300 | 100 = 500): string {
  switch (shade) {
    case 700:
      return CHART_COLORS_700_CSS_VARS[color];
    case 300:
      return CHART_COLORS_700_CSS_VARS[color];
    case 100:
      return CHART_COLORS_100_CSS_VARS[color];
    default:
      return CHART_COLORS_500_CSS_VARS[color];
  }
}

/**
 * Gets the color representation for CO2 values.
 *
 * @param {number} rco2Value - The CO2 value in ppm (parts per million)
 * @param {boolean} [dark=false] - Whether to use dark mode colors
 * @returns {{ bgColor: string; textColor: string }} Object containing background and text colors
 *   - bgColor: CSS color value for the background
 *   - textColorClass: CSS color class for the text that ensures readability
 */
export function getCO2Color(
  rco2Value: number,
  shade: 500 | 700 | 300 | 100 = 500
): { bgColor: string; textColorClass: string } {
  let color = ChartColorsType.DEFAULT;

  const level = getMeasurementLevel(MeasureNames.RCO2, rco2Value);

  if (level === MeasurementLevels.GOOD) {
    color = ChartColorsType.GREEN;
  } else if (level === MeasurementLevels.MODERATE) {
    color = ChartColorsType.YELLOW;
  } else if (level === MeasurementLevels.UNHEALTHY_SENSITIVE_GROUPS) {
    color = ChartColorsType.ORANGE;
  } else if (level === MeasurementLevels.INCORRECT) {
    color = ChartColorsType.GRAY;
  }

  return {
    bgColor: getAQColorByShade(color, shade),
    textColorClass: 'text-light'
  };
}

/**
 * Gets the color for AQI value
 *
 * @param {number} aqi - US AQI value (0-500 scale)
 * @returns {{ bgColor: string; textColor: string }} Object containing background and text colors
 *   - bgColor: CSS color value for the background
 *   - textColorClass: CSS color class for the text that ensures readability
 */
export function getAQIColor(
  aqi: number,
  shade: 500 | 700 | 300 | 100 = 500
): { bgColor: string; textColorClass: string } {
  let color = ChartColorsType.DEFAULT;
  const level = getMeasurementLevel(MeasureNames.PM_AQI, aqi);

  if (level === MeasurementLevels.GOOD) {
    color = ChartColorsType.GREEN;
  } else if (level === MeasurementLevels.MODERATE) {
    color = ChartColorsType.YELLOW;
  } else if (level === MeasurementLevels.UNHEALTHY_SENSITIVE_GROUPS) {
    color = ChartColorsType.ORANGE;
  } else if (level === MeasurementLevels.UNHEALTHY) {
    color = ChartColorsType.RED;
  } else if (level === MeasurementLevels.VERY_UNHEALTHY) {
    color = ChartColorsType.VIOLET;
  } else {
    color = ChartColorsType.PURPLE;
  }

  return {
    bgColor: getAQColorByShade(color, shade),
    textColorClass: 'text-light'
  };
}

/**
 * Gets the color for a given measure and value
 *
 * @param {MeasureNames} measure - The measure to get the color for
 * @param {number} value - The value to get the color for
 * @param {boolean} [dark=false] - Whether to use dark mode colors
 * @returns {{ bgColor: string; textColor: string }} Object containing background and text colors
 *   - bgColor: CSS color value for the background
 *   - textColorClass: CSS color class for the text that ensures readability
 */
export function getColorForMeasure(
  measure: MeasureNames,
  value: number,
  shade: 500 | 700 | 300 | 100 = 500
): { bgColor: string; textColorClass: string } {
  switch (measure) {
    case MeasureNames.PM25:
      return getPM25Color(value, shade);
    case MeasureNames.RCO2:
      return getCO2Color(value, shade);
    case MeasureNames.PM_AQI:
      return getAQIColor(value, shade);
    default:
      return { bgColor: '', textColorClass: '' };
  }
}
