import { HttpException, Injectable, Logger } from '@nestjs/common';
import { Cron, CronExpression } from '@nestjs/schedule';
import TasksRepository from './tasks.repository';
import { TasksHttp } from './tasks.http';
import { AirgradientModel } from './tasks.model';
import { ConfigService } from '@nestjs/config';
import { OpenAQApiLocationsResponse, OpenAQApiParametersResponse } from './model/openaq.model';
import { OPENAQ_PROVIDERS } from 'src/constants/openaq-providers';

@Injectable()
export class TasksService {
  private openAQApiKey = '';

  constructor(
    private readonly tasksRepository: TasksRepository,
    private readonly http: TasksHttp,
    private readonly configService: ConfigService,
  ) {
    const apiKey = this.configService.get<string>('API_KEY_OPENAQ');
    if (apiKey) {
      this.openAQApiKey = apiKey;
    }
  }

  private readonly logger = new Logger(TasksService.name);

  @Cron('*/15 * * * *')
  async newData() {
    const start = Date.now();

    // Fetch data from the airgradient external API
    const url = 'https://api.airgradient.com/public/api/v1/world/locations/measures/current';
    const data = await this.http.fetch<AirgradientModel[]>(url);
    this.logger.debug('Total data: ' + data.length);

    const success = await this.tasksRepository.insertAg(data);
    if (success) {
      const duration = Date.now() - start;
      this.logger.debug(
        `Successfully insert new airgradient latest measures. Time spend ${duration}ms`,
      );
    }
  }

  @Cron(CronExpression.EVERY_DAY_AT_MIDNIGHT)
  async runSyncOpenAQLocations() {
    this.logger.debug('Run job sync OpenAQ locations');
    const providersId = OPENAQ_PROVIDERS.map(p => p.id);

    const before = Date.now();

    // TODO: Improve this to run asynchronously for each providers, then wait after loop
    for (var i = 0; i < providersId.length; i++) {
      await this.performSyncLocations(providersId[i]);
    }

    const after = Date.now();
    const duration = after - before;
    this.logger.debug(`runOpenAQ() time spend: ${duration}`);
  }

  @Cron(CronExpression.EVERY_HOUR)
  async runGetOpenAQLatest() {
    this.logger.log('Run job retrieve OpenAQ latest value');
    const before = Date.now();

    let locationIds = await this.tasksRepository.retrieveOpenAQLocationId();
    if (locationIds === null) {
      // NOTE: Right now ingore until runSyncOpenAQLocations() already triggered
      this.logger.warn('No openaq locationId found');
      return;
    }

    const locationIdsLength = Object.keys(locationIds).length;
    var pageCounter = 1;
    var matchCounter = 0;

    this.logger.debug(
      `Start request to openaq parameters endpoint with interest total locationId ${locationIdsLength}`,
    );
    while (matchCounter < locationIdsLength) {
      // Parameters '2' is pm2.5 parameter id
      const url = `https://api.openaq.org/v3/parameters/2/latest?limit=1000&page=${pageCounter}`;
      var data: OpenAQApiParametersResponse | null;
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
      var batches = [];
      for (var i = 0; i < data.results.length; i++) {
        if (Object.hasOwn(locationIds, data.results[i].locationsId)) {
          // LocationId is in intereset, push so later will be inserted
          var batch = {};
          // locationId here is the actual locationId from table, not from openaq
          batch['locationId'] = locationIds[data.results[i].locationsId.toString()];
          batch['pm25'] = data.results[i].value;
          batch['measuredAt'] = data.results[i].datetime.utc;
          batches.push(batch);

          matchCounter = matchCounter + 1;
        }
      }

      //this.logger.debug(batchValues);
      this.logger.debug(matchCounter);
      if (batches.length > 0) {
        // Only insert if batch more than one
        this.tasksRepository.insertNewOpenAQLatest(batches);
      }

      pageCounter = pageCounter + 1;
    }

    if (matchCounter < locationIdsLength) {
      this.logger.warn(`Total OpenAQ locations that not match ${locationIdsLength - matchCounter}`);
    }

    const after = Date.now();
    const duration = after - before;
    this.logger.debug(
      `runGetOpenAQLatest() time spend: ${duration} with total page request ${pageCounter}`,
    );
  }

  async performSyncLocations(providerId: number) {
    var finish = false;
    var pageCounter = 1;
    var total = 0;

    while (finish === false) {
      const url = `https://api.openaq.org/v3/locations?monitor=true&page=${pageCounter}&limit=500&providers_id=${providerId}`;
      var data: OpenAQApiLocationsResponse;
      try {
        data = await this.http.fetch<OpenAQApiLocationsResponse>(url, {
          'x-api-key': this.openAQApiKey,
        });
      } catch (error) {
        if (error instanceof HttpException && error.getStatus() === 404) {
          this.logger.debug('Requested page already empty for locations endpoint');
          break;
        } else {
          break;
        }
      }

      if (!data || !data.results) break;

      var locations = [];
      for (var i = 0; i < data.results.length; i++) {
        var location = {};
        location['referenceId'] = data.results[i].id;
        location['locationName'] = data.results[i].name;
        location['providerName'] = data.results[i].provider.name;
        location['ownerName'] = data.results[i].owner.name;
        location['sensorType'] = 'Reference';
        location['timezone'] = data.results[i].timezone;
        location['coordinate'] = [
          data.results[i].coordinates.latitude,
          data.results[i].coordinates.longitude,
        ];

        if (data.results[i].licenses !== null) {
          location['licenses'] = data.results[i].licenses
            .map(license => `'${license.name}'`)
            .join(',');
        } else {
          location['licenses'] = null;
        }

        locations.push(location);
      }

      if (locations.length > 0) {
        this.tasksRepository.upsertOpenAQLocations(locations);
        total += locations.length;
      }

      if (data.results.length < 500) {
        finish = true;
      }

      pageCounter++;
    }
  }
}
