import {
  Controller,
  Post,
  UsePipes,
  ValidationPipe,
  ClassSerializerInterceptor,
  UseInterceptors,
  Get,
  Delete,
  Param,
  Body,
  Patch,
  HttpCode,
  Query,
  Req,
} from '@nestjs/common';
import { NotificationsService } from './notifications.service';
import {
  ApiBadRequestResponse,
  ApiBody,
  ApiConflictResponse,
  ApiCreatedResponse,
  ApiNoContentResponse,
  ApiNotFoundResponse,
  ApiOkResponse,
  ApiOperation,
  ApiParam,
  ApiQuery,
  ApiTags,
} from '@nestjs/swagger';
import { NotificationEntity } from './notification.entity';
import { CreateNotificationDto } from './create-notification.dto';
import { UpdateNotificationDto } from './update-notification.dto';
import { Request } from 'express';
import { HasFullAccess } from 'src/auth/decorators/access-level.decorator';

/**
 * Controller for managing push notification registrations.
 *
 * ## Field Aliasing (Backwards Compatibility)
 *
 * The following fields have been renamed. Both old and new names are accepted on input,
 * but only new names are returned in responses:
 *
 * | New Field      | Legacy Field      | Precedence                    |
 * |----------------|-------------------|-------------------------------|
 * | `threshold`    | `threshold_ug_m3` | `threshold` takes precedence  |
 * | `display_unit` | `unit`            | `display_unit` takes precedence |
 */
@Controller('map/api/v1/notifications')
@ApiTags('Notifications')
@UsePipes(new ValidationPipe({ transform: true }))
@UseInterceptors(ClassSerializerInterceptor)
export class NotificationsController {
  constructor(private readonly notificationsService: NotificationsService) {}

  @Post('registrations')
  @ApiOperation({
    summary: 'Create a notification registration',
    description: `
Creates a new notification registration for a player/device.

## Notification Types

- **threshold**: Triggered when a monitored value exceeds the specified threshold
- **scheduled**: Sent at specific times on selected days of the week

## Field Aliasing

For backwards compatibility, the following legacy field names are accepted:
- \`threshold_ug_m3\` → use \`threshold\` instead
- \`unit\` → use \`display_unit\` instead

If both old and new field names are provided, **the new field takes precedence**.

Responses will only return the new field names.

## Conflict Handling

Only one threshold notification per player per location is allowed.
If a duplicate is attempted, a 409 Conflict response is returned.
    `,
  })
  @ApiCreatedResponse({
    type: NotificationEntity,
    description: 'Notification registration created successfully',
  })
  @ApiBody({ type: CreateNotificationDto })
  @ApiConflictResponse({
    description:
      'A threshold notification already exists for this player and location. ' +
      'Update or delete the existing registration first.',
  })
  @ApiBadRequestResponse({
    description:
      'Validation failed. Possible causes: invalid field values, missing required fields, ' +
      'location not found, or attempting to set threshold fields on scheduled notification (or vice versa).',
  })
  async createNotification(
    @Body() notification: CreateNotificationDto,
    @HasFullAccess() hasFullAccess: boolean,
    @Req() request: Request,
  ): Promise<NotificationEntity> {
    return await this.notificationsService.createNotification(notification, hasFullAccess, request);
  }

  @Get('players/:playerId/registrations')
  @ApiOperation({
    summary: 'Get notification registrations for a player',
    description: `
Retrieves all notification registrations for a specific player, optionally filtered by location.

Results are sorted by creation date (newest first).

## Response Fields

Responses use the new field names:
- \`threshold\` (not \`threshold_ug_m3\`)
- \`display_unit\` (not \`unit\`)
    `,
  })
  @ApiParam({
    name: 'playerId',
    description: 'OneSignal Player ID',
    example: 'bc4b5e61-bd55-4d71-a052-b1e24cffca8b',
  })
  @ApiQuery({
    name: 'locationId',
    required: false,
    type: Number,
    description: 'Filter by location ID',
    example: 65159,
  })
  @ApiOkResponse({
    type: NotificationEntity,
    isArray: true,
    description: 'List of notification registrations',
  })
  async getRegistrations(
    @Param('playerId') playerId: string,
    @Query('locationId') locationId?: number,
  ): Promise<NotificationEntity[]> {
    return await this.notificationsService.getRegisteredNotifications(playerId, locationId);
  }

  @Patch('players/:playerId/registrations/:id')
  @ApiOperation({
    summary: 'Update a notification registration',
    description: `
Updates an existing notification registration.

## Field Aliasing

For backwards compatibility, the following legacy field names are accepted:
- \`threshold_ug_m3\` → use \`threshold\` instead
- \`unit\` → use \`display_unit\` instead

If both old and new field names are provided, **the new field takes precedence**.

## Restrictions

- Cannot change \`alarm_type\` (create a new registration instead)
- Cannot set threshold fields on scheduled notifications
- Cannot set scheduled fields on threshold notifications
    `,
  })
  @ApiParam({
    name: 'playerId',
    description: 'OneSignal Player ID',
    example: 'bc4b5e61-bd55-4d71-a052-b1e24cffca8b',
  })
  @ApiParam({
    name: 'id',
    description: 'Notification registration ID',
    example: '42',
  })
  @ApiOkResponse({
    type: NotificationEntity,
    description: 'Notification registration updated successfully',
  })
  @ApiBody({ type: UpdateNotificationDto })
  @ApiNotFoundResponse({
    description: 'Notification registration not found',
  })
  @ApiBadRequestResponse({
    description:
      'Validation failed. Possible causes: player ID mismatch, invalid field values, ' +
      'or attempting to set threshold fields on scheduled notification (or vice versa).',
  })
  async updateNotification(
    @Param('playerId') playerId: string,
    @Param('id') id: string,
    @Body() notification: UpdateNotificationDto,
    @Req() request: Request,
  ): Promise<NotificationEntity> {
    return await this.notificationsService.updateRegisteredNotification(
      playerId,
      id,
      notification,
      request,
    );
  }

  @Delete('players/:playerId/registrations/:id')
  @ApiOperation({
    summary: 'Delete a notification registration',
    description: 'Permanently deletes a notification registration.',
  })
  @ApiParam({
    name: 'playerId',
    description: 'OneSignal Player ID',
    example: 'bc4b5e61-bd55-4d71-a052-b1e24cffca8b',
  })
  @ApiParam({
    name: 'id',
    description: 'Notification registration ID',
    example: '42',
  })
  @ApiNoContentResponse({
    description: 'Notification registration deleted successfully',
  })
  @ApiNotFoundResponse({
    description: 'Notification registration not found',
  })
  @ApiBadRequestResponse({
    description: 'Player ID does not match the notification registration',
  })
  @HttpCode(204)
  async deleteRegistration(
    @Param('playerId') playerId: string,
    @Param('id') id: string,
    @Req() request: Request,
  ): Promise<void> {
    return await this.notificationsService.deleteRegisteredNotification(playerId, id, request);
  }
}
