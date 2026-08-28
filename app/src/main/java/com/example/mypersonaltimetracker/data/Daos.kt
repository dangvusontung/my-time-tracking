package com.example.mypersonaltimetracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY inAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE inAt >= :fromMs ORDER BY inAt DESC")
    fun observeSince(fromMs: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE outAt IS NULL ORDER BY inAt DESC")
    fun observeOpen(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE outAt IS NULL ORDER BY inAt DESC LIMIT 1")
    suspend fun getOpen(): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<SessionEntity>)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    @Update
    suspend fun update(session: SessionEntity)

    @Delete
    suspend fun delete(session: SessionEntity)
}

@Dao
interface DayExceptionDao {
    @Query("SELECT * FROM day_exceptions")
    fun observeAll(): Flow<List<DayExceptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(exception: DayExceptionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exceptions: List<DayExceptionEntity>)

    @Query("DELETE FROM day_exceptions WHERE date = :date")
    suspend fun delete(date: String)

    @Query("DELETE FROM day_exceptions")
    suspend fun deleteAll()
}

@Dao
interface DayNoteDao {
    @Query("SELECT * FROM day_notes")
    fun observeAll(): Flow<List<DayNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: DayNoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<DayNoteEntity>)

    @Query("DELETE FROM day_notes WHERE date = :date")
    suspend fun delete(date: String)

    @Query("DELETE FROM day_notes")
    suspend fun deleteAll()
}

@Dao
interface WeekTargetDao {
    @Query("SELECT * FROM week_targets")
    fun observeAll(): Flow<List<WeekTargetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(target: WeekTargetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(targets: List<WeekTargetEntity>)

    @Query("DELETE FROM week_targets WHERE weekStart = :weekStart")
    suspend fun delete(weekStart: String)

    @Query("DELETE FROM week_targets")
    suspend fun deleteAll()
}
