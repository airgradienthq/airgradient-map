import {
  CHART_COLORS_CSS_VARS,
  CHART_COLORS_DARKENED_CSS_VARS,
  CHART_COLORS_LIGHT_VAR,
  CHART_COLORS_DARK_VAR
} from '~/constants/shared/colors';
import { ChartColorsType, MeasureNames } from '~/types';

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
  dark = false,
  newChartColors = false
): { bgColor: string; textColorClass: string } {
  let result = ChartColorsType.DEFAULT;

  if (pmValue <= 9) {
    result = ChartColorsType.GREEN;
  } else if (pmValue <= 35.4) {
    result = ChartColorsType.YELLOW;
  } else if (pmValue <= 55.4) {
    result = ChartColorsType.ORANGE;
  } else if (pmValue <= 125.4) {
    result = ChartColorsType.RED;
  } else if (pmValue <= 225.4) {
    result = ChartColorsType.PURPLE;
  } else {
    result = ChartColorsType.BROWN;
  }

  if (newChartColors) {
    return {
      bgColor: dark ? CHART_COLORS_DARK_VAR[result] : CHART_COLORS_LIGHT_VAR[result],
      textColorClass: getTextColorClassForBG(result, dark)
    };
  }
  return {
    bgColor: dark ? CHART_COLORS_DARKENED_CSS_VARS[result] : CHART_COLORS_CSS_VARS[result],
    textColorClass: getTextColorClassForBG(result, dark)
  };
}

/**
 * Determines the appropriate text color for a given background color type.
 *
 * @param {ChartColorsType} bgColor - The background color type from ChartColorsType enum
 * @param {boolean} [isBGDark=false] - Whether the background is using dark mode colors
 * @returns {string} CSS class for text that ensures readable contrast
 * @private
 */
function getTextColorClassForBG(bgColor: ChartColorsType, isBGDark: boolean = false): string {
  return [ChartColorsType.GREEN, ChartColorsType.YELLOW].includes(bgColor) && !isBGDark
    ? 'text-dark'
    : 'text-light';
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
  dark = false,
  newChartColors = false
): { bgColor: string; textColorClass: string } {
  let color = ChartColorsType.DEFAULT;
  const configuration = [
    { index: 1, color: ChartColorsType.GREEN, max: 449, label: 'excellent' },
    { index: 2, color: ChartColorsType.YELLOW, max: 499, label: 'good' },
    { index: 3, color: ChartColorsType.ORANGE, max: 799, label: 'moderate' },
    { index: 4, color: ChartColorsType.GRAY, max: 10000, label: 'incorrect' }
  ];

  configuration?.sort((a, b) => b.index - a.index);
  configuration?.forEach(configItem => {
    if (rco2Value <= configItem.max) {
      color = configItem.color;
    }
  });

  if (newChartColors) {
    return {
      bgColor: dark ? CHART_COLORS_DARK_VAR[color] : CHART_COLORS_LIGHT_VAR[color],
      textColorClass: getTextColorClassForBG(color, dark)
    };
  }
  return {
    bgColor: dark ? CHART_COLORS_DARKENED_CSS_VARS[color] : CHART_COLORS_CSS_VARS[color],
    textColorClass: getTextColorClassForBG(color, dark)
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
export function getAQIColor(aqi: number): { bgColor: string; textColorClass: string } {
  let color = ChartColorsType.DEFAULT;

  if (aqi <= 50) {
    color = ChartColorsType.GREEN;
  } else if (aqi <= 100) {
    color = ChartColorsType.YELLOW;
  } else if (aqi <= 150) {
    color = ChartColorsType.ORANGE;
  } else if (aqi <= 200) {
    color = ChartColorsType.RED;
  } else if (aqi <= 300) {
    color = ChartColorsType.PURPLE;
  } else {
    color = ChartColorsType.BROWN;
  }

  return {
    bgColor: CHART_COLORS_CSS_VARS[color],
    textColorClass: getTextColorClassForBG(color)
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
  dark = false
): { bgColor: string; textColorClass: string } {
  switch (measure) {
    case MeasureNames.PM25:
      return getPM25Color(value, dark);
    case MeasureNames.RCO2:
      return getCO2Color(value, dark);
    case MeasureNames.PM_AQI:
      return getAQIColor(value);
    default:
      return { bgColor: '', textColorClass: '' };
  }
}
