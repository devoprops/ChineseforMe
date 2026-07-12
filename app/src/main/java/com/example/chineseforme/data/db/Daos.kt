package com.example.chineseforme.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TextWorkDao {
    @Query("SELECT * FROM text_works ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<TextWorkEntity>>

    @Query("SELECT * FROM text_works WHERE id = :id")
    suspend fun getById(id: Long): TextWorkEntity?

    @Query("SELECT * FROM text_works WHERE sourceKey = :sourceKey LIMIT 1")
    suspend fun getBySourceKey(sourceKey: String): TextWorkEntity?

    @Insert
    suspend fun insert(work: TextWorkEntity): Long

    @Update
    suspend fun update(work: TextWorkEntity)

    @Query("DELETE FROM text_works WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE text_works SET lastOpenedSentenceIndex = :index WHERE id = :workId")
    suspend fun updateLastSentence(workId: Long, index: Int)
}

@Dao
interface SentenceDao {
    @Query("SELECT * FROM sentences WHERE workId = :workId ORDER BY indexInWork ASC")
    fun observeForWork(workId: Long): Flow<List<SentenceEntity>>

    @Query("SELECT * FROM sentences WHERE workId = :workId ORDER BY indexInWork ASC")
    suspend fun listForWork(workId: Long): List<SentenceEntity>

    @Query("SELECT * FROM sentences WHERE id = :id")
    suspend fun getById(id: Long): SentenceEntity?

    @Insert
    suspend fun insertAll(sentences: List<SentenceEntity>): List<Long>

    @Update
    suspend fun update(sentence: SentenceEntity)

    @Query("UPDATE sentences SET parallelEnglish = :english WHERE id = :sentenceId")
    suspend fun setParallelEnglish(sentenceId: Long, english: String?)

    @Query("DELETE FROM sentences WHERE workId = :workId")
    suspend fun deleteForWork(workId: Long)
}

@Dao
interface OverrideDao {
    @Query("SELECT * FROM sentence_group_overrides WHERE sentenceId = :sentenceId")
    suspend fun getForSentence(sentenceId: Long): SentenceGroupOverrideEntity?

    @Insert
    suspend fun insert(entity: SentenceGroupOverrideEntity): Long

    @Update
    suspend fun update(entity: SentenceGroupOverrideEntity)

    @Query("DELETE FROM sentence_group_overrides WHERE sentenceId = :sentenceId")
    suspend fun deleteForSentence(sentenceId: Long)

    @Transaction
    suspend fun upsertSpans(sentenceId: Long, spansJson: String) {
        val existing = getForSentence(sentenceId)
        if (existing == null) {
            insert(SentenceGroupOverrideEntity(sentenceId = sentenceId, spansJson = spansJson))
        } else {
            update(existing.copy(spansJson = spansJson))
        }
    }
}

@Dao
interface PersonalPhraseDao {
    @Query("SELECT * FROM personal_phrases")
    suspend fun all(): List<PersonalPhraseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(phrase: PersonalPhraseEntity): Long
}

@Dao
interface GlossDao {
    @Query("SELECT COUNT(*) FROM gloss_entries")
    suspend fun count(): Int

    @Query("DELETE FROM gloss_entries")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<GlossEntryEntity>)

    @Query(
        """
        SELECT * FROM gloss_entries
        WHERE traditional = :surface OR simplified = :surface
        ORDER BY frequency DESC
        """
    )
    suspend fun lookup(surface: String): List<GlossEntryEntity>

    @Query(
        """
        SELECT * FROM gloss_entries
        WHERE length(traditional) >= 2
          AND (
            traditional LIKE '%' || :ch || '%'
            OR simplified LIKE '%' || :ch || '%'
          )
        ORDER BY frequency DESC
        LIMIT 40
        """
    )
    suspend fun entriesContaining(ch: String): List<GlossEntryEntity>

    @Query(
        """
        SELECT traditional, simplified, frequency FROM gloss_entries
        WHERE length(traditional) >= 2
        ORDER BY frequency DESC
        """
    )
    suspend fun multiCharSurfaces(): List<GlossSurfaceRow>

    @Query("SELECT * FROM gloss_entries WHERE traditional = :surface LIMIT 1")
    suspend fun firstTraditional(surface: String): GlossEntryEntity?
}

data class GlossSurfaceRow(
    val traditional: String,
    val simplified: String,
    val frequency: Double
)

@Dao
interface AppMetaDao {
    @Query("SELECT value FROM app_meta WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: AppMetaEntity)
}

@Dao
interface StrokeStatsDao {
    @Query("SELECT * FROM stroke_char_stats WHERE character = :character LIMIT 1")
    suspend fun get(character: String): StrokeCharStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StrokeCharStatsEntity)
}
