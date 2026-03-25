-- Drop unique constraints on user_id to allow multiple restaurants per user
ALTER TABLE restaurants DROP CONSTRAINT IF EXISTS ukjh0uq0ansrvx9k5me0lvdsvmt CASCADE;
