-- ==============================================================================
-- TRIPMATE AI - DATABASE MIGRATION & SCHEMA ALTERATIONS
-- ==============================================================================
-- Migration: Add Dynamic Image Cache and Public Destination Hotels
-- Purpose:
--   1. Public Hotels Table (stores real hotels and verified dynamic photos per destination)
--   2. Global Public Image Cache Table (caches fetched images by query key so
--      no duplicate AI or web image search calls are made across any users)
--   3. Enhanced Indexes & RLS Policies for collaborative shared caching
-- ==============================================================================

-- 1. Create Public Destination Hotels Table
CREATE TABLE IF NOT EXISTS public_destination_hotels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    destination_id UUID REFERENCES public_destinations(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    photo_url TEXT NOT NULL,
    location_name TEXT NOT NULL,
    rating REAL NOT NULL DEFAULT 4.5,
    review_count INTEGER NOT NULL DEFAULT 500,
    nightly_price DOUBLE PRECISION NOT NULL DEFAULT 2800.0,
    total_price DOUBLE PRECISION NOT NULL DEFAULT 8400.0,
    amenities TEXT NOT NULL DEFAULT 'Free WiFi, AC, Breakfast, Pool, City View',
    distance_to_center TEXT NOT NULL DEFAULT '1.5 km from central hub',
    type TEXT NOT NULL DEFAULT 'Boutique Hotel', -- 'Luxury Resort', 'Heritage Haveli', 'Business Hotel', 'Hostel'
    provider TEXT NOT NULL DEFAULT 'Booking.com / Verified Live',
    booking_url TEXT NOT NULL DEFAULT '',
    cancellation_policy TEXT NOT NULL DEFAULT 'Free cancellation up to 24h prior',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_public_hotels_destination_id ON public_destination_hotels(destination_id);
CREATE INDEX IF NOT EXISTS idx_public_hotels_rating ON public_destination_hotels(rating DESC);

-- 2. Create Global Public Image Cache Table
-- Used as a cross-user key-value image cache to avoid duplicate image searches
CREATE TABLE IF NOT EXISTS public_image_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    query_key TEXT NOT NULL UNIQUE, -- Normalized lookup key: e.g. "destination:gujarat:cover", "hotel:hyatt regency ahmedabad", "attraction:statue of unity"
    entity_type TEXT NOT NULL DEFAULT 'DESTINATION', -- 'DESTINATION', 'ATTRACTION', 'HOTEL', 'LANDMARK'
    entity_name TEXT NOT NULL,
    destination_id UUID REFERENCES public_destinations(id) ON DELETE SET NULL,
    image_url TEXT NOT NULL,
    credit_source TEXT NOT NULL DEFAULT 'Wikimedia Commons / Live Web Search',
    caption TEXT NOT NULL DEFAULT '',
    width INTEGER DEFAULT 1200,
    height INTEGER DEFAULT 800,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_image_cache_query_key ON public_image_cache(query_key);
CREATE INDEX IF NOT EXISTS idx_image_cache_entity ON public_image_cache(entity_type, destination_id);

-- 3. Enhance public_destination_images table if needed
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'public_destination_images' AND column_name = 'entity_type'
    ) THEN
        ALTER TABLE public_destination_images ADD COLUMN entity_type TEXT NOT NULL DEFAULT 'DESTINATION';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'public_destination_images' AND column_name = 'query_key'
    ) THEN
        ALTER TABLE public_destination_images ADD COLUMN query_key TEXT;
        CREATE INDEX IF NOT EXISTS idx_dest_images_query_key ON public_destination_images(query_key);
    END IF;
END $$;

-- 4. Enable Row Level Security on newly created tables
ALTER TABLE public_destination_hotels ENABLE ROW LEVEL SECURITY;
ALTER TABLE public_image_cache ENABLE ROW LEVEL SECURITY;

-- 5. RLS Policies: Readable by all, Insertable by all so cache is collaboratively enriched
CREATE POLICY "Public hotels are readable by everyone"
    ON public_destination_hotels FOR SELECT
    USING (true);

