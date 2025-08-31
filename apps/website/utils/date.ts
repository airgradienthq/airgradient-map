import { DateTime } from 'luxon';
import { HistoryPeriodUnit } from '~/types';

/**
 * Returns a date range from today to a given number of months ago.
 * @param unit - The unit of time to use for the range.
 * @param agoCount - The number of units to go back.
 * @returns { start: string, end: string } - ISO strings for start and end.
 */
export function getDateRangeFromToday(
  unit: HistoryPeriodUnit,
  agoCount: number
): { start: string; end: string } {
  const end = DateTime.now();
  const start = end.minus({ [unit]: agoCount });
  return {
    start: start.toISO({ suppressMilliseconds: true }),
    end: end.toISO({ suppressMilliseconds: true })
  };
}
