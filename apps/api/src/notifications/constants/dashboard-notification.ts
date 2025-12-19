import { DashboardNotificationPayload } from '../dto/dashboard-notification.dto';

export const DEFAULT_DASHBOARD_NOTIFICATION_PAYLOAD: Pick<
  DashboardNotificationPayload,
  | 'active'
  | 'alarmType'
  | 'description'
  | 'locationGroupId'
  | 'locationGroupName'
  | 'operator'
  | 'title'
  | 'triggerDelay'
  | 'triggerOnlyWhenOpen'
  | 'triggerType'
> = {
  active: true,
  alarmType: 'location',
  description: 'Threshold alarm generated via mobile application',
  locationGroupId: null,
  locationGroupName: null,
  operator: 'greater',
  title: 'Mobile App Notification',
  triggerDelay: 0,
  triggerOnlyWhenOpen: false,
  triggerType: 'always',
};
