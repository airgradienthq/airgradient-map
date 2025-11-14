import { SensorType } from '../shared/sensor-type';

export interface UpsertLocationOwnerInput {
  ownerReferenceId: number;
  ownerName?: string;
  ownerUrl?: string;
  locationReferenceId: number;
  locationName?: string;
  sensorType: SensorType;
  timezone: string;
  coordinateLatitude?: number;
  coordinateLongitude?: number;
  licenses?: string[];
  provider: string;
}
