import { HttpException, Injectable, Logger } from '@nestjs/common';
import { Cron, CronExpression } from '@nestjs/schedule';
import { TasksRepository, UpsertLocationOwnerInput } from './tasks.repository';
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

  @Cron(CronExpression.EVERY_DAY_AT_MIDNIGHT)
  async runSyncAirgradientLocations() {
    const start = Date.now();

    // Fetch data from the airgradient external API
    const url = 'https://api.airgradient.com/public/api/v1/world/locations/measures/current';
    const data = await this.http.fetch<AirgradientModel[]>(url);
    this.logger.log(`AirGradient total public data: ${data.length}`);

    // map location data for upsert function
    const locationOwnerInput: UpsertLocationOwnerInput[] = data.map(raw => ({
      ownerName: raw.publicContributorName,
      ownerUrl: raw.publicPlaceUrl,
      ownerDescription: raw.publicPlaceUrl,
      locationReferenceId: raw.locationId,
      locationName: raw.publicLocationName,
      sensorType: 'Small Sensor',
      timezone: raw.timezone,
      coordinateLatitude: raw.latitude,
      coordinateLongitude: raw.longitude,
      licenses: ['CC BY-SA 4.0'],
      provider: 'AirGradient',
    }));

    await this.tasksRepository.upsertLocationsAndOwners('AirGradient', locationOwnerInput);
    // TODO: Add success check
    // TODO need to iterate every 500?
  }

  @Cron('*/1 * * * *')
  async getAirgradientLatest() {
    this.logger.log('Run job retrieve AirGradient latest value');
    const start = Date.now();

    // Fetch data from the airgradient external API
    const url = 'https://api.airgradient.com/public/api/v1/world/locations/measures/current';
    const data = await this.http.fetch<AirgradientModel[]>(url);
    this.logger.log(`AirGradient total public data: ${data.length}`);

    await this.tasksRepository.insertNewAirgradientLatest(data);
    // TODO: Add success check
    // TODO need to iterate every 500?
  }

  @Cron(CronExpression.EVERY_DAY_AT_MIDNIGHT)
  async runSyncOpenAQLocations() {

    this.logger.debug('Run job sync OpenAQ locations');
    const providersId = OPENAQ_PROVIDERS.map(p => p.id);

    const before = Date.now();

    // TODO: Improve this to run asynchronously for each providers, then wait after loop
    for (var i = 0; i < providersId.length; i++) {
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

    let locationIds = await this.tasksRepository.retrieveOpenAQLocationId();
    if (locationIds === null) {
      // NOTE: Right now ignore until runSyncOpenAQLocations() already triggered
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

  async performSyncOpenAQLocations(providerId: number) {
    var finish = false;
    var pageCounter = 1;
    var total = 0;

    while (finish === false) {
      // Retrieve every 1000 data maximum, so it will sync to database every 500 row
      const url = `https://api.openaq.org/v3/locations?monitor=true&page=${pageCounter}&limit=500&providers_id=${providerId}`;
      const data = await this.http.fetch<OpenAQApiLocationsResponse>(url, {
        'x-api-key': this.openAQApiKey,
      });
      // TODO: response error check

      // map location data for upsert function
      const locationOwnerInput: UpsertLocationOwnerInput[] = data.results.map(raw => ({
        ownerName: raw.owner.name,
        ownerUrl: null,
        ownerDescription: null,
        locationReferenceId: raw.id,
        locationName: raw.name,
        sensorType: 'Reference', // NOTE: Hardcoded
        timezone: raw.timezone,
        coordinateLatitude: raw.coordinates.latitude,
        coordinateLongitude: raw.coordinates.longitude,
        licenses: (raw.licenses ?? []).map(license => license.name), // Check if its null first
        provider: raw.provider.name,
      }));

      await this.tasksRepository.upsertLocationsAndOwners('AirGradient', locationOwnerInput);
      // TODO: Add success check?

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
