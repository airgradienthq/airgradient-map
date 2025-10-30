import { DateTime } from 'luxon';

export enum BucketSize {
  FifteenMinutes = '15m',
  OneHour = '1h',
  EightHours = '8h',
  OneDay = '1d',
  OneWeek = '1w',
  OneMonth = '1M',
  OneYear = '1y',
}

export function roundToBucket(isoString: string, bucketSize: BucketSize): DateTime {
  // Convert the ISO string into a Luxon DateTime object.
  // Use { setZone: true } to ensure the timezone from the string is respected.
  const dt = DateTime.fromISO(isoString, { setZone: true });

  // Ensure the conversion was successful before proceeding.
  if (!dt.isValid) {
    throw new Error('Invalid ISO date string provided.');
  }

  switch (bucketSize) {
    case BucketSize.FifteenMinutes: {
      const totalMinutesForFifteen = dt.hour * 60 + dt.minute;
      const roundedMinutesForFifteen = Math.floor(totalMinutesForFifteen / 15) * 15;
      return dt.set({
        hour: Math.floor(roundedMinutesForFifteen / 60),
        minute: roundedMinutesForFifteen % 60,
        second: 0,
        millisecond: 0,
      });
    }

    case BucketSize.OneHour:
      return dt.startOf('hour');

    case BucketSize.EightHours: {
      const roundedHoursForEight = Math.floor(dt.hour / 8) * 8;
      return dt.set({
        hour: roundedHoursForEight,
        minute: 0,
        second: 0,
        millisecond: 0,
      });
    }

    case BucketSize.OneDay:
      return dt.startOf('day');

    case BucketSize.OneWeek:
      return dt.startOf('week'); // weeks always start on Mondays

    case BucketSize.OneMonth:
      return dt.startOf('month');

    case BucketSize.OneYear:
      return dt.startOf('year');

    default:
      return dt;
  }
}
