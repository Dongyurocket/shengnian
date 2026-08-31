package com.voiceink.app.di

import android.content.Context
import androidx.room.Room
import com.voiceink.app.core.AppJson
import com.voiceink.app.data.local.AppDatabase
import com.voiceink.app.data.local.dao.CategoryDao
import com.voiceink.app.data.local.dao.EmbeddingDao
import com.voiceink.app.data.local.dao.LinkDao
import com.voiceink.app.data.local.dao.NoteDao
import com.voiceink.app.data.local.dao.TagDao
import com.voiceink.app.data.local.dao.TodoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
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

    @Provides
    fun provideTodoDao(db: AppDatabase): TodoDao = db.todoDao()

    @Provides
    fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideLinkDao(db: AppDatabase): LinkDao = db.linkDao()

    @Provides
    fun provideEmbeddingDao(db: AppDatabase): EmbeddingDao = db.embeddingDao()

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideJson(): Json = AppJson
}
