import { Inject, Injectable, Logger } from '@nestjs/common';
import { Pool, PoolClient, QueryResult } from 'pg';
import { CONNECTION_POOL } from './database.module-definition';

@Injectable()
class DatabaseService {
  private readonly logger = new Logger('Database');
  constructor(@Inject(CONNECTION_POOL) private readonly pool: Pool) {}

  async runQuery(query: string, params?: unknown[]): Promise<QueryResult<any>> {
    return this.queryWithLogging(this.pool, query, params);
  }

  getLogMessage(query: string, params?: unknown[]) {
    if (!params) {
      return `Query: ${query}`;
    }
    return `Query: ${query} Params: ${JSON.stringify(params)}`;
  }

  async queryWithLogging(source: Pool | PoolClient, query: string, params?: unknown[]): Promise<QueryResult<any>> {
    // message without unnecessary spaces and newlines
    const message = this.getLogMessage(query, params).replace(/\n|/g, '').replace(/  +/g, ' ');

    try {
      const result = await source.query(query, params);
      // this.logger.log(message);
      return result;
    } catch (error) {
      this.logger.error(message);
      throw error;
    }
  }

  async getPoolClient(): Promise<PoolClient> {
    const poolClient = await this.pool.connect();

    return new Proxy(poolClient, {
      get: (target: PoolClient, propertyName: keyof PoolClient) => {
        if (propertyName === 'query') {
          return (query: string, params?: unknown[]) => {
            return this.queryWithLogging(target, query, params);
          };
        }
        return target[propertyName];
      },
    });
  }
}

export default DatabaseService;
