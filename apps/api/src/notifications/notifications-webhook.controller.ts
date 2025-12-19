import { Body, Controller, HttpCode, Post } from '@nestjs/common';
import { ApiBody, ApiExcludeController, ApiOperation } from '@nestjs/swagger';
import { NotificationsService } from './notifications.service';
import { ExternalNotificationTriggerDto } from './dto/external-trigger.dto';
import { BatchResult } from './notification.model';

@ApiExcludeController()
@Controller('private/map/api/v1/notifications')
export class NotificationsWebhookController {
  constructor(private readonly notificationsService: NotificationsService) {}

  @Post('triggers')
  @HttpCode(202)
  @ApiBody({ type: ExternalNotificationTriggerDto, isArray: true })
  async processExternalTriggers(
    @Body() triggers: ExternalNotificationTriggerDto[],
  ): Promise<BatchResult> {
    return this.notificationsService.processExternalTriggers(triggers);
  }
}
