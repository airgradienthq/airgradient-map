import { Controller, Get, Query, UsePipes, ValidationPipe } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiOkResponse } from '@nestjs/swagger';
import { FiresDataService } from './fires-data.service';
import { FiresDataAreaQuery } from './dto/fires-data-area.query';
import { FiresDataEntity } from './fires-data.entity';

/**
 * Controller for fires data endpoints
 * Provides access to NASA FIRMS wildfire detection data in GeoJSON format
 */
@Controller('map/api/v1/fires-data')
@ApiTags('Fires Data')
@UsePipes(new ValidationPipe({ transform: true }))
export class FiresDataController {
  constructor(private readonly firesDataService: FiresDataService) {}

  /**
   * Get fires data within a bounding box
   * Returns fire detections from VIIRS satellite for the specified geographic area
   *
   * Example: GET /map/api/v1/fires-data/current?xmin=-125&xmax=-114&ymin=32&ymax=42&hours=48
   */
  @Get('current')
  @ApiOperation({
    summary: 'Get current wildfire detection data for a geographic area',
    description: `
      Retrieves NASA FIRMS wildfire detection data within the specified bounding box.
      Data includes VIIRS satellite fire detections with confidence levels and fire radiative power.

      Features:
      - Fire location (latitude/longitude)
      - Confidence level (low/nominal/high)
      - Fire Radiative Power (FRP) in megawatts
      - Brightness temperature
      - Satellite source and acquisition time
      - Day/night detection flag

      Data is returned in GeoJSON FeatureCollection format for easy mapping.
    `,
  })
  @ApiOkResponse({
    description: 'Fires data retrieved successfully',
    type: FiresDataEntity,
  })
  async getCurrentFiresData(@Query() query: FiresDataAreaQuery): Promise<FiresDataEntity> {
    return this.firesDataService.getFiresDataInArea(
      query.xmin,
      query.xmax,
      query.ymin,
      query.ymax,
      query.hours,
      query.confidence,
    );
  }
}