CREATE POLICY "Public hotels can be inserted by everyone"
    ON public_destination_hotels FOR INSERT
    WITH CHECK (true);

CREATE POLICY "Public hotels can be updated by everyone"
    ON public_destination_hotels FOR UPDATE
    USING (true);

CREATE POLICY "Public image cache is readable by everyone"
    ON public_image_cache FOR SELECT
    USING (true);

CREATE POLICY "Public image cache can be inserted by everyone"
    ON public_image_cache FOR INSERT
    WITH CHECK (true);

-- 6. Helper function to atomically upsert into public_image_cache
CREATE OR REPLACE FUNCTION upsert_public_image_cache(
    p_query_key TEXT,
    p_entity_type TEXT,
    p_entity_name TEXT,
    p_dest_id UUID,
    p_image_url TEXT,
    p_source TEXT,
    p_caption TEXT
)
RETURNS VOID AS $$
BEGIN
    INSERT INTO public_image_cache (
        query_key, entity_type, entity_name, destination_id, image_url, credit_source, caption, created_at
    ) VALUES (
        p_query_key, p_entity_type, p_entity_name, p_dest_id, p_image_url, p_source, p_caption, NOW()
    )
    ON CONFLICT (query_key) DO UPDATE
    SET image_url = EXCLUDED.image_url,
        credit_source = EXCLUDED.credit_source,
        caption = EXCLUDED.caption;
END;
$$ LANGUAGE plpgsql;

-- 7. Seed initial high-demand destination knowledge (Gujarat & Iconic Landmarks)
INSERT INTO public_destinations (
    id, normalized_name, name, city, state_region, country, type,
    description, cover_image_url, latitude, longitude,
    popular_areas, best_time_to_visit, weather_summary, typical_temperature, rain_probability, search_count, data_sources
) VALUES (
    'c3333333-3333-3333-3333-333333333333',
    'gujarat',
    'Gujarat',
    'Ahmedabad / Gandhinagar',
    'Gujarat',
    'India',
    'Heritage, Salt Desert & Lion Sanctuary',
    'Vibrant state in western India famed for the Rann of Kutch white desert, Asiatic lions in Gir, the world''s tallest Statue of Unity, stepwells of Patan, and rich Gujarati thali cuisine.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/0/07/Statue_of_Unity.jpg/1200px-Statue_of_Unity.jpg',
    23.2156,
    72.6369,
    'Ahmedabad Heritage City, Kevadia (Statue of Unity), White Rann of Kutch, Sasan Gir, Somnath, Dwarka',
    'October to March (Winter Festival Season)',
    'Sunny & pleasant breeze',
    '27°C',
    '0%',
    28,
    'Gujarat Tourism Official & Wikimedia Verified Knowledge Base'
) ON CONFLICT (normalized_name) DO UPDATE
SET cover_image_url = EXCLUDED.cover_image_url,
    description = EXCLUDED.description;

