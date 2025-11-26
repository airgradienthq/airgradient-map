import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsString,
  IsNumber,
  IsBoolean,
  IsArray,
  IsEnum,
  IsOptional,
  Min,
  Matches,
} from 'class-validator';
import { NotificationType, NotificationDisplayUnit, NotificationParameter, MonitorType } from './notification.model';
import { IsValidTimezone } from './validators/timezone.validator';

/**
 * DTO for creating a new notification registration.
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
 */
export class CreateNotificationDto {
  @ApiProperty({
    description: 'OneSignal Player ID obtained from the mobile app after OneSignal initialization',
    example: 'bc4b5e61-bd55-4d71-a052-b1e24cffca8b',
  })
  @IsString()
  player_id: string;

  @ApiProperty({
    description: 'Internal user identifier',
    example: '123',
  })
  @IsString()
  user_id: string;

  @ApiProperty({
    description:
      'ID of the location to monitor for air quality. Must be an existing location in the system.',
    example: 65159,
  })
  @IsNumber()
  @Min(1)
  location_id: number;

  @ApiProperty({
    description:
      'Type of notification: "threshold" for value-based alerts, "scheduled" for time-based notifications. Only one threshold notification per player per location per parameter is allowed.',
    enum: NotificationType,
    enumName: 'NotificationType',
    example: NotificationType.THRESHOLD,
  })
  @IsEnum(NotificationType)
  alarm_type: NotificationType;

  @ApiProperty({
    description:
      'Parameter to monitor for notifications. Determines what measurement value triggers threshold notifications or is reported in scheduled notifications.',
    enum: NotificationParameter,
    enumName: 'NotificationParameter',
    example: NotificationParameter.PM25,
    examples: {
      pm25: { value: 'pm25', description: 'PM2.5 particulate matter' },
      rco2: { value: 'rco2', description: 'CO2 concentration' },
      tvoc_index: { value: 'tvoc_index', description: 'Total Volatile Organic Compounds index' },
      nox_index: { value: 'nox_index', description: 'NOx index' },
      atmp: { value: 'atmp', description: 'Temperature' },
      rhum: { value: 'rhum', description: 'Relative humidity' },
    },
  })
  @IsEnum(NotificationParameter)
  parameter: NotificationParameter;

  // ─────────────────────────────────────────────────────────────────────────────
  // Threshold fields (required when alarm_type = "threshold")
  // ─────────────────────────────────────────────────────────────────────────────

  @ApiPropertyOptional({
    description:
      'Threshold value that triggers the notification. Required for threshold-type notifications. ' +
      'The unit depends on the parameter being monitored (e.g., μg/m³ for PM2.5, ppm for CO2). ' +
      '**Note:** If both `threshold` and `threshold_ug_m3` are provided, `threshold` takes precedence.',
    example: 50,
  })
  @IsOptional()
  @IsNumber()
  threshold?: number;

  @ApiPropertyOptional({
    description:
      '**[Legacy - use `threshold` instead]** Threshold value in μg/m³. ' +
      'Accepts the same values as `threshold`. If both are provided, `threshold` takes precedence.',
    example: 50,
    deprecated: true,
  })
  @IsOptional()
  @IsNumber()
  threshold_ug_m3?: number;

  @ApiPropertyOptional({
    description:
      'How often to send notifications when threshold is exceeded. ' +
      '"once" sends a single notification per exceedance (resets when value drops below threshold). ' +
      '"1h" to "24h" sends notifications at the specified interval while threshold remains exceeded.',
    pattern: '^(once|([1-9]|1[0-9]|2[0-4])h)$',
    example: 'once',
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
  // Scheduled fields (required when alarm_type = "scheduled")
  // ─────────────────────────────────────────────────────────────────────────────

  @ApiPropertyOptional({
    description:
      'Days of the week to send scheduled notifications. Use lowercase day names. ' +
      'Required for scheduled-type notifications. Empty array disables notifications.',
    type: [String],
    enum: ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'],
    example: ['monday', 'tuesday', 'wednesday', 'thursday', 'friday'],
  })
  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  scheduled_days?: string[];

  @ApiPropertyOptional({
    description:
      'Time to send scheduled notifications in HH:mm format (24-hour). Required for scheduled-type notifications.',
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
      'IANA timezone for scheduled notifications (e.g., "America/New_York", "Europe/London", "Asia/Bangkok"). ' +
      'Required for scheduled-type notifications.',
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
    description: 'Whether the notification is active and should be processed. Defaults to true.',
    default: true,
    example: true,
  })
  @IsBoolean()
  @IsOptional()
  active?: boolean;

  @ApiPropertyOptional({
    description:
      'Display unit for notification values. Required field. ' +
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
      'Accepts the same values as `display_unit`. If both are provided, `display_unit` takes precedence.',
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
      'Type of monitor: "owned" for user\'s own device, "public" for community monitor. Defaults to "public".',
    enum: MonitorType,
    enumName: 'MonitorType',
    default: MonitorType.PUBLIC,
    example: MonitorType.PUBLIC,
  })
  @IsOptional()
  @IsEnum(MonitorType)
  monitor_type?: MonitorType;

  @ApiPropertyOptional({
    description:
      'Place ID for owned monitors. Required when monitor_type is "owned", ignored for "public" monitors.',
    example: 123,
  })
  @IsOptional()
  @IsNumber()
  place_id?: number;
}
