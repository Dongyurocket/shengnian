package com.voiceink.app.di

import android.content.Context
import androidx.room.Room
import com.voiceink.app.data.local.AppDatabase
import com.voiceink.app.data.local.dao.NoteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "voiceink.db")
            // MVP 开发期 schema 演进频繁，直接重建；正式发版前替换为显式 Migration
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()
}