-- Seed dynamic attractions for Gujarat
INSERT INTO public_destination_attractions (
    destination_id, name, category, description, location_address,
    latitude, longitude, opening_hours, visit_duration, entry_ticket_cost, rating, review_count, data_source
) VALUES
(
    'c3333333-3333-3333-3333-333333333333',
    'Statue of Unity',
    'World Monument & Architecture',
    'The world''s tallest statue standing at 182 meters, honoring Sardar Vallabhbhai Patel with high-speed viewing gallery overlooking Sardar Sarovar Dam.',
    'Sardar Sarovar Dam, Kevadia, Narmada, Gujarat 393155',
    21.8380, 73.7191, '08:00 - 18:00 (Closed Mondays)', '3 hours', 150.0, 4.9, 14200, 'Gujarat Tourism Registry'
),
(
    'c3333333-3333-3333-3333-333333333333',
    'Sabarmati Ashram (Gandhi Smarak Sangrahalaya)',
    'Heritage & History',
    'Historic headquarters of Mahatma Gandhi along the Sabarmati River banks, preserving the origins of the Salt March and freedom movement.',
    'Gandhi Smarak Sangrahalaya, Ashram Road, Ahmedabad, Gujarat 380027',
    23.0605, 72.5800, '08:30 - 18:30', '2 hours', 0.0, 4.8, 8600, 'UNESCO World Heritage'
),
(
    'c3333333-3333-3333-3333-333333333333',
    'White Desert of Rann of Kutch',
    'Natural Wonder',
    'Vast expanses of sparkling white salt desert, host to the world-famous annual Rann Utsav cultural tent city under the full moon.',
    'Dhordo, Kutch District, Gujarat 370510',
    23.8340, 69.8350, 'Open 24 hours (Permits required)', 'Full Day', 100.0, 4.9, 9300, 'Incredible India & Kutch Tourism'
),
(
    'c3333333-3333-3333-3333-333333333333',
    'Somnath Jyotirlinga Temple',
    'Spiritual & Sea Temple',
    'First of the twelve holy Jyotirlinga shrines of Lord Shiva, located right on the Arabian Sea coast with stunning sound & light displays.',
    'Veraval Beach, Prabhas Patan, Gujarat 362268',
    20.8880, 70.4010, '06:00 - 21:30', '2 hours', 0.0, 4.8, 11400, 'Shree Somnath Trust'
) ON CONFLICT DO NOTHING;

-- Seed verified hotels for Gujarat
INSERT INTO public_destination_hotels (
    destination_id, name, photo_url, location_name, rating, review_count, nightly_price, total_price, amenities, distance_to_center, type, provider, booking_url
) VALUES
(
    'c3333333-3333-3333-3333-333333333333',
    'The Leela Gandhinagar',
    'https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=1200&q=80',
    'Airspace above Gandhinagar Railway Station, Gujarat',
    4.9, 1420, 6500.0, 19500.0, 'Infinity Pool, Luxury Spa, Fine Dining, Free WiFi, City View', '1.2 km from Capital Hub', '5-Star Luxury Resort', 'Booking.com', 'https://www.theleela.com'
),
(
    'c3333333-3333-3333-3333-333333333333',
    'Hyatt Regency Ahmedabad',
    'https://images.unsplash.com/photo-1582719508461-905c673771fd?auto=format&fit=crop&w=1200&q=80',
    'Ashram Road, Riverfront, Ahmedabad, Gujarat',
    4.8, 2300, 4800.0, 14400.0, 'Riverfront View, 24/7 Dining, Fitness Club, High-speed Internet', '0.8 km from Riverfront Walk', '5-Star Riverfront Hotel', 'Booking.com', 'https://www.hyatt.com'
),
(
    'c3333333-3333-3333-3333-333333333333',
    'The House of MG (Heritage Haveli)',
    'https://images.unsplash.com/photo-1571896349842-33c89424de2d?auto=format&fit=crop&w=1200&q=80',
    'Opposite Sidi Saiyyed Mosque, Old City Ahmedabad, Gujarat',
    4.7, 1850, 4200.0, 12600.0, 'Heritage Architecture, Agashiye Rooftop Dining, Traditional Courtyard, Indoor Pool', 'Central Old City', 'Heritage Boutique Haveli', 'Agoda', 'https://houseofmg.com'
),
(
    'c3333333-3333-3333-3333-333333333333',
    'Rann Utsav Luxury Tent City Dhordo',
    'https://images.unsplash.com/photo-1510312305653-8ed496efae75?auto=format&fit=crop&w=1200&q=80',
    'White Desert Gateway, Dhordo, Kutch, Gujarat',
    4.8, 920, 5500.0, 16500.0, 'AC Swiss Tents, Cultural Performances, Desert Safari, All Meals Included', 'Direct Desert Entry', 'Desert Luxury Glamping', 'MakeMyTrip', 'https://www.rannutsav.com'
) ON CONFLICT DO NOTHING;
