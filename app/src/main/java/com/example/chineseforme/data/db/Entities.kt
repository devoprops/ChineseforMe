package com.example.chineseforme.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "text_works",
    indices = [Index(value = ["sourceKey"], unique = true)]
)
data class TextWorkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val createdAtEpochMs: Long,
    val lastOpenedSentenceIndex: Int = 0,
    /** Stable id for bundled/re-imported sources, e.g. bundled:Zhuan Falun.txt */
    val sourceKey: String? = null
)

@Entity(
    tableName = "sentences",
    foreignKeys = [
        ForeignKey(
            entity = TextWorkEntity::class,
            parentColumns = ["id"],
            childColumns = ["workId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workId")]
)
data class SentenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workId: Long,
    val indexInWork: Int,
    val text: String,
    val parallelEnglish: String? = null
)

@Entity(
    tableName = "sentence_group_overrides",
    foreignKeys = [
        ForeignKey(
            entity = SentenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sentenceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sentenceId"], unique = true)]
)
data class SentenceGroupOverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sentenceId: Long,
    /** JSON array of {"start":n,"end":n} spans covering the full sentence. */
    val spansJson: String
)

@Entity(
    tableName = "personal_phrases",
    indices = [Index(value = ["surface"], unique = true)]
)
data class PersonalPhraseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surface: String,
    val pinyin: String = "",
    val sensesJson: String = "[]",
    val frequencyBoost: Double = 1_000_000.0
)

@Entity(
    tableName = "gloss_entries",
    indices = [
        Index("traditional"),
        Index("simplified")
    ]
)
data class GlossEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val traditional: String,
    val simplified: String,
    val pinyin: String,
    val definition: String,
    val frequency: Double
)

@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey val key: String,
    val value: String
)
