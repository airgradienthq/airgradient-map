import { ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsString,
  IsNumber,
  IsBoolean,
  IsArray,
  IsEnum,
  IsOptional,
  Matches,
} from 'class-validator';
import { NotificationDisplayUnit, NotificationParameter, MonitorType } from './notification.model';
import { IsValidTimezone } from './validators/timezone.validator';

/**
 * DTO for updating an existing notification registration.
 *
 * ## Field Aliasing (Backwards Compatibility)
 *
 * The following fields have been renamed but both old and new names are accepted:
 *
 * | New Field      | Legacy Field      | Precedence                    |
 * |----------------|-------------------|-------------------------------|
 * | `threshold`    | `threshold_ug_m3` | `threshold` takes precedence  |
 * | `display_unit` | `unit`            | `display_unit` takes precedence |
 *
 * If both old and new field names are provided in the same request, the **new field takes precedence**.
 *
 * Responses will only return the new field names (`threshold`, `display_unit`).
 *
 * ## Parameter and Display Unit Validation
 *
 * Each parameter only accepts specific display units:
 *
 * | Parameter    | Valid Display Units       |
 * |--------------|---------------------------|
 * | `pm25`       | `ug`, `us_aqi`            |
 * | `rco2`       | `ppm`                     |
 * | `tvoc_index` | `index`                   |
 * | `nox_index`  | `index`                   |
 * | `atmp`       | `celsius`, `fahrenheit`   |
 * | `rhum`       | `percent`                 |
 *
 * ## Important Notes
 *
 * - You cannot change `alarm_type` via update. Create a new notification instead.
 * - Threshold fields cannot be set on scheduled notifications and vice versa.
 */
export class UpdateNotificationDto {
  // ─────────────────────────────────────────────────────────────────────────────
  // Common fields
  // ─────────────────────────────────────────────────────────────────────────────

  @ApiPropertyOptional({
    description:
      'Parameter being monitored. **Note:** Changing parameter is not recommended - create a new notification instead.',
    enum: NotificationParameter,
    enumName: 'NotificationParameter',
    example: NotificationParameter.PM25,
  })
  @IsOptional()
  @IsEnum(NotificationParameter)
  parameter?: NotificationParameter;

  // ─────────────────────────────────────────────────────────────────────────────
  // Threshold fields (only for alarm_type = "threshold")
  // ─────────────────────────────────────────────────────────────────────────────

  @ApiPropertyOptional({
    description:
      'Threshold value that triggers the notification. ' +
      '**Note:** If both `threshold` and `threshold_ug_m3` are provided, `threshold` takes precedence.',
    example: 50,
  })
  @IsOptional()
  @IsNumber()
  threshold?: number;

  @ApiPropertyOptional({
    description:
      '**[Legacy - use `threshold` instead]** Threshold value. ' +
      'If both `threshold` and `threshold_ug_m3` are provided, `threshold` takes precedence.',
    example: 50,
    deprecated: true,
  })
  @IsOptional()
  @IsNumber()
  threshold_ug_m3?: number;

  @ApiPropertyOptional({
    description:
      'How often to send notifications when threshold is exceeded. ' +
      '"once" sends a single notification per exceedance. ' +
      '"1h" to "24h" sends at the specified interval while exceeded.',
    pattern: '^(once|([1-9]|1[0-9]|2[0-4])h)$',
    example: '6h',
    examples: {
      once: { value: 'once', description: 'Single notification per exceedance' },
      hourly: { value: '1h', description: 'Every hour while exceeded' },
      sixHours: { value: '6h', description: 'Every 6 hours while exceeded' },
      daily: { value: '24h', description: 'Once per day while exceeded' },
    },
  })
  @IsOptional()
  @IsString()
  @Matches(/^(once|([1-9]|1[0-9]|2[0-4])h)$/, {
    message:
      'threshold_cycle must be "once" or hour format "1h" to "24h" (e.g., "1h", "6h", "13h", "24h")',
  })
  threshold_cycle?: string;

