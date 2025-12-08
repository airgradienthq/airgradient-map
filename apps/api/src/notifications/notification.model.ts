export enum NotificationType {
  THRESHOLD = 'threshold',
  SCHEDULED = 'scheduled',
}

export enum NotificationPMUnit {
  UG = 'ug',
  US_AQI = 'us_aqi',
}

export interface NotificationJob {
  playerId: string;
  locationName: string;
  value: number;
  unit: NotificationPMUnit;
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
