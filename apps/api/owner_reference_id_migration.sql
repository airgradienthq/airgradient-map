-- =====================================================
-- Owner Reference ID Migration
-- =====================================================
-- Fixes owner table reference_ids ONLY
-- =====================================================

-- 1. Ensure owner.reference_id is VARCHAR
ALTER TABLE owner ALTER COLUMN reference_id TYPE VARCHAR(255) USING reference_id::TEXT;

-- 2. Add prefixes to owner reference_ids
UPDATE owner o
SET reference_id = CONCAT('ag_', reference_id)
WHERE EXISTS (
    SELECT 1 FROM location l 
    WHERE l.owner_id = o.id 
    AND l.data_source = 'AirGradient'
)
AND reference_id NOT LIKE 'ag_%';

UPDATE owner o
SET reference_id = CONCAT('oaq_', reference_id)
WHERE EXISTS (
    SELECT 1 FROM location l 
    WHERE l.owner_id = o.id 
    AND l.data_source = 'OpenAQ'
)
AND NOT EXISTS (
    SELECT 1 FROM location l 
    WHERE l.owner_id = o.id 
    AND l.data_source = 'AirGradient'
)
AND reference_id NOT LIKE 'oaq_%';

UPDATE owner
SET reference_id = CONCAT('unknown_', reference_id)
WHERE NOT EXISTS (
    SELECT 1 FROM location l 
    WHERE l.owner_id = owner.id
)
AND reference_id NOT LIKE 'ag_%'
AND reference_id NOT LIKE 'oaq_%';

-- 3. Fix owner constraint
ALTER TABLE owner DROP CONSTRAINT IF EXISTS owner_reference_id_key;
ALTER TABLE owner ADD CONSTRAINT owner_reference_id_key UNIQUE (reference_id);

-- 5. Verify
SELECT 
    COUNT(*) as total_owners,
    COUNT(CASE WHEN reference_id LIKE 'ag_%' THEN 1 END) as ag_prefixed,
    COUNT(CASE WHEN reference_id LIKE 'oaq_%' THEN 1 END) as oaq_prefixed,
    COUNT(CASE WHEN reference_id LIKE 'unknown_%' THEN 1 END) as unknown_prefixed
FROM owner;