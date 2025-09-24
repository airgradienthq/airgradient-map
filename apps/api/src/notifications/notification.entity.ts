import { ApiProperty } from '@nestjs/swagger';
import { AlarmType, NotificationPMUnit } from './notification.model';

export class NotificationEntity {
  @ApiProperty()
  id: number;

  @ApiProperty()
  player_id: string;

  @ApiProperty()
  created_at: Date;

  @ApiProperty()
  updated_at: Date;

  @ApiProperty()
  user_id: string;

  @ApiProperty({
    enum: AlarmType,
    enumName: 'AlarmType',
    description: 'Type of notification: threshold-based or scheduled'
  })
  alarm_type: AlarmType;

  @ApiProperty()
  location_id: number;

  @ApiProperty()
  threshold_ug_m3: number | null;

  @ApiProperty()
  threshold_cycle: string | null;

  @ApiProperty({
    description: 'Days on which scheduled notifications are sent. Empty array means no notifications will be sent.',
    type: [String],
    enum: ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'],
  })
  scheduled_days: string[];

  @ApiProperty()
  scheduled_time: string;

  @ApiProperty()
  scheduled_timezone: string;

  @ApiProperty()
  active: boolean;

  @ApiProperty({
    enum: NotificationPMUnit,
    enumName: 'NotificationPMUnit',
  })
  unit: NotificationPMUnit;

  constructor(partial: Partial<NotificationEntity>) {
    Object.assign(this, partial);
  }
}
