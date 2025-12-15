import { Controller, Get, Req, UnauthorizedException } from '@nestjs/common';
import { Request } from 'express';
import { CoreApiService } from 'src/utils/core-api.service';

@Controller('/private/map/api/v1/core-api/')
export class CoreApiController {
  constructor(private readonly coreApiService: CoreApiService) {}

  @Get('user')
  async getCurrentUser(@Req() req: Request): Promise<any> {
    // Check if cookies are present
    if (!this.coreApiService.hasCookies(req)) {
      throw new UnauthorizedException('No authentication cookies found. Please log in first.');
    }

    // Forward request to Core API
    try {
      const user = await this.coreApiService.get<any>(req, '/auth/user');
      return {
        success: true,
        message: 'Cookie authentication successful',
        user,
      };
    } catch (error) {
      throw new UnauthorizedException(
        `Core API authentication failed: ${error.message}. Your cookies may be invalid or expired.`,
      );
    }
  }
}
