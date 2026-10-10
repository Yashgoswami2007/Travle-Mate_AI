-- ==============================================================================
-- Migration: 06-device-identity-sharing.sql
-- Description: Unauthenticated Device Identity sharing and expense synchronization
-- ==============================================================================

-- We are enabling device-based sync without Supabase Auth.
-- The trip_id (UUID v4) acts as the capability token (shared secret).
-- Anyone with the trip_id can read/write expenses and members for that trip.

-- 1. Relax RLS for user_trips
DROP POLICY IF EXISTS "Users can manage own trips" ON public.user_trips;
DROP POLICY IF EXISTS "Users can insert own trips" ON public.user_trips;
DROP POLICY IF EXISTS "Owners and editors can update trips" ON public.user_trips;

CREATE POLICY "Anyone with trip_id can manage trips"
    ON public.user_trips FOR ALL
    USING (true)
    WITH CHECK (true);

-- 2. Relax RLS for user_group_members
DROP POLICY IF EXISTS "Users can manage own group members" ON public.user_group_members;

CREATE POLICY "Anyone with trip_id can manage group members"
    ON public.user_group_members FOR ALL
    USING (true)
    WITH CHECK (true);

-- 3. Relax RLS for user_expenses
DROP POLICY IF EXISTS "Users can manage own expenses" ON public.user_expenses;

CREATE POLICY "Anyone with trip_id can manage expenses"
    ON public.user_expenses FOR ALL
    USING (true)
    WITH CHECK (true);

-- 4. Relax RLS for user_expense_splits
DROP POLICY IF EXISTS "Users can manage own splits" ON public.user_expense_splits;

CREATE POLICY "Anyone with trip_id can manage splits"
    ON public.user_expense_splits FOR ALL
    USING (true)
    WITH CHECK (true);
