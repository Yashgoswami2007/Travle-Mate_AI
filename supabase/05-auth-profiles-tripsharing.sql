-- ==============================================================================
-- TRIPMATE AI - SUPABASE GOOGLE OAUTH, PROFILES & TRIP SHARING MIGRATION
-- Migration: 05-auth-profiles-tripsharing.sql
-- ==============================================================================
-- Highlights:
-- 1. Separation of Public Profiles (discovery/search) and Private Profiles (sensitive).
-- 2. Resolution of UUID vs TEXT types across user_trips, expenses, and auth.users.
-- 3. Transactional RPCs for accepting/rejecting invitations and removing members,
--    ensuring membership and chat access remain 100% atomic and consistent.
-- 4. Robust Row Level Security (RLS) for collaborative trips, expenses, and chats.
-- ==============================================================================

-- 1. PUBLIC PROFILES (Searchable discovery: username, display_name, avatar)
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    username TEXT UNIQUE NOT NULL,
    display_name TEXT NOT NULL,
    avatar_url TEXT DEFAULT '',
    bio TEXT DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_profiles_username_lower ON public.profiles (lower(username));
CREATE INDEX IF NOT EXISTS idx_profiles_display_name ON public.profiles (display_name);

-- 2. PRIVATE USER PROFILES (Sensitive preferences & contact info: only owner can read)
CREATE TABLE IF NOT EXISTS public.user_private_profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT NOT NULL DEFAULT '',
    home_location TEXT NOT NULL DEFAULT 'Delhi, India',
    currency TEXT NOT NULL DEFAULT '₹',
    diet_preference TEXT NOT NULL DEFAULT 'Vegetarian',
    travel_style TEXT NOT NULL DEFAULT 'Relaxed',
    budget_tier TEXT NOT NULL DEFAULT 'Medium',
    preferred_accommodation TEXT NOT NULL DEFAULT 'Boutique Hotel',
    preferred_transport TEXT NOT NULL DEFAULT 'Train',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. ENSURE CHAT USERS SYNCS WITH PROFILES (for backward compatibility with 04-chat-system.sql)
