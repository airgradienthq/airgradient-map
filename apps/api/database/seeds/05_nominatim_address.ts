import { Knex } from 'knex';
import * as fs from 'fs';
import * as path from 'path';
const Papa = require('papaparse');

type NominatimAddressRow = {
  latitude: number | null;
  longitude: number | null;
  street: string | null;
  village: string | null;
  town: string | null;
  city: string | null;
  district: string | null;
  county: string | null;
  state: string | null;
  country: string | null;
  country_code: string | null;
  label: string | null;
};

const sanitizeString = (value: any): string | null => {
  if (value === undefined || value === null) return null;
  const trimmed = String(value).trim();
  return trimmed === '' ? null : trimmed;
};

const toNumber = (value: any): number | null => {
  if (value === undefined || value === null) return null;
  const trimmed = String(value).trim();
  if (trimmed === '') return null;
  const num = typeof value === 'number' ? value : Number(trimmed);
  return Number.isFinite(num) ? num : null;
};

export async function seed(knex: Knex): Promise<void> {
  const csvPath = path.join(__dirname, '../data/nominatim_address.csv');
  const csvData = fs.readFileSync(csvPath, 'utf8');

  const parsed = Papa.parse(csvData, {
    header: true,
    dynamicTyping: true,
    skipEmptyLines: true,
  });

  const addresses: NominatimAddressRow[] = parsed.data
    .map((row: any) => ({
      latitude: toNumber(row.latitude),
      longitude: toNumber(row.longitude),
      street: sanitizeString(row.street),
      village: sanitizeString(row.village),
      town: sanitizeString(row.town),
      city: sanitizeString(row.city),
      district: sanitizeString(row.district),
      county: sanitizeString(row.county),
      state: sanitizeString(row.state),
      country: sanitizeString(row.country),
      country_code: sanitizeString(row.country_code),
      label: sanitizeString(row.label),
    }))
    .filter((row: NominatimAddressRow) => row.latitude !== null && row.longitude !== null);

  await knex('nominatim_address').del();
  await knex.raw('ALTER SEQUENCE nominatim_address_id_seq RESTART WITH 1');

  if (addresses.length > 0) {
    const latitudes = addresses.map(row => row.latitude as number);
    const longitudes = addresses.map(row => row.longitude as number);
    const streets = addresses.map(row => row.street);
    const villages = addresses.map(row => row.village);
    const towns = addresses.map(row => row.town);
    const cities = addresses.map(row => row.city);
    const districts = addresses.map(row => row.district);
    const counties = addresses.map(row => row.county);
    const states = addresses.map(row => row.state);
    const countries = addresses.map(row => row.country);
    const countryCodes = addresses.map(row => row.country_code);
    const labels = addresses.map(row => row.label);

    await knex.raw(
      `
      INSERT INTO public.nominatim_address (
        coordinate, street, village, town, city, district, county, state, country, country_code, label
      )
      SELECT
        ST_SetSRID(ST_MakePoint(lon, lat), 4326) AS coordinate,
        street, village, town, city, district, county, state, country, country_code, label
      FROM unnest(
        ?::double precision[],
        ?::double precision[],
        ?::text[],
        ?::text[],
        ?::text[],
        ?::text[],
        ?::text[],
        ?::text[],
        ?::text[],
        ?::text[],
        ?::text[],
        ?::text[]
      ) AS t(
        lat, lon, street, village, town, city, district, county, state, country, country_code, label
      );
    `,
      [
        latitudes,
        longitudes,
        streets,
        villages,
        towns,
        cities,
        districts,
        counties,
        states,
        countries,
        countryCodes,
        labels,
      ],
    );
  }

  const maxIdResult = await knex('nominatim_address').max('id as max_id').first();
  const nextSequence = (maxIdResult?.max_id ?? 0) + 1;
  await knex.raw(`ALTER SEQUENCE nominatim_address_id_seq RESTART WITH ${nextSequence}`);

  console.log(`Seeded ${addresses.length} nominatim addresses`);
}
