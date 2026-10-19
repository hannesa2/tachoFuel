package com.example.vespatacho.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TankSizeEntryDao {
    @Insert
    suspend fun insert(entry: TankSizeEntry): Long

    @Update
    suspend fun update(entry: TankSizeEntry)

    @Delete
    suspend fun delete(entry: TankSizeEntry)

    @Query("SELECT * FROM tank_size_entries WHERE vehicleId = :vehicleId ORDER BY validFrom ASC")
    fun getByVehicle(vehicleId: Long): Flow<List<TankSizeEntry>>

    @Query("SELECT * FROM tank_size_entries WHERE vehicleId = :vehicleId ORDER BY validFrom ASC")
    suspend fun getByVehicleOnce(vehicleId: Long): List<TankSizeEntry>

    @Query("SELECT * FROM tank_size_entries")
    suspend fun getAllOnce(): List<TankSizeEntry>
}
