export enum AlarmType {
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
  imageUrl?: string;
  retries?: number;
  lastError?: string;
}

export interface BatchResult {
  successful: string[];
  failed: Array<{ playerId: string; error: string }>;
  totalTime: number;
}
