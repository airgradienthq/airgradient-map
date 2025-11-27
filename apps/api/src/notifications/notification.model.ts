export enum NotificationType {
  THRESHOLD = 'threshold',
  SCHEDULED = 'scheduled',
}

export enum NotificationDisplayUnit {
  UG = 'ug',
  US_AQI = 'us_aqi',
  PPM = 'ppm',
  INDEX = 'index',
  CELSIUS = 'celsius',
  FAHRENHEIT = 'fahrenheit',
  PERCENT = 'percent',
}

export enum NotificationParameter {
  PM25 = 'pm25',
  RCO2 = 'rco2',
  TVOC_INDEX = 'tvoc_index',
  NOX_INDEX = 'nox_index',
  ATMP = 'atmp',
  RHUM = 'rhum',
}

export enum MonitorType {
  OWNED = 'owned',
  PUBLIC = 'public',
}

/**
 * Maps each parameter to its valid display units
 */
export const PARAMETER_VALID_UNITS: Record<NotificationParameter, NotificationDisplayUnit[]> = {
  [NotificationParameter.PM25]: [NotificationDisplayUnit.UG, NotificationDisplayUnit.US_AQI],
  [NotificationParameter.RCO2]: [NotificationDisplayUnit.PPM],
  [NotificationParameter.TVOC_INDEX]: [NotificationDisplayUnit.INDEX],
  [NotificationParameter.NOX_INDEX]: [NotificationDisplayUnit.INDEX],
  [NotificationParameter.ATMP]: [NotificationDisplayUnit.CELSIUS, NotificationDisplayUnit.FAHRENHEIT],
  [NotificationParameter.RHUM]: [NotificationDisplayUnit.PERCENT],
};

export interface NotificationJob {
  playerId: string;
  locationName: string;
  value: number;
  unit: NotificationDisplayUnit;
  unitLabel: string;
  imageUrl?: string;
  title?: { en: string; de: string };
  retries?: number;
  lastError?: string;
  androidAccentColor?: string;
  isScheduledNotificationNoData?: boolean;
}

export interface LatestLocationMeasurementData {
  locationId: number;
  pm25: number | null;
  rco2: number | null;
  tvoc_index: number | null;
  nox_index: number | null;
  atmp: number | null;
  rhum: number | null;
  measuredAt: Date;
  locationName: string;
  sensorType: string;
  dataSource: string;
}

export interface BatchResult {
  successful: string[];
  failed: Array<{ playerId: string; error: string }>;
  totalTime: number;
}
