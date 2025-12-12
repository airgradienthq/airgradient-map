import { AQILevels } from 'src/types/shared/aq-levels.types';

export const AQ_LEVELS_COLORS: Record<AQILevels, string> = {
  [AQILevels.GOOD]: '#58D32F',
  [AQILevels.MODERATE]: '#FFDA3E',
  [AQILevels.UNHEALTHY_SENSITIVE]: '#FF9300',
  [AQILevels.UNHEALTHY]: '#E1243B',
  [AQILevels.VERY_UNHEALTHY]: '#7C2DC1',
  [AQILevels.HAZARDOUS]: '#822B22',
  [AQILevels.NO_DATA]: '#778899',
} as const;
