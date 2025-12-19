export interface DashboardNotificationPayload {
  active: boolean;
  alarmType: string;
  description: string;
  locationGroupId: number | null;
  locationGroupName: string | null;
  locationId: number;
  measure: string;
  operator: string;
  threshold: number;
  title: string;
  triggerDelay: number;
  triggerOnlyWhenOpen: boolean;
  triggerType: string;
}
