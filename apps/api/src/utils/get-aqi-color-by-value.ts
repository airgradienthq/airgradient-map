import { AQILevels } from "src/types/shared/aq-levels.types";

export function getAQIColor(pm25: number): AQILevels {
    let color = AQILevels.GOOD;
  
    if (pm25 <= 9) {
      color = AQILevels.GOOD;
    } else if (pm25 <= 35.4) {
      color = AQILevels.MODERATE;
    } else if (pm25 <= 55.4) {
      color = AQILevels.UNHEALTHY_SENSITIVE;
    } else if (pm25 <= 125.4) {
      color = AQILevels.UNHEALTHY;
    } else if (pm25 <= 225.4) {
      color = AQILevels.VERY_UNHEALTHY;
    } else {
      color = AQILevels.HAZARDOUS;
    }
  
    return color;
  }

