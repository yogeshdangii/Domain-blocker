package com.yogesh.domainblocker


import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow


//room can't store list , so we convert to strings with comma
class Converters {
    @TypeConverter
    // to convert list to string for storage
    fun fromStringList(value: List<String>): String {
        return value.joinToString(",")
    }

    // to convert back comma vali string back to list of strings
    @TypeConverter
    fun toStringList(value: String): List<String> {
        return if (value.isEmpty()) emptyList() else value.split(",")
    }
}

// Data access object  , sql commands to read/write
@Dao
interface BundleDao {
    @Query("SELECT * FROM bundles")
    fun getAllBundles(): Flow<List<DomainBundle>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBundle(bundle: DomainBundle)

    @Query("DELETE FROM bundles WHERE name = :name")
    suspend fun deleteBundleByName(name: String)

    @Query("UPDATE bundles SET isEnabled = :isEnabled WHERE name = :name")
    suspend fun updateBundleStatus(name: String, isEnabled: Boolean)
}

// building the database
@Database(entities = [DomainBundle::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bundleDao(): BundleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "firewall_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}