import { createParamDecorator, ExecutionContext, Logger } from '@nestjs/common';
import { ClientAccessLevel } from '../enums/client-access-level.enum';

const logger = new Logger('AccessLevelDecorator');

export const AccessLevel = createParamDecorator(
  (data: unknown, ctx: ExecutionContext): ClientAccessLevel => {
    const request = ctx.switchToHttp().getRequest<{
      clientAccessLevel?: ClientAccessLevel;
    }>();

    return request.clientAccessLevel ?? ClientAccessLevel.PUBLIC;
  },
);

export const HasFullAccess = createParamDecorator(
  (data: unknown, ctx: ExecutionContext): boolean => {
    const request = ctx.switchToHttp().getRequest<{
      clientAccessLevel?: ClientAccessLevel;
    }>();

    logger.debug(
      `clientAccessLevel=${request.clientAccessLevel}; trusted=${
        request.clientAccessLevel === ClientAccessLevel.TRUSTED
      }`,
    );

    return request.clientAccessLevel === ClientAccessLevel.TRUSTED;
  },
);
