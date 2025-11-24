import { NotificationDisplayUnit } from './notification.model';

export const NOTIFICATION_UNIT_LABELS: Record<NotificationDisplayUnit, string> = {
  [NotificationDisplayUnit.UG]: 'μg/m³',
  [NotificationDisplayUnit.US_AQI]: 'US AQI',
  [NotificationDisplayUnit.PPM]: 'ppm',
  [NotificationDisplayUnit.INDEX]: 'index',
  [NotificationDisplayUnit.CELSIUS]: '°C',
  [NotificationDisplayUnit.FAHRENHEIT]: '°F',
  [NotificationDisplayUnit.PERCENT]: '%',
};
