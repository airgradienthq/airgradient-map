import { NotificationParameter } from './notification.model';

/**
 * Get mascot image URL based on parameter type and value
 */
export function getMascotImageUrl(
  parameter: NotificationParameter,
  value: number | null,
): string {
  if (value === null) {
    return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-no-data.png';
  }

  switch (parameter) {
    case NotificationParameter.PM25:
      return getPM25MascotUrl(value);
    case NotificationParameter.RCO2:
      return getCO2MascotUrl(value);
    case NotificationParameter.TVOC_INDEX:
      return getTVOCMascotUrl(value);
    case NotificationParameter.NOX_INDEX:
      return getNOxMascotUrl(value);
    case NotificationParameter.ATMP:
      return getTemperatureMascotUrl();
    case NotificationParameter.RHUM:
      return getHumidityMascotUrl();
    default:
      return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-no-data.png';
  }
}

/**
 * PM2.5 mascot with AQI-based bands
 * Based on US EPA AQI breakpoints
 */
function getPM25MascotUrl(pm25: number): string {
  if (pm25 <= 9) {
    return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-good.png';
  } else if (pm25 <= 35.4) {
    return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-moderate.png';
  } else if (pm25 <= 55.4) {
    return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-unhealthy-sensitive.png';
  } else if (pm25 <= 125.4) {
    return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-unhealthy.png';
  } else if (pm25 <= 225.4) {
    return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-very-unhealthy.png';
  } else {
    return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-hazardous.png';
  }
}

/**
 * CO2 mascot with concentration-based bands
 * Based on indoor air quality standards
 * Bands: Excellent (≤800), Good (≤1000), Moderate (≤1500), Poor (≤2000), Dangerous (≤3000), Hazardous (>3000)
 */
function getCO2MascotUrl(co2: number): string {
  if (co2 <= 800) {
    // Excellent (green)
    return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-good.png';
  } else if (co2 <= 1000) {
    // Good (yellow)
    return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-moderate.png';
  } else if (co2 <= 1500) {
    // Moderate (orange)
    return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-unhealthy-sensitive.png';
  } else if (co2 <= 2000) {
    // Poor (red)
    return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-unhealthy.png';
  } else if (co2 <= 3000) {
    // Dangerous (purple)
    return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-very-unhealthy.png';
  } else {
    // Hazardous (brown) - covers up to 10000
    return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-hazardous.png';
  }
}

/**
 * TVOC index mascot with index-based bands
 * Bands: Low (≤150), Moderate (≤250), Elevated (≤400), High (>400)
 */
function getTVOCMascotUrl(tvocIndex: number): string {
  if (tvocIndex <= 150) {
    // Low (green)
    return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-good.png';
  } else if (tvocIndex <= 250) {
    // Moderate (yellow)
    return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-moderate.png';
  } else if (tvocIndex <= 400) {
    // Elevated (orange)
    return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-unhealthy-sensitive.png';
  } else {
    // High (red)
    return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-unhealthy.png';
  }
}

/**
 * NOx index mascot with index-based bands
 * Bands: Low (≤20), Moderate (≤150), Elevated (≤300), High (>300)
 */
function getNOxMascotUrl(noxIndex: number): string {
  if (noxIndex <= 20) {
    // Low (green)
    return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-good.png';
  } else if (noxIndex <= 150) {
    // Moderate (yellow)
    return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-moderate.png';
  } else if (noxIndex <= 300) {
    // Elevated (orange)
    return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-unhealthy-sensitive.png';
  } else {
    // High (red)
    return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-unhealthy.png';
  }
}

/**
 * Temperature mascot - single image, no bands
 */
function getTemperatureMascotUrl(): string {
  return 'https://www.airgradient.com/images/alert-icons-mascot/temperature.png';
}

/**
 * Humidity mascot - single image, no bands
 */
function getHumidityMascotUrl(): string {
  return 'https://www.airgradient.com/images/alert-icons-mascot/humidity.png';
}
