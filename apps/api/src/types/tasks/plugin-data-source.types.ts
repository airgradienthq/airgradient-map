import { InsertLatestMeasuresInput } from './latest-measures-input.types';
import { UpsertLocationOwnerInput } from './location-owner-input.types';

export interface PluginDataSourceOutput {
  success: boolean;
  count: number;
  data: InsertLatestMeasuresInput[] | UpsertLocationOwnerInput[] | [];
  metadata?: Record<string, any>;
  error?: string;
}

export interface PluginDataSource {
  latest(args?: Record<string, any>): Promise<PluginDataSourceOutput>;
  location(args?: Record<string, any>): Promise<PluginDataSourceOutput>;
}
