import { AQILevels } from 'src/types/shared/aq-levels.types';

export const AQ_LEVELS_COLORS: Record<AQILevels, string> = {
  [AQILevels.GOOD]: '#33CC33',
  [AQILevels.MODERATE]: '#F0B900',
  [AQILevels.UNHEALTHY_SENSITIVE]: '#FF9933',
  [AQILevels.UNHEALTHY]: '#E63333',
  [AQILevels.VERY_UNHEALTHY]: '#9933E6',
  [AQILevels.HAZARDOUS]: '#8C3333',
  [AQILevels.NO_DATA]: '#778899',
} as const;
