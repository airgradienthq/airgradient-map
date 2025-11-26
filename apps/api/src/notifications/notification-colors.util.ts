import { NotificationParameter } from './notification.model';

/**
 * Standard color palette for air quality indicators
 */
const COLOR_PALETTE = {
  GREEN: '#33CC33',
  YELLOW: '#F0B900',
  ORANGE: '#FF9933',
  RED: '#E63333',
  PURPLE: '#9933E6',
  BROWN: '#8C3333',
  BLUE: '#1b75bc',
  GRAY: '#778899',
} as const;

/**
 * Get Android accent color based on parameter type and value
 * Returns hex color in Android ARGB format (FF + hex color)
 */
export function getAndroidAccentColor(
  parameter: NotificationParameter,
  value: number | null,
): string {
  if (value === null) {
    return formatAndroidColor(COLOR_PALETTE.GRAY);
  }

  let hexColor: string;

  switch (parameter) {
    case NotificationParameter.PM25:
      hexColor = getPM25Color(value);
      break;
    case NotificationParameter.RCO2:
      hexColor = getCO2Color(value);
      break;
    case NotificationParameter.TVOC_INDEX:
      hexColor = getTVOCColor(value);
      break;
    case NotificationParameter.NOX_INDEX:
      hexColor = getNOxColor(value);
      break;
    case NotificationParameter.ATMP:
      hexColor = getTemperatureColor();
      break;
    case NotificationParameter.RHUM:
      hexColor = getHumidityColor();
      break;
    default:
      hexColor = COLOR_PALETTE.GRAY;
  }

  return formatAndroidColor(hexColor);
}

/**
 * Convert hex color to Android ARGB format
 * Example: #33CC33 -> FF33CC33
 */
function formatAndroidColor(hexColor: string): string {
  return hexColor.replace('#', 'FF');
}

/**
 * PM2.5 color based on AQI bands
 */
function getPM25Color(pm25: number): string {
  if (pm25 <= 9) {
    return COLOR_PALETTE.GREEN; // Good
  } else if (pm25 <= 35.4) {
    return COLOR_PALETTE.YELLOW; // Moderate
  } else if (pm25 <= 55.4) {
    return COLOR_PALETTE.ORANGE; // Unhealthy for Sensitive
  } else if (pm25 <= 125.4) {
    return COLOR_PALETTE.RED; // Unhealthy
  } else if (pm25 <= 225.4) {
    return COLOR_PALETTE.PURPLE; // Very Unhealthy
  } else {
    return COLOR_PALETTE.BROWN; // Hazardous
  }
}

/**
 * CO2 color based on concentration bands
 * Bands: Excellent (≤800), Good (≤1000), Moderate (≤1500), Poor (≤2000), Dangerous (≤3000), Hazardous (>3000)
 */
function getCO2Color(co2: number): string {
  if (co2 <= 800) {
    return COLOR_PALETTE.GREEN; // Excellent
  } else if (co2 <= 1000) {
    return COLOR_PALETTE.YELLOW; // Good
  } else if (co2 <= 1500) {
    return COLOR_PALETTE.ORANGE; // Moderate
  } else if (co2 <= 2000) {
    return COLOR_PALETTE.RED; // Poor
  } else if (co2 <= 3000) {
    return COLOR_PALETTE.PURPLE; // Dangerous
  } else {
    return COLOR_PALETTE.BROWN; // Hazardous
  }
}

/**
 * TVOC index color based on index bands
 * Bands: Low (≤150), Moderate (≤250), Elevated (≤400), High (>400)
 */
function getTVOCColor(tvocIndex: number): string {
  if (tvocIndex <= 150) {
    return COLOR_PALETTE.GREEN; // Low
  } else if (tvocIndex <= 250) {
    return COLOR_PALETTE.YELLOW; // Moderate
  } else if (tvocIndex <= 400) {
    return COLOR_PALETTE.ORANGE; // Elevated
  } else {
    return COLOR_PALETTE.RED; // High
  }
}

/**
 * NOx index color based on index bands
 * Bands: Low (≤20), Moderate (≤150), Elevated (≤300), High (>300)
 */
function getNOxColor(noxIndex: number): string {
  if (noxIndex <= 20) {
    return COLOR_PALETTE.GREEN; // Low
  } else if (noxIndex <= 150) {
    return COLOR_PALETTE.YELLOW; // Moderate
  } else if (noxIndex <= 300) {
    return COLOR_PALETTE.ORANGE; // Elevated
  } else {
    return COLOR_PALETTE.RED; // High
  }
}

/**
 * Temperature color - fixed blue color
 */
function getTemperatureColor(): string {
  return COLOR_PALETTE.BLUE;
}

/**
 * Humidity color - fixed blue color
 */
function getHumidityColor(): string {
  return COLOR_PALETTE.BLUE;
}
