package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.*
import com.example.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserEntity::class,
        TripEntity::class,
        ItineraryItemEntity::class,
        DestinationEntity::class,
        PlaceEntity::class,
        DestinationImageEntity::class,
        RestaurantEntity::class,
        TransportOptionEntity::class,
        HotelOptionEntity::class,
        GroupMemberEntity::class,
        ExpenseEntity::class,
        ExpenseSplitEntity::class,
        BookingEntity::class,
        SearchHistoryEntity::class,
        ChatMessageEntity::class,
        UserSavedDestinationEntity::class,
        ImageCacheEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun destinationDao(): DestinationDao
    abstract fun destinationImageDao(): DestinationImageDao
    abstract fun imageCacheDao(): ImageCacheDao
    abstract fun userSavedDestinationDao(): UserSavedDestinationDao
    abstract fun tripDao(): TripDao
    abstract fun travelSearchDao(): TravelSearchDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tripmate_database.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // SeedData has been moved to an explicit demo mode action
                            // to prevent demo data from leaking into production flows.
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
