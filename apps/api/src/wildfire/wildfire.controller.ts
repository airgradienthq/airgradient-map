import { Controller, Get, Query, ValidationPipe, UsePipes } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiResponse } from '@nestjs/swagger';
import { WildfireService } from './wildfire.service';
import { WildfireQueryDto } from './wildfire.dto';

@Controller('map/api/v1/wildfire')
@ApiTags('Wildfire')
export class WildfireController {
  constructor(private readonly wildfireService: WildfireService) {}

  @Get('geojson')
  @ApiOperation({ summary: 'Get wildfire data as GeoJSON within bounds' })
  @ApiResponse({ status: 200, description: 'Returns wildfire data as GeoJSON' })
  @UsePipes(new ValidationPipe({ transform: true }))
  async getWildfiresGeoJSON(@Query() query: WildfireQueryDto) {
    return this.wildfireService.getWildfiresGeoJSON(query);
  }
}
