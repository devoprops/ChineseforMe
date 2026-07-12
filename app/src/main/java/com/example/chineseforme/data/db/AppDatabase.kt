package com.example.chineseforme.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TextWorkEntity::class,
        SentenceEntity::class,
        SentenceGroupOverrideEntity::class,
        PersonalPhraseEntity::class,
        GlossEntryEntity::class,
        AppMetaEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun textWorkDao(): TextWorkDao
    abstract fun sentenceDao(): SentenceDao
    abstract fun overrideDao(): OverrideDao
    abstract fun personalPhraseDao(): PersonalPhraseDao
    abstract fun glossDao(): GlossDao
    abstract fun appMetaDao(): AppMetaDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chinese_for_me.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
