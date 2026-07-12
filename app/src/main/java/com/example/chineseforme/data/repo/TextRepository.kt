package com.example.chineseforme.data.repo

import com.example.chineseforme.data.db.AppDatabase
import com.example.chineseforme.data.db.SentenceEntity
import com.example.chineseforme.data.db.TextWorkEntity
import com.example.chineseforme.domain.segmentation.SentenceSplitter
import kotlinx.coroutines.flow.Flow

class TextRepository(private val db: AppDatabase) {
    fun observeWorks(): Flow<List<TextWorkEntity>> = db.textWorkDao().observeAll()

    fun observeSentences(workId: Long): Flow<List<SentenceEntity>> =
        db.sentenceDao().observeForWork(workId)

    suspend fun getWork(id: Long): TextWorkEntity? = db.textWorkDao().getById(id)

    suspend fun getSentence(id: Long): SentenceEntity? = db.sentenceDao().getById(id)

    suspend fun listSentences(workId: Long): List<SentenceEntity> =
        db.sentenceDao().listForWork(workId)

    suspend fun importText(
        title: String,
        content: String,
        sourceKey: String? = null
    ): Long {
        if (sourceKey != null) {
            val existing = db.textWorkDao().getBySourceKey(sourceKey)
            if (existing != null) {
                if (existing.content == content) return existing.id
                return refreshWorkContent(existing, title, content)
            }
        }
        return insertNewWork(title, content, sourceKey)
    }

    private suspend fun refreshWorkContent(
        existing: TextWorkEntity,
        title: String,
        content: String
    ): Long {
        db.sentenceDao().deleteForWork(existing.id)
        db.textWorkDao().update(
            existing.copy(
                title = title.ifBlank { existing.title },
                content = content,
                lastOpenedSentenceIndex = 0
            )
        )
        val sentences = SentenceSplitter.split(content).mapIndexed { index, text ->
            SentenceEntity(workId = existing.id, indexInWork = index, text = text)
        }
        if (sentences.isNotEmpty()) {
            db.sentenceDao().insertAll(sentences)
        }
        return existing.id
    }

    private suspend fun insertNewWork(
        title: String,
        content: String,
        sourceKey: String?
    ): Long {
        val workId = db.textWorkDao().insert(
            TextWorkEntity(
                title = title.ifBlank { "Untitled" },
                content = content,
                createdAtEpochMs = System.currentTimeMillis(),
                sourceKey = sourceKey
            )
        )
        val sentences = SentenceSplitter.split(content).mapIndexed { index, text ->
            SentenceEntity(workId = workId, indexInWork = index, text = text)
        }
        if (sentences.isNotEmpty()) {
            db.sentenceDao().insertAll(sentences)
        }
        return workId
    }

    suspend fun setSentenceParallelEnglish(sentenceId: Long, english: String?) {
        db.sentenceDao().setParallelEnglish(
            sentenceId,
            english?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    suspend fun applyParallelEnglishByIndex(workId: Long, englishByIndex: List<String?>) {
        val sentences = db.sentenceDao().listForWork(workId)
        sentences.forEachIndexed { index, sentence ->
            val english = englishByIndex.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() }
            if (english != null) {
                db.sentenceDao().setParallelEnglish(sentence.id, english)
            }
        }
    }

    suspend fun deleteWork(id: Long) {
        db.textWorkDao().delete(id)
    }

    suspend fun setLastSentence(workId: Long, index: Int) {
        db.textWorkDao().updateLastSentence(workId, index)
    }
}
