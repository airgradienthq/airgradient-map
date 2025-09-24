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

  @ApiProperty()
  alarm_type: AlarmType;

  @ApiProperty()
  location_id: number;

  @ApiProperty()
  threshold_ug_m3: number | null;

  @ApiProperty()
  threshold_category: string | null;

  @ApiProperty()
  threshold_cycle: string | null;

  @ApiProperty()
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
