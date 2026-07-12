package com.example.chineseforme

import android.app.Application
import com.example.chineseforme.data.db.AppDatabase
import com.example.chineseforme.data.dictionary.DictionaryImporter
import com.example.chineseforme.data.importing.BundledTextCatalog
import com.example.chineseforme.data.importing.DocumentTextExtractor
import com.example.chineseforme.data.repo.TextRepository
import com.example.chineseforme.data.settings.SettingsRepository
import com.example.chineseforme.data.stroke.KangxiRadicalRepository
import com.example.chineseforme.data.stroke.StrokeDataRepository
import com.example.chineseforme.domain.analysis.SentenceAnalyzer
import com.example.chineseforme.domain.segmentation.DictSegmenter
import com.example.chineseforme.domain.translation.NotionalTranslationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ChineseForMeApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: AppDatabase
        private set
    lateinit var textRepository: TextRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var documentExtractor: DocumentTextExtractor
        private set
    lateinit var bundledTextCatalog: BundledTextCatalog
        private set
    lateinit var segmenter: DictSegmenter
        private set
    lateinit var analyzer: SentenceAnalyzer
        private set
    lateinit var notionalTranslationService: NotionalTranslationService
        private set
    lateinit var strokeDataRepository: StrokeDataRepository
        private set
    lateinit var kangxiRadicalRepository: KangxiRadicalRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.get(this)
        textRepository = TextRepository(database)
        settingsRepository = SettingsRepository(this)
        documentExtractor = DocumentTextExtractor(this)
        bundledTextCatalog = BundledTextCatalog(this)
        segmenter = DictSegmenter(database.glossDao(), database.personalPhraseDao())
        analyzer = SentenceAnalyzer(database.glossDao(), database.overrideDao(), segmenter)
        notionalTranslationService = NotionalTranslationService(database.glossDao(), segmenter)
        strokeDataRepository = StrokeDataRepository(this)
        kangxiRadicalRepository = KangxiRadicalRepository(this)

        appScope.launch {
            DictionaryImporter(this@ChineseForMeApp, database).ensureLoaded()
            segmenter.ensureIndex()
        }
    }
}
