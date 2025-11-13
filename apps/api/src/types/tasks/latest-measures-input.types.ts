export interface InsertLatestMeasuresInput {
  locationId?: number;
  locationReferenceId: number;
  pm25?: number;
  pm10?: number;
  atmp?: number;
  rhum?: number;
  rco2?: number;
  o3?: number;
  no2?: number;
  measuredAt: string;
}
