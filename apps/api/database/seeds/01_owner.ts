import { Knex } from 'knex';
import * as fs from 'fs';
import * as path from 'path';
const Papa = require('papaparse');

export async function seed(knex: Knex): Promise<void> {
  const csvPath = path.join(__dirname, '../data/owner.csv');
  const csvData = fs.readFileSync(csvPath, 'utf8');

  const parsed = Papa.parse(csvData, {
    header: true,
    dynamicTyping: true,
    skipEmptyLines: true,
  });

  // Process the data - now INCLUDING the id
  const processedData = parsed.data.map((row: any) => ({
    id: row.id,
    owner_name: row.owner_name || null,
    url: row.url || null,
    reference_id: row.reference_id || null,
  }));

  // Clear existing data in orderto prevent foreign key constrain
  await knex('measurement').del();
  await knex('location').del();
  await knex('owner').del();

  // Reset the sequence to avoid conflicts
  await knex.raw('ALTER SEQUENCE owner_id_seq RESTART WITH 1');

  // Insert with explicit IDs
  await knex('owner').insert(processedData);

  // Update the sequence to continue from the max ID
  const maxId = Math.max(...processedData.map(row => row.id));
  await knex.raw(`ALTER SEQUENCE owner_id_seq RESTART WITH ${maxId + 1}`);

  console.log(`Seeded ${processedData.length} owner`);
}
