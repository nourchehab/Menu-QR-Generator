-- Migration: Increase ai_reasoning column size from 500 to 1000 characters
-- This is needed because Gemini's explanations often exceed 500 characters

ALTER TABLE menu_items ALTER COLUMN ai_reasoning TYPE VARCHAR(1000);
