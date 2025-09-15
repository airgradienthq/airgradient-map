import { MeasureNames } from '~/types';

export function getChartFontSize(measure: string) {
  let annotationsFontSize = 12;

  if (measure === MeasureNames.RCO2) {
    return 11;
  }

  if (window.innerWidth < 900) {
    annotationsFontSize = 11;
  }
  if (window.innerWidth < 768) {
    annotationsFontSize = 10;
  }
  if (window.innerWidth < 414) {
    annotationsFontSize = 9;
  }
  if (window.innerWidth < 375) {
    annotationsFontSize = 8;
  }
  return annotationsFontSize;
}
