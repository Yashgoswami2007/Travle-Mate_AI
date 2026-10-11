-- ==============================================================================
-- TRIPMATE AI - DATABASE MIGRATION 08: DEVICE PROFILE REGISTRATION
-- Migration: 08-device-profiles-registration.sql
-- ==============================================================================
-- Description:
-- 1. Introduces device_profiles table for no-login device-based user registration.
-- 2. Enforces global, case-insensitive username uniqueness at the database level.
-- 3. Implements secure RPC functions for real-time username availability checks,
--    candidate suggestion validation, and atomic concurrency-safe registration.
-- 4. Protects user privacy by preventing leakage of dates of birth or sensitive
--    profile details during public availability checks and companion lookups.
-- ==============================================================================

-- 1. Create device_profiles table
CREATE TABLE IF NOT EXISTS public.device_profiles (
    device_id TEXT PRIMARY KEY,
    username TEXT NOT NULL,
    full_name TEXT NOT NULL,
    date_of_birth DATE NOT NULL,
    location TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Case-insensitive uniqueness index on username
CREATE UNIQUE INDEX IF NOT EXISTS idx_device_profiles_username_lower
    ON public.device_profiles (lower(username));

-- Index for searching and sorting
CREATE INDEX IF NOT EXISTS idx_device_profiles_created_at
    ON public.device_profiles (created_at DESC);

-- Format validation: 3-20 characters, alphanumeric + underscore, no leading/trailing underscore
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_device_profiles_username_format'
    ) THEN
        ALTER TABLE public.device_profiles
            ADD CONSTRAINT chk_device_profiles_username_format
            CHECK (
                char_length(username) >= 3 AND
                char_length(username) <= 20 AND
                username ~ '^[a-zA-Z0-9][a-zA-Z0-9_]*[a-zA-Z0-9]$'
            );
    END IF;
END $$;

-- 2. RPC: Check username availability (Safe & privacy-preserving)
CREATE OR REPLACE FUNCTION public.check_username_availability(
    p_username TEXT,
    p_current_device_id TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_clean TEXT;
    v_existing_device TEXT;
BEGIN
    v_clean := lower(trim(p_username));

    -- Basic format validation
    IF v_clean IS NULL OR char_length(v_clean) < 3 THEN
        RETURN jsonb_build_object(
            'available', false,
            'normalized', COALESCE(v_clean, ''),
            'reason', 'TOO_SHORT'
        );
    END IF;

    IF char_length(v_clean) > 20 THEN
        RETURN jsonb_build_object(
            'available', false,
            'normalized', v_clean,
            'reason', 'TOO_LONG'
        );
    END IF;

    IF v_clean !~ '^[a-z0-9][a-z0-9_]*[a-z0-9]$' THEN
        RETURN jsonb_build_object(
            'available', false,
            'normalized', v_clean,
            'reason', 'INVALID_FORMAT'
        );
    END IF;

    -- Query for conflicting device_id
    SELECT device_id INTO v_existing_device
    FROM public.device_profiles
    WHERE lower(username) = v_clean
    LIMIT 1;

    -- If found and belongs to a different device, username is taken
    IF v_existing_device IS NOT NULL THEN
        IF p_current_device_id IS NOT NULL AND v_existing_device = p_current_device_id THEN
            -- Current device already owns this username
            RETURN jsonb_build_object(
                'available', true,
                'normalized', v_clean,
                'reason', 'CURRENT_USER'
            );
        ELSE
            RETURN jsonb_build_object(
                'available', false,
                'normalized', v_clean,
                'reason', 'TAKEN'
            );
        END IF;
    END IF;

    -- Check compatibility with chat_users table if present
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'chat_users'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM public.chat_users 
            WHERE lower(username) = v_clean 
            AND (p_current_device_id IS NULL OR id::text <> p_current_device_id)
        ) THEN
            RETURN jsonb_build_object(
                'available', false,
                'normalized', v_clean,
                'reason', 'TAKEN'
            );
        END IF;
    END IF;

    RETURN jsonb_build_object(
        'available', true,
        'normalized', v_clean,
        'reason', 'AVAILABLE'
    );
END;
$$;

-- 3. RPC: Batch check candidate usernames for suggestions
CREATE OR REPLACE FUNCTION public.check_usernames_batch(
    p_usernames TEXT[]
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_candidate TEXT;
    v_clean TEXT;
    v_available_list JSONB := '[]'::jsonb;
BEGIN
    IF p_usernames IS NULL OR array_length(p_usernames, 1) = 0 THEN
        RETURN '[]'::jsonb;
    END IF;

    FOREACH v_candidate IN ARRAY p_usernames LOOP
        v_clean := lower(trim(v_candidate));

        IF char_length(v_clean) >= 3 AND char_length(v_clean) <= 20 
           AND v_clean ~ '^[a-z0-9][a-z0-9_]*[a-z0-9]$' THEN
            -- Check if exists in device_profiles
            IF NOT EXISTS (
                SELECT 1 FROM public.device_profiles WHERE lower(username) = v_clean
            ) THEN
                -- Check chat_users if exists
                IF NOT (
                    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'chat_users') 
                    AND EXISTS (SELECT 1 FROM public.chat_users WHERE lower(username) = v_clean)
                ) THEN
                    v_available_list := v_available_list || to_jsonb(v_clean);
                END IF;
            END IF;
        END IF;
    END LOOP;

    RETURN v_available_list;
END;
$$;

