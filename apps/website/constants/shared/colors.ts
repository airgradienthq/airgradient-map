/**
 * @fileoverview Color constants that correspond to _variables.scss color definitions
 */

import { ChartColorsType, MascotColorsType } from '~/types/shared/colors';

export const CHART_COLORS_CSS_VARS: Record<ChartColorsType, string> = {
  [ChartColorsType.GREEN]: '#1DE206',
  [ChartColorsType.YELLOW]: '#E3E021',
  [ChartColorsType.ORANGE]: '#FF6701',
  [ChartColorsType.RED]: '#E20411',
  [ChartColorsType.PURPLE]: '#7F01E1',
  [ChartColorsType.BROWN]: '#903305',
  [ChartColorsType.BLUE]: '#1b75bc',
  [ChartColorsType.GRAY]: '#778899',
  [ChartColorsType.LIGHTGRAY]: '#d5d5d5',
  [ChartColorsType.DEFAULT]: '#d5d5d5'
};

export const CHART_COLORS_DARKENED_CSS_VARS: Record<ChartColorsType, string> = {
  [ChartColorsType.GREEN]: '#2b9b20',
  [ChartColorsType.YELLOW]: '#c7ac1d',
  [ChartColorsType.ORANGE]: '#b94f04',
  [ChartColorsType.RED]: '#881218',
  [ChartColorsType.PURPLE]: '#521681',
  [ChartColorsType.BROWN]: '#54230b',
  [ChartColorsType.BLUE]: '#134f7e',
  [ChartColorsType.GRAY]: '#4c5660',
  [ChartColorsType.LIGHTGRAY]: '#989696',
  [ChartColorsType.DEFAULT]: '#989696'
};

export const CHART_COLORS_LIGHT_VAR: Record<ChartColorsType, string> = {
  [ChartColorsType.GREEN]: '#d2f7d3',
  [ChartColorsType.YELLOW]: '#fff7d4',
  [ChartColorsType.ORANGE]: '#f9d1b7',
  [ChartColorsType.RED]: '#ffd9e1',
  [ChartColorsType.PURPLE]: '#f6d2ff',
  [ChartColorsType.BROWN]: '#f4c3dc',
  [ChartColorsType.BLUE]: '#1b75bc',
  [ChartColorsType.GRAY]: '#778899',
  [ChartColorsType.LIGHTGRAY]: '#d5d5d5',
  [ChartColorsType.DEFAULT]: '#d5d5d5'
};

export const CHART_COLORS_DARK_VAR: Record<ChartColorsType, string> = {
  [ChartColorsType.GREEN]: '#005121',
  [ChartColorsType.YELLOW]: '#d19500',
  [ChartColorsType.ORANGE]: '#b74a00',
  [ChartColorsType.RED]: '#b2263a',
  [ChartColorsType.PURPLE]: '#58006d',
  [ChartColorsType.BROWN]: '#59122d',
  [ChartColorsType.BLUE]: '#134f7e',
  [ChartColorsType.GRAY]: '#4c5660',
  [ChartColorsType.LIGHTGRAY]: '#989696',
  [ChartColorsType.DEFAULT]: '#989696'
};

export const MASCOT_COLORS_CSS_VARS: Record<MascotColorsType, string> = {
  [MascotColorsType.GREEN]: '#007a31',
  [MascotColorsType.YELLOW]: '#ffd731',
  [MascotColorsType.ORANGE]: '#ff6701',
  [MascotColorsType.PINK]: '#f84b5f',
  [MascotColorsType.VIOLET]: '#ae2ece',
  [MascotColorsType.PURPLE]: '#7f1f44',
  [MascotColorsType.DEFAULT]: '#989696'
};
