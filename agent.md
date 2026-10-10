# AGENT.MD — TripMate AI (Travel Mate) Engineering Guide

Welcome to **TripMate AI** (`Travle-Mate`), a production-grade, AI-powered native Android travel companion and itinerary engine. This document serves as the canonical reference manual and operational guide for autonomous AI coding agents, contributors, and developers working on this codebase.

---

## 1. Project Overview & Identity

- **Application Name**: TripMate AI (`app_name`: "TripMate AI", workspace: `Travle-Mate`)
- **Package Namespace**: `com.example`
- **Application ID**: `com.aistudio.tripmate.aiqjrv`
- **Platform**: Native Android (100% Kotlin + Jetpack Compose + Material 3)
- **Minimum SDK**: 24 (Android 7.0 Nougat)
- **Target SDK**: 35 (Android 15)
- **Compile SDK**: 36 (Android 16 / Vanilla Ice Cream)
- **JVM Target / Toolchain**: Java 21 / Java 11 bytecode compatibility

### Core Value Proposition
TripMate AI combines generative AI (Google Gemini) with real-world travel data, Wikimedia imagery, Open-Meteo weather intelligence, and a two-layer caching database (Room Local + Supabase Shared Cache) to deliver:
1. **Multi-Day Dynamic AI Itinerary Synthesis**: Interactive wizard that generates day-by-day travel schedules tailored to budget, pace, companion group, and travel style.
2. **Live Destination Discovery**: Autocomplete search with geocoding, real-world ticket fares, hotel listings, dining spots, and high-definition Wikimedia photography.
3. **Conversational Travel Assistant**: In-trip conversational concierge capable of modifying database itineraries in real time through structured AI tool actions.
4. **Group Expense & Split Tracking**: Equal and customized expense splitting among travel companions with settle-up calculation.
5. **Real-time Peer & Group Messaging**: In-app messenger built on Supabase for group communication and coordination.

---

## 2. Architecture & Technology Stack

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                           TRIPMATE AI ARCHITECTURE                          │
├─────────────────────────────────────────────────────────────────────────────┤
│  PRESENTATION LAYER (Jetpack Compose + Material 3)                          │
│  • Screens: Home, Trips, Explore, DestinationDetail, TripDetail, Wizard,    │
│             AiChat, Expenses, ChatInbox, ChatConversation, Profile          │
│  • Components: TactileButton, KeyboardAwareTextField, CommonComponents      │
│  • Navigation: StateFlow-driven Screen router in MainActivity               │
├─────────────────────────────────────────────────────────────────────────────┤
│  DOMAIN LAYER                                                               │
│  • AiTravelPlannerEngine (Structured tool action execution)                 │
│  • ItineraryPlannerWizard (Step questionnaire configuration & synthesis)    │
├─────────────────────────────────────────────────────────────────────────────┤
│  DATA & REPOSITORY LAYER                                                    │
│  • MainViewModel (Central reactive state orchestration)                     │
│  • TripRepository, DestinationRepository, UserRepository                    │
│  • ChatRepository, ChatIdentityRepository                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│  DATA STORAGE & EXTERNAL SERVICES                                           │
│  • Local Database: Room (tripmate_database.db)                              │
│  • Remote Database: Supabase PostgreSQL (User RLS + Public Cache)           │
│  • AI Engine: Google Gemini API (gemini-2.5-flash / gemini-3.8-flash)        │
│  • Location & Geocoding: Android Geocoder + Photon / Nominatim OSM API      │
│  • Imagery: Wikimedia Commons REST API via PlaceImageService                │
│  • Weather: Open-Meteo REST API via WeatherService                          │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Technology Matrix
| Domain | Technology / Library | Version / Details |
|---|---|---|
| **Language** | Kotlin | `2.2.10` |
| **UI Framework** | Jetpack Compose + Material 3 | Compose BOM `2024.09.00` |
| **Build Tool** | Gradle | `9.3.1` (AGP `9.1.1`, KSP `2.3.5`) |
| **Local Database** | Android Room | `2.7.0` (with KSP compiler & Coroutines KTX) |
| **Backend / DB** | Supabase (PostgreSQL) | PostgREST REST API + Custom PL/pgSQL RPCs |
| **AI LLM** | Google Gemini API | `gemini-2.5-flash`, `gemini-3.8-flash`, Firebase AI |
| **HTTP Client** | OkHttp & Retrofit | OkHttp `4.10.0`, Retrofit `2.12.0` |
| **JSON Serialization**| Moshi | `1.15.2` (with Moshi Kotlin Codegen via KSP) |
| **Image Loading** | Coil Compose | `2.7.0` |
| **Asynchronous** | Kotlin Coroutines & Flow | `1.10.2` (`StateFlow`, `SharedFlow`) |
| **Secrets Engine** | Secrets Gradle Plugin | `2.0.1` (injects `.env` into `BuildConfig`) |

