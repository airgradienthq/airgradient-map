import { UsePipes, ValidationPipe, Controller, Get, Param, Query, BadRequestException } from '@nestjs/common';
import { Logger } from '@nestjs/common';
import FindOneParams from 'src/utils/findOneParams';
import PaginationQuery from 'src/utils/paginationQuery';
import { LocationService } from './location.service';
import { LocationEntity } from './location.entity';
import {
  ApiNotFoundResponse,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
  ApiBadRequestResponse,
  ApiParam,
} from '@nestjs/swagger';
import { ApiPaginatedResponse, Pagination } from 'src/utils/pagination.dto';
import MeasureTypeQuery from 'src/utils/measureTypeQuery';
import TimeseriesQuery from './timeseriesQuery';
import TimeseriesDto from './timeseries.dto';
import LocationMeasuresDto from './locationMeasures.dto';
import { CigarettesSmokedDto } from './cigarettesSmoked.dto';
import { LocationDailyAverageDto } from './locationDailyAverage.dto';
import { DailyAverageQuery } from './dailyAverageQuery';

@Controller('map/api/v1/locations')
@ApiTags('Locations')
export class LocationController {
  constructor(private readonly locationService: LocationService) {}

  private readonly logger = new Logger(LocationController.name);

  @Get()
  @ApiOperation({
    summary: 'Get all monitoring locations',
    description:
      'Retrieve detailed information about all air quality monitoring locations including coordinates, owner info, and sensor specifications.',
  })
  @ApiPaginatedResponse(LocationEntity, 'Successfully retrieved all locations', '')
  @UsePipes(new ValidationPipe({ transform: true }))
  async getLocations(@Query() { page, pagesize }: PaginationQuery) {
    const locationsEntity = await this.locationService.getLocations(page, pagesize);
    return new Pagination(locationsEntity, page, pagesize);
  }

  @Get(':id')
  @ApiOperation({
    summary: 'Get specific location details',
    description:
      'Retrieve comprehensive information for a single monitoring location by its unique identifier.',
  })
  @ApiParam({
    name: 'id',
    description: 'Unique location identifier',
    example: 12345,
    type: Number,
  })
  @ApiOkResponse({
    type: LocationEntity,
    description: 'Location details successfully retrieved',
  })
  @ApiNotFoundResponse({ description: 'Location not found' })
  @ApiBadRequestResponse({ description: 'Invalid location ID format' })
  @UsePipes(new ValidationPipe({ transform: true }))
  async getLocationById(@Param() { id }: FindOneParams) {
    return await this.locationService.getLocationById(id);
  }

  @Get(':id/measures/current')
  @ApiOperation({
    summary: 'Get latest measurements for a location',
    description:
      'Retrieve the most recent air quality measurements for a specific monitoring location.',
  })
  @ApiParam({
    name: 'id',
    description: 'Location identifier',
    example: 12345,
  })
  @ApiOkResponse({
    type: LocationMeasuresDto,
    description: 'Latest measurements successfully retrieved',
  })
  @ApiNotFoundResponse({ description: 'Location not found or no recent measurements available' })
  @UsePipes(new ValidationPipe({ transform: true }))
  async getLastmeasuresByLocationId(@Param() { id }: FindOneParams): Promise<LocationMeasuresDto> {
    const result = await this.locationService.getLocationLastMeasures(id);
    return new LocationMeasuresDto(result);
  }

  @Get(':id/cigarettes/smoked')
  @ApiOperation({
    summary: 'Retrieve number of cigarettes smoked equivalent to amount of air pollution',
  })
  @ApiParam({
    name: 'id',
    description: 'Location identifier',
    example: 12345,
  })
  @ApiOkResponse({ type: CigarettesSmokedDto })
  @ApiNotFoundResponse({ description: 'Location not found or no measurements available' })
  @UsePipes(new ValidationPipe({ transform: true }))
  async getCigarettesSmoked(@Param() { id }: FindOneParams): Promise<CigarettesSmokedDto> {
    const result = await this.locationService.getCigarettesSmoked(id);
    return new CigarettesSmokedDto(result);
  }

  @Get(':id/measures/history')
  @ApiOperation({
    summary: 'Get historical measurements for a location',
    description:
      'Retrieve time-series data for a specific location within a date range. Supports various bucket sizes for data aggregation.',
  })
  @ApiParam({
    name: 'id',
    description: 'Location identifier',
    example: 12345,
  })
  @ApiPaginatedResponse(
    TimeseriesDto,
    'Historical data successfully retrieved',
    'start and end query format is "yyyy-mm-dd hh:mm" or "yyyy-mm-dd"; bucketsize query follow ISO 8601 duration format',
  )
  @ApiBadRequestResponse({ description: 'Invalid date format, bucket size, or date range' })
  @ApiNotFoundResponse({ description: 'Location not found' })
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

  @Get(':id/averages/daily')
  @ApiOperation({
    summary: 'Daily averages by location and date range',
    description: 'Retrieve daily average values for a specific location within a specified date range',
  })
  @ApiParam({
    name: 'id',
    description: 'Location identifier',
    example: 1,
  })
  @ApiOkResponse({
    type: LocationDailyAverageDto,
    description: 'Latest measurements successfully retrieved',
  })
  @ApiBadRequestResponse({ description: 'Invalid date format.' })
  @ApiNotFoundResponse({ description: 'Location not found or no daily measurements available' })
  @UsePipes(new ValidationPipe({ transform: true }))
  async getDailyAverage(
    @Param() { id }: FindOneParams,
    @Query() dailyAverage: DailyAverageQuery,
    @Query() { measure }: MeasureTypeQuery,
  ): Promise<LocationDailyAverageDto> {
    const start = new Date(dailyAverage.start);
    const end = new Date(dailyAverage.end);
    if (start > end) {
      throw new BadRequestException('Invalid date. Start date must be before the end date.');
    }
    const maxRange = 365 * 24 * 60 * 60 * 1000;  // 1 year
    if ((end.getTime() - start.getTime()) > maxRange) {
      throw new BadRequestException('Max 1 year range.');
    }
    const dailyAverages = await this.locationService.getDailyAverages(id, dailyAverage.start, dailyAverage.end, measure);
    return {
      locationId: id,
      startDate: dailyAverage.start,
      endDate: dailyAverage.end,
      dailyAverages,
    };
  }
}
