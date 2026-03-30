-- Add AI categorization fields to menu_items table
-- Migration for Spring Boot AI integration

ALTER TABLE menu_items 
ADD COLUMN IF NOT EXISTS suggested_category VARCHAR(100);

ALTER TABLE menu_items 
ADD COLUMN IF NOT EXISTS ai_confidence DOUBLE PRECISION;

ALTER TABLE menu_items 
ADD COLUMN IF NOT EXISTS ai_reasoning VARCHAR(500);

ALTER TABLE menu_items 
ADD COLUMN IF NOT EXISTS ai_analyzed_at TIMESTAMP;

-- Index on suggested_category for quick lookups
CREATE INDEX IF NOT EXISTS idx_suggested_category ON menu_items(suggested_category);

-- Index on ai_analyzed_at to find recently analyzed items
CREATE INDEX IF NOT EXISTS idx_ai_analyzed_at ON menu_items(ai_analyzed_at DESC);
