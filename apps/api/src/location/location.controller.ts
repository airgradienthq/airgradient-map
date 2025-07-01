import { UsePipes, ValidationPipe, Controller, Get, Param, Query } from '@nestjs/common';
import { Logger } from '@nestjs/common';
import FindOneParams from 'src/utils/findOneParams';
import PaginationQuery from 'src/utils/paginationQuery';
import { LocationService } from './location.service';
import LocationEntity from './location.entity';
import { ApiNotFoundResponse, ApiOkResponse, ApiOperation } from '@nestjs/swagger';
import { ApiPaginatedResponse, Pagination } from 'src/utils/pagination.dto';
import MeasureTypeQuery from 'src/utils/measureTypeQuery';
import TimeseriesQuery from './timeseriesQuery';
import TimeseriesDto from './timeseries.dto';
import LocationMeasuresDto from './locationMeasures.dto';

@Controller('map/api/v1/locations')
export class LocationController {
  constructor(private readonly locationService: LocationService) {}

  private readonly logger = new Logger(LocationController.name);

  @Get()
  @ApiPaginatedResponse(LocationEntity, 'Retrieve detailed information for all locations', '')
  @UsePipes(new ValidationPipe({ transform: true }))
  async getLocations(@Query() { page, pagesize }: PaginationQuery) {
    const locationsEntity = await this.locationService.getLocations(page, pagesize);
    return new Pagination(locationsEntity, page, pagesize);
  }

  @Get(':id')
  @ApiOperation({
    summary: 'Retrieve detailed information for specific location id',
  })
  @ApiOkResponse({ type: LocationEntity })
  @ApiNotFoundResponse()
  @UsePipes(new ValidationPipe({ transform: true }))
  async getLocationById(@Param() { id }: FindOneParams) {
    return await this.locationService.getLocationById(id);
  }

  @Get(':id/measures/current')
  @ApiOperation({ summary: 'Retrieve measures of a location' })
  @ApiOkResponse({ type: LocationMeasuresDto })
  @ApiNotFoundResponse()
  @UsePipes(new ValidationPipe({ transform: true }))
  async getLastmeasuresByLocationId(@Param() { id }: FindOneParams): Promise<LocationMeasuresDto> {
    const result = await this.locationService.getLocationLastMeasures(id);
    return new LocationMeasuresDto(result);
  }

  @Get(':id/measures/history')
  @ApiPaginatedResponse(
    TimeseriesDto,
    'Retrieve history measures of a location based on range of timestamp',
    'start and end query format is "yyyy-mm-dd hh:mm" or "yyyy-mm-dd"; bucketsize query follow ISO 8601 duration format',
  )
  @UsePipes(new ValidationPipe({ transform: true }))
  async getmeasuresHistoryByLocationIdl(
    @Param() { id }: FindOneParams,
    @Query() { measure }: MeasureTypeQuery,
    @Query() timeseries: TimeseriesQuery,
  ): Promise<Pagination<TimeseriesDto>> {
    const history = await this.locationService.getLocationMeasuresHistory(
      id,
      timeseries.start,
      timeseries.end,
      timeseries.bucketSize,
      measure,
    );
    const timeseriesDto = history.map(
      (timeseries: TimeseriesDto) => new TimeseriesDto(timeseries.timebucket, timeseries.value),
    );

    return new Pagination(timeseriesDto, null, null);
  }
}
