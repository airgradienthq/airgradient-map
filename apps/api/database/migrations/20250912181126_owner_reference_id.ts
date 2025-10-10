import { Knex } from 'knex';

export async function up(knex: Knex): Promise<void> {
  // 1. Ensure owner.reference_id is VARCHAR
  await knex.raw(`
    ALTER TABLE owner ALTER COLUMN reference_id TYPE VARCHAR(255) USING reference_id::TEXT
  `);

  // 2. Add prefixes to owner reference_ids
  
  // AirGradient prefix
  await knex.raw(`
    UPDATE owner 
    SET reference_id = CONCAT('ag_', reference_id)
    WHERE EXISTS (
        SELECT 1 FROM location l 
        WHERE l.owner_id = owner.id 
        AND l.data_source = 'AirGradient'
    )
    AND reference_id NOT LIKE 'ag_%'
  `);

  // OpenAQ prefix
  await knex.raw(`
    UPDATE owner 
    SET reference_id = CONCAT('oaq_', reference_id)
    WHERE EXISTS (
        SELECT 1 FROM location l 
        WHERE l.owner_id = owner.id 
        AND l.data_source = 'OpenAQ'
    )
    AND NOT EXISTS (
        SELECT 1 FROM location l 
        WHERE l.owner_id = owner.id 
        AND l.data_source = 'AirGradient'
    )
    AND reference_id NOT LIKE 'oaq_%'
  `);

  // Unknown prefix
  await knex.raw(`
    UPDATE owner
    SET reference_id = CONCAT('unknown_', reference_id)
    WHERE NOT EXISTS (
        SELECT 1 FROM location l 
        WHERE l.owner_id = owner.id
    )
    AND reference_id NOT LIKE 'ag_%'
    AND reference_id NOT LIKE 'oaq_%'
  `);

  // 3. Fix owner constraint
  await knex.raw('ALTER TABLE owner DROP CONSTRAINT IF EXISTS owner_reference_id_key');
  await knex.raw('ALTER TABLE owner ADD CONSTRAINT owner_reference_id_key UNIQUE (reference_id)');

  // 4. Verify (optional - shows results in migration log)
  const result = await knex.raw(`
    SELECT 
        COUNT(*) as total_owners,
        COUNT(CASE WHEN reference_id LIKE 'ag_%' THEN 1 END) as ag_prefixed,
        COUNT(CASE WHEN reference_id LIKE 'oaq_%' THEN 1 END) as oaq_prefixed,
        COUNT(CASE WHEN reference_id LIKE 'unknown_%' THEN 1 END) as unknown_prefixed
    FROM owner
  `);
  
  console.log('Migration completed. Owner prefixes:', result.rows[0]);
}

export async function down(knex: Knex): Promise<void> {
  // Remove prefixes
  await knex.raw(`UPDATE owner SET reference_id = REPLACE(reference_id, 'ag_', '') WHERE reference_id LIKE 'ag_%'`);
  await knex.raw(`UPDATE owner SET reference_id = REPLACE(reference_id, 'oaq_', '') WHERE reference_id LIKE 'oaq_%'`);
  await knex.raw(`UPDATE owner SET reference_id = REPLACE(reference_id, 'unknown_', '') WHERE reference_id LIKE 'unknown_%'`);
  
  // Change back to integer (if possible)
  await knex.raw(`ALTER TABLE owner ALTER COLUMN reference_id TYPE INTEGER USING reference_id::INTEGER`);
}
