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
} from '@nestjs/common';
import { NotificationsService } from './notifications.service';
import {
  ApiBadRequestResponse,
  ApiBody,
  ApiConflictResponse,
  ApiNoContentResponse,
  ApiOkResponse,
  ApiParam,
  ApiQuery,
  ApiTags,
} from '@nestjs/swagger';
import { NotificationEntity } from './notification.entity';
import { CreateNotificationDto } from './create-notification.dto';
import { UpdateNotificationDto } from './update-notification.dto';

@Controller('map/api/v1/notifications')
@ApiTags('Notifications')
@UsePipes(new ValidationPipe({ transform: true }))
@UseInterceptors(ClassSerializerInterceptor)
export class NotificationsController {
  constructor(private readonly notificationsService: NotificationsService) {}

  @Post('registrations')
  @ApiOkResponse({
    type: NotificationEntity,
    description: 'Notification created successfully',
  })
  @ApiBody({ type: CreateNotificationDto })
  @ApiConflictResponse({
    description:
      'Conflict - a threshold notification already exists for this player and location combination',
  })
  @ApiBadRequestResponse({
    description: 'Bad request - validation failed or location not found',
  })
  async createNotification(
    @Body() notification: CreateNotificationDto,
  ): Promise<NotificationEntity> {
    return await this.notificationsService.createNotification(notification);
  }

  @Get('players/:playerId/registrations')
  @ApiParam({ name: 'playerId', description: 'Player ID', required: true })
  @ApiQuery({
    name: 'locationId',
    required: false,
    type: Number,
    description: 'Optional location ID filter',
  })
  @ApiOkResponse({ type: NotificationEntity, isArray: true })
  @ApiBadRequestResponse({ description: 'Bad request' })
  async getRegistrations(
    @Param('playerId') playerId: string,
    @Query('locationId') locationId?: number,
  ): Promise<NotificationEntity[]> {
    return await this.notificationsService.getRegisteredNotifications(playerId, locationId);
  }

  @Patch('players/:playerId/registrations/:id')
  @ApiParam({ name: 'playerId', description: 'Player ID' })
  @ApiParam({ name: 'id', description: 'Registration ID' })
  @ApiOkResponse({
    type: NotificationEntity,
    description: 'Notification updated successfully',
  })
  @ApiBody({ type: UpdateNotificationDto })
  @ApiBadRequestResponse({ description: 'Bad request' })
  async updateNotification(
    @Param('playerId') playerId: string,
    @Param('id') id: string,
    @Body() notification: UpdateNotificationDto,
  ): Promise<NotificationEntity> {
    return await this.notificationsService.updateRegisteredNotification(playerId, id, notification);
  }

  @Delete('players/:playerId/registrations/:id')
  @ApiParam({ name: 'playerId', description: 'Player ID' })
  @ApiParam({ name: 'id', description: 'Registration ID' })
  @ApiNoContentResponse({ description: 'Deleted successfully' })
  @HttpCode(204)
  async deleteRegistration(
    @Param('playerId') playerId: string,
    @Param('id') id: string,
  ): Promise<void> {
    return await this.notificationsService.deleteRegisteredNotification(playerId, id);
  }
}
