/**
 * @fileoverview Color constants that correspond to _variables.scss color definitions
 */

import { ChartColorsType } from '~/types/shared/colors';

export const CHART_COLORS_700_CSS_VARS: Record<ChartColorsType, string> = {
  [ChartColorsType.GREEN]: '#005108',
  [ChartColorsType.YELLOW]: '#D19500',
  [ChartColorsType.ORANGE]: '#B74A00',
  [ChartColorsType.RED]: '#770E22',
  [ChartColorsType.PURPLE]: '#401172',
  [ChartColorsType.BROWN]: '#5B1B14',
  [ChartColorsType.BLUE]: '#003351',
  [ChartColorsType.GRAY]: '#595959',
  [ChartColorsType.LIGHTGRAY]: '#868686',
  [ChartColorsType.DEFAULT]: '#868686'
};

export const CHART_COLORS_500_CSS_VARS: Record<ChartColorsType, string> = {
  [ChartColorsType.GREEN]: '#58D32F',
  [ChartColorsType.YELLOW]: '#FFDA3E',
  [ChartColorsType.ORANGE]: '#FF9300',
  [ChartColorsType.RED]: '#E1243B',
  [ChartColorsType.PURPLE]: '#7C2DC1',
  [ChartColorsType.BROWN]: '#822B22',
  [ChartColorsType.BLUE]: '#1b75bc',
  [ChartColorsType.GRAY]: '#a3a1a1',
  [ChartColorsType.LIGHTGRAY]: '#d9d9d9',
  [ChartColorsType.DEFAULT]: '#d9d9d9'
};

export const CHART_COLORS_300_CSS_VARS: Record<ChartColorsType, string> = {
  [ChartColorsType.GREEN]: '#2A7F0A',
  [ChartColorsType.YELLOW]: '#FFE7B3',
  [ChartColorsType.ORANGE]: '#ffa466',
  [ChartColorsType.RED]: '#ffb3c3',
  [ChartColorsType.PURPLE]: '#BF92EF',
  [ChartColorsType.BROWN]: '#CE615B',
  [ChartColorsType.BLUE]: '#33a9d6',
  [ChartColorsType.GRAY]: '#a3a1a1',
  [ChartColorsType.LIGHTGRAY]: '#d9d9d9',
  [ChartColorsType.DEFAULT]: '#d9d9d9'
};

export const CHART_COLORS_100_CSS_VARS: Record<ChartColorsType, string> = {
  [ChartColorsType.GREEN]: '#d2f7d3',
  [ChartColorsType.YELLOW]: '#FFF7D4',
  [ChartColorsType.ORANGE]: '#FFE5D7',
  [ChartColorsType.RED]: '#FFD9E1',
  [ChartColorsType.PURPLE]: '#EAD7FF',
  [ChartColorsType.BROWN]: '#FFE4E3',
  [ChartColorsType.BLUE]: '#d3e9f9',
  [ChartColorsType.GRAY]: '#d9d9d9',
  [ChartColorsType.LIGHTGRAY]: '#F8F2EB',
  [ChartColorsType.DEFAULT]: '#F8F2EB'
};
