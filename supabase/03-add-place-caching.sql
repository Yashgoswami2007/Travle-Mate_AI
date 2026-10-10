-- Migration: Add Place Caching Support
-- Adds image_url, normalized_name, and unique constraints to prevent duplicate places

ALTER TABLE public_destination_attractions
ADD COLUMN IF NOT EXISTS image_url TEXT NOT NULL DEFAULT '';

ALTER TABLE public_destination_attractions
ADD COLUMN IF NOT EXISTS normalized_name TEXT;

ALTER TABLE public_destination_attractions
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Create unique constraint to prevent duplicate places under the same destination using destination_id and normalized_name
CREATE UNIQUE INDEX IF NOT EXISTS idx_attractions_dest_name ON public_destination_attractions (
    destination_id,
    normalized_name
);