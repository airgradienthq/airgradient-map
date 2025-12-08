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
  Logger,
} from '@nestjs/common';
import { Request } from 'express';
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
import { HasFullAccess } from 'src/auth/decorators/access-level.decorator';

@Controller('map/api/v1/notifications')
@ApiTags('Notifications')
@UsePipes(new ValidationPipe({ transform: true }))
@UseInterceptors(ClassSerializerInterceptor)
export class NotificationsController {
  private readonly logger = new Logger(NotificationsController.name);

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
    @Req() req: Request,
    @Body() notification: CreateNotificationDto,
    @HasFullAccess() hasFullAccess: boolean,
  ): Promise<NotificationEntity> {
    // Log cookies received from iOS app
    const cookies = req.headers.cookie;
    if (cookies) {
      this.logger.log(`📝 Received cookies from iOS: ${cookies.substring(0, 50)}...`);
    } else {
      this.logger.warn('⚠️  No cookies received from iOS app');
    }

    return await this.notificationsService.createNotification(notification, hasFullAccess);
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
    @Req() req: Request,
    @Param('playerId') playerId: string,
    @Query('locationId') locationId?: number,
  ): Promise<NotificationEntity[]> {
    // Log cookies received from iOS app
    const cookies = req.headers.cookie;
    if (cookies) {
      this.logger.log(`📝 GET registrations - Cookies received: ${cookies.substring(0, 50)}...`);
    } else {
      this.logger.warn('⚠️  GET registrations - No cookies received');
    }

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
    @Req() req: Request,
    @Param('playerId') playerId: string,
    @Param('id') id: string,
    @Body() notification: UpdateNotificationDto,
  ): Promise<NotificationEntity> {
    // Log cookies received from iOS app
    const cookies = req.headers.cookie;
    if (cookies) {
      this.logger.log(`📝 PATCH registration - Cookies received: ${cookies.substring(0, 50)}...`);
    } else {
      this.logger.warn('⚠️  PATCH registration - No cookies received');
    }

    return await this.notificationsService.updateRegisteredNotification(playerId, id, notification);
  }

  @Delete('players/:playerId/registrations/:id')
  @ApiParam({ name: 'playerId', description: 'Player ID' })
  @ApiParam({ name: 'id', description: 'Registration ID' })
  @ApiNoContentResponse({ description: 'Deleted successfully' })
  @HttpCode(204)
  async deleteRegistration(
    @Req() req: Request,
    @Param('playerId') playerId: string,
    @Param('id') id: string,
  ): Promise<void> {
    // Log cookies received from iOS app
    const cookies = req.headers.cookie;
    if (cookies) {
      this.logger.log(`📝 DELETE registration - Cookies received: ${cookies.substring(0, 50)}...`);
    } else {
      this.logger.warn('⚠️  DELETE registration - No cookies received');
    }

    return await this.notificationsService.deleteRegisteredNotification(playerId, id);
  }
}
