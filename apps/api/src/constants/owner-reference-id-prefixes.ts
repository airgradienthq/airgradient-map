import { DataSource } from 'src/types';

export const OWNER_REFERENCE_ID_PREFIXES: Record<DataSource, string> = {
  [DataSource.AIRGRADIENT]: 'ag_',
  [DataSource.OPENAQ]: 'oaq_',
  [DataSource.DUSTBOY]: 'db_',
  [DataSource.SENSORCOMMUNITY]: 'sc_',
};
