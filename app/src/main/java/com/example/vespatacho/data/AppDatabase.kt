package com.example.vespatacho.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Vehicle::class, GasReading::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gasReadingDao(): GasReadingDao
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
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `gas_readings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `vehicleId` INTEGER NOT NULL DEFAULT 1, `km` INTEGER, `price` REAL, `liter` REAL, `rawOcrText` TEXT, `timestamp` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "INSERT INTO `gas_readings` (`vehicleId`, `km`, `rawOcrText`, `timestamp`) SELECT `vehicleId`, `km`, `rawOcrText`, `timestamp` FROM `km_readings`",
                )
                db.execSQL(
                    "INSERT INTO `gas_readings` (`vehicleId`, `price`, `liter`, `timestamp`) SELECT `vehicleId`, `price`, `liter`, `timestamp` FROM `fuel_readings`",
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vespa_tacho.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
