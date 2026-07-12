package com.example.chineseforme.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.chineseforme.ChineseForMeApp
import com.example.chineseforme.domain.model.StudyMode
import com.example.chineseforme.ui.library.LibraryScreen
import com.example.chineseforme.ui.library.LibraryViewModel
import com.example.chineseforme.ui.modes.ModePlaceholderScreen
import com.example.chineseforme.ui.reader.ReaderScreen
import com.example.chineseforme.ui.reader.ReaderViewModel
import com.example.chineseforme.ui.settings.SettingsScreen
import com.example.chineseforme.ui.study.StudyScreen
import com.example.chineseforme.ui.study.StudyViewModel

object Routes {
    const val Library = "library"
    const val Settings = "settings"
    /** Shared sentence picker for every study mode. */
    const val Select = "select/{workId}/{mode}"
    const val Study = "study/{workId}/{sentenceId}"
    const val Memorize = "memorize/{workId}/{sentenceId}"
    const val Stroke = "stroke/{workId}/{sentenceId}"

    fun select(workId: Long, mode: StudyMode) = "select/$workId/${mode.routeKey}"
    fun study(workId: Long, sentenceId: Long) = "study/$workId/$sentenceId"
    fun memorize(workId: Long, sentenceId: Long) = "memorize/$workId/$sentenceId"
    fun stroke(workId: Long, sentenceId: Long) = "stroke/$workId/$sentenceId"
}

@Composable
fun ChineseForMeNav() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as ChineseForMeApp
    val scope = rememberCoroutineScope()

    NavHost(navController = navController, startDestination = Routes.Library) {
        composable(Routes.Library) {
            val vm: LibraryViewModel = viewModel(
                factory = LibraryViewModel.Factory(
                    app.textRepository,
                    app.documentExtractor,
                    app.bundledTextCatalog,
                    app.settingsRepository
                )
            )
            LibraryScreen(
                viewModel = vm,
                onOpenMode = { workId, mode ->
                    navController.navigate(Routes.select(workId, mode))
                },
                onOpenSettings = { navController.navigate(Routes.Settings) }
            )
        }
        composable(Routes.Settings) {
            SettingsScreen(
                settingsRepository = app.settingsRepository,
                scope = scope,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Routes.Select,
            arguments = listOf(
                navArgument("workId") { type = NavType.LongType },
                navArgument("mode") { type = NavType.StringType }
            )
        ) { entry ->
            val workId = entry.arguments!!.getLong("workId")
            val mode = StudyMode.fromRoute(entry.arguments!!.getString("mode")!!)
            val vm: ReaderViewModel = viewModel(
                key = "select-$workId-${mode.routeKey}",
                factory = ReaderViewModel.Factory(workId, app.textRepository)
            )
            ReaderScreen(
                viewModel = vm,
                mode = mode,
                onBack = { navController.popBackStack() },
                onOpenSentence = { sentenceId, _ ->
                    val dest = when (mode) {
                        StudyMode.Standard -> Routes.study(workId, sentenceId)
                        StudyMode.Memorize -> Routes.memorize(workId, sentenceId)
                        StudyMode.Stroke -> Routes.stroke(workId, sentenceId)
                    }
                    navController.navigate(dest)
                }
            )
        }
        composable(
            Routes.Study,
            arguments = listOf(
                navArgument("workId") { type = NavType.LongType },
                navArgument("sentenceId") { type = NavType.LongType }
            )
        ) { entry ->
            val workId = entry.arguments!!.getLong("workId")
            val sentenceId = entry.arguments!!.getLong("sentenceId")
            val vm: StudyViewModel = viewModel(
                factory = StudyViewModel.Factory(
                    sentenceId = sentenceId,
                    workId = workId,
                    textRepository = app.textRepository,
                    analyzer = app.analyzer,
                    notionalTranslationService = app.notionalTranslationService,
                    settingsRepository = app.settingsRepository
                )
            )
            StudyScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Routes.Memorize,
            arguments = listOf(
                navArgument("workId") { type = NavType.LongType },
                navArgument("sentenceId") { type = NavType.LongType }
            )
        ) { entry ->
            val sentenceId = entry.arguments!!.getLong("sentenceId")
            var preview by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(sentenceId) {
                preview = app.textRepository.getSentence(sentenceId)?.text
            }
            ModePlaceholderScreen(
                title = "Memorize",
                blurb = "Fill characters with configurable hints. Practice will use this selected sentence.",
                sentencePreview = preview,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Routes.Stroke,
            arguments = listOf(
                navArgument("workId") { type = NavType.LongType },
                navArgument("sentenceId") { type = NavType.LongType }
            )
        ) { entry ->
            val sentenceId = entry.arguments!!.getLong("sentenceId")
            var preview by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(sentenceId) {
                preview = app.textRepository.getSentence(sentenceId)?.text
            }
            ModePlaceholderScreen(
                title = "Stroke practice",
                blurb = "Large stroke canvas with pinyin and gloss helpers for characters in this sentence.",
                sentencePreview = preview,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
