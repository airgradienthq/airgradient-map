import { MeasurementLevels, MeasureNames } from '~/types';

export function getMeasurementLevel(measure: MeasureNames, value: number): MeasurementLevels {
  switch (measure) {
    case MeasureNames.PM25:
      return getPM25Level(value);
    case MeasureNames.CO2:
      return getCO2Level(value);
    default:
      return getAQILevel(value);
  }
}

function getPM25Level(value: number): MeasurementLevels {
  if (value <= 9) {
    return MeasurementLevels.GOOD;
  } else if (value <= 35.4) {
    return MeasurementLevels.MODERATE;
  } else if (value <= 55.4) {
    return MeasurementLevels.UNHEALTHY_SENSITIVE_GROUPS;
  } else if (value <= 125.4) {
    return MeasurementLevels.UNHEALTHY;
  } else if (value <= 225.4) {
    return MeasurementLevels.VERY_UNHEALTHY;
  } else if (value <= 10000) {
    return MeasurementLevels.HAZARDOUS;
  }
  return MeasurementLevels.GOOD;
}

function getCO2Level(value: number): MeasurementLevels {
  if (value <= 449) {
    return MeasurementLevels.GOOD;
  } else if (value <= 549) {
    return MeasurementLevels.MODERATE;
  } else if (value <= 749) {
    return MeasurementLevels.UNHEALTHY_SENSITIVE_GROUPS;
  } else if (value <= 10000) {
    return MeasurementLevels.INCORRECT;
  }
  return MeasurementLevels.GOOD;
}

function getAQILevel(value: number): MeasurementLevels {
  if (value <= 50) {
    return MeasurementLevels.GOOD;
  } else if (value <= 100) {
    return MeasurementLevels.MODERATE;
  } else if (value <= 150) {
    return MeasurementLevels.UNHEALTHY_SENSITIVE_GROUPS;
  } else if (value <= 200) {
    return MeasurementLevels.UNHEALTHY;
  } else if (value <= 300) {
    return MeasurementLevels.VERY_UNHEALTHY;
  }
  return MeasurementLevels.HAZARDOUS;
}
