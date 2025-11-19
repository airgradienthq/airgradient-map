import { Injectable, Logger } from '@nestjs/common';
import MeasurementRepository from './measurement.repository';
import Supercluster from 'supercluster';
import MeasurementCluster from './measurementCluster.model';
import { ConfigService } from '@nestjs/config';
import { getEPACorrectedPM } from 'src/utils/getEpaCorrectedPM';
import { MeasurementEntity } from './measurement.entity';
import {
  MeasurementServiceResult,
  MeasurementsByAreaResult,
  MeasurementClusterResult,
  MeasureType,
} from '../types/measurement/measurement.types';
import { MeasurementGeoJSONFeature } from '../types/shared/geojson.types';
import { DataSource } from 'src/types/shared/data-source';
import { MEASUREMENT_CLUSTER_CONFIG } from 'src/constants/measurement-cluster.constants';

@Injectable()
export class MeasurementService {
  private readonly logger = new Logger(MeasurementService.name);

  // Configuration from constants or environment
  private readonly CLUSTER_MIN_POINTS: number;
  private readonly CLUSTER_RADIUS: number;
  private readonly CLUSTER_MAX_ZOOM: number;

  constructor(
    private readonly measurementRepository: MeasurementRepository,
    private readonly configService: ConfigService,
  ) {
    // Allow environment overrides
    this.CLUSTER_MIN_POINTS = this.configService.get<number>(
      'MAP_CLUSTER_MIN_POINTS',
      MEASUREMENT_CLUSTER_CONFIG.MIN_POINTS,
    );
    this.CLUSTER_RADIUS = this.configService.get<number>(
      'MAP_CLUSTER_RADIUS',
      MEASUREMENT_CLUSTER_CONFIG.RADIUS,
    );
    this.CLUSTER_MAX_ZOOM = this.configService.get<number>(
      'MAP_CLUSTER_MAX_ZOOM',
      MEASUREMENT_CLUSTER_CONFIG.MAX_ZOOM,
    );
  }

  async getLastMeasurements(
    measure?: MeasureType,
    page = 1,
    pagesize = 100,
  ): Promise<MeasurementServiceResult> {
    const offset = pagesize * (page - 1); // Calculate the offset for query
    const measurements = await this.measurementRepository.retrieveLatest(offset, pagesize, measure);

    return this.setEPACorrectedPM(measurements);
  }

  async getLastMeasurementsByArea(
    xMin: number,
    yMin: number,
    xMax: number,
    yMax: number,
    measure?: MeasureType,
  ): Promise<MeasurementsByAreaResult> {
    const measurements = await this.measurementRepository.retrieveLatestByArea(
      xMin,
      yMin,
      xMax,
      yMax,
      true,
      measure,
    );

    return this.setEPACorrectedPM(measurements);
  }

  async getLastMeasurementsByCluster(
    xMin: number,
    yMin: number,
    xMax: number,
    yMax: number,
    zoom: number,
    excludeOutliers: boolean,
    measure?: MeasureType,
    minPoints?: number,
    radius?: number,
    maxZoom?: number,
  ): Promise<MeasurementClusterResult> {
    // Default set to pm25 if not provided
    measure = measure || MeasureType.PM25;

    // Query locations by certain area with measurementType as the value
    const locations = await this.measurementRepository.retrieveLatestByArea(
      xMin,
      yMin,
      xMax,
      yMax,
      excludeOutliers,
      measure,
    );

    if (locations.length === 0) {
      // Directly return if query result empty
      return new Array<MeasurementCluster>();
    }

    const clusterMinPoints = minPoints ?? this.CLUSTER_MIN_POINTS;
    const clusterRadius = radius ?? this.CLUSTER_RADIUS;
    const clusterMaxZoom = maxZoom ?? this.CLUSTER_MAX_ZOOM;

    this.logger.debug(`clusterMinPoints ${clusterMinPoints}`);
    this.logger.debug(`clusterRadius ${clusterRadius}`);
    this.logger.debug(`clusterMaxZoom ${clusterMaxZoom}`);

    // converting to .geojson features array
    let geojson = new Array<MeasurementGeoJSONFeature>();
    locations.map(point => {
      const value =
        measure === MeasureType.PM25 && point.dataSource === DataSource.AIRGRADIENT
          ? getEPACorrectedPM(point.pm25, point.rhum)
          : point[measure];
      geojson.push({
        type: 'Feature',
        geometry: {
          type: 'Point',
          coordinates: [point.longitude, point.latitude],
        },
        properties: {
          locationId: point.locationId,
          locationName: point.locationName,
          sensorType: point.sensorType,
          dataSource: point.dataSource,
          value,
        },
      });
    });

    // Cluster data points and return cluster calculation results only if zoom level below clusterMaxZoom
    let clusters: any;
    if (zoom > clusterMaxZoom) {
      clusters = geojson;
    } else {
      const clustersIndexes = new Supercluster({
        radius: clusterRadius,
        minPoints: clusterMinPoints,
        map: props => ({
          sum: props.value,
        }),
        reduce: (accumulate, props) => {
          accumulate.sum += props.sum;
        },
      });
      clustersIndexes.load(geojson);
      clusters = clustersIndexes.getClusters([xMin, yMin, xMax, yMax], zoom);
    }

    // Map to to array of MeasurementClusterModel
    const clustersModel = clusters.map(
      (clusterResult: Partial<MeasurementCluster>) => new MeasurementCluster(clusterResult),
    );

    return clustersModel;
  }

  private setEPACorrectedPM(measurements: MeasurementEntity[]) {
    return measurements.map(point => {
      if (point.dataSource === DataSource.AIRGRADIENT && (point.pm25 || point.pm25 === 0)) {
        point.pm25 = getEPACorrectedPM(point.pm25, point.rhum);
      }
      return point;
    });
  }
}
