package com.thivyanstudios.hark.di

import android.content.Context
import androidx.room.Room
import com.thivyanstudios.hark.data.local.HarkDatabase
import com.thivyanstudios.hark.data.local.TranscriptionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideHarkDatabase(@ApplicationContext context: Context): HarkDatabase {
        return Room.databaseBuilder(
            context,
            HarkDatabase::class.java,
            "hark_database"
        ).build()
    }

    @Provides
    fun provideTranscriptionDao(database: HarkDatabase): TranscriptionDao {
        return database.transcriptionDao()
    }
}
