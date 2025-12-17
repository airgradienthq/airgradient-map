import { Controller, Get, Headers, HttpStatus, Res } from '@nestjs/common';
import { ApiOkResponse, ApiOperation, ApiTags } from '@nestjs/swagger';
import { Response } from 'express';
import { AqiStandardsService } from './aqi-standards.service';
import { AqiStandardsDto } from './aqi-standards.dto';

@Controller('map/api/v1/aqi-standards')
@ApiTags('AQI Standards')
export class AqiStandardsController {
  constructor(private readonly aqiStandardsService: AqiStandardsService) {}

  @Get()
  @ApiOperation({
    summary: 'Get AQI standards configuration',
    description: 'Returns the AQI bands/standards JSON used by the frontend and mobile apps.',
  })
  @ApiOkResponse({ description: 'AQI standards JSON payload', type: AqiStandardsDto })
  async getAqiStandards(
    @Res({ passthrough: true }) res: Response,
    @Headers('if-none-match') ifNoneMatch?: string,
  ): Promise<AqiStandardsDto | void> {
    const { payload, etag } = this.aqiStandardsService.getAqiStandards();

    res.setHeader('Cache-Control', 'public, max-age=3600, stale-while-revalidate=300');
    res.setHeader('ETag', etag);

    if (ifNoneMatch && ifNoneMatch === etag) {
      res.status(HttpStatus.NOT_MODIFIED);
      return;
    }

    return payload;
  }
}
