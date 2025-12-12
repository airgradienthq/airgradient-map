import * as path from 'path';
import { Injectable, Logger } from '@nestjs/common';
import { Cron, CronExpression } from '@nestjs/schedule';
import { ConfigService } from '@nestjs/config';

import { TasksRepository } from './tasks.repository';
import { NotificationsService } from 'src/notifications/notifications.service';

import {
  DataSource,
  UpsertLocationOwnerInput,
  PluginDataSource,
  InsertLatestMeasuresInput,
} from 'src/types';

@Injectable()
export class TasksService {
  private openAQApiKey = '';
  private readonly logger = new Logger(TasksService.name);
  private isAirgradientLatestJobRunning = false;
  private dataSourcePath = '';

  constructor(
    private readonly tasksRepository: TasksRepository,
    private readonly configService: ConfigService,
    private readonly notificationsService: NotificationsService,
  ) {
    const apiKey = this.configService.get<string>('API_KEY_OPENAQ');
    if (apiKey) {
      this.openAQApiKey = apiKey;
    }

    this.dataSourcePath = path.resolve(process.cwd(), 'data-source');
  }

  private async syncLocations(
    folder: string,
    fileName: string,
    dataSource: DataSource,
    args?: Record<string, any>,
  ): Promise<void> {
    try {
      const before = Date.now();
      this.logger.log(`Run job sync ${fileName} locations`);

      // load file and run
      const filePath = path.join(this.dataSourcePath, folder, fileName);
      const plugin = (await import(filePath)) as PluginDataSource;
      const result = await plugin.location(args);

      // Validate results
      if (!result.success) {
        this.logger.error(`Sync ${fileName} location error: ${result.error}`);
        return;
      }
      if (result.count == 0) {
        this.logger.error(`Sync ${fileName} location error: no data available`);
        return;
      }
      this.logger.debug(result.data[0]);

      await this.tasksRepository.upsertLocationsAndOwners(
        dataSource,
        result.metadata.allowApiAccess,
        result.metadata.dataSourceUrl,
        result.data as UpsertLocationOwnerInput[],
      );
      this.logger.log(
        `Sync ${fileName} locations with total public data: ${result.count} time spend: ${Date.now() - before}ms`,
      );
    } catch (err) {
      this.logger.error(`Sync ${fileName} locations job failed: ${err}`);
    }
  }

  private async getLatest(
    folder: string,
    fileName: string,
    dataSource: DataSource,
    args?: Record<string, any>,
  ): Promise<void> {
    try {
      const before = Date.now();
      this.logger.log(`Run job get ${fileName} latest`);

      // load file and run
      const filePath = path.join(this.dataSourcePath, folder, fileName);
      const plugin = (await import(filePath)) as PluginDataSource;
      const result = await plugin.latest(args);

      // Validate results
      if (!result.success) {
        this.logger.error(`Get ${fileName} latest error: ${result.error}`);
        return;
      }
      if (result.count == 0) {
        this.logger.error(`Get ${fileName} latest error: no data available`);
        return;
      }
      this.logger.debug(result.data[0]);

      await this.tasksRepository.insertLatestMeasures(
        dataSource,
        result.metadata.locationIdAvailable,
        result.data as InsertLatestMeasuresInput[],
      );
      this.logger.log(
        `Get ${fileName} latest with total public data: ${result.count} time spend: ${Date.now() - before}ms`,
      );
    } catch (err) {
      this.logger.error(`Get ${fileName} latest job failed: ${err}`);
    }
  }

  @Cron(CronExpression.EVERY_HOUR)
  async syncAirgradientLocations(): Promise<void> {
    return await this.syncLocations('public', 'airgradient.js', DataSource.AIRGRADIENT);
  }

  @Cron(CronExpression.EVERY_DAY_AT_11PM)
  async syncOpenAQLocations(): Promise<void> {
    return await this.syncLocations('public', 'openaq.js', DataSource.OPENAQ, {
      apiKey: this.openAQApiKey,
    });
  }

  @Cron('10 23 * * *') // EVERY_DAY_AT_11_10_PM
  async syncDustBoyLocations(): Promise<void> {
    return await this.syncLocations('private', 'dustboy.js', DataSource.DUSTBOY);
  }

  @Cron('20 * * * *') // At minute 20 EVERY HOUR
  async syncSensorCommunityLocations(): Promise<void> {
    return await this.syncLocations('public', 'sensorcommunity.js', DataSource.SENSORCOMMUNITY);
  }

  @Cron(CronExpression.EVERY_MINUTE)
  async getAirgradientLatest(): Promise<void> {
    if (this.isAirgradientLatestJobRunning) {
      this.logger.warn(
        'AirGradient latest job skipped because a previous run is still in progress',
      );
      return;
    }

    this.isAirgradientLatestJobRunning = true;
    await this.getLatest('public', 'airgradient.js', DataSource.AIRGRADIENT);
    this.isAirgradientLatestJobRunning = false;
  }

  @Cron(CronExpression.EVERY_HOUR)
  async getOpenAQLatest(): Promise<void> {
    try {
      const referenceIdToIdMap = await this.tasksRepository.retrieveLocationIds(DataSource.OPENAQ);
      const referenceIdToIdMapLength = Object.keys(referenceIdToIdMap).length;

      if (referenceIdToIdMapLength === 0) {
        // NOTE: Right now ignore until runSyncOpenAQLocations() already triggered
        this.logger.warn('No openaq locationId found');
        return;
      }

      this.logger.debug(
        `Start request to openaq parameters endpoint with interest total locationId count ${referenceIdToIdMapLength}`,
      );

      return await this.getLatest('public', 'openaq.js', DataSource.OPENAQ, {
        apiKey: this.openAQApiKey,
        referenceIdToIdMap: referenceIdToIdMap,
        referenceIdToIdMapLength: referenceIdToIdMapLength,
      });
    } catch (err) {
      this.logger.error(`Get openaq.js latest job failed: ${err}`);
    }
  }

  @Cron('10 * * * *') // At minute 10 EVERY HOUR
  async getDustBoyLatest(): Promise<void> {
    await this.getLatest('private', 'dustboy.js', DataSource.DUSTBOY);
  }

  @Cron(CronExpression.EVERY_5_MINUTES)
  async getSensorCommunityLatest(): Promise<void> {
    await this.getLatest('public', 'sensorcommunity.js', DataSource.SENSORCOMMUNITY);
  }

  @Cron(CronExpression.EVERY_MINUTE)
  async sendNotifications() {
    const startTime = Date.now();
    this.logger.log('Starting scheduled notification check...');

    try {
      const result = await this.notificationsService.processAllNotifications();
      const duration = Date.now() - startTime;

      this.logger.log(`Notification job completed in ${duration}ms:`, {
        successful: result.successful.length,
        failed: result.failed.length,
        totalTime: result.totalTime,
      });

      if (result.failed.length > 0) {
        this.logger.warn('Notifications failed:', result.failed);
      }
    } catch (error) {
      this.logger.error('Notification job failed:', error);
    }
  }
}
