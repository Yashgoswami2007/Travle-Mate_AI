-- ==============================================================================
-- TRIPMATE AI - SUPABASE TWO-LAYER DATABASE ARCHITECTURE
-- ==============================================================================
-- Layer 1: User Database (Private user data, secured with Row Level Security)
-- Layer 2: Public Destination Database (Shared cache & reusable knowledge base)
-- Concurrency: Atomic generation locks to prevent duplicate AI research calls
-- ==============================================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ==============================================================================
-- LAYER 2: PUBLIC DESTINATION DATABASE (SHARED CACHE & KNOWLEDGE BASE)
-- ==============================================================================

-- 1. Public Destinations Table
-- Stores canonical, reusable destination knowledge
CREATE TABLE IF NOT EXISTS public_destinations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    normalized_name TEXT NOT NULL UNIQUE, -- e.g., 'moradabad', 'jaipur', 'paris'
    name TEXT NOT NULL,                  -- Display name, e.g., 'Moradabad', 'Jaipur'
    city TEXT NOT NULL,
    state_region TEXT NOT NULL,
    country TEXT NOT NULL DEFAULT 'India',
    type TEXT NOT NULL DEFAULT 'City Destination', -- 'Heritage City', 'Brass City', 'Hill Station', etc.
    description TEXT NOT NULL,
    cover_image_url TEXT NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    popular_areas TEXT NOT NULL DEFAULT '',
    best_time_to_visit TEXT NOT NULL DEFAULT 'October to March',
    weather_summary TEXT NOT NULL DEFAULT 'Pleasant',
    typical_temperature TEXT NOT NULL DEFAULT '25°C',
    rain_probability TEXT NOT NULL DEFAULT '10%',
    search_count INTEGER NOT NULL DEFAULT 1,
    data_sources TEXT NOT NULL DEFAULT 'Gemini AI & Public Knowledge Base',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_verified_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for instant normalized name cache lookups
CREATE INDEX IF NOT EXISTS idx_public_destinations_normalized_name ON public_destinations(normalized_name);
CREATE INDEX IF NOT EXISTS idx_public_destinations_search_count ON public_destinations(search_count DESC);

