package com.example.chineseforme.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.chineseforme.ChineseForMeApp
import com.example.chineseforme.ui.library.LibraryScreen
import com.example.chineseforme.ui.library.LibraryViewModel
import com.example.chineseforme.ui.reader.ReaderScreen
import com.example.chineseforme.ui.reader.ReaderViewModel
import com.example.chineseforme.ui.settings.SettingsScreen
import com.example.chineseforme.ui.study.StudyScreen
import com.example.chineseforme.ui.study.StudyViewModel

object Routes {
    const val Library = "library"
    const val Settings = "settings"
    const val Reader = "reader/{workId}"
    const val Study = "study/{workId}/{sentenceId}"

    fun reader(workId: Long) = "reader/$workId"
    fun study(workId: Long, sentenceId: Long) = "study/$workId/$sentenceId"
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
                    app.bundledTextCatalog
                )
            )
            LibraryScreen(
                viewModel = vm,
                onOpenWork = { id -> navController.navigate(Routes.reader(id)) },
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
            Routes.Reader,
            arguments = listOf(navArgument("workId") { type = NavType.LongType })
        ) { entry ->
            val workId = entry.arguments!!.getLong("workId")
            val vm: ReaderViewModel = viewModel(
                factory = ReaderViewModel.Factory(workId, app.textRepository)
            )
            ReaderScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenSentence = { sentenceId, _ ->
                    navController.navigate(Routes.study(workId, sentenceId))
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
    }
}
