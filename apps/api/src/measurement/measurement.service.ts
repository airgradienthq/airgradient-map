import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Supercluster from 'supercluster';

import { getEPACorrectedPM } from 'src/utils/getEpaCorrectedPM';
import { MeasurementEntity } from './measurement.entity';
import MeasurementRepository from './measurement.repository';
import MeasurementCluster from './measurementCluster.model';
import RedisCacheService from '../redis-cache/redis-cache.service';

@Injectable()
export class MeasurementService {
  // Default constant values
  private clusterRadius = 80;
  private clusterMaxZoom = 8;
  private clusterTtlSecond = 900; // TTL = 15 min

  constructor(
    private readonly measurementRepository: MeasurementRepository,
    private readonly configService: ConfigService,
    private readonly redis: RedisCacheService,
  ) {
    const clusterRadius = this.configService.get<number>('MAP_CLUSTER_RADIUS');
    if (clusterRadius) {
      this.clusterRadius = clusterRadius;
    }

    const clusterMaxZoom = this.configService.get<number>('MAP_CLUSTER_MAX_ZOOM');
    if (clusterMaxZoom) {
      this.clusterMaxZoom = clusterMaxZoom;
    }

    const clusterTtlSecond = this.configService.get<number>('MAP_CLUSTER_TTL_SECONDS');
    if (clusterTtlSecond) {
      this.clusterTtlSecond = clusterTtlSecond;
    }
  }

  async getLastMeasurements(measure?: string, page = 1, pagesize = 100) {
    const offset = pagesize * (page - 1); // Calculate the offset for query
    const measurements = await this.measurementRepository.retrieveLatest(offset, pagesize, measure);

    return this.setEPACorrectedPM(measurements);
  }

  async getLastMeasurementsByArea(
    xMin: number,
    yMin: number,
    xMax: number,
    yMax: number,
    measure?: string,
  ): Promise<MeasurementEntity[]> {
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
    measure?: string,
  ): Promise<MeasurementCluster[]> {
    // Default set to pm25 if not provided
    measure = measure || 'pm25';

    const cacheKey = `cluster:${xMin}:${yMin}:${xMax}:${yMax}:${zoom}:${measure}`;

    // Try Redis cache
    const cached = await this.redis.get(cacheKey);
    if (cached) {
      const parsed = JSON.parse(cached);
      return parsed.map((item: any) => new MeasurementCluster(item));
    }

    // Fallback to DB query
    // Query locations by certain area with measurementType as the value
    const locations = await this.measurementRepository.retrieveLatestByArea(
      xMin,
      yMin,
      xMax,
      yMax,
      measure,
    );
    if (locations.length === 0) {
      await this.redis.set(cacheKey, JSON.stringify([]), this.clusterTtlSecond);
      // Directly return if query result empty
      return new Array<MeasurementCluster>();
    }

    // Converting to .geojson features array
    let geojson = new Array<any>();
    locations.map(point => {
      const value =
        measure === 'pm25' && point.dataSource === 'AirGradient'
          ? getEPACorrectedPM(point.pm25, point.rhum)
          : point[measure];
      geojson.push({
        type: 'Feature',
        geometry: {
          type: 'Point',
          coordinates: [point.latitude, point.longitude],
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
    var clusters: any;
    if (zoom > this.clusterMaxZoom) {
      clusters = geojson;
    } else {
      const clustersIndexes = new Supercluster({
        radius: this.clusterRadius,
        map: props => ({
          sum: props.value,
        }),
        reduce: (accumulate, props) => {
          accumulate.sum += props.sum;
        },
      });
      clustersIndexes.load(geojson);
      clusters = clustersIndexes.getClusters([yMin, xMin, yMax, xMax], zoom);
    }

    // Map to to array of MeasurementClusterModel
    const clustersModel = clusters.map(
      (clusterResult: Partial<MeasurementCluster>) => new MeasurementCluster(clusterResult),
    );

    // Cache it
    await this.redis.set(cacheKey, JSON.stringify(clusters), this.clusterTtlSecond);

    return clustersModel;
  }

  private setEPACorrectedPM(measurements: MeasurementEntity[]) {
    return measurements.map(point => {
      if (point.dataSource === 'AirGradient' && (point.pm25 || point.pm25 === 0)) {
        point.pm25 = getEPACorrectedPM(point.pm25, point.rhum);
      }
      return point;
    });
  }
}
