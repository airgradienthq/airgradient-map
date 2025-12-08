/**
 * @fileoverview Color constants that correspond to _variables.scss color definitions
 */

import { ChartColorsType } from '~/types/shared/colors';

export const CHART_COLORS_700_CSS_VARS: Record<ChartColorsType, string> = {
  [ChartColorsType.GREEN]: '#007a30',
  [ChartColorsType.YELLOW]: '#9c8804',
  [ChartColorsType.ORANGE]: '#c46404',
  [ChartColorsType.RED]: '#9f1c1c',
  [ChartColorsType.PURPLE]: '#6e2385',
  [ChartColorsType.BROWN]: '#3e0808',
  [ChartColorsType.BLUE]: '#003351',
  [ChartColorsType.GRAY]: '#595959',
  [ChartColorsType.LIGHTGRAY]: '#868686',
  [ChartColorsType.DEFAULT]: '#868686'
};

export const CHART_COLORS_500_CSS_VARS: Record<ChartColorsType, string> = {
  [ChartColorsType.GREEN]: '#33cb33',
  [ChartColorsType.YELLOW]: '#d7cb21',
  [ChartColorsType.ORANGE]: '#f7922e',
  [ChartColorsType.RED]: '#e53332',
  [ChartColorsType.PURPLE]: '#994de5',
  [ChartColorsType.BROWN]: '#8c3333',
  [ChartColorsType.BLUE]: '#1b75bc',
  [ChartColorsType.GRAY]: '#a3a1a1',
  [ChartColorsType.LIGHTGRAY]: '#d9d9d9',
  [ChartColorsType.DEFAULT]: '#d9d9d9'
};

export const CHART_COLORS_300_CSS_VARS: Record<ChartColorsType, string> = {
  [ChartColorsType.GREEN]: '#3eec83',
  [ChartColorsType.YELLOW]: '#fbe582',
  [ChartColorsType.ORANGE]: '#ffa466',
  [ChartColorsType.RED]: '#f79090',
  [ChartColorsType.PURPLE]: '#c787f7',
  [ChartColorsType.BROWN]: '#b86464',
  [ChartColorsType.BLUE]: '#33a9d6',
  [ChartColorsType.GRAY]: '#a3a1a1',
  [ChartColorsType.LIGHTGRAY]: '#d9d9d9',
  [ChartColorsType.DEFAULT]: '#d9d9d9'
};

export const CHART_COLORS_100_CSS_VARS: Record<ChartColorsType, string> = {
  [ChartColorsType.GREEN]: '#d2f7d3',
  [ChartColorsType.YELLOW]: '#fff7d4',
  [ChartColorsType.ORANGE]: '#f9ddc9',
  [ChartColorsType.RED]: '#fde8e8',
  [ChartColorsType.PURPLE]: '#f6d2ff',
  [ChartColorsType.BROWN]: '#fac3c3',
  [ChartColorsType.BLUE]: '#d3e9f9',
  [ChartColorsType.GRAY]: '#d9d9d9',
  [ChartColorsType.LIGHTGRAY]: '#F8F2EB',
  [ChartColorsType.DEFAULT]: '#F8F2EB'
};
