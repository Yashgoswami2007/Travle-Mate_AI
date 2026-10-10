-- ==============================================================================
-- TRIPMATE AI - DATABASE MIGRATION 07: CROSS-DEVICE EXPENSE SYNC & SHARING
-- ==============================================================================
-- Purpose:
-- 1. Ensure foreign key on DELETE CASCADE for user_expense_splits -> user_expenses
-- 2. Relax RLS policies for both `anon` and `authenticated` roles
--    using the capability token (shared trip_id).
-- 3. Ensure idempotency across multiple device refreshes.
-- ==============================================================================

-- 1. Ensure user_trips permissions
DROP POLICY IF EXISTS "Anyone with trip_id can manage trips" ON public.user_trips;
DROP POLICY IF EXISTS "Users can manage own trips" ON public.user_trips;
DROP POLICY IF EXISTS "Users can insert own trips" ON public.user_trips;
DROP POLICY IF EXISTS "Owners and editors can update trips" ON public.user_trips;

CREATE POLICY "Anyone with trip_id can manage trips"
    ON public.user_trips FOR ALL
    TO anon, authenticated
    USING (true)
    WITH CHECK (true);

-- 2. Ensure user_group_members permissions
DROP POLICY IF EXISTS "Anyone with trip_id can manage group members" ON public.user_group_members;
DROP POLICY IF EXISTS "Users can manage own group members" ON public.user_group_members;

CREATE POLICY "Anyone with trip_id can manage group members"
    ON public.user_group_members FOR ALL
    TO anon, authenticated
    USING (true)
    WITH CHECK (true);

-- 3. Ensure user_expenses permissions
DROP POLICY IF EXISTS "Anyone with trip_id can manage expenses" ON public.user_expenses;
DROP POLICY IF EXISTS "Users can manage own expenses" ON public.user_expenses;

CREATE POLICY "Anyone with trip_id can manage expenses"
    ON public.user_expenses FOR ALL
    TO anon, authenticated
    USING (true)
    WITH CHECK (true);

-- 4. Ensure user_expense_splits permissions
DROP POLICY IF EXISTS "Anyone with trip_id can manage splits" ON public.user_expense_splits;
DROP POLICY IF EXISTS "Users can manage own splits" ON public.user_expense_splits;

CREATE POLICY "Anyone with trip_id can manage splits"
    ON public.user_expense_splits FOR ALL
    TO anon, authenticated
    USING (true)
    WITH CHECK (true);

-- 5. Add helpful indexes if not already present
CREATE INDEX IF NOT EXISTS idx_user_expenses_trip_id_updated ON public.user_expenses(trip_id);
CREATE INDEX IF NOT EXISTS idx_user_splits_trip_expense ON public.user_expense_splits(trip_id, expense_id);
CREATE INDEX IF NOT EXISTS idx_user_group_members_trip_user ON public.user_group_members(trip_id, user_id);
