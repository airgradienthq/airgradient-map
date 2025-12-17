import { Controller, Get, HttpStatus, Req, Res } from '@nestjs/common';
import { ApiOkResponse, ApiOperation, ApiTags } from '@nestjs/swagger';
import { Request, Response } from 'express';
import { AqiStandardsService } from './aqi-standards.service';
import { AqiStandardsDto } from './aqi-standards.dto';

@Controller('map/api/v1/aqi-standards')
@ApiTags('AQI Standards')
export class AqiStandardsController {
  constructor(private readonly aqiStandardsService: AqiStandardsService) {}

  @Get()
  @ApiOperation({
    summary: 'Get AQI standards configuration',
    description:
      'Returns the AQI bands/standards JSON used by the frontend and mobile apps. Clients should cache the payload and revalidate by sending the last received ETag via the If-None-Match header.',
  })
  @ApiOkResponse({
    description: 'AQI standards JSON payload',
    type: AqiStandardsDto,
    headers: {
      ETag: {
        description:
          'Opaque ID for cache revalidation. Send back via If-None-Match to avoid downloading unchanged payloads.',
        schema: { type: 'string', example: 'W/"c0ffee1234abcd5678ef"' },
      },
      'Cache-Control': {
        description: 'Cache policy for the AQI standards payload.',
        schema: { type: 'string', example: 'public, max-age=3600, stale-while-revalidate=300' },
      },
    },
  })
  async getAqiStandards(
    @Res({ passthrough: true }) res: Response,
    @Req() req: Request,
  ): Promise<AqiStandardsDto | void> {
    const { payload, etag } = this.aqiStandardsService.getAqiStandards();
    const ifNoneMatch = req.get('if-none-match');

    res.setHeader('Cache-Control', 'public, max-age=3600, stale-while-revalidate=300');
    res.setHeader('ETag', etag);

    if (ifNoneMatch && ifNoneMatch === etag) {
      res.status(HttpStatus.NOT_MODIFIED);
      return;
    }

    return payload;
  }
}