---

## 3. Repository Directory Structure

```text
Travle-Mate/
├── .env                              # Local runtime secrets (GEMINI, SUPABASE)
├── .env.example                      # Template for environment credentials
├── build.gradle.kts                  # Root build script
├── settings.gradle.kts               # Module and plugin management
├── gradle.properties                 # JVM arguments & AndroidX flags
├── local.properties                  # Local Android SDK path (sdk.dir)
├── metadata.json                     # AI Studio / App capability manifest
├── update_supabase_cache.py          # Python migration helper script
├── gradle/
│   ├── libs.versions.toml            # Central Gradle Version Catalog
│   └── wrapper/
│       └── gradle-wrapper.properties # Gradle distribution definition
├── supabase/                         # Supabase SQL schemas and migrations
│   ├── README.md                     # Database architectural guide
│   ├── schema.sql                    # Production schema (Tables, RLS, Indexes)
│   ├── alter-db.sql                  # Schema modifications & patches
│   ├── 03-add-place-caching.sql      # Destination place caching schema
│   └── 04-chat-system.sql            # P2P & group messaging schema
└── app/
    ├── build.gradle.kts              # Application build config & dependencies
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml   # Permissions (INTERNET, ACCESS_COARSE/FINE_LOCATION)
        │   ├── res/                  # Drawables, mipmaps, strings, XML configs
        │   └── java/com/example/
        │       ├── MainActivity.kt   # Entry activity & screen router
        │       ├── auth/
        │       │   └── AuthState.kt  # User authentication state
        │       ├── data/
        │       │   ├── local/
        │       │   │   ├── AppDatabase.kt     # Room database definition
        │       │   │   ├── SeedData.kt        # Default seed & offline demo dataset
        │       │   │   ├── dao/               # Room DAOs (Trip, Destination, Expense, etc.)
        │       │   │   └── entity/            # Room entities (Entities.kt)
        │       │   ├── remote/
        │       │   │   ├── GeminiTravelService.kt            # Gemini AI direct client
        │       │   │   ├── SupabaseDestinationCacheService.kt# Shared cache with mutex lock
        │       │   │   ├── GeocodingService.kt               # OSM & Android geocoding
        │       │   │   ├── WeatherService.kt                 # Open-Meteo weather client
        │       │   │   ├── PlaceImageService.kt              # Wikimedia Commons resolver
        │       │   │   └── LiveTravelSearchService.kt        # Real-time search abstractions
        │       │   └── repository/
        │       │       ├── Repositories.kt                   # Trip, Destination, UserRepository
        │       │       ├── ChatRepository.kt                 # Supabase chat messenger
        │       │       └── ChatIdentityRepository.kt         # Chat user profile & identity
        │       ├── domain/
        │       │   └── ai/
        │       │       ├── AiTravelPlannerEngine.kt          # Conversational action planner
        │       │       └── ItineraryPlannerWizard.kt         # Wizard questionnaire engine
        │       └── ui/
        │           ├── components/
        │           │   ├── CommonComponents.kt               # Shared cards, badges, chips
        │           │   ├── KeyboardAwareTextField.kt         # IME-aware input field
        │           │   └── TactileButton.kt                  # Tactile haptic feedback button
        │           ├── theme/
        │           │   ├── Color.kt                          # Design system palette
        │           │   ├── Theme.kt                          # MyApplicationTheme definition
        │           │   └── Type.kt                           # Material 3 typography
        │           ├── viewmodel/
        │           │   └── MainViewModel.kt                  # Central application ViewModel
        │           └── screens/
        │               ├── home/HomeScreen.kt                # Feed, recent searches, active trip
        │               ├── trips/TripsListScreen.kt          # User trips overview
        │               ├── trip/TripDetailScreen.kt          # Day-by-day timeline, route map
        │               ├── destination/DestinationDetailScreen.kt # Live destination deep-dive
        │               ├── explore/ExploreScreen.kt          # Search & destination catalog
        │               ├── itinerary_wizard/TripWizardScreen.kt # Step-by-step generator
        │               ├── ai/AiChatScreen.kt                # In-trip AI conversational chat
        │               ├── expenses/ExpensesScreen.kt        # Expense logging & split solver
        │               ├── chat/                             # Peer chat inbox, conversation, overlays
        │               ├── map/TripRouteMap.kt               # Visual route map
        │               └── profile/ProfileScreen.kt          # User settings, preferences, demo load
        └── test/                                             # Unit & Robolectric tests
```

