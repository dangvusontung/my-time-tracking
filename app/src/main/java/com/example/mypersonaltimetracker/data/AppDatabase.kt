package com.example.mypersonaltimetracker.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.mypersonaltimetracker.domain.DayType

class Converters {
    @TypeConverter
    fun dayTypeToString(value: DayType): String = value.name

    @TypeConverter
    fun stringToDayType(value: String): DayType = DayType.valueOf(value)
}

@Database(
    entities = [
        SessionEntity::class,
        DayExceptionEntity::class,
        WeekTargetEntity::class,
        DayNoteEntity::class,
        WatchReceiptEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchReceiptDao(): WatchReceiptDao
    abstract fun sessionDao(): SessionDao
    abstract fun dayExceptionDao(): DayExceptionDao
    abstract fun weekTargetDao(): WeekTargetDao
    abstract fun dayNoteDao(): DayNoteDao

    companion object {
        const val NAME = "time_tracker.db"
    }
}
