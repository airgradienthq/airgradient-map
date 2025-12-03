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
    // CURRENT: API key
    const providedKey = request.headers['x-api-key'] as string;

    const trustedClientApiKey = this.configService.get<string>('TRUSTED_CLIENT_API_KEY');

    if (trustedClientApiKey && providedKey === trustedClientApiKey) {
      return ClientAccessLevel.TRUSTED;
    }

    // FUTURE: add other mechanisms (JWT, etc.) here
    return ClientAccessLevel.PUBLIC;
  }
}
