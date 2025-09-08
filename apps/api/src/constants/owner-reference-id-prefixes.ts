export const OWNER_REFERENCE_ID_PREFIXES = {
  AIRGRADIENT: 'ag_',
  OPENAQ: 'oaq_',
  UNKNOWN: 'unknown_',
} as const;

export type OwnerReferenceIdPrefix = typeof OWNER_REFERENCE_ID_PREFIXES[keyof typeof OWNER_REFERENCE_ID_PREFIXES];