import { HttpException, Injectable, Logger } from '@nestjs/common';
import { Cron, CronExpression } from '@nestjs/schedule';
import { ConfigService } from '@nestjs/config';
import * as path from 'path';

import { TasksRepository } from './tasks.repository';
import { TasksHttp } from './tasks.http';
import { AirgradientModel } from './model/airgradient.model';
import { OpenAQApiLocationsResponse, OpenAQApiParametersResponse } from './model/openaq.model';
import { OPENAQ_PROVIDERS } from 'src/constants/openaq-providers';
import { UpsertLocationOwnerInput } from 'src/types/tasks/upsert-location-input';
import { SensorType } from 'src/types/shared/sensor-type';
import { AG_DEFAULT_LICENSE } from 'src/constants/ag-default-license';
import { OLD_AG_BASE_API_URL } from 'src/constants/old-ag-base-api-url';
import { NotificationsService } from 'src/notifications/notifications.service';

@Injectable()
export class TasksService {
  private openAQApiKey = '';
  private readonly logger = new Logger(TasksService.name);
  private isAirgradientLatestJobRunning = false;
  private dataSourcePath = '';

  constructor(
    private readonly tasksRepository: TasksRepository,
    private readonly http: TasksHttp,
    private readonly configService: ConfigService,
    private readonly notificationsService: NotificationsService,
  ) {
    const apiKey = this.configService.get<string>('API_KEY_OPENAQ');
    if (apiKey) {
      this.openAQApiKey = apiKey;
    }

    this.dataSourcePath = path.resolve(process.cwd(), 'data-source');
  }

  @Cron(CronExpression.EVERY_HOUR)
  async runSyncAirgradientLocations() {
    // load file and run
    const filePath = path.join(this.dataSourcePath, 'public', 'airgradient.js');
    const plugin = await import(filePath);
    const result = await plugin.location();

    // Check if success or not
    if (!result.success) {
      this.logger.error(`Sync airgradient location error: ${result.error}`);
      return;
    }

    if (result.count == 0 || result.data == null) {
      this.logger.error('Sync airgradient location error: no data available');
      return;
    }

    this.logger.log(`Sync AirGradient locations with total public data: ${result.count}`);

    // NOTE: optimization needed to upsert in chunk?
    await this.tasksRepository.upsertLocationsAndOwners(
      'AirGradient',
      result.data as UpsertLocationOwnerInput[],
    );
  }

