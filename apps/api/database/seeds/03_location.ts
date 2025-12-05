import { Knex } from 'knex';
import * as fs from 'fs';
import * as path from 'path';
const Papa = require('papaparse');

export async function seed(knex: Knex): Promise<void> {
  const csvPath = path.join(__dirname, '../data/location.csv');
  const csvData = fs.readFileSync(csvPath, 'utf8');

  const parsed = Papa.parse(csvData, {
    header: true,
    dynamicTyping: true,
    skipEmptyLines: true,
  });

  // Clear existing data first - delete measurements before locations due to FK constraint
  await knex('measurement').del();
  await knex('location').del();
  await knex.raw('ALTER SEQUENCE location_id_seq RESTART WITH 1');

  // Process and insert one by one to handle the coordinate conversion
  for (const row of parsed.data) {
    // Simple licenses parsing - just store as array of strings
    let licenses = null;
    if (row.licenses && row.licenses.trim()) {
      if (row.licenses.trim() === '{}') {
        licenses = [];
      } else {
        // Extract content between quotes and create array
        const match = row.licenses.match(/"([^"]+)"/);
        if (match) {
          licenses = [match[1]]; // e.g., ["CC BY-SA 4.0"]
        } else {
          licenses = [row.licenses]; // fallback
        }
      }
    }

    // Insert using raw SQL to handle the coordinate properly
    await knex.raw(
      `
      INSERT INTO location (
        id, owner_id, reference_id, sensor_type, location_name, 
        timezone, coordinate, deteted_at, licenses, data_source_id, provider
      ) VALUES (?, ?, ?, ?, ?, ?, ST_GeomFromText(?, 4326), ?, ?, ?, ?)
    `,
      [
        row.id,
        row.owner_id,
        row.reference_id,
        row.sensor_type,
        row.location_name || null,
        row.timezone,
        row.coordinate,
        row.deteted_at ? new Date(row.deteted_at) : null,
        licenses,
        Number(row.data_source_id) || 1,
        row.provider || null,
      ],
    );
  }

  // Update the sequence
  const result = await knex('location').max('id as max_id').first();
  const maxId = result?.max_id || 0;
  await knex.raw(`ALTER SEQUENCE location_id_seq RESTART WITH ${maxId + 1}`);

  console.log(`Seeded ${parsed.data.length} locations with PostgreSQL timestamps`);
}
