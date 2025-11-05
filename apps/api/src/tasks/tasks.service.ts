import { HttpException, Injectable, Logger } from '@nestjs/common';
import { Cron, CronExpression } from '@nestjs/schedule';
import { ConfigService } from '@nestjs/config';

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
  }

  @Cron(CronExpression.EVERY_HOUR)
  async runSyncAirgradientLocations() {
    // const start = Date.now();

    // Fetch data from the airgradient external API
    const url = OLD_AG_BASE_API_URL;
    const data = await this.http.fetch<AirgradientModel[]>(url, {
      Origin: 'https://airgradient.com',
    });
    this.logger.log(`Sync AirGradient locations with total public data: ${data.length}`);

    // map location data for upsert function
    const locationOwnerInput: UpsertLocationOwnerInput[] = data.map(raw => ({
      ownerReferenceId: raw.placeId,
      ownerName: raw.publicContributorName,
      ownerUrl: raw.publicPlaceUrl,
      locationReferenceId: raw.locationId,
      locationName: raw.publicLocationName,
      sensorType: SensorType.SMALL_SENSOR,
      timezone: raw.timezone,
      coordinateLatitude: raw.latitude,
      coordinateLongitude: raw.longitude,
      licenses: [AG_DEFAULT_LICENSE],
      provider: 'AirGradient',
    }));

    // NOTE: optimization needed to upsert in chunk?
    await this.tasksRepository.upsertLocationsAndOwners('AirGradient', locationOwnerInput);
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
    // const start = Date.now();

    try {
      // Fetch data from the airgradient external API
      const url = OLD_AG_BASE_API_URL;
      const data = await this.http.fetch<AirgradientModel[]>(url, {
        Origin: 'https://airgradient.com',
      });
      this.logger.log(`Sync AirGradient latest measures total public data: ${data.length}`);
      await this.tasksRepository.insertNewAirgradientLatest(data);
    } finally {
      this.isAirgradientLatestJobRunning = false;
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
    this.logger.debug('Run job sync OpenAQ locations');
    const providersId = OPENAQ_PROVIDERS.map(p => p.id);

    const before = Date.now();

    // TODO: Improve this to run asynchronously for each providers, then wait after loop
    for (let i = 0; i < providersId.length; i++) {
      await this.performSyncOpenAQLocations(providersId[i]);
    }

    const after = Date.now();
    const duration = after - before;
    this.logger.debug(`Sync OpenAQ locations time spend: ${duration}`);
  }

  @Cron(CronExpression.EVERY_HOUR)
  async runGetOpenAQLatest() {
    this.logger.log('Run job retrieve OpenAQ latest value');
    const before = Date.now();

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
      `runGetOpenAQLatest() time spend: ${duration} with total page request ${pageCounter}`,
    );
  }

  async performSyncOpenAQLocations(providerId: number) {
    let finish = false;
    let pageCounter = 1;
    let total = 0;

    while (finish === false) {
      // Retrieve every 1000 data maximum, so it will sync to database every 500 row
      const url = `https://api.openaq.org/v3/locations?monitor=true&page=${pageCounter}&limit=500&providers_id=${providerId}`;
      const data = await this.http.fetch<OpenAQApiLocationsResponse>(url, {
        'x-api-key': this.openAQApiKey,
      });
      // TODO: response error check

      // map location data for upsert function
      const locationOwnerInput: UpsertLocationOwnerInput[] = data.results.map(raw => ({
        ownerReferenceId: raw.owner.id,
        ownerName: raw.owner.name,
        locationReferenceId: raw.id,
        locationName: raw.name,
        sensorType: SensorType.REFERENCE, // NOTE: Hardcoded
        timezone: raw.timezone,
        coordinateLatitude: raw.coordinates.latitude,
        coordinateLongitude: raw.coordinates.longitude,
        licenses: (raw.licenses ?? []).map(license => license.name), // Check if its null first
        provider: raw.provider.name,
      }));

      await this.tasksRepository.upsertLocationsAndOwners('OpenAQ', locationOwnerInput);

      // Sometimes `found` field is a string
      const t = typeof data.meta.found;
      if (t === 'number') {
        let foundInt = Number(data.meta.found);
        total = total + Number(data.meta.found);

        // Check if this batch is the last batch
        if (foundInt <= data.meta.limit) {
          finish = true;
          this.logger.debug(`ProviderId ${providerId} loop finish with total page ${pageCounter}`);
        }
      } else {
        total = total + data.meta.limit;
      }

      pageCounter = pageCounter + 1;
    }
  }
}
