import { InsertLatestMeasuresInput } from './latest-measures-input.types';
import { UpsertLocationOwnerInput } from './location-owner-input.types';

interface PluginDataSourceOutput {
  success: boolean;
  count: number;
  error?: string;
}

export interface PluginDataSourceLatestOutput extends PluginDataSourceOutput {
  data: InsertLatestMeasuresInput[];
  metadata: {
    locationIdAvailable: boolean;
  };
}

export interface PluginDataSourceLocationOutput extends PluginDataSourceOutput {
  data: UpsertLocationOwnerInput[];
  metadata: {
    allowApiAccess: boolean;
    dataSourceUrl: string;
  };
}

export interface PluginDataSource {
  latest(args?: Record<string, any>): Promise<PluginDataSourceLatestOutput>;
  location(args?: Record<string, any>): Promise<PluginDataSourceLocationOutput>;
}
