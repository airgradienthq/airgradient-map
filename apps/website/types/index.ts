/**
 * Centralized module for exporting TypeScript types.
 *
 * This file serves as an index for all TypeScript types used throughout the application.
 *
 * - Place all application types in this directory for better organization.
 * - Use this index file to import types from other files in the types directory.
 *
 * Example usage:
 *
 * import { SomeType } from '~/types';
 *
 * This approach allows for cleaner imports and better maintainability of type definitions.
 *
 * Ensure that any new types are added to their respective files and re-exported here
 * for consistent access across the application.
 */

export * from './map';
export * from './shared';
export * from './deps';
export * from './plugins';
export * from './store';
