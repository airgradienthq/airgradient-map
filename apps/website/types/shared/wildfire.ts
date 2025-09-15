export interface WildfireProperties {
  brightness: number;
  confidence: number;
  frp: number;
  intensity: 'low' | 'medium' | 'high' | 'extreme';
  acq_date: string;
  acq_time: string;
  satellite: string;
  instrument: string;
}

export interface WildfireFeature {
  type: 'Feature';
  geometry: {
    type: 'Point';
    coordinates: [number, number];
  };
  properties: WildfireProperties;
}

export interface WildfireGeoJSON {
  type: 'FeatureCollection';
  features: WildfireFeature[];
}

export interface WildfireQueryParams {
  north: number;
  south: number;
  east: number;
  west: number;
  days?: number;
  source?: string;
}
