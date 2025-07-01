import { UsePipes, ValidationPipe, Controller, Get, Query } from '@nestjs/common';
import { UseInterceptors, ClassSerializerInterceptor } from '@nestjs/common';
import { Logger } from '@nestjs/common';
import { MeasurementService } from './measurement.service';
import { Pagination, ApiPaginatedResponse } from 'src/utils/pagination.dto';
import AreaQuery from './areaQuery';
import MeasureTypeQuery from '../utils/measureTypeQuery';
import MeasurementEntity from './measurement.entity';
import MeasurementClusterModel from './measurementCluster.model';
import PaginationQuery from 'src/utils/paginationQuery';

@Controller('map/api/v1/measurements')
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
  ): Promise<Pagination<MeasurementEntity>> {
    const measurementEntity = await this.measurementService.getLastMeasurements(
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
  ): Promise<Pagination<MeasurementEntity>> {
    const measurementEntity = await this.measurementService.getLastMeasurementsByArea(
      area.xmin,
      area.ymin,
      area.xmax,
      area.ymax,
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
  ): Promise<Pagination<MeasurementClusterModel>> {
    const measurementClusterModel = await this.measurementService.getLastMeasurementsByCluster(
      area.xmin,
      area.ymin,
      area.xmax,
      area.ymax,
      area.zoom,
      measure,
    );
    return new Pagination(measurementClusterModel, null, null);
  }
}