  @Cron(CronExpression.EVERY_MINUTE)
  async getAirgradientLatest() {
    if (this.isAirgradientLatestJobRunning) {
      this.logger.warn(
        'AirGradient latest job skipped because a previous run is still in progress',
      );
      return;
    }

    this.isAirgradientLatestJobRunning = true;
    this.logger.log('Run job retrieve AirGradient latest value');
    const before = Date.now();
    let totalData: number;

    try {
      // Fetch data from the airgradient external API
      const url = OLD_AG_BASE_API_URL;
      const data = await this.http.fetch<AirgradientModel[]>(url, {
        Origin: 'https://airgradient.com',
      });
      totalData = data.length;
      this.logger.log(`Sync AirGradient latest measures total public data: ${totalData}`);
      await this.tasksRepository.insertNewAirgradientLatest(data);
    } finally {
      this.isAirgradientLatestJobRunning = false;
      this.logger.debug(
        `getAirgradientLatest() time spend: ${Date.now() - before}ms with total data point: ${totalData}`,
      );
    }
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

  @Cron(CronExpression.EVERY_DAY_AT_MIDNIGHT)
  async runSyncOpenAQLocations() {
    const before = Date.now();
    this.logger.log('Run job sync OpenAQ locations');

    const filePath = path.join(this.dataSourcePath, 'public', 'openaq.js');
    const plugin = await import(filePath);
    const result = await plugin.location(this.openAQApiKey);

    // Check if success or not
    if (!result.success) {
      this.logger.error(`Sync OpenAQ location error: ${result.error}`);
      return;
    }

    if (result.count == 0 || result.data == null) {
      this.logger.error('Sync OpenAQ location error: no data available');
      return;
    }

    this.logger.debug(`Total OpenAQ data: ${result.count}`);
    this.logger.debug(result.data[0]);

    // TODO: Temporarily put here batch process here, should be on repository.upsertLocationsAndOwners()
    const batchSize = 1000;
    for (let i = 0; i < result.count; i += batchSize) {
      this.logger.debug(`Inserting from idx ${i}`);
      await this.tasksRepository.upsertLocationsAndOwners(
        'OpenAQ',
        result.data.slice(i, i + batchSize) as UpsertLocationOwnerInput[],
      );
    }

    const after = Date.now();
    const duration = after - before;
    this.logger.log(`Sync OpenAQ locations time spend: ${duration}ms`);
  }

  @Cron(CronExpression.EVERY_HOUR)
  async runGetOpenAQLatest() {
    this.logger.log('Run job retrieve OpenAQ latest value');
    const before = Date.now();
    let totalData: number = 0;

    const referenceIdToIdMap = await this.tasksRepository.retrieveOpenAQLocationId();
    const referenceIdToIdMapLength = Object.keys(referenceIdToIdMap).length;

    if (referenceIdToIdMapLength === 0) {
      // NOTE: Right now ignore until runSyncOpenAQLocations() already triggered
      this.logger.warn('No openaq locationId found');
      return;
    }

    let maxPages = -1;
    let pageCounter = 1;
    let matchCounter = 0;

    this.logger.debug(
      `Start request to openaq parameters endpoint with interest total locationId ${referenceIdToIdMapLength}`,
    );

    while (matchCounter < referenceIdToIdMapLength) {
      // Parameters '2' is pm2.5 parameter id
      const url = `https://api.openaq.org/v3/parameters/2/latest?limit=1000&page=${pageCounter}`;
      let data: OpenAQApiParametersResponse | null;
      try {
        data = await this.http.fetch<OpenAQApiParametersResponse>(url, {
          'x-api-key': this.openAQApiKey,
        });
      } catch (error) {
        if (error instanceof HttpException && error.getStatus() === 404) {
          this.logger.debug('Requested page already empty for parameters endpoint');
          break;
        } else {
          // TODO: What needs to be done here? Now just stop
          break;
        }
      }

      // Check each parameters locationId if it match to one of the already saved openaq location
      let batches = [];

      for (let i = 0; i < data.results.length; i++) {
        const locationReferenceId = data.results[i].locationsId.toString();

        if (locationReferenceId in referenceIdToIdMap) {
          batches.push({
            locationReferenceId: Number(locationReferenceId),
            locationId: referenceIdToIdMap[locationReferenceId],
            pm25: data.results[i].value,
            measuredAt: data.results[i].datetime.utc,
          });
          matchCounter = matchCounter + 1;
        }
      }

      //this.logger.debug(batchValues);
      this.logger.debug(matchCounter);
      if (batches.length > 0) {
        // Only insert if batch more than one
        totalData += batches.length;
        await this.tasksRepository.insertNewOpenAQLatest(batches);
      }

      if (maxPages === -1) {
        const found = Number(data.meta.found);
        const limit = Number(data.meta.limit);
        if (!isNaN(found) && !isNaN(limit) && limit > 0) {
          maxPages = Math.ceil(found / limit);
        }
      }
      if (pageCounter == maxPages) {
        this.logger.debug('Reached the last page of OpenAQ latest data.');
        break;
      }

      pageCounter = pageCounter + 1;
    }

    if (matchCounter < referenceIdToIdMapLength) {
      this.logger.warn(
        `Total OpenAQ locations that not match ${referenceIdToIdMapLength - matchCounter}`,
      );
    }

    const after = Date.now();
    const duration = after - before;
    this.logger.debug(
      `runGetOpenAQLatest() time spend: ${duration}ms with total page request: ${pageCounter} and total data point: ${totalData}`,
    );
  }
}
