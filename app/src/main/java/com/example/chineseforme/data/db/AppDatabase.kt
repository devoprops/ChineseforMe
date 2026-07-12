package com.example.chineseforme.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TextWorkEntity::class,
        SentenceEntity::class,
        SentenceGroupOverrideEntity::class,
        PersonalPhraseEntity::class,
        GlossEntryEntity::class,
        AppMetaEntity::class,
        StrokeCharStatsEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun textWorkDao(): TextWorkDao
    abstract fun sentenceDao(): SentenceDao
    abstract fun overrideDao(): OverrideDao
    abstract fun personalPhraseDao(): PersonalPhraseDao
    abstract fun glossDao(): GlossDao
    abstract fun appMetaDao(): AppMetaDao
    abstract fun strokeStatsDao(): StrokeStatsDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS stroke_char_stats (
                        character TEXT NOT NULL PRIMARY KEY,
                        attempts INTEGER NOT NULL,
                        successes INTEGER NOT NULL,
                        fails INTEGER NOT NULL,
                        hintsUsed INTEGER NOT NULL,
                        bestStreak INTEGER NOT NULL,
                        currentStreak INTEGER NOT NULL,
                        lastPracticedEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chinese_for_me.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
