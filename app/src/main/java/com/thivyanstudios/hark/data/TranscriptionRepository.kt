package com.thivyanstudios.hark.data

import com.thivyanstudios.hark.data.local.TranscriptionDao
import com.thivyanstudios.hark.data.local.TranscriptionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptionRepository @Inject constructor(
    private val transcriptionDao: TranscriptionDao
) {
    val allTranscriptions: Flow<List<TranscriptionEntity>> = transcriptionDao.getAllTranscriptions()

    suspend fun insert(text: String) {
        if (text.isBlank()) return
        transcriptionDao.insertTranscription(TranscriptionEntity(text = text))
    }

    suspend fun deleteAll() {
        transcriptionDao.deleteAll()
    }
    
    suspend fun delete(id: Long) {
        transcriptionDao.deleteById(id)
    }
}