---

## 4. Two-Layer Database Architecture

A fundamental architectural design of TripMate AI is the strict separation between **Private User Data** and **Shared Public Knowledge**:

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│    LAYER 1: USER DATABASE (PRIVATE)  │ LAYER 2: PUBLIC CACHE (SHARED / AI)  │
├──────────────────────────────────────┼──────────────────────────────────────┤
│ • users / user_profiles              │ • public_destinations (Canonical)    │
│ • trips / user_trips                 │ • public_destination_attractions     │
│ • itinerary_items                    │ • public_destination_images (Cover)  │
│ • expenses & expense_splits          │ • public_destination_restaurants     │
│ • group_members                      │ • public_destination_generation_jobs │
│ • user_saved_destinations            │                                      │
│ • search_history & user_bookings     │                                      │
│                                      │                                      │
│ Storage: Local Room DB + Supabase    │ Storage: Shared Supabase Database    │
│ Security: User-scoped (auth.uid())   │ Security: Public Read, Service-Write │
└──────────────────────────────────────┴──────────────────────────────────────┘
```

### The "Cache-First, Zero-Duplicate AI" Flow
When any user searches for a destination (e.g. *"Moradabad"*, *"Kyoto"*, *"Prague"*):

```text
User searches "Kyoto"
         │
         ▼
1. Normalize query: "kyoto"
         │
         ▼
2. Check Local Room Database Cache
   ├── FOUND: Return instantly (0 ms latency, 0 network requests)
   └── NOT FOUND: Query Supabase Public Destination Table
         │
         ├── FOUND in Supabase:
         │   • Save to Local Room DB for future offline usage
         │   • Return cached destination + attractions + cover image
         │   • NO AI CALL MADE! (Cost: $0)
         │
         └── NOT FOUND in Supabase:
             • Acquire Concurrency Lock in public_destination_generation_jobs
             • If lock acquired:
                 1. Call Gemini API to research itinerary, landmarks, costs
                 2. Call PlaceImageService (Wikimedia) for high-res photos
                 3. Call WeatherService (Open-Meteo) for climate profile
                 4. Write compiled data into Supabase Public Database
                 5. Insert into local Room database
                 6. Release lock (status = 'COMPLETED')
             • If lock was held by another user:
                 • Poll job status and reuse the result once finished
```

---

## 5. Key Modules Deep Dive

### 5.1. AI Travel Engine (`domain/ai`)
- **`ItineraryPlannerWizard.kt`**: Houses the dynamic questionnaire steps (`getDefaultQuestions()`). Captures duration, companion count, starting point, budget tier, pace (`Relaxed`, `Balanced`, `Packed`), and dietary preferences.
- **`AiTravelPlannerEngine.kt`**: Translates natural language user chat into structured actions (`AiActionResult.Success`, `AiActionResult.QuestionPrompt`, `AiActionResult.Informational`). Allows the AI to modify, reorder, add, or delete itinerary items in the database.
- **`GeminiTravelService.kt`**:
  - Connects to Google Gemini via `https://generativelanguage.googleapis.com/v1beta/models/...:generateContent`.
  - Automatically falls back through candidate models: `gemini-2.5-flash`, `gemini-3.8-flash`, `gemini-flash-latest`, `gemini-2.5-pro`.
  - Configured with high timeouts (`60s`) for comprehensive multi-day travel synthesis.

### 5.2. Live Data Integrations (`data/remote`)
- **`PlaceImageService.kt`**: Queries Wikimedia Commons REST API using destination titles to fetch authentic, royalty-free high-resolution photography without paid Google Places API keys.
- **`GeocodingService.kt`**: Queries OpenStreetMap Photon and Nominatim APIs to resolve lat/long coordinates, city names, and country codes with Android Geocoder fallback.
- **`WeatherService.kt`**: Fetches real-time temperature, weather codes, and seasonal forecasts from the Open-Meteo API.
- **`SupabaseDestinationCacheService.kt`**: Manages remote Supabase PostgREST synchronization and handles in-flight deduplication via `ConcurrentHashMap` and coroutine `Mutex`.

### 5.3. Peer & Group Chat System (`data/repository/ChatRepository.kt`)
- Direct and group messaging backed by Supabase tables (`chat_conversations`, `chat_messages`, `chat_participants`, `chat_identities`).
- Generates anonymous or custom usernames with avatar initials.
- Real-time unread count badges integrated into the bottom navigation bar.

