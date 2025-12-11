import { Controller, Get, Req, UnauthorizedException } from '@nestjs/common';
import { ApiTags, ApiOkResponse, ApiUnauthorizedResponse, ApiOperation } from '@nestjs/swagger';
import { Request } from 'express';
import { DashboardApiService } from 'src/utils/dashboard-api.service';

@Controller('map/api/v1/dashboard-auth')
@ApiTags('🔒 Internal - Dashboard Auth (Private)')
export class DashboardAuthController {
  constructor(private readonly dashboardApiService: DashboardApiService) {}

  @Get('user')
  @ApiOperation({
    summary: '🔒 INTERNAL - Dashboard API authentication',
    description: `
**⚠️ INTERNAL USE**

**Note:** This will not work for external users as it requires access to AirGradient's Dashboard API.
    `,
  })
  @ApiOkResponse({
    description: 'User data retrieved successfully',
  })
  @ApiUnauthorizedResponse({
    description: 'No cookies provided or invalid authentication',
  })
  async getCurrentUser(@Req() req: Request): Promise<any> {
    // Check if cookies are present
    if (!this.dashboardApiService.hasCookies(req)) {
      throw new UnauthorizedException('No authentication cookies found. Please log in first.');
    }

    // Forward request to Dashboard API
    try {
      const user = await this.dashboardApiService.get<any>(req, '/auth/user');
      return {
        success: true,
        message: 'Cookie authentication successful',
        user,
      };
    } catch (error) {
      throw new UnauthorizedException(
        `Dashboard API authentication failed: ${error.message}. Your cookies may be invalid or expired.`,
      );
    }
  }
}
