import { Injectable } from '@nestjs/common';
import DatabaseService from 'src/database/database.service';
import { NotificationEntity } from './notification.entity';

@Injectable()
export class NotificationsRepository {
  constructor(private readonly databaseService: DatabaseService) {}

  async createNotification(notification: NotificationEntity): Promise<NotificationEntity> {
    const result = await this.databaseService.runQuery(
      `INSERT INTO notifications (
        player_id, user_id, alarm_type, location_id,
        threshold_ug_m3, threshold_category, threshold_cycle,
        scheduled_days, scheduled_time, scheduled_timezone,
        active, unit
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
      RETURNING *`,
      [
        notification.player_id,
        notification.user_id,
        notification.alarm_type,
        notification.location_id,
        notification.threshold_ug_m3,
        notification.threshold_category,
        notification.threshold_cycle,
        notification.scheduled_days,
        notification.scheduled_time,
        notification.scheduled_timezone,
        notification.active,
        notification.unit,
      ],
    );
    return result.rows[0];
  }

  async getNotifications(playerId: string = null, locationId: number = null): Promise<NotificationEntity[]> {
    let query = 'SELECT * FROM notifications';
    const params = [];
    const conditions = [];

    if (playerId) {
      conditions.push(`player_id = $${params.length + 1}`);
      params.push(playerId);
    }
    if (locationId) {
      conditions.push(`location_id = $${params.length + 1}`);
      params.push(locationId);
    }

    if (conditions.length > 0) {
      query += ' WHERE ' + conditions.join(' AND ');
    }

    const result = await this.databaseService.runQuery(query, params);
    return result.rows;
  }

  async getNotificationById(id: string): Promise<NotificationEntity> {
    const result = await this.databaseService.runQuery('SELECT * FROM notifications WHERE id = $1', [id]);
    return result.rows[0];
  }

  async updateNotification(notification: NotificationEntity): Promise<NotificationEntity> {
    const result = await this.databaseService.runQuery(
      'UPDATE notifications SET player_id = $1, user_id = $2, alarm_type = $3, location_id = $4, threshold_ug_m3 = $5, threshold_category = $6, threshold_cycle = $7, scheduled_days = $8, scheduled_time = $9, scheduled_timezone = $10, active = $11, unit = $12 WHERE id = $13 RETURNING *',
      [
        notification.player_id,
        notification.user_id,
        notification.alarm_type,
        notification.location_id,
        notification.threshold_ug_m3,
        notification.threshold_category,
        notification.threshold_cycle,
        notification.scheduled_days,
        notification.scheduled_time,
        notification.scheduled_timezone,
        notification.active,
        notification.unit,
        notification.id,
      ],
    );
    return result.rows[0];
  }

  async deleteNotificationById(id: string): Promise<void> {
    await this.databaseService.runQuery('DELETE FROM notifications WHERE id = $1', [id]);
  }

  async getScheduledNotificationsForNow(): Promise<NotificationEntity[]> {
    const query = `
      SELECT * FROM notifications
      WHERE active = true
        AND alarm_type = 'scheduled'
        AND scheduled_time = LPAD(EXTRACT(HOUR FROM (NOW() AT TIME ZONE scheduled_timezone))::text, 2, '0')
                          || ':' ||
                          LPAD(EXTRACT(MINUTE FROM (NOW() AT TIME ZONE scheduled_timezone))::text, 2, '0')
        AND scheduled_days @> ARRAY[
          CASE EXTRACT(DOW FROM (NOW() AT TIME ZONE scheduled_timezone))
            WHEN 0 THEN 'sunday'
            WHEN 1 THEN 'monday'
            WHEN 2 THEN 'tuesday'
            WHEN 3 THEN 'wednesday'
            WHEN 4 THEN 'thursday'
            WHEN 5 THEN 'friday'
            WHEN 6 THEN 'saturday'
          END
        ]
    `;

    const result = await this.databaseService.runQuery(query, []);
    return result.rows;
  }
}
