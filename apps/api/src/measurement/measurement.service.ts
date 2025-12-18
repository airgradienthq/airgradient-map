import { Injectable, Logger } from '@nestjs/common';
import MeasurementRepository from './measurement.repository';
import Supercluster from 'supercluster';
import MeasurementCluster from './measurementCluster.model';
import { ConfigService } from '@nestjs/config';
import { getPMWithEPACorrectionIfNeeded } from 'src/utils/getEpaCorrectedPM';
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
import OutlierRealtimeQuery from './outlierRealtimeQuery';
import { OutlierService, OutlierCalculationOptions } from 'src/outlier/outlier.service';

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
    private readonly outlierService: OutlierService,
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
    hasFullAccess: boolean,
    measure?: MeasureType,
    page = 1,
    pagesize = 100,
  ): Promise<MeasurementServiceResult> {
    const offset = pagesize * (page - 1); // Calculate the offset for query
    const measurements = await this.measurementRepository.retrieveLatest(
      hasFullAccess,
      offset,
      pagesize,
      measure,
    );

    return this.setEPACorrectedPM(measurements);
  }

  async getLastMeasurementsByArea(
    xMin: number,
    yMin: number,
    xMax: number,
    yMax: number,
    hasFullAccess: boolean,
    measure?: MeasureType,
  ): Promise<MeasurementsByAreaResult> {
    const measurements = await this.measurementRepository.retrieveLatestByArea(
      xMin,
      yMin,
      xMax,
      yMax,
      true,
      hasFullAccess,
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
    hasFullAccess: boolean,
    measure?: MeasureType,
    minPoints?: number,
    radius?: number,
    maxZoom?: number,
    outlierQuery?: OutlierRealtimeQuery,
  ): Promise<MeasurementClusterResult> {
    // Default set to pm25 if not provided
    measure = measure || MeasureType.PM25;

    const outliersOnly = outlierQuery?.outliersOnly === true;

    const hasDynamicOutlierParams =
      outlierQuery?.outlierRadiusMeters !== undefined ||
      outlierQuery?.outlierWindowHours !== undefined ||
      outlierQuery?.outlierMinNearby !== undefined ||
      outlierQuery?.outlierAbsoluteThreshold !== undefined ||
      outlierQuery?.outlierZScoreThreshold !== undefined ||
      outlierQuery?.outlierZScoreMinMean !== undefined ||
      outlierQuery?.outlierUseStoredOutlierFlagForNeighbors !== undefined ||
      outlierQuery?.outlierEnableSameValueCheck !== undefined ||
      outlierQuery?.outlierSameValueWindowHours !== undefined ||
      outlierQuery?.outlierSameValueMinCount !== undefined ||
      outlierQuery?.outlierSameValueIncludeZero !== undefined;

    const shouldApplyDynamicOutlier =
      (excludeOutliers || outliersOnly) && measure === MeasureType.PM25 && hasDynamicOutlierParams;

    const shouldFilterStoredOutliers =
      !shouldApplyDynamicOutlier && excludeOutliers && !outliersOnly;
    const shouldReturnStoredOutliersOnly = !shouldApplyDynamicOutlier && outliersOnly;

    // Query locations by certain area with measurementType as the value
    const locations = await this.measurementRepository.retrieveLatestByArea(
      xMin,
      yMin,
      xMax,
      yMax,
      shouldFilterStoredOutliers,
      hasFullAccess,
      measure,
      shouldReturnStoredOutliersOnly,
    );

    if (locations.length === 0) {
      // Directly return if query result empty
      return new Array<MeasurementCluster>();
    }

    const outlierFilteredLocations = shouldApplyDynamicOutlier
      ? await this.filterOutliersWithOverrides(locations, outlierQuery)
      : locations;

    if (outlierFilteredLocations.length === 0) {
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
    outlierFilteredLocations.map(point => {
      const value =
        measure === MeasureType.PM25
          ? getPMWithEPACorrectionIfNeeded(point.dataSource as DataSource, point.pm25, point.rhum)
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

  private async filterOutliersWithOverrides(
    locations: MeasurementEntity[],
    outlierQuery?: OutlierRealtimeQuery,
  ): Promise<MeasurementEntity[]> {
    const outliersOnly = outlierQuery?.outliersOnly === true;

    const outlierOptions: OutlierCalculationOptions = {
      radiusMeters: outlierQuery?.outlierRadiusMeters,
      measuredAtIntervalHours: outlierQuery?.outlierWindowHours,
      minNearbyCount: outlierQuery?.outlierMinNearby,
      absoluteThreshold: outlierQuery?.outlierAbsoluteThreshold,
      zScoreThreshold: outlierQuery?.outlierZScoreThreshold,
      zScoreMinMean: outlierQuery?.outlierZScoreMinMean,
      enableSameValueCheck: outlierQuery?.outlierEnableSameValueCheck,
      sameValueWindowHours: outlierQuery?.outlierSameValueWindowHours,
      sameValueMinCount: outlierQuery?.outlierSameValueMinCount,
      sameValueIncludeZero: outlierQuery?.outlierSameValueIncludeZero,
      useStoredOutlierFlagForNeighbors:
        outlierQuery?.outlierUseStoredOutlierFlagForNeighbors ?? false,
    };

    const dataPoints = locations
      .filter(
        location =>
          location.pm25 !== null &&
          location.pm25 !== undefined &&
          location.locationReferenceId !== undefined &&
          location.measuredAt,
      )
      .map(location => ({
        locationReferenceId: location.locationReferenceId as number,
        pm25: location.pm25 as number,
        measuredAt: new Date(location.measuredAt).toISOString(),
        dataSource: location.dataSource,
      }));

    if (dataPoints.length === 0) {
      return locations;
    }

    const outlierResults = await this.outlierService.calculateBatchIsPm25Outlier(
      undefined,
      dataPoints,
      outlierOptions,
    );

    return locations.filter(location => {
      if (location.locationReferenceId === undefined || location.pm25 === null) {
        return outliersOnly ? false : true;
      }

      const key = `${location.locationReferenceId}_${new Date(location.measuredAt).toISOString()}`;
      const isOutlier = outlierResults.get(key) === true;
      return outliersOnly ? isOutlier : !isOutlier;
    });
  }

  private setEPACorrectedPM(measurements: MeasurementEntity[]) {
    return measurements.map(point => {
      if (point.pm25 || point.pm25 === 0) {
        point.pm25 = getPMWithEPACorrectionIfNeeded(
          point.dataSource as DataSource,
          point.pm25,
          point.rhum,
        );
      }
      return point;
    });
  }
}
