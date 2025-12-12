import { Knex } from 'knex';
import * as fs from 'fs';
import * as path from 'path';
const Papa = require('papaparse');

export async function seed(knex: Knex): Promise<void> {
  const csvPath = path.join(__dirname, '../data/data_source.csv');
  const csvData = fs.readFileSync(csvPath, 'utf8');

  const parsed = Papa.parse(csvData, {
    header: true,
    dynamicTyping: true,
    skipEmptyLines: true,
  });

  // Process the data - now INCLUDING the id
  const processedData = parsed.data.map((row: any) => ({
    id: row.id,
    name: row.name,
    allow_api_access: row.allow_api_access,
    url: row.url
  }));

  // Clear existing data in order to prevent foreign key constrain
  await knex('data_source').del();

  // Reset the sequence to avoid conflicts
  await knex.raw('ALTER SEQUENCE data_source_id_seq RESTART WITH 1');

  // Insert with explicit IDs
  await knex('data_source').insert(processedData);

  // Update the sequence to continue from the max ID
  const maxId = Math.max(...processedData.map(row => row.id));
  await knex.raw(`ALTER SEQUENCE data_source_id_seq RESTART WITH ${maxId + 1}`);

  console.log(`Seeded ${processedData.length} data_source`);
}
