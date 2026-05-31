package com.thivyanstudios.hark.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptionDao {
    @Query("SELECT * FROM transcriptions ORDER BY timestamp DESC")
    fun getAllTranscriptions(): Flow<List<TranscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscription(transcription: TranscriptionEntity)

    @Query("DELETE FROM transcriptions")
    suspend fun deleteAll()
    
    @Query("DELETE FROM transcriptions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
