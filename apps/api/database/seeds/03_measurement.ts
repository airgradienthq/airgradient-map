import { Knex } from 'knex';
import * as fs from 'fs';
import * as path from 'path';
const Papa = require('papaparse');

export async function seed(knex: Knex): Promise<void> {
  const csvPath = path.join(__dirname, '../data/measurement.csv');
  const csvData = fs.readFileSync(csvPath, 'utf8');
  
  const parsed = Papa.parse(csvData, {
    header: true,
    dynamicTyping: true,
    skipEmptyLines: true
  });
  
  // Clear existing data first
  await knex('measurement').del();
  
  console.log(`Processing ${parsed.data.length} measurement records...`);
  
  // Get actual UTC time (not local time) and 6 hours ago
  const nowUTC = new Date();
  nowUTC.setTime(nowUTC.getTime() + nowUTC.getTimezoneOffset() * 60 * 1000);
  const sixHoursAgoUTC = new Date(nowUTC.getTime() - 6 * 60 * 60 * 1000);
  
  // Find time range in original data
  const originalTimes = parsed.data.map(row => new Date(row.measured_at + 'Z'));
  const minTime = Math.min(...originalTimes.map(d => d.getTime()));
  const maxTime = Math.max(...originalTimes.map(d => d.getTime()));
  const originalSpan = maxTime - minTime;
  
  // Process data and map to last 6 hours
  const processedData = parsed.data.map((row: any, index: number) => {
    const originalDate = new Date(row.measured_at + 'Z');
    
    // Map original time to last 6 hours range
    const position = originalSpan === 0 ? 0 : (originalDate.getTime() - minTime) / originalSpan;
    const sixHourSpan = 6 * 60 * 60 * 1000;
    const adjustedTime = sixHoursAgoUTC.getTime() + (position * sixHourSpan);
    
    return {
      location_id: row.location_id,
      pm25: row.pm25 ?? null,
      pm10: row.pm10 ?? null,
      atmp: row.atmp ?? null,
      rhum: row.rhum ?? null,
      rco2: row.rco2 ?? null,
      o3: row.o3 ?? null,
      no2: row.no2 ?? null,
      measured_at: new Date(adjustedTime),
    };
  });
  
  // Insert in batches
  const batchSize = 1000;
  for (let i = 0; i < processedData.length; i += batchSize) {
    const batch = processedData.slice(i, i + batchSize);
    await knex.batchInsert('measurement', batch, 100);
    
    if (i % 5000 === 0 || i + batchSize >= processedData.length) {
      console.log(`Inserted ${Math.min(i + batchSize, processedData.length)} of ${processedData.length} records`);
    }
  }
  
  console.log(`Seeded ${processedData.length} measurements mapped to the last 6 hours`);
}
