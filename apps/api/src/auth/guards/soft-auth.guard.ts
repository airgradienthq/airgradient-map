import { CanActivate, ExecutionContext, Injectable } from '@nestjs/common';
import { Request } from 'express';
import { ClientAccessLevel } from '../enums/client-access-level.enum';
import { ConfigService } from '@nestjs/config';

@Injectable()
export class SoftAuthGuard implements CanActivate {
  constructor(private readonly configService: ConfigService) {}

  canActivate(context: ExecutionContext): boolean {
    const request = context
      .switchToHttp()
      .getRequest<Request & { clientAccessLevel?: ClientAccessLevel }>();

    request.clientAccessLevel = this.detectAccessLevel(request);
    return true; // never block
  }

  private detectAccessLevel(request: Request): ClientAccessLevel {
    /*
      AirGradient has some special agreements with some data providers that allow us to display their data, but not expose it through the public API. 
      For this reason, AirGradient uses the `data-permission-context` header.
      PLEASE DO NOT USE THIS HEADER FOR YOUR OWN APPS.
    */
    const providedContext = request.headers['data-permission-context'] as string;

    const trustedContext = this.configService.get<string>('DATA_PERMISSION_CONTEXT_HEADER');

    if (trustedContext && providedContext === trustedContext) {
      return ClientAccessLevel.TRUSTED;
    }

    // FUTURE: add other mechanisms (JWT, etc.) here
    return ClientAccessLevel.PUBLIC;
  }
}
