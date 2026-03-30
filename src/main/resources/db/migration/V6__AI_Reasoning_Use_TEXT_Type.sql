-- Migration: Change ai_reasoning column to TEXT for unlimited length
-- Gemini's explanations can be very long and exceed 1000 characters

ALTER TABLE menu_items ALTER COLUMN ai_reasoning TYPE TEXT;
