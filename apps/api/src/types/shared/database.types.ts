/**
 * Database query result types
 */

import { QueryResult as PgQueryResult } from 'pg';

/**
 * Generic database query result wrapper
 */
export interface QueryResult<T> extends PgQueryResult<T> {
  rows: T[];
  rowCount: number;
  command: string;
}

/**
 * Paginated result for API responses
 */
export interface PaginatedResult<T> {
  data: T[];
  total: number;
  page: number;
  pageSize: number;
  totalPages?: number;
}

/**
 * Database connection result
 */
export interface DatabaseConnectionResult {
  success: boolean;
  error?: string;
}

/**
 * Type for database query parameters
 * Using unknown[] is safer than any[] for query parameters
 */
export type QueryParams = unknown[];
