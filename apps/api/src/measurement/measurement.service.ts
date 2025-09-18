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

@Injectable()
export class MeasurementService {
  // Default constant values
  private clusterRadius = 80;
  private clusterMaxZoom = 8;
  private clusterMinPoints = 2;
  private readonly logger = new Logger(MeasurementService.name);

  constructor(
    private readonly measurementRepository: MeasurementRepository,
    private readonly configService: ConfigService,
  ) {
    const clusterRadius = this.configService.get<number>('MAP_CLUSTER_RADIUS');
    if (clusterRadius) {
      this.clusterRadius = clusterRadius;
    }

    const clusterMaxZoom = this.configService.get<number>('MAP_CLUSTER_MAX_ZOOM');
    if (clusterMaxZoom) {
      this.clusterMaxZoom = clusterMaxZoom;
    }
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
      measure,
    );
    if (locations.length === 0) {
      // Directly return if query result empty
      return new Array<MeasurementCluster>();
    }

    this.clusterMinPoints = minPoints ?? this.clusterMinPoints;
    this.clusterRadius = radius ?? this.clusterRadius;
    this.clusterMaxZoom = maxZoom ?? this.clusterMaxZoom;
    this.logger.debug(`minPoints ${this.clusterMinPoints}`);
    this.logger.debug(`radius ${this.clusterRadius}`);
    this.logger.debug(`maxZoom ${this.clusterMaxZoom}`);

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
    if (zoom > this.clusterMaxZoom) {
      clusters = geojson;
    } else {
      const clustersIndexes = new Supercluster({
        radius: this.clusterRadius,
        minPoints: this.clusterMinPoints,
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
