/**
 * @fileoverview Color constants that correspond to _variables.scss color definitions
 */

import { ChartColorsType } from '~/types/shared/colors';

export const CHART_COLORS_CSS_VARS: Record<ChartColorsType, string> = {
  [ChartColorsType.GREEN]: '#007a31',
  [ChartColorsType.YELLOW]: '#ffd731',
  [ChartColorsType.ORANGE]: '#ff6701',
  [ChartColorsType.RED]: '#e20411',
  [ChartColorsType.VIOLET]: '#ae2ece',
  [ChartColorsType.PURPLE]: '#7f1f44',
  [ChartColorsType.BLUE]: '#1b75bc',
  [ChartColorsType.GRAY]: '#a3a1a1',
  [ChartColorsType.LIGHTGRAY]: '#d9d9d9',
  [ChartColorsType.DEFAULT]: '#d9d9d9'
};

export const CHART_COLORS_DARK_CSS_VARS: Record<ChartColorsType, string> = {
  [ChartColorsType.GREEN]: '#005121',
  [ChartColorsType.YELLOW]: '#d19500',
  [ChartColorsType.ORANGE]: '#b74a00',
  [ChartColorsType.RED]: '#7a1f23',
  [ChartColorsType.VIOLET]: '#58006d',
  [ChartColorsType.PURPLE]: '#59122d',
  [ChartColorsType.BLUE]: '#003351',
  [ChartColorsType.GRAY]: '#595959',
  [ChartColorsType.LIGHTGRAY]: '#868686',
  [ChartColorsType.DEFAULT]: '#868686'
};

export const CHART_COLORS_MEDIUM_CSS_VARS: Record<ChartColorsType, string> = {
  [ChartColorsType.GREEN]: '#34d675',
  [ChartColorsType.YELLOW]: '#ffe471',
  [ChartColorsType.ORANGE]: '#ffa466',
  [ChartColorsType.RED]: '#f77171',
  [ChartColorsType.VIOLET]: '#da8eed',
  [ChartColorsType.PURPLE]: '#ce5b8a',
  [ChartColorsType.BLUE]: '#33a9d6',
  [ChartColorsType.GRAY]: '#a3a1a1',
  [ChartColorsType.LIGHTGRAY]: '#d9d9d9',
  [ChartColorsType.DEFAULT]: '#d9d9d9'
};

export const CHART_COLORS_LIGHT_CSS_VARS: Record<ChartColorsType, string> = {
  [ChartColorsType.GREEN]: '#d2f7d3',
  [ChartColorsType.YELLOW]: '#fff7d4',
  [ChartColorsType.ORANGE]: '#f9d1b7',
  [ChartColorsType.RED]: '#fde8e8',
  [ChartColorsType.VIOLET]: '#f6d2ff',
  [ChartColorsType.PURPLE]: '#f4c3dc',
  [ChartColorsType.BLUE]: '#d3e9f9',
  [ChartColorsType.GRAY]: '#d9d9d9',
  [ChartColorsType.LIGHTGRAY]: '#F8F2EB',
  [ChartColorsType.DEFAULT]: '#F8F2EB'
};
