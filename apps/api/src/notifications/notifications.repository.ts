import { Injectable, Logger } from '@nestjs/common';
import DatabaseService from 'src/database/database.service';
import { NotificationEntity } from './notification.entity';

@Injectable()
export class NotificationsRepository {
  private readonly logger = new Logger(NotificationsRepository.name);

  constructor(private readonly databaseService: DatabaseService) {}

  async createNotification(notification: NotificationEntity): Promise<NotificationEntity> {
    try {
      const result = await this.databaseService.runQuery(
        `INSERT INTO notifications (
          player_id, user_id, alarm_type, location_id,
          threshold_ug_m3, threshold_cycle,
          scheduled_days, scheduled_time, scheduled_timezone,
          active, unit
        ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
        RETURNING *`,
        [
          notification.player_id,
          notification.user_id,
          notification.alarm_type,
          notification.location_id,
          notification.threshold_ug_m3,
          notification.threshold_cycle,
          notification.scheduled_days,
          notification.scheduled_time,
          notification.scheduled_timezone,
          notification.active,
          notification.unit,
        ],
      );
      return result.rows[0];
    } catch (error) {
      this.logger.error('Failed to create notification', { error: error.message });
      throw error;
    }
  }

  async getNotifications(
    playerId: string = null,
    locationId: number = null,
  ): Promise<NotificationEntity[]> {
    try {
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
    } catch (error) {
      this.logger.error('Failed to get notifications', { error: error.message });
      return [];
    }
  }

  async getNotificationById(id: string): Promise<NotificationEntity> {
    try {
      const result = await this.databaseService.runQuery(
        'SELECT * FROM notifications WHERE id = $1',
        [id],
      );
      return result.rows[0];
    } catch (error) {
      this.logger.error('Failed to get notification by id', {
        notificationId: id,
        error: error.message,
      });
      return null;
    }
  }

  async updateNotification(notification: NotificationEntity): Promise<NotificationEntity> {
    try {
      const result = await this.databaseService.runQuery(
        'UPDATE notifications SET player_id = $1, user_id = $2, alarm_type = $3, location_id = $4, threshold_ug_m3 = $5, threshold_cycle = $6, scheduled_days = $7, scheduled_time = $8, scheduled_timezone = $9, active = $10, unit = $11 WHERE id = $12 RETURNING *',
        [
          notification.player_id,
          notification.user_id,
          notification.alarm_type,
          notification.location_id,
          notification.threshold_ug_m3,
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
    } catch (error) {
      this.logger.error('Failed to update notification', {
        notificationId: notification.id,
        error: error.message,
      });
      throw error;
    }
  }

  async deleteNotificationById(id: string): Promise<void> {
    try {
      await this.databaseService.runQuery('DELETE FROM notifications WHERE id = $1', [id]);
    } catch (error) {
      this.logger.error('Failed to delete notification', {
        notificationId: id,
        error: error.message,
      });
      throw error;
    }
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

    try {
      const result = await this.databaseService.runQuery(query, []);
      return result.rows;
    } catch (error) {
      this.logger.error('Failed to fetch scheduled notifications', {
        error: error.message,
      });

      return [];
    }
  }

  async getActiveThresholdNotifications(): Promise<NotificationEntity[]> {
    try {
      const result = await this.databaseService.runQuery(
        `SELECT * FROM notifications
         WHERE active = true
           AND alarm_type = 'threshold'
         ORDER BY id`,
        [],
      );
      return result.rows;
    } catch (error) {
      this.logger.error('Failed to get active threshold notifications', { error: error.message });
      return [];
    }
  }

  async updateNotificationState(
    id: number,
    updates: { was_exceeded?: boolean; last_notified_at?: Date },
  ): Promise<void> {
    try {
      const setParts = [];
      const values = [];
      let paramIndex = 1;

      if (updates.was_exceeded !== undefined) {
        setParts.push(`was_exceeded = $${paramIndex++}`);
        values.push(updates.was_exceeded);
      }

      if (updates.last_notified_at !== undefined) {
        setParts.push(`last_notified_at = $${paramIndex++}`);
        values.push(updates.last_notified_at);
      }

      if (setParts.length === 0) {
        return;
      }

      values.push(id);
      const query = `UPDATE notifications SET ${setParts.join(', ')} WHERE id = $${paramIndex}`;

      await this.databaseService.runQuery(query, values);
    } catch (error) {
      this.logger.error('Failed to update notification state', {
        notificationId: id,
        updates,
        error: error.message,
      });
      throw error;
    }
  }
}
