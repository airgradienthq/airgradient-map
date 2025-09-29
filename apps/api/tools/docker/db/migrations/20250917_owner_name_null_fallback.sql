  -- =====================================================
-- Owner Name Null Fallback Migration
-- =====================================================
-- Normalize owner_name column to NULL for empty or unknown values
-- =====================================================
  ALTER TABLE owner 
  ALTER COLUMN owner_name DROP NOT NULL;

  UPDATE owner
  SET owner_name = NULL
  WHERE owner_name = '' OR owner_name = 'unknown';