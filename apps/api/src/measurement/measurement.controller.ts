import { UsePipes, ValidationPipe, Controller, Get, Query } from '@nestjs/common';
import { UseInterceptors, ClassSerializerInterceptor } from '@nestjs/common';
import { Logger } from '@nestjs/common';
import { MeasurementService } from './measurement.service';
import { Pagination, ApiPaginatedResponse } from 'src/utils/pagination.dto';
import { ApiTags } from '@nestjs/swagger';
import AreaQuery from './areaQuery';
import MeasureTypeQuery from '../utils/measureTypeQuery';
import { MeasurementEntity } from './measurement.entity';
import MeasurementClusterModel from './measurementCluster.model';
import PaginationQuery from 'src/utils/paginationQuery';
import ClusterQuery from './clusterQuery';
import ExcludeOutliersQuery from 'src/utils/excludeOutliersQuery';
import { HasFullAccess } from 'src/auth/decorators/access-level.decorator';
import OutlierRealtimeQuery from './outlierRealtimeQuery';

@Controller('map/api/v1/measurements')
@ApiTags('Measurements')
@UseInterceptors(ClassSerializerInterceptor)
export class MeasurementController {
  constructor(private readonly measurementService: MeasurementService) {}

  private readonly logger = new Logger(MeasurementController.name);

  @Get('/current')
  @ApiPaginatedResponse(
    MeasurementEntity,
    'Retrieves the current measurements from all locations',
    'Use measure query to specify and return only the desired measurement type',
  )
  @UsePipes(new ValidationPipe({ transform: true }))
  async getLastMeasurements(
    @Query() { measure }: MeasureTypeQuery,
    @Query() { page, pagesize }: PaginationQuery,
    @HasFullAccess() hasFullAccess: boolean,
  ): Promise<Pagination<MeasurementEntity>> {
    const measurementEntity = await this.measurementService.getLastMeasurements(
      hasFullAccess,
      measure,
      page,
      pagesize,
    );
    return new Pagination(measurementEntity, page, pagesize);
  }

  @Get('/current/area')
  @ApiPaginatedResponse(
    MeasurementEntity,
    'Retrieve the current measurements from all locations within a specific area',
    'X and Y query to determine bottom left and top right coordinate of digital maps; Use measure query to specify and return only the desired measurement type',
  )
  @UsePipes(new ValidationPipe({ transform: true }))
  async getLastMeasurementsByArea(
    @Query() { measure }: MeasureTypeQuery,
    @Query() area: AreaQuery,
    @HasFullAccess() hasFullAccess: boolean,
  ): Promise<Pagination<MeasurementEntity>> {
    const measurementEntity = await this.measurementService.getLastMeasurementsByArea(
      area.xmin,
      area.ymin,
      area.xmax,
      area.ymax,
      hasFullAccess,
      measure,
    );
    return new Pagination(measurementEntity, null, null);
  }

  @Get('/current/cluster')
  @ApiPaginatedResponse(
    MeasurementClusterModel,
    'Retrieve the current measurements from clustered locations within a specific area',
    "X, Y and zoom query to determine bottom left and top right coordinate of digital maps; Measure query to define 'value' field value on response, default to 'pm25'",
  )
  @UsePipes(new ValidationPipe({ transform: true }))
  async getLastMeasurementsByCluster(
    @Query() { measure }: MeasureTypeQuery,
    @Query() area: AreaQuery,
    @Query() cluster: ClusterQuery,
    @Query() { excludeOutliers }: ExcludeOutliersQuery,
    @Query() outlierQuery: OutlierRealtimeQuery,
    @HasFullAccess() hasFullAccess: boolean,
  ): Promise<Pagination<MeasurementClusterModel>> {
    const measurementClusterModel = await this.measurementService.getLastMeasurementsByCluster(
      area.xmin,
      area.ymin,
      area.xmax,
      area.ymax,
      area.zoom,
      excludeOutliers,
      hasFullAccess,
      measure,
      cluster.minPoints,
      cluster.radius,
      cluster.maxZoom,
      outlierQuery,
    );
    return new Pagination(measurementClusterModel, null, null);
  }
}
