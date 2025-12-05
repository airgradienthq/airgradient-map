// IMPORTANT: Enum Values must match the values in the database

export enum DataSource {
  AIRGRADIENT = 'AirGradient',
  OPENAQ = 'OpenAQ',
  DUSTBOY = 'DustBoy',
}

export type DataSourceType = `${DataSource}`;
