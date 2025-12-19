import { UsePipes, ValidationPipe, Controller, Get, Param, Query } from '@nestjs/common';
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
  ApiExtraModels,
  getSchemaPath,
} from '@nestjs/swagger';
import { ApiPaginatedResponse, Pagination } from 'src/utils/pagination.dto';
import MeasureTypeQuery from 'src/utils/measureTypeQuery';
import TimeseriesQuery from './timeseriesQuery';
import TimeseriesDto from './timeseries.dto';
import LocationMeasuresDto from './locationMeasures.dto';
import { CigarettesSmokedDto } from './cigarettesSmoked.dto';
import { CigarettesArrayDto } from './cigarettesArray.dto';
import { MeasurementAveragesDto } from './averages.dto';
import { AveragesQueryDto } from './averagesQuery.dto';
import { CigarettesQueryDto } from './cigarettesQuery.dto';
import ExcludeOutliersQuery from 'src/utils/excludeOutliersQuery';
import { HasFullAccess } from 'src/auth/decorators/access-level.decorator';

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
  async getLocations(
    @Query() { page, pagesize }: PaginationQuery,
    @HasFullAccess() hasFullAccess: boolean,
  ) {
    const locationsEntity = await this.locationService.getLocations(hasFullAccess, page, pagesize);
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
  async getLocationById(@Param() { id }: FindOneParams, @HasFullAccess() hasFullAccess: boolean) {
    return await this.locationService.getLocationById(id, hasFullAccess);
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
  async getLastmeasuresByLocationId(
    @Param() { id }: FindOneParams,
    @HasFullAccess() hasFullAccess: boolean,
  ): Promise<LocationMeasuresDto> {
    const result = await this.locationService.getLocationLastMeasures(id, hasFullAccess);
    return new LocationMeasuresDto(result);
  }

  @Get(':id/cigarettes/smoked')
  @ApiOperation({
    summary: 'Get cigarettes equivalent for air pollution exposure',
    description:
      'Calculates the equivalent number of cigarettes smoked based on PM2.5 exposure levels for different time periods. Uses the Berkeley Earth conversion: 22 µg/m³ PM2.5 = 1 cigarette per day. Returns flat object for default timeframes (backward compatible), or structured array for custom timeframes.',
  })
  @ApiParam({
    name: 'id',
    description: 'Location identifier',
    example: 12345,
    type: Number,
  })
  @ApiExtraModels(CigarettesSmokedDto, CigarettesArrayDto)
  @ApiOkResponse({
    description:
      'Cigarette equivalents for multiple time periods. Returns flat object for default timeframes (backward compatible), or structured array for custom timeframes.',
    schema: {
      oneOf: [
        { $ref: getSchemaPath(CigarettesSmokedDto) },
        { $ref: getSchemaPath(CigarettesArrayDto) },
      ],
    },
  })
  @ApiNotFoundResponse({ description: 'Location not found or no PM2.5 data available' })
  @ApiBadRequestResponse({ description: 'Invalid location ID format or timeframe format' })
  @UsePipes(new ValidationPipe({ transform: true }))
  async getCigarettesSmoked(
    @Param() { id }: FindOneParams,
    @Query() { timeframes }: CigarettesQueryDto,
    @HasFullAccess() hasFullAccess: boolean,
  ): Promise<CigarettesSmokedDto | CigarettesArrayDto> {
    const result = await this.locationService.getCigarettesSmoked(id, hasFullAccess, timeframes);

    // Format response based on whether custom timeframes were request
    if (timeframes) {
      // Custom timeframes: return structured array format
      const cigarettesArray = Object.entries(result).map(([timeframe, value]) => ({
        timeframe,
        value,
      }));
      return new CigarettesArrayDto(id, cigarettesArray);
    } else {
      // Default timeframes: return flat object for backward compatibility
      return new CigarettesSmokedDto(result);
    }
  }

  @Get(':id/averages')
  @ApiOperation({
    summary: 'Get measurement averages for a location',
    description: `Calculate average values for a specified 
    measurement type across different time periods (6h, 
    24h, 7d, 30d, 90d) for a specific location. Returns 
    null for periods with insufficient data. Defaults to
    PM2.5 if measure is not specified.`,
  })
  @ApiParam({
    name: 'id',
    description: 'Location identifier',
    example: 12345,
    type: Number,
  })
  @ApiOkResponse({
    type: MeasurementAveragesDto,
    description: 'Measurement averages for multiple time periods',
  })
  @ApiNotFoundResponse({ description: 'Location not found or no measurement data available' })
  @ApiBadRequestResponse({ description: 'Invalid location ID format or measure type' })
  @UsePipes(new ValidationPipe({ transform: true }))
  async getLocationMeasurementAverages(
    @Param() { id }: FindOneParams,
    @Query() { measure }: MeasureTypeQuery,
    @Query() { periods }: AveragesQueryDto,
    @HasFullAccess() hasFullAccess: boolean,
  ): Promise<MeasurementAveragesDto> {
    const result = await this.locationService.getLocationAverages(
      id,
      measure,
      hasFullAccess,
      periods,
    );
    return new MeasurementAveragesDto(result);
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
  @ApiPaginatedResponse(TimeseriesDto, 'Historical data successfully retrieved', '')
  @ApiBadRequestResponse({ description: 'Invalid date format, bucket size, or date range' })
  @ApiNotFoundResponse({ description: 'Location not found' })
  @UsePipes(new ValidationPipe({ transform: true }))
  async getmeasuresHistoryByLocationId(
    @Param() { id }: FindOneParams,
    @Query() { measure }: MeasureTypeQuery,
    @Query() timeseries: TimeseriesQuery,
    @Query() { excludeOutliers }: ExcludeOutliersQuery,
    @HasFullAccess() hasFullAccess: boolean,
  ): Promise<Pagination<TimeseriesDto>> {
    const history = await this.locationService.getLocationMeasuresHistory(
      id,
      timeseries.start,
      timeseries.end,
      timeseries.bucketSize,
      excludeOutliers,
      hasFullAccess,
      measure,
    );
    const timeseriesDto = history.map(
      (timeseries: TimeseriesDto) => new TimeseriesDto(timeseries.timebucket, timeseries.value),
    );

    return new Pagination(timeseriesDto, null, null);
  }
}
