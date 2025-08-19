// export const MASCOT_ICONS = [
//     'AQI_Mascot_Icon_Good.svg',
//     'AQI_Mascot_Icon_Moderate.svg',
//     'AQI_Mascot_Icon_Unhealthy_Sensitive_Groups.svg',
//     'AQI_Mascot_Icon_Unhealthy.svg',
//     'AQI_Mascot_Icon_Very_Unhealthy.svg',
//     'AQI_Mascot_Icon_Hazardous.svg'
//   ];

import { MeasurementLevels } from '~/types';

//   export const MASCOT_ICONS_ALT_TEXT = [
//     'Good Mascot Icon',
//     'Moderate Mascot Icon',
//     'Unhealthy for Sensitive Groups Mascot Icon',
//     'Unhealthy Mascot Icon',
//     'Very Unhealthy Mascot Icon',
//     'Hazardous Mascot Icon'
//   ];

export interface MeasurementDisplayIconConfig {
  iconPath: string;
  textLabelIndex: number;
}

export const MEASUREMENT_DISPLAY_ICON_CONFIG_BY_LEVELS: Record<
  MeasurementLevels,
  MeasurementDisplayIconConfig
> = {
  [MeasurementLevels.GOOD]: {
    iconPath: 'AQI_Mascot_Icon_Good.svg',
    textLabelIndex: 0,
  },
  [MeasurementLevels.MODERATE]: {
    iconPath: 'AQI_Mascot_Icon_Moderate.svg',
    textLabelIndex: 1,
  },
  [MeasurementLevels.UNHEALTHY_SENSITIVE_GROUPS]: {
    iconPath: 'AQI_Mascot_Icon_Unhealthy_Sensitive_Groups.svg',
    textLabelIndex: 2,
  },
  [MeasurementLevels.UNHEALTHY]: {
    iconPath: 'AQI_Mascot_Icon_Unhealthy.svg',
    textLabelIndex: 3,
  },
  [MeasurementLevels.VERY_UNHEALTHY]: {
    iconPath: 'AQI_Mascot_Icon_Very_Unhealthy.svg',
    textLabelIndex: 4,
  },
  [MeasurementLevels.HAZARDOUS]: {
    iconPath: 'AQI_Mascot_Icon_Hazardous.svg',
    textLabelIndex: 5,
  },
  [MeasurementLevels.INCORRECT]: {
    iconPath: 'AQI_Mascot_Icon_Moderate.svg',
    textLabelIndex: -1,
  },
};