### 5.4. Expense Engine (`data/repository/Repositories.kt`, `ui/screens/expenses`)
- Records trip expenses with categories: Transport, Accommodation, Food, Activities, Shopping, Other.
- Supports automatic equal splitting across all group members.
- Tracks `isSettled` boolean per split, allowing companion balance reconciliations.

---

## 6. Design System & Styling Guidelines

TripMate AI adheres to a distinct, warm, editorial travel aesthetic.

### 6.1. Color Palette (`com.example.ui.theme.Color`)
```kotlin
// Primary Navy tones
val TravelNavyDark = Color(0xFF0F172A)      // Headers, dark cards, snackbars
val TravelNavyPrimary = Color(0xFF1E3A5F)   // Primary buttons, selected icons
val TravelNavySecondary = Color(0xFF475569) // Subtitles, inactive navigation
val TravelNavyContainer = Color(0xFFE2E8F0) // Tab indicator, light badges

// Warm Sunset / Terracotta Accents
val TravelTerracotta = Color(0xFFE06D53)    // Hero CTA buttons, highlights
val TravelAmberAccent = Color(0xFFD97706)   // Rating stars, price accents

// Backgrounds & Surfaces
val TravelSandBackground = Color(0xFFFBF9F5)// Main app background (warm sand)
val TravelSurfaceCard = Color(0xFFFFFFFF)   // Elevate content surfaces
val TravelSurfaceVariant = Color(0xFFF1F5F9)// Subtle background fill
val TravelBorderSubtle = Color(0xFFE2E8F0)  // Border lines

// Functional Status
val TravelForestGreen = Color(0xFF16A34A)   // Within budget, success
val TravelWarningRed = Color(0xFFDC2626)    // Over budget, delete actions
```

### 6.2. UI Design Rules
1. **Never use generic pure black (`#000000`) or default blue**: Always use `TravelNavyDark` or `TravelNavyPrimary`.
2. **Always respect Window Insets**: Use `WindowInsets.safeDrawing` on root Scaffolds and `WindowInsets.navigationBars` on bottom bars to prevent Android 15/16 edge-to-edge clipping.
3. **Interactive Elements**: Use `TactileButton` for CTAs to provide spring bounce animation and haptic feedback.
4. **Input Fields**: Use `KeyboardAwareTextField` to ensure keyboard adjustments do not obscure input fields.
5. **Testing Tags**: Every interactive element must supply a `.testTag("...")` modifier for UI testing and automated validation.

---

## 7. Environment & Secrets Management

The project uses the **Secrets Gradle Plugin** to load configuration from the root `.env` file into `com.example.BuildConfig`.

### Required `.env` Variables
```properties
# GEMINI_API_KEY: Required for Gemini AI API calls (itinerary generation, chat)
GEMINI_API_KEY=your_gemini_api_key_here

# SUPABASE CREDENTIALS: Required for two-layer database & destination cache
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_PUBLISHABLE_KEY=your_supabase_publishable_key_here
SUPABASE_SECRET_KEY=your_supabase_secret_key_here
```

### Accessing Secrets in Code
```kotlin
// Direct Kotlin access via generated BuildConfig
val apiKey = BuildConfig.GEMINI_API_KEY
val supabaseUrl = BuildConfig.SUPABASE_URL
val supabaseAnonKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
```

> **Security Note**: Never commit real API keys into version control. Keep `.env` in `.gitignore`. Provide placeholders in `.env.example`.

---

## 8. Build, Test, and Execution Commands

### Android SDK & JDK Requirements
- **JDK**: Java 21 (Default path: `C:\Program Files\Android\Android Studio\jbr`)
- **Android SDK**: Path specified in `local.properties`:
  ```properties
  sdk.dir=C\:\\Users\\HP\\AppData\\Local\\Android\\Sdk
  ```

### Common Gradle Commands (PowerShell / Terminal)
```powershell
# Build debug APK
.\gradlew assembleDebug

# Run unit tests (including Robolectric)
.\gradlew test

# Run Android lint
.\gradlew lint

# Clean build artifacts and Gradle cache
.\gradlew clean

# Build and generate release bundle (AAB)
.\gradlew bundleRelease
```

---

## 9. State Management & Navigation Architecture

### 9.1. ViewModel Organization (`MainViewModel.kt`)
The app uses a single centralized `MainViewModel` scoped to the application:
- **`currentScreen`**: `StateFlow<Screen>` indicating the active screen destination.
- **`screenStack`**: Mutable backstack list to support `navigateBack()`.
- **`allTrips` / `currentTrip`**: Reactive flows derived from Room DAOs.
- **`searchQuery` / `searchResults`**: Manages debounced local search and real-time geocoding.
- **`toastMessage`**: `MutableSharedFlow<String>` consumed in `MainActivity` to show uniform Snackbars.

