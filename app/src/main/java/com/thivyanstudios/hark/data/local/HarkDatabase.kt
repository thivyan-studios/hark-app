package com.thivyanstudios.hark.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TranscriptionEntity::class], version = 1, exportSchema = false)
abstract class HarkDatabase : RoomDatabase() {
    abstract fun transcriptionDao(): TranscriptionDao
}