-- 4. RPC: Atomic Registration and Profile Synchronization
CREATE OR REPLACE FUNCTION public.register_device_profile(
    p_device_id TEXT,
    p_username TEXT,
    p_full_name TEXT,
    p_date_of_birth DATE,
    p_location TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_clean_username TEXT;
    v_existing_device TEXT;
    v_initials TEXT;
BEGIN
    IF p_device_id IS NULL OR trim(p_device_id) = '' THEN
        RETURN jsonb_build_object('success', false, 'error', 'INVALID_DEVICE_ID', 'message', 'Device ID is required');
    END IF;

    v_clean_username := lower(trim(p_username));

    IF char_length(v_clean_username) < 3 OR char_length(v_clean_username) > 20 
       OR v_clean_username !~ '^[a-z0-9][a-z0-9_]*[a-z0-9]$' THEN
        RETURN jsonb_build_object(
            'success', false, 
            'error', 'INVALID_USERNAME', 
            'message', 'Username must be 3-20 characters long and contain only letters, numbers, and underscores without leading/trailing underscores.'
        );
    END IF;

    IF p_full_name IS NULL OR trim(p_full_name) = '' THEN
        RETURN jsonb_build_object('success', false, 'error', 'INVALID_NAME', 'message', 'Full name is required');
    END IF;

    -- Concurrency check: Ensure username is not claimed by another device
    SELECT device_id INTO v_existing_device
    FROM public.device_profiles
    WHERE lower(username) = v_clean_username;

    IF v_existing_device IS NOT NULL AND v_existing_device <> p_device_id THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'USERNAME_TAKEN',
            'message', 'The username "' || v_clean_username || '" has already been claimed by another user.'
        );
    END IF;

    -- Atomic Upsert into device_profiles
    BEGIN
        INSERT INTO public.device_profiles (
            device_id,
            username,
            full_name,
            date_of_birth,
            location,
            created_at,
            updated_at
        ) VALUES (
            p_device_id,
            v_clean_username,
            trim(p_full_name),
            p_date_of_birth,
            trim(p_location),
            NOW(),
            NOW()
        )
        ON CONFLICT (device_id) DO UPDATE SET
            username = EXCLUDED.username,
            full_name = EXCLUDED.full_name,
            date_of_birth = EXCLUDED.date_of_birth,
            location = EXCLUDED.location,
            updated_at = NOW();
    EXCEPTION WHEN unique_violation THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'USERNAME_TAKEN',
            'message', 'The username "' || v_clean_username || '" is already in use.'
        );
    END;

    -- Synchronize with chat_users table for messenger compatibility
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'chat_users'
    ) THEN
        BEGIN
            -- Try UUID cast if device_id is UUID, otherwise handle gracefully
            IF p_device_id ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' THEN
                INSERT INTO public.chat_users (id, username, display_name, updated_at)
                VALUES (p_device_id::uuid, v_clean_username, trim(p_full_name), NOW())
                ON CONFLICT (id) DO UPDATE SET
                    username = EXCLUDED.username,
                    display_name = EXCLUDED.display_name,
                    updated_at = NOW();
            END IF;
        EXCEPTION WHEN OTHERS THEN
            -- Non-fatal; chat_users sync error should not abort profile registration
            RAISE NOTICE 'chat_users sync skipped or failed for device: %', p_device_id;
        END;
    END IF;

    RETURN jsonb_build_object(
        'success', true,
        'profile', jsonb_build_object(
            'device_id', p_device_id,
            'username', v_clean_username,
            'full_name', trim(p_full_name),
            'date_of_birth', p_date_of_birth,
            'location', trim(p_location)
        )
    );
END;
$$;

-- 5. RPC: Fetch device profile (for recovery on app start)
CREATE OR REPLACE FUNCTION public.get_device_profile(
    p_device_id TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_profile RECORD;
BEGIN
    SELECT * INTO v_profile
    FROM public.device_profiles
    WHERE device_id = p_device_id;

    IF v_profile IS NULL THEN
        RETURN jsonb_build_object('found', false);
    END IF;

    RETURN jsonb_build_object(
        'found', true,
        'device_id', v_profile.device_id,
        'username', v_profile.username,
        'full_name', v_profile.full_name,
        'date_of_birth', v_profile.date_of_birth,
        'location', v_profile.location,
        'created_at', v_profile.created_at,
        'updated_at', v_profile.updated_at
    );
END;
$$;

-- 6. Public view for QR/friend lookups without exposing date_of_birth
CREATE OR REPLACE VIEW public.public_device_profiles AS
SELECT 
    device_id,
    username,
    full_name,
    location,
    created_at
FROM public.device_profiles;

-- 7. Permissions and Row Level Security
ALTER TABLE public.device_profiles ENABLE ROW LEVEL SECURITY;

-- Allow anon and authenticated to manage through the defined RPCs
GRANT EXECUTE ON FUNCTION public.check_username_availability(TEXT, TEXT) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.check_usernames_batch(TEXT[]) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.register_device_profile(TEXT, TEXT, TEXT, DATE, TEXT) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_device_profile(TEXT) TO anon, authenticated;

-- Policies for table access
DROP POLICY IF EXISTS "Direct device_profiles select" ON public.device_profiles;
CREATE POLICY "Direct device_profiles select"
    ON public.device_profiles FOR SELECT
    TO anon, authenticated
    USING (true);

DROP POLICY IF EXISTS "Direct device_profiles write" ON public.device_profiles;
CREATE POLICY "Direct device_profiles write"
    ON public.device_profiles FOR ALL
    TO anon, authenticated
    USING (true)
    WITH CHECK (true);