### 9.2. Sealed Navigation Class
```kotlin
sealed class Screen {
    object Home : Screen()
    object Trips : Screen()
    object Explore : Screen()
    object Expenses : Screen()
    object Profile : Screen()
    object ChatInbox : Screen()
    data class DestinationDetail(val destinationId: String) : Screen()
    data class TripDetail(val tripId: String) : Screen()
    data class TripWizard(val destinationId: String? = null) : Screen()
    data class AiChat(val tripId: String? = null) : Screen()
    data class ChatConversation(val convId: String) : Screen()
}
```

---

## 10. AI Agent Working Rules & Guardrails

When modifying, extending, or fixing code in this project, adhere strictly to the following rules:

1. **Preserve Database Compatibility**:
   - Room database version is currently `3` (`AppDatabase.kt`).
   - If adding or changing fields in `Entities.kt`, ensure default values are provided or update migrations accordingly.
   - Room queries returning reactive streams must return `Flow<List<T>>` or `Flow<T?>`.
2. **Coroutines & Threading**:
   - Never run disk I/O, database queries, or network requests on `Dispatchers.Main`.
   - Always wrap database/network operations in `withContext(Dispatchers.IO)`.
3. **Compose Best Practices**:
   - Do not pass heavy state objects by value if unnecessary; observe `StateFlow` using `collectAsStateWithLifecycle()` or `collectAsState()`.
   - Ensure previews have sample data and wrap with `MyApplicationTheme`.
4. **Preserve Seed Data Independence**:
   - The app boots clean without auto-inserting `SeedData`.
   - `SeedData` is only explicitly loaded via `loadDemoData()` from `ProfileScreen`. Do not force seed data injection on cold boot.
5. **Preserve Documentation & Comments**:
   - Do not remove existing architectural comments, SQL explanations, or documentation unless explicitly asked.
6. **Graceful Network Degradation**:
   - All external network calls (Gemini, Supabase, Wikimedia, Open-Meteo) must catch exceptions and provide fallback UI states rather than crashing the app.

---

## 11. Quick Reference File Map

| Purpose | Relevant File / Directory |
|---|---|
| Navigation & App Entry | [`MainActivity.kt`](file:///c:/Users/HP/Downloads/Travle-Mate/app/src/main/java/com/example/MainActivity.kt) |
| Central App State | [`MainViewModel.kt`](file:///c:/Users/HP/Downloads/Travle-Mate/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt) |
| Room Entities & Models | [`Entities.kt`](file:///c:/Users/HP/Downloads/Travle-Mate/app/src/main/java/com/example/data/local/entity/Entities.kt) |
| Local Database Config | [`AppDatabase.kt`](file:///c:/Users/HP/Downloads/Travle-Mate/app/src/main/java/com/example/data/local/AppDatabase.kt) |
| Gemini AI Integration | [`GeminiTravelService.kt`](file:///c:/Users/HP/Downloads/Travle-Mate/app/src/main/java/com/example/data/remote/GeminiTravelService.kt) |
| Supabase Shared Cache | [`SupabaseDestinationCacheService.kt`](file:///c:/Users/HP/Downloads/Travle-Mate/app/src/main/java/com/example/data/remote/SupabaseDestinationCacheService.kt) |
| Repositories | [`Repositories.kt`](file:///c:/Users/HP/Downloads/Travle-Mate/app/src/main/java/com/example/data/repository/Repositories.kt) |
| Chat Messaging Engine | [`ChatRepository.kt`](file:///c:/Users/HP/Downloads/Travle-Mate/app/src/main/java/com/example/data/repository/ChatRepository.kt) |
| AI Itinerary Wizard | [`TripWizardScreen.kt`](file:///c:/Users/HP/Downloads/Travle-Mate/app/src/main/java/com/example/ui/screens/itinerary_wizard/TripWizardScreen.kt) |
| Color & Theme Tokens | [`Color.kt`](file:///c:/Users/HP/Downloads/Travle-Mate/app/src/main/java/com/example/ui/theme/Color.kt) |
| Supabase SQL Schema | [`supabase/schema.sql`](file:///c:/Users/HP/Downloads/Travle-Mate/supabase/schema.sql) |
| Gradle Versions Catalog | [`libs.versions.toml`](file:///c:/Users/HP/Downloads/Travle-Mate/gradle/libs.versions.toml) |