CREATE TABLE IF NOT EXISTS public.chat_users (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    username TEXT UNIQUE NOT NULL CHECK (char_length(username) >= 3),
    display_name TEXT NOT NULL,
    avatar_path TEXT,
    bio TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. AUTO-PROFILE CREATION TRIGGER ON auth.users
CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS TRIGGER AS $$
DECLARE
    v_raw_username TEXT;
    v_clean_username TEXT;
    v_full_name TEXT;
    v_avatar TEXT;
BEGIN
    v_full_name := COALESCE(
        NEW.raw_user_meta_data->>'full_name',
        NEW.raw_user_meta_data->>'name',
        split_part(COALESCE(NEW.email, 'traveler'), '@', 1)
    );
    v_avatar := COALESCE(
        NEW.raw_user_meta_data->>'avatar_url',
        NEW.raw_user_meta_data->>'picture',
        ''
    );
    
    v_raw_username := LOWER(REGEXP_REPLACE(split_part(COALESCE(NEW.email, 'user'), '@', 1), '[^a-zA-Z0-9_]', '_', 'g'));
    IF length(v_raw_username) < 3 THEN
        v_raw_username := 'user_' || substr(NEW.id::text, 1, 6);
    END IF;
    
    v_clean_username := v_raw_username;
    WHILE EXISTS (SELECT 1 FROM public.profiles WHERE username = v_clean_username) LOOP
        v_clean_username := v_raw_username || '_' || floor(random() * 8999 + 1000)::text;
    END LOOP;

    -- Upsert Public Profile
    INSERT INTO public.profiles (id, username, display_name, avatar_url)
    VALUES (NEW.id, v_clean_username, v_full_name, v_avatar)
    ON CONFLICT (id) DO UPDATE
    SET display_name = EXCLUDED.display_name,
        avatar_url = CASE WHEN profiles.avatar_url = '' THEN EXCLUDED.avatar_url ELSE profiles.avatar_url END,
        updated_at = NOW();

    -- Upsert chat_users for chat compatibility
    INSERT INTO public.chat_users (id, username, display_name, avatar_path)
    VALUES (NEW.id, v_clean_username, v_full_name, v_avatar)
    ON CONFLICT (id) DO UPDATE
    SET display_name = EXCLUDED.display_name,
        avatar_path = CASE WHEN chat_users.avatar_path IS NULL OR chat_users.avatar_path = '' THEN EXCLUDED.avatar_path ELSE chat_users.avatar_path END,
        updated_at = NOW();

    -- Upsert Private Profile
    INSERT INTO public.user_private_profiles (id, email)
    VALUES (NEW.id, COALESCE(NEW.email, ''))
    ON CONFLICT (id) DO UPDATE
    SET email = EXCLUDED.email,
        updated_at = NOW();

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS tr_on_auth_user_created ON auth.users;
CREATE TRIGGER tr_on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_auth_user();

-- 5. TRIP MEMBERS TABLE (Collaborators on trips)
CREATE TABLE IF NOT EXISTS public.trip_members (
    trip_id UUID NOT NULL REFERENCES public.user_trips(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER')) DEFAULT 'EDITOR',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (trip_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_trip_members_user ON public.trip_members(user_id);
CREATE INDEX IF NOT EXISTS idx_trip_members_trip ON public.trip_members(trip_id);

-- 6. TRIP INVITATIONS TABLE
CREATE TABLE IF NOT EXISTS public.trip_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES public.user_trips(id) ON DELETE CASCADE,
    inviter_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    invitee_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')) DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(trip_id, invitee_id)
);

CREATE INDEX IF NOT EXISTS idx_trip_invitations_invitee ON public.trip_invitations(invitee_id, status);
CREATE INDEX IF NOT EXISTS idx_trip_invitations_inviter ON public.trip_invitations(inviter_id);

-- 7. CHAT CONVERSATIONS LINK TO TRIPS (Auto group chat for trips)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'chat_conversations' AND column_name = 'trip_id'
    ) THEN
        ALTER TABLE public.chat_conversations ADD COLUMN trip_id UUID REFERENCES public.user_trips(id) ON DELETE CASCADE;
        CREATE UNIQUE INDEX IF NOT EXISTS idx_chat_conversations_trip_id ON public.chat_conversations(trip_id) WHERE trip_id IS NOT NULL;
    END IF;
END $$;

-- 8. TRANSACTIONAL RPCS (Atomic invitation acceptance, rejection, and member removal)

-- Accept Trip Invitation
CREATE OR REPLACE FUNCTION public.accept_trip_invitation(p_invitation_id UUID)
RETURNS JSONB AS $$
DECLARE
    v_inv RECORD;
    v_trip RECORD;
    v_conv_id UUID;
    v_user_profile RECORD;
BEGIN
    -- 1. Find and validate invitation
    SELECT * INTO v_inv FROM public.trip_invitations WHERE id = p_invitation_id;
    IF v_inv IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Invitation not found');
    END IF;

    IF v_inv.invitee_id <> auth.uid() THEN
        RETURN jsonb_build_object('success', false, 'error', 'Unauthorized: You are not the invitee');
    END IF;

    IF v_inv.status <> 'PENDING' THEN
        RETURN jsonb_build_object('success', false, 'error', 'Invitation is already ' || v_inv.status);
    END IF;

    -- 2. Mark invitation ACCEPTED
    UPDATE public.trip_invitations
    SET status = 'ACCEPTED', updated_at = NOW()
    WHERE id = p_invitation_id;

    -- 3. Insert into trip_members (prevents duplicate memberships via ON CONFLICT)
    INSERT INTO public.trip_members (trip_id, user_id, role, joined_at)
    VALUES (v_inv.trip_id, auth.uid(), 'EDITOR', NOW())
    ON CONFLICT (trip_id, user_id) DO UPDATE SET role = EXCLUDED.role;

    -- 4. Mirror into user_group_members for local synchronization
    SELECT * INTO v_user_profile FROM public.profiles WHERE id = auth.uid();
    INSERT INTO public.user_group_members (trip_id, user_id, name, email, is_current_user)
    VALUES (
        v_inv.trip_id,
        auth.uid()::text,
        COALESCE(v_user_profile.display_name, 'Traveler'),
        '',
        FALSE
    )
    ON CONFLICT DO NOTHING;

    -- 5. Find or create trip group chat conversation and add member atomically
    SELECT id INTO v_conv_id FROM public.chat_conversations WHERE trip_id = v_inv.trip_id;
    IF v_conv_id IS NULL THEN
        SELECT destination_name INTO v_trip FROM public.user_trips WHERE id = v_inv.trip_id;
        INSERT INTO public.chat_conversations (type, title, created_by, trip_id)
        VALUES ('GROUP', COALESCE(v_trip.destination_name, 'Trip') || ' Group Chat', v_inv.inviter_id, v_inv.trip_id)
        RETURNING id INTO v_conv_id;

        -- Add inviter to chat
        INSERT INTO public.chat_members (conversation_id, user_id, role)
        VALUES (v_conv_id, v_inv.inviter_id, 'ADMIN')
        ON CONFLICT (conversation_id, user_id) DO NOTHING;
    END IF;

    -- Add current user to trip chat
    INSERT INTO public.chat_members (conversation_id, user_id, role)
    VALUES (v_conv_id, auth.uid(), 'MEMBER')
    ON CONFLICT (conversation_id, user_id) DO NOTHING;

    RETURN jsonb_build_object(
        'success', true,
        'trip_id', v_inv.trip_id,
        'conversation_id', v_conv_id
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Reject Trip Invitation
CREATE OR REPLACE FUNCTION public.reject_trip_invitation(p_invitation_id UUID)
RETURNS JSONB AS $$
DECLARE
    v_inv RECORD;
BEGIN
    SELECT * INTO v_inv FROM public.trip_invitations WHERE id = p_invitation_id;
    IF v_inv IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Invitation not found');
    END IF;

    IF v_inv.invitee_id <> auth.uid() THEN
        RETURN jsonb_build_object('success', false, 'error', 'Unauthorized: You are not the invitee');
    END IF;

    UPDATE public.trip_invitations
    SET status = 'REJECTED', updated_at = NOW()
    WHERE id = p_invitation_id;

    RETURN jsonb_build_object('success', true);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Remove Trip Member (Revokes trip and trip chat access atomically)
CREATE OR REPLACE FUNCTION public.remove_trip_member(p_trip_id UUID, p_member_user_id UUID)
RETURNS JSONB AS $$
DECLARE
    v_is_owner BOOLEAN;
    v_conv_id UUID;
BEGIN
    -- Check if caller is owner of the trip OR is leaving their own membership
    SELECT (
        user_id = auth.uid()::text OR
        EXISTS (SELECT 1 FROM public.trip_members WHERE trip_id = p_trip_id AND user_id = auth.uid() AND role = 'OWNER')
    ) INTO v_is_owner
    FROM public.user_trips WHERE id = p_trip_id;

    IF NOT (v_is_owner OR p_member_user_id = auth.uid()) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Unauthorized to remove member');
    END IF;

    -- 1. Remove from trip_members
    DELETE FROM public.trip_members WHERE trip_id = p_trip_id AND user_id = p_member_user_id;

    -- 2. Remove from user_group_members
    DELETE FROM public.user_group_members WHERE trip_id = p_trip_id AND user_id = p_member_user_id::text;

    -- 3. Remove from trip group chat
    SELECT id INTO v_conv_id FROM public.chat_conversations WHERE trip_id = p_trip_id;
    IF v_conv_id IS NOT NULL THEN
        DELETE FROM public.chat_members WHERE conversation_id = v_conv_id AND user_id = p_member_user_id;
    END IF;

    -- 4. Mark any invitation as removed/rejected
    UPDATE public.trip_invitations
    SET status = 'REJECTED', updated_at = NOW()
    WHERE trip_id = p_trip_id AND invitee_id = p_member_user_id;

    RETURN jsonb_build_object('success', true);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 9. ROW LEVEL SECURITY (RLS) POLICIES

-- Enable RLS
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_private_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.trip_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.trip_invitations ENABLE ROW LEVEL SECURITY;

-- Profiles: Public discovery (Authenticated can read non-sensitive fields; user can update own)
DROP POLICY IF EXISTS "Profiles are readable by authenticated users" ON public.profiles;
CREATE POLICY "Profiles are readable by authenticated users"
    ON public.profiles FOR SELECT
    TO authenticated
    USING (true);

DROP POLICY IF EXISTS "Users can update own profile" ON public.profiles;
CREATE POLICY "Users can update own profile"
    ON public.profiles FOR UPDATE
    TO authenticated
    USING (auth.uid() = id);

-- Private Profiles: Only the owner can select or update
DROP POLICY IF EXISTS "Private profiles are accessible only by owner" ON public.user_private_profiles;
CREATE POLICY "Private profiles are accessible only by owner"
    ON public.user_private_profiles FOR ALL
    TO authenticated
    USING (auth.uid() = id);

-- Trip Members: Readable by trip participants
DROP POLICY IF EXISTS "Trip members are readable by collaborators" ON public.trip_members;
CREATE POLICY "Trip members are readable by collaborators"
    ON public.trip_members FOR SELECT
    TO authenticated
    USING (
        user_id = auth.uid() OR
        EXISTS (SELECT 1 FROM public.trip_members tm WHERE tm.trip_id = trip_members.trip_id AND tm.user_id = auth.uid()) OR
        EXISTS (SELECT 1 FROM public.user_trips ut WHERE ut.id = trip_members.trip_id AND ut.user_id = auth.uid()::text)
    );

DROP POLICY IF EXISTS "Trip owners can insert members" ON public.trip_members;
CREATE POLICY "Trip owners can insert members"
    ON public.trip_members FOR INSERT
    TO authenticated
    WITH CHECK (
        EXISTS (SELECT 1 FROM public.user_trips ut WHERE ut.id = trip_members.trip_id AND ut.user_id = auth.uid()::text) OR
        user_id = auth.uid()
    );

-- Trip Invitations: Inviter and Invitee only
DROP POLICY IF EXISTS "Trip invitations visible to inviter and invitee" ON public.trip_invitations;
CREATE POLICY "Trip invitations visible to inviter and invitee"
    ON public.trip_invitations FOR SELECT
    TO authenticated
    USING (inviter_id = auth.uid() OR invitee_id = auth.uid());

DROP POLICY IF EXISTS "Users can create invitations for trips they own" ON public.trip_invitations;
CREATE POLICY "Users can create invitations for trips they own"
    ON public.trip_invitations FOR INSERT
    TO authenticated
    WITH CHECK (
        inviter_id = auth.uid() AND
        (
            EXISTS (SELECT 1 FROM public.user_trips ut WHERE ut.id = trip_invitations.trip_id AND ut.user_id = auth.uid()::text) OR
            EXISTS (SELECT 1 FROM public.trip_members tm WHERE tm.trip_id = trip_invitations.trip_id AND tm.user_id = auth.uid() AND tm.role IN ('OWNER', 'EDITOR'))
        )
    );

DROP POLICY IF EXISTS "Invitee can update invitation status" ON public.trip_invitations;
CREATE POLICY "Invitee can update invitation status"
    ON public.trip_invitations FOR UPDATE
    TO authenticated
    USING (invitee_id = auth.uid());

-- User Trips: Extended to trip_members with UUID vs TEXT cast
DROP POLICY IF EXISTS "Users can manage own trips" ON public.user_trips;
CREATE POLICY "Users can manage own trips"
    ON public.user_trips FOR SELECT
    TO authenticated
    USING (
        user_id = auth.uid()::text OR
        EXISTS (SELECT 1 FROM public.trip_members tm WHERE tm.trip_id = user_trips.id AND tm.user_id = auth.uid())
    );

DROP POLICY IF EXISTS "Users can insert own trips" ON public.user_trips;
CREATE POLICY "Users can insert own trips"
    ON public.user_trips FOR INSERT
    TO authenticated
    WITH CHECK (user_id = auth.uid()::text);

DROP POLICY IF EXISTS "Owners and editors can update trips" ON public.user_trips;
CREATE POLICY "Owners and editors can update trips"
    ON public.user_trips FOR UPDATE
    TO authenticated
    USING (
        user_id = auth.uid()::text OR
        EXISTS (SELECT 1 FROM public.trip_members tm WHERE tm.trip_id = user_trips.id AND tm.user_id = auth.uid() AND tm.role IN ('OWNER', 'EDITOR'))
    );

-- User Expenses: Readable & creatable by trip owner and members
DROP POLICY IF EXISTS "Users can manage own expenses" ON public.user_expenses;
CREATE POLICY "Users can manage own expenses"
    ON public.user_expenses FOR ALL
    TO authenticated
    USING (
        user_id = auth.uid()::text OR
        EXISTS (SELECT 1 FROM public.trip_members tm WHERE tm.trip_id = user_expenses.trip_id AND tm.user_id = auth.uid())
    );

-- User Expense Splits: Readable & creatable by trip members
DROP POLICY IF EXISTS "Users can manage own splits" ON public.user_expense_splits;
CREATE POLICY "Users can manage own splits"
    ON public.user_expense_splits FOR ALL
    TO authenticated
    USING (
        user_id = auth.uid()::text OR
        EXISTS (SELECT 1 FROM public.trip_members tm WHERE tm.trip_id = user_expense_splits.trip_id AND tm.user_id = auth.uid())
    );