-- 2. Public Destination Attractions Table
-- Separate table for key attractions and landmarks per destination
CREATE TABLE IF NOT EXISTS public_destination_attractions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    destination_id UUID NOT NULL REFERENCES public_destinations(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    category TEXT NOT NULL, -- 'Historical', 'Architecture', 'Market', 'Religious', 'Nature', etc.
    description TEXT NOT NULL,
    location_address TEXT NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    opening_hours TEXT NOT NULL DEFAULT '09:00 - 18:00',
    visit_duration TEXT NOT NULL DEFAULT '2 hours',
    entry_ticket_cost DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    best_time TEXT NOT NULL DEFAULT 'Morning',
    crowd_level TEXT NOT NULL DEFAULT 'Moderate',
    rating REAL NOT NULL DEFAULT 4.5,
    review_count INTEGER NOT NULL DEFAULT 100,
    data_source TEXT NOT NULL DEFAULT 'Gemini AI & Tourism Registry',
    order_index INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_attractions_destination_id ON public_destination_attractions(destination_id);

-- 3. Public Destination Images Table
-- Separate table storing cover and gallery images with metadata & credits
CREATE TABLE IF NOT EXISTS public_destination_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    destination_id UUID NOT NULL REFERENCES public_destinations(id) ON DELETE CASCADE,
    attraction_id UUID REFERENCES public_destination_attractions(id) ON DELETE SET NULL,
    image_url TEXT NOT NULL,
    caption TEXT NOT NULL DEFAULT '',
    credit_source TEXT NOT NULL DEFAULT 'Wikimedia Commons / Unsplash Verified',
    is_cover BOOLEAN NOT NULL DEFAULT FALSE,
    width INTEGER,
    height INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_images_destination_id ON public_destination_images(destination_id);

-- 4. Public Destination Dining Table
-- Famous food, iconic eateries, and heritage restaurants
CREATE TABLE IF NOT EXISTS public_destination_restaurants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    destination_id UUID NOT NULL REFERENCES public_destinations(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    cuisine TEXT NOT NULL,
    dietary_types TEXT NOT NULL DEFAULT 'Vegetarian, Multi-cuisine',
    price_range TEXT NOT NULL DEFAULT '₹₹',
    average_cost_for_two DOUBLE PRECISION NOT NULL DEFAULT 600.0,
    rating REAL NOT NULL DEFAULT 4.5,
    location_address TEXT NOT NULL,
    opening_hours TEXT NOT NULL DEFAULT '10:00 - 22:30',
    image_url TEXT NOT NULL,
    external_link TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_restaurants_destination_id ON public_destination_restaurants(destination_id);

-- 5. Destination Generation Lock & Job System
-- Concurrency control: Prevents multiple concurrent users/requests from making duplicate AI calls
CREATE TABLE IF NOT EXISTS public_destination_generation_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    normalized_name TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL DEFAULT 'IN_PROGRESS' CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED')),
    locked_by TEXT NOT NULL DEFAULT 'worker_app',
    error_message TEXT,
    destination_id UUID REFERENCES public_destinations(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    locked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_generation_jobs_normalized ON public_destination_generation_jobs(normalized_name);

-- ==============================================================================
-- LAYER 1: USER DATABASE (PRIVATE USER DATA, STRICTLY SECURED)
-- ==============================================================================

-- 1. User Profiles
CREATE TABLE IF NOT EXISTS user_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL UNIQUE, -- Linked to Auth UID or App User ID
    name TEXT NOT NULL,
    email TEXT NOT NULL DEFAULT '',
    home_location TEXT NOT NULL DEFAULT 'Delhi, India',
    currency TEXT NOT NULL DEFAULT '₹',
    diet_preference TEXT NOT NULL DEFAULT 'Vegetarian',
    travel_style TEXT NOT NULL DEFAULT 'Relaxed',
    budget_tier TEXT NOT NULL DEFAULT 'Medium',
    preferred_accommodation TEXT NOT NULL DEFAULT 'Boutique Hotel',
    preferred_transport TEXT NOT NULL DEFAULT 'Train',
    avatar_initials TEXT NOT NULL DEFAULT 'TM',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_profiles_user_id ON user_profiles(user_id);

-- 2. User Trips
CREATE TABLE IF NOT EXISTS user_trips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    destination_id UUID REFERENCES public_destinations(id) ON DELETE SET NULL,
    destination_name TEXT NOT NULL,
    destination_region TEXT NOT NULL,
    destination_country TEXT NOT NULL,
    cover_image_url TEXT NOT NULL,
    start_date TEXT NOT NULL,
    end_date TEXT NOT NULL,
    number_of_days INTEGER NOT NULL DEFAULT 4,
    travellers_count INTEGER NOT NULL DEFAULT 2,
    travellers_description TEXT NOT NULL DEFAULT 'Couple',
    starting_location TEXT NOT NULL DEFAULT 'Delhi',
    total_budget DOUBLE PRECISION NOT NULL DEFAULT 25000.0,
    spent_budget DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    pace TEXT NOT NULL DEFAULT 'Relaxed',
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_trips_user_id ON user_trips(user_id);

-- 3. User Itinerary Items
CREATE TABLE IF NOT EXISTS user_itinerary_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    trip_id UUID NOT NULL REFERENCES user_trips(id) ON DELETE CASCADE,
    day_number INTEGER NOT NULL,
    title TEXT NOT NULL,
    location_name TEXT NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    start_time TEXT NOT NULL DEFAULT '09:30',
    end_time TEXT NOT NULL DEFAULT '11:00',
    duration_minutes INTEGER NOT NULL DEFAULT 90,
    category TEXT NOT NULL DEFAULT 'Attraction',
    estimated_cost DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    travel_time_from_previous TEXT NOT NULL DEFAULT '15 mins',
    notes TEXT NOT NULL DEFAULT '',
    source TEXT NOT NULL DEFAULT 'TripMate AI',
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    order_index INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_itinerary_user_trip ON user_itinerary_items(user_id, trip_id);

-- 4. User Expenses & Budgets
CREATE TABLE IF NOT EXISTS user_expenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    trip_id UUID NOT NULL REFERENCES user_trips(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT 'Food',
    amount DOUBLE PRECISION NOT NULL,
    paid_by_member_id TEXT NOT NULL,
    paid_by_name TEXT NOT NULL,
    split_type TEXT NOT NULL DEFAULT 'EQUAL',
    date TEXT NOT NULL DEFAULT 'Today',
    notes TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_expenses_trip_id ON user_expenses(trip_id);

-- 5. User Expense Splits
CREATE TABLE IF NOT EXISTS user_expense_splits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    expense_id UUID NOT NULL REFERENCES user_expenses(id) ON DELETE CASCADE,
    trip_id UUID NOT NULL REFERENCES user_trips(id) ON DELETE CASCADE,
    member_id TEXT NOT NULL,
    member_name TEXT NOT NULL,
    share_amount DOUBLE PRECISION NOT NULL,
    is_settled BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_user_splits_expense ON user_expense_splits(expense_id);

-- 6. User Group Members
CREATE TABLE IF NOT EXISTS user_group_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    trip_id UUID NOT NULL REFERENCES user_trips(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    email TEXT NOT NULL DEFAULT '',
    avatar_color_hex TEXT NOT NULL DEFAULT '#1E3A5F',
    is_current_user BOOLEAN NOT NULL DEFAULT FALSE
);

-- 7. User Saved Destinations
CREATE TABLE IF NOT EXISTS user_saved_destinations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    destination_id UUID NOT NULL REFERENCES public_destinations(id) ON DELETE CASCADE,
    saved_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, destination_id)
);

CREATE INDEX IF NOT EXISTS idx_user_saved_destinations ON user_saved_destinations(user_id);

-- 8. User Search History
CREATE TABLE IF NOT EXISTS user_search_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    query TEXT NOT NULL,
    destination_id UUID REFERENCES public_destinations(id) ON DELETE SET NULL,
    destination_name TEXT NOT NULL,
    searched_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_search_history ON user_search_history(user_id, searched_at DESC);

-- 9. User Bookings
CREATE TABLE IF NOT EXISTS user_bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    trip_id UUID NOT NULL REFERENCES user_trips(id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    title TEXT NOT NULL,
    provider TEXT NOT NULL,
    confirmation_number TEXT NOT NULL,
    date TEXT NOT NULL,
    cost DOUBLE PRECISION NOT NULL,
    notes TEXT NOT NULL DEFAULT '',
    is_confirmed BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ==============================================================================
-- STORED PROCEDURES & HELPER FUNCTIONS
-- ==============================================================================

-- Normalizes any search query text for exact and fuzzy caching
CREATE OR REPLACE FUNCTION normalize_destination_name(p_input TEXT)
RETURNS TEXT AS $$
BEGIN
    RETURN LOWER(TRIM(REGEXP_REPLACE(p_input, '[^a-zA-Z0-9\s]', '', 'g')));
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Increments the search popularity count and updates timestamp
CREATE OR REPLACE FUNCTION increment_destination_search(p_dest_id UUID)
RETURNS VOID AS $$
BEGIN
    UPDATE public_destinations
    SET search_count = search_count + 1,
        updated_at = NOW()
    WHERE id = p_dest_id;
END;
$$ LANGUAGE plpgsql;

-- Concurrency Generation Lock: Attempt to acquire lock for a new destination
CREATE OR REPLACE FUNCTION acquire_destination_generation_lock(
    p_normalized_name TEXT,
    p_worker_id TEXT DEFAULT 'app_worker',
    p_timeout_seconds INTEGER DEFAULT 60
)
RETURNS TABLE (
    acquired BOOLEAN,
    existing_dest_id UUID,
    job_id UUID,
    job_status TEXT
) AS $$
DECLARE
    v_dest_id UUID;
    v_job_id UUID;
    v_status TEXT;
    v_locked_at TIMESTAMPTZ;
BEGIN
    -- 1. Check if destination already exists in Public DB
    SELECT id INTO v_dest_id FROM public_destinations WHERE normalized_name = p_normalized_name LIMIT 1;
    IF v_dest_id IS NOT NULL THEN
        -- Destination exists: No AI research required!
        PERFORM increment_destination_search(v_dest_id);
        RETURN QUERY SELECT FALSE, v_dest_id, NULL::UUID, 'CACHED'::TEXT;
        RETURN;
    END IF;

    -- 2. Check if a generation job already exists
    SELECT id, status, locked_at INTO v_job_id, v_status, v_locked_at
    FROM public_destination_generation_jobs
    WHERE normalized_name = p_normalized_name;

    IF v_job_id IS NOT NULL THEN
        -- If already in progress and not timed out, tell caller to wait
        IF v_status = 'IN_PROGRESS' AND (NOW() - v_locked_at) < (p_timeout_seconds || ' seconds')::INTERVAL THEN
            RETURN QUERY SELECT FALSE, NULL::UUID, v_job_id, 'IN_PROGRESS'::TEXT;
            RETURN;
        END IF;

        -- If timed out or previously failed, re-acquire lock
        UPDATE public_destination_generation_jobs
        SET status = 'IN_PROGRESS',
            locked_by = p_worker_id,
            locked_at = NOW(),
            error_message = NULL
        WHERE id = v_job_id;

        RETURN QUERY SELECT TRUE, NULL::UUID, v_job_id, 'RE_ACQUIRED'::TEXT;
        RETURN;
    END IF;

    -- 3. Create fresh generation job lock
    INSERT INTO public_destination_generation_jobs (normalized_name, status, locked_by, locked_at)
    VALUES (p_normalized_name, 'IN_PROGRESS', p_worker_id, NOW())
    RETURNING id INTO v_job_id;

    RETURN QUERY SELECT TRUE, NULL::UUID, v_job_id, 'ACQUIRED'::TEXT;
END;
$$ LANGUAGE plpgsql;

-- Release Generation Lock upon successful AI research and storage
CREATE OR REPLACE FUNCTION release_destination_generation_lock(
    p_job_id UUID,
    p_destination_id UUID,
    p_success BOOLEAN DEFAULT TRUE,
    p_error TEXT DEFAULT NULL
)
RETURNS VOID AS $$
BEGIN
    IF p_success THEN
        UPDATE public_destination_generation_jobs
        SET status = 'COMPLETED',
            destination_id = p_destination_id,
            completed_at = NOW()
        WHERE id = p_job_id;
    ELSE
        UPDATE public_destination_generation_jobs
        SET status = 'FAILED',
            error_message = p_error,
            completed_at = NOW()
        WHERE id = p_job_id;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- ==============================================================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- ==============================================================================

-- Enable RLS on Public Tables
ALTER TABLE public_destinations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public_destination_attractions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public_destination_images ENABLE ROW LEVEL SECURITY;
ALTER TABLE public_destination_restaurants ENABLE ROW LEVEL SECURITY;
ALTER TABLE public_destination_generation_jobs ENABLE ROW LEVEL SECURITY;

-- Enable RLS on User Tables
ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_trips ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_itinerary_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_expenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_expense_splits ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_group_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_saved_destinations ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_search_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_bookings ENABLE ROW LEVEL SECURITY;

-- Public Data: Anyone (Anon or Authenticated) can READ public destinations
CREATE POLICY "Public destinations are readable by everyone"
    ON public_destinations FOR SELECT
    USING (true);

CREATE POLICY "Public attractions are readable by everyone"
    ON public_destination_attractions FOR SELECT
    USING (true);

CREATE POLICY "Public images are readable by everyone"
    ON public_destination_images FOR SELECT
    USING (true);

CREATE POLICY "Public restaurants are readable by everyone"
    ON public_destination_restaurants FOR SELECT
    USING (true);

CREATE POLICY "Public generation jobs can be checked by everyone"
    ON public_destination_generation_jobs FOR SELECT
    USING (true);

-- User Data: Users can only see and manipulate their OWN data
CREATE POLICY "Users can manage own profile"
    ON user_profiles FOR ALL
    USING (user_id = auth.uid()::text OR user_id = CURRENT_USER);

CREATE POLICY "Users can manage own trips"
    ON user_trips FOR ALL
    USING (user_id = auth.uid()::text OR user_id = CURRENT_USER);

CREATE POLICY "Users can manage own itinerary items"
    ON user_itinerary_items FOR ALL
    USING (user_id = auth.uid()::text OR user_id = CURRENT_USER);

CREATE POLICY "Users can manage own expenses"
    ON user_expenses FOR ALL
    USING (user_id = auth.uid()::text OR user_id = CURRENT_USER);

CREATE POLICY "Users can manage own splits"
    ON user_expense_splits FOR ALL
    USING (user_id = auth.uid()::text OR user_id = CURRENT_USER);

CREATE POLICY "Users can manage own group members"
    ON user_group_members FOR ALL
    USING (user_id = auth.uid()::text OR user_id = CURRENT_USER);

CREATE POLICY "Users can manage saved destinations"
    ON user_saved_destinations FOR ALL
    USING (user_id = auth.uid()::text OR user_id = CURRENT_USER);

CREATE POLICY "Users can manage search history"
    ON user_search_history FOR ALL
    USING (user_id = auth.uid()::text OR user_id = CURRENT_USER);

CREATE POLICY "Users can manage bookings"
    ON user_bookings FOR ALL
    USING (user_id = auth.uid()::text OR user_id = CURRENT_USER);

-- ==============================================================================
-- INITIAL PUBLIC CACHE SEED DATA (INCLUDING MORADABAD & MAJOR HUBS)
-- ==============================================================================

-- 1. Moradabad (Brass City of India)
INSERT INTO public_destinations (
    id, normalized_name, name, city, state_region, country, type,
    description, cover_image_url, latitude, longitude,
    popular_areas, best_time_to_visit, weather_summary, typical_temperature, search_count, data_sources
) VALUES (
    'a1111111-1111-1111-1111-111111111111',
    'moradabad',
    'Moradabad',
    'Moradabad',
    'Uttar Pradesh',
    'India',
    'Handicraft & Heritage Hub',
    'Globally renowned as the Pital Nagri (Brass City of India), Moradabad is celebrated for its historic brass handicrafts, rich Rohilla architectural heritage along the Ramganga River, and bustling artisan bazaars.',
    'https://images.unsplash.com/photo-1599661046289-e31897846e41?auto=format&fit=crop&w=1200&q=80',
    28.8386,
    78.7733,
    'Peetal Mandi, Civil Lines, Budh Bazaar, Ramganga Waterfront, Sambhal Road',
    'October to March (Pleasant winter climate)',
    'Mild breeze & clear skies',
    '23°C',
    12,
    'Official UP Tourism & Verified Public Cache'
) ON CONFLICT (normalized_name) DO NOTHING;

-- Attractions for Moradabad
INSERT INTO public_destination_attractions (
    destination_id, name, category, description, location_address,
    latitude, longitude, opening_hours, visit_duration, entry_ticket_cost, rating, review_count
) VALUES
(
    'a1111111-1111-1111-1111-111111111111',
    'Peetal Mandi (Brass Handicraft Market)',
    'Cultural Market',
    'The historic brass market showcasing master metal artisans, intricate brass artifacts, chandeliers, and engraved utensils.',
    'Peetal Mandi, Old City, Moradabad',
    28.8350, 78.7700, '10:00 - 21:00', '2 hours', 0.0, 4.7, 1850
),
(
    'a1111111-1111-1111-1111-111111111111',
    'Raza Library & Cultural Archive',
    'Heritage & History',
    'Renowned repository of historic Indo-Islamic manuscripts, royal decrees, Mughal miniatures, and rare texts.',
    'Near Rampur Highway Corridor, Moradabad',
    28.8420, 78.7810, '10:00 - 17:00', '2.5 hours', 50.0, 4.6, 940
),
(
    'a1111111-1111-1111-1111-111111111111',
    'Jama Masjid & Ramganga River Promenade',
    'Architecture',
    'Majestic 17th-century heritage structure overlooking the Ramganga River banks with grand sandstone domes.',
    'Near Civil Lines, Moradabad',
    28.8405, 78.7755, '06:00 - 19:30', '1.5 hours', 0.0, 4.8, 2200
),
(
    'a1111111-1111-1111-1111-111111111111',
    'Sai Temple & Eco Gardens',
    'Peaceful Garden',
    'Sprawling landscaped floral gardens and serene temple grounds popular for tranquil evening walks.',
    'Kanth Road, Moradabad',
    28.8650, 78.7520, '06:00 - 21:00', '1.5 hours', 0.0, 4.5, 760
) ON CONFLICT DO NOTHING;

-- Images for Moradabad
INSERT INTO public_destination_images (
    destination_id, image_url, caption, credit_source, is_cover
) VALUES
(
    'a1111111-1111-1111-1111-111111111111',
    'https://images.unsplash.com/photo-1599661046289-e31897846e41?auto=format&fit=crop&w=1200&q=80',
    'Moradabad City and Heritage Gateway',
    'Wikimedia Commons Public Domain',
    TRUE
),
(
    'a1111111-1111-1111-1111-111111111111',
    'https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=800&q=80',
    'Artisans crafting brass ware at Peetal Mandi',
    'UP Handicrafts Board',
    FALSE
) ON CONFLICT DO NOTHING;

-- 2. Jaipur (Pink City)
INSERT INTO public_destinations (
    id, normalized_name, name, city, state_region, country, type,
    description, cover_image_url, latitude, longitude,
    popular_areas, best_time_to_visit, weather_summary, typical_temperature, search_count, data_sources
) VALUES (
    'b2222222-2222-2222-2222-222222222222',
    'jaipur',
    'Jaipur',
    'Jaipur',
    'Rajasthan',
    'India',
    'Royal UNESCO Heritage City',
    'The historic Pink City and capital of Rajasthan, famous for majestic hill forts, honeycomb royal facades, vibrant bazaars, and exquisite palaces.',
    'https://images.unsplash.com/photo-1599661046289-e31897846e41?auto=format&fit=crop&w=1200&q=80',
    26.9124,
    75.7873,
    'Old Pink City, Amer, C-Scheme, Nahargarh Hills',
    'October to March',
    'Crisp & sunny',
    '26°C',
    85,
    'Rajasthan Tourism & Verified Cache'
) ON CONFLICT (normalized_name) DO NOTHING;

-- Attractions for Jaipur
INSERT INTO public_destination_attractions (
    destination_id, name, category, description, location_address,
    latitude, longitude, opening_hours, visit_duration, entry_ticket_cost, rating, review_count
) VALUES
(
    'b2222222-2222-2222-2222-222222222222',
    'Amber Palace & Fort',
    'Historical Fort',
    'Imposing hilltop fortress crafted from yellow and pink sandstone with the dazzling Sheesh Mahal.',
    'Devisinghpura, Amer, Jaipur',
    26.9855, 75.8513, '08:00 - 17:30', '2.5 hours', 200.0, 4.8, 3840
),
(
    'b2222222-2222-2222-2222-222222222222',
    'Hawa Mahal (Palace of Winds)',
    'Royal Architecture',
    'Iconic five-story honeycomb facade with 953 intricate jharokhas casements.',
    'Hawa Mahal Rd, Badi Choupad, Pink City',
    26.9239, 75.8267, '09:00 - 17:00', '1 hour', 50.0, 4.7, 5120
),
(
    'b2222222-2222-2222-2222-222222222222',
    'City Palace Jaipur',
    'Heritage Palace',
    'Grand complex of courtyards, gardens, Chandra Mahal, and royal armory museum.',
    'Gangori Bazar, Pink City, Jaipur',
    26.9258, 75.8236, '09:30 - 17:00', '2 hours', 300.0, 4.6, 2950
) ON CONFLICT DO NOTHING;
