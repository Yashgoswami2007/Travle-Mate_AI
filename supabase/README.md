# Supabase Database Architecture for TripMate AI

This directory contains the production-grade PostgreSQL / Supabase SQL schema implementing the **two-layer database system** and **smart destination caching with duplicate prevention**.

---

## 1. Architecture Overview

The system splits travel data into two completely distinct and purposefully optimized data layers:

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                       TRIPMATE AI DATA ARCHITECTURE                         │
├──────────────────────────────────────┬──────────────────────────────────────┤
│    LAYER 1: USER DATABASE (PRIVATE)  │ LAYER 2: PUBLIC CACHE (SHARED / AI)  │
├──────────────────────────────────────┼──────────────────────────────────────┤
│ • User Profiles & Preferences        │ • Public Canonical Destinations      │
│ • User Trips & Days                  │ • Public Key Attractions             │
│ • User Itineraries & Schedules       │ • Public Destination Images (Cover)  │
│ • User Budgets & Expense Splits      │ • Public Destination Restaurants     │
│ • User Search Logs & Bookings        │ • Atomic Generation Lock System      │
│                                      │                                      │
│ Secured via Row Level Security (RLS) │ Shared Public Knowledge Base (Read)  │
│ strictly filtered by auth.uid()      │ AI Research Engine (Cache-Write)     │
└──────────────────────────────────────┴──────────────────────────────────────┘
```

---

## 2. Configured Supabase Credentials

The application is configured to interface with Supabase using your project keys:
-- **Publishable (Anon) Key**: `your_supabase_publishable_key_here`
  - Used by clients to read public destinations, attractions, and public images.
  - Used with user JWT tokens to read/write private user database tables.
-- **Secret (Service Role) Key**: `your_supabase_secret_key_here`
  - Used strictly in backend / secure cloud worker routines to write AI research results into the shared public destination tables and release generation locks.
  - Never packaged on client devices.

---

## 3. Destination Cache & Concurrency Flow

When a user searches for a destination (e.g. `Moradabad`, `Jaipur`, or any new city):

```text
User searches "Moradabad"
         │
         ▼
Normalize name: normalize_destination_name("Moradabad") -> "moradabad"
         │
         ▼
Query Public Cache (public_destinations WHERE normalized_name = 'moradabad')
         │
    ┌────┴───────────────────────────┐
    ▼                                ▼
[EXISTS in Cache]             [DOES NOT EXIST]
    │                                │
    ▼                                ▼
Increment search_count          Acquire Lock in public_destination_generation_jobs
Return cached data:                  │
• Destination details          ┌─────┴────────────────────────┐
• Key attractions              ▼                              ▼
• Verified images          [Lock Won]                   [Lock in Progress]
NO AI CALL MADE!               │                              │
                               ▼                              ▼
                      Call Gemini AI / Search API     Wait / poll job status
                      Research destination            Once complete, reuse
                      Fetch Wikimedia images          cached result directly
                               │                      NO DUPLICATE AI CALL!
                               ▼
                      Insert into:
                      • public_destinations
                      • public_destination_attractions
                      • public_destination_images
                               │
                               ▼
                      Release lock: status = 'COMPLETED'
                      Return structured data to user
```

---

## 4. Tables Specification

### Layer 1: User Database Tables (Private)
1. **`user_profiles`**: User details, dietary preferences, travel pace, budget tier.
2. **`user_trips`**: User's planned or active trips, linked by `user_id`.
3. **`user_itinerary_items`**: User's activities, day schedules, and personalized notes.
4. **`user_expenses`**: Expenses logged by the user or trip members.
5. **`user_expense_splits`**: Equal and custom expense sharing breakdown.
6. **`user_group_members`**: Trip travel companions and payment assignments.
7. **`user_saved_destinations`**: Bookmarked destinations linked by `user_id`.
8. **`user_search_history`**: User's personal search query logs.
9. **`user_bookings`**: Transport, hotel, and activity booking confirmations.

### Layer 2: Public Destination Database Tables (Shared Cache)
1. **`public_destinations`**:
   - `id`: UUID Primary Key
   - `normalized_name`: Indexed slug (e.g. `moradabad`, `jaipur`)
   - `name`: Display name
   - `city`, `state_region`, `country`
   - `description`, `cover_image_url`
   - `latitude`, `longitude`
   - `popular_areas`, `best_time_to_visit`
   - `weather_summary`, `typical_temperature`
   - `search_count`, `data_sources`
   - `created_at`, `updated_at`, `last_verified_at`
2. **`public_destination_attractions`**:
   - `id`: UUID Primary Key
   - `destination_id`: References `public_destinations(id)`
   - `name`, `category`, `description`
   - `location_address`, `latitude`, `longitude`
   - `opening_hours`, `visit_duration`, `entry_ticket_cost`
   - `rating`, `review_count`, `data_source`
3. **`public_destination_images`**:
   - `id`: UUID Primary Key
   - `destination_id`: References `public_destinations(id)`
   - `attraction_id`: Optional reference to attraction
   - `image_url`, `caption`, `credit_source`, `is_cover`
4. **`public_destination_generation_jobs`**:
   - Concurrency table for atomic generation locks preventing duplicate AI research.

---

## 5. How to Deploy to Supabase

1. Open your [Supabase Dashboard](https://supabase.com/dashboard).
2. Navigate to the **SQL Editor** in the left sidebar.
3. Paste the contents of `supabase/schema.sql` and click **Run**.
4. All tables, foreign key constraints, indexes, RLS policies, and helper procedures (`acquire_destination_generation_lock`, `release_destination_generation_lock`) will be created automatically.
