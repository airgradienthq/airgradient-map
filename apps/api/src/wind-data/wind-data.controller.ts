import { Controller, Get, Query, UsePipes, ValidationPipe } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiOkResponse } from '@nestjs/swagger';
import { WindDataService } from './wind-data.service';
import { WindDataAreaQuery } from './dto/wind-data-area.query';
import { WindDataEntity } from './wind-data.entity';

/**
 * Controller for wind data endpoints
 * Provides access to GFS wind forecast data in optimized grid format
 */
@Controller('map/api/v1/wind-data')
@ApiTags('Wind Data')
@UsePipes(new ValidationPipe({ transform: true }))
export class WindDataController {
  constructor(private readonly windDataService: WindDataService) {}

  /**
   * Get wind data within a bounding box
   * Returns the latest forecast data for the specified geographic area
   *
   * Example: GET /map/api/v1/wind-data/current?xmin=-10&xmax=40&ymin=35&ymax=70
   */
  @Get('current')
  @ApiOperation({
    summary: 'Get current wind forecast data for a geographic area',
    description: `
      Retrieves the latest GFS wind forecast data within the specified bounding box.
      Data is returned in an optimized grid format with U and V wind components.

      Grid format:
      - Data ordered north-to-south, west-to-east
      - Index calculation: index = y * nx + x
      - U-component: eastward wind (m/s)
      - V-component: northward wind (m/s)

      Compatible with leaflet-velocity library via simple transformation.
    `,
  })
  @ApiOkResponse({
    description: 'Wind data retrieved successfully',
    type: WindDataEntity,
  })
  async getCurrentWindData(@Query() query: WindDataAreaQuery): Promise<WindDataEntity> {
    return this.windDataService.getWindDataInArea(query.xmin, query.xmax, query.ymin, query.ymax);
  }
}