  // ─────────────────────────────────────────────────────────────────────────────
  // Scheduled fields (only for alarm_type = "scheduled")
  // ─────────────────────────────────────────────────────────────────────────────

  @ApiPropertyOptional({
    description:
      'Days of the week to send scheduled notifications. Use lowercase day names. ' +
      'Empty array disables notifications.',
    type: [String],
    enum: ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'],
    example: ['monday', 'tuesday', 'wednesday', 'thursday', 'friday'],
  })
  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  scheduled_days?: string[];

  @ApiPropertyOptional({
    description: 'Time to send scheduled notifications in HH:mm format (24-hour).',
    pattern: '^([01]?[0-9]|2[0-3]):[0-5][0-9]$',
    example: '09:00',
  })
  @IsOptional()
  @IsString()
  @Matches(/^([01]?[0-9]|2[0-3]):[0-5][0-9]$/, {
    message: 'scheduled_time must be in HH:mm format (e.g., 09:30, 23:45)',
  })
  scheduled_time?: string;

  @ApiPropertyOptional({
    description:
      'IANA timezone for scheduled notifications (e.g., "America/New_York", "Europe/London", "Asia/Bangkok").',
    example: 'Asia/Bangkok',
  })
  @IsOptional()
  @IsString()
  @IsValidTimezone()
  scheduled_timezone?: string;

  // ─────────────────────────────────────────────────────────────────────────────
  // Common fields
  // ─────────────────────────────────────────────────────────────────────────────

  @ApiPropertyOptional({
    description: 'Whether the notification is active and should be processed.',
    example: true,
  })
  @IsOptional()
  @IsBoolean()
  active?: boolean;

  @ApiPropertyOptional({
    description:
      'Display unit for notification values. ' +
      '**Note:** If both `display_unit` and `unit` are provided, `display_unit` takes precedence.',
    enum: NotificationDisplayUnit,
    enumName: 'NotificationDisplayUnit',
    example: NotificationDisplayUnit.UG,
    examples: {
      micrograms: { value: 'ug', description: 'Micrograms per cubic meter (PM2.5)' },
      usAqi: { value: 'us_aqi', description: 'US Air Quality Index' },
      ppm: { value: 'ppm', description: 'Parts per million (CO2)' },
      index: { value: 'index', description: 'Index value (TVOC, NOx)' },
      celsius: { value: 'celsius', description: 'Degrees Celsius' },
      fahrenheit: { value: 'fahrenheit', description: 'Degrees Fahrenheit' },
      percent: { value: 'percent', description: 'Percentage (Humidity)' },
    },
  })
  @IsOptional()
  @IsEnum(NotificationDisplayUnit)
  display_unit?: NotificationDisplayUnit;

  @ApiPropertyOptional({
    description:
      '**[Legacy - use `display_unit` instead]** Unit for values. ' +
      'If both `display_unit` and `unit` are provided, `display_unit` takes precedence.',
    enum: NotificationDisplayUnit,
    enumName: 'NotificationDisplayUnit',
    example: NotificationDisplayUnit.UG,
    deprecated: true,
  })
  @IsOptional()
  @IsEnum(NotificationDisplayUnit)
  unit?: NotificationDisplayUnit;

  @ApiPropertyOptional({
    description:
      'Type of monitor: "owned" for user\'s own device, "public" for community monitor.',
    enum: MonitorType,
    enumName: 'MonitorType',
    example: MonitorType.PUBLIC,
  })
  @IsOptional()
  @IsEnum(MonitorType)
  monitor_type?: MonitorType;

  @ApiPropertyOptional({
    description:
      'Place ID for owned monitors. Required when monitor_type is "owned".',
    example: 123,
  })
  @IsOptional()
  @IsNumber()
  place_id?: number;
}
