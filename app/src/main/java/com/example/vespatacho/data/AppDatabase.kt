package com.example.vespatacho.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [KmReading::class, FuelReading::class, Vehicle::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun kmReadingDao(): KmReadingDao
    abstract fun fuelReadingDao(): FuelReadingDao
    abstract fun vehicleDao(): VehicleDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fuel_readings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `price` REAL NOT NULL, `liter` REAL NOT NULL, `timestamp` INTEGER NOT NULL)",
                )
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vehicles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)",
                )
                db.execSQL("INSERT INTO `vehicles` (`id`, `name`) VALUES (1, 'Vespa')")
                db.execSQL("ALTER TABLE `km_readings` ADD COLUMN `vehicleId` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `fuel_readings` ADD COLUMN `vehicleId` INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vespa_tacho.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
