class Meta {
  name: string;
  website: string;
  page: number;
  limit: number;
  found: number | string;

  constructor(meta: Meta) {
    this.name = meta.name;
    this.website = meta.website;
    this.page = meta.page;
    this.limit = meta.limit;
    this.found = meta.found;
  }
}

class Owner {
  id: number;
  name: string;

  constructor(owner: Owner) {
    this.id = owner.id;
    this.name = owner.name;
  }
}

class Provider {
  id: number;
  name: string;

  constructor(provider: Provider) {
    this.id = provider.id;
    this.name = provider.name;
  }
}

class Parameter {
  id: number;
  name: string;
  units: string;
  displayName: string;

  constructor(parameter: Parameter) {
    this.id = parameter.id;
    this.name = parameter.name;
    this.units = parameter.units;
    this.displayName = parameter.displayName;
  }
}

class DateTime {
  utc: string;
  local: string;

  constructor(utc: string, local: string) {
    this.utc = utc;
    this.local = local;
  }
}

export class Sensor {
  id: number;
  name: string;
  parameter: Parameter;

  constructor(sensor: Sensor) {
    this.id = sensor.id;
    this.name = sensor.name;
    this.parameter = new Parameter(sensor.parameter);
  }
}

class Coordinates {
  latitude: number;
  longitude: number;

  constructor(coordinates: Coordinates) {
    this.latitude = coordinates.latitude;
    this.longitude = coordinates.longitude;
  }
}

class License {
  id: number;
  name: string;
  attribution: any;
  dateFrom: string;
  dateTo: string | null;

  constructor(license: License) {
    this.id = license.id;
    this.name = license.name;
    this.attribution = license.attribution;
    this.dateFrom = license.dateFrom;
    this.dateTo = license.dateTo;
  }
}

class ResultLocations {
  id: number;
  name: string;
  locality: string;
  timezone: string;
  country: any;
  owner: Owner;
  provider: Provider;
  isMobile: boolean;
  isMonitor: boolean;
  instruments: any;
  sensors: Sensor[];
  coordinates: Coordinates;
  licenses: License[];
  bounds: number[];
  distance: number | null;
  datetimeFirst: DateTime;
  datetimeLast: DateTime;

  constructor(result: ResultLocations) {
    this.id = result.id;
    this.name = result.name;
    this.locality = result.locality;
    this.timezone = result.timezone;
    this.country = result.country;
    this.owner = new Owner(result.owner);
    this.provider = new Provider(result.provider);
    this.isMobile = result.isMobile;
    this.isMonitor = result.isMonitor;
    this.instruments = result.instruments;
    this.sensors = result.sensors.map(s => new Sensor(s));
    this.coordinates = new Coordinates(result.coordinates);
    this.licenses = result.licenses;
    this.bounds = [...result.bounds];
    this.distance = result.distance;
    this.datetimeFirst = result.datetimeFirst;
    this.datetimeLast = result.datetimeLast;
  }
}

class ResultParameters {
  datetime: DateTime;
  value: number;
  coordinates: Coordinates;
  sensorsId: number;
  locationsId: number;

  constructor(result: ResultParameters) {
    this.datetime = result.datetime;
    this.value = result.value;
    this.coordinates = result.coordinates;
    this.sensorsId = result.sensorsId;
    this.locationsId = result.locationsId;
  }
}

export class OpenAQApiParametersResponse {
  meta: Meta;
  results: ResultParameters[];

  constructor(response: OpenAQApiParametersResponse) {
    this.meta = response.meta;
    this.results = response.results.map(r => new ResultParameters(r));
  }
}

export class OpenAQApiLocationsResponse {
  meta: Meta;
  results: ResultLocations[];

  constructor(response: OpenAQApiLocationsResponse) {
    this.meta = new Meta(response.meta);
    this.results = response.results.map(r => new ResultLocations(r));
  }
}
