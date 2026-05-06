package com.example.financeapp.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.financeapp.data.DocumentRepository
import com.example.financeapp.ui.document.DocumentDetailScreen
import com.example.financeapp.ui.document.DocumentListScreen
import com.example.financeapp.ui.document.DocumentViewModel
import com.example.financeapp.ui.document.DocumentViewModelFactory
import com.example.financeapp.ui.document.ImportScreen
import com.example.financeapp.ui.screen.HomeScreen

object Routes {
    const val HOME = "home"
    const val IMPORT = "import"
    const val DOCUMENT_LIST = "document_list"
    const val DOCUMENT_DETAIL = "document_detail/{documentId}"

    fun documentDetail(documentId: Long): String = "document_detail/$documentId"
}

@Composable
fun AppNavHost(
    repository: DocumentRepository
) {
    val navController = rememberNavController()
    val documentViewModel: DocumentViewModel = viewModel(
        factory = DocumentViewModelFactory(repository)
    )

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateImport = { navController.navigate(Routes.IMPORT) },
                onNavigateDocumentList = { navController.navigate(Routes.DOCUMENT_LIST) }
            )
        }
        composable(Routes.IMPORT) {
            ImportScreen(viewModel = documentViewModel)
        }
        composable(Routes.DOCUMENT_LIST) {
            DocumentListScreen(
                viewModel = documentViewModel,
                onOpenDetail = { id -> navController.navigate(Routes.documentDetail(id)) }
            )
        }
        composable(
            route = Routes.DOCUMENT_DETAIL,
            arguments = listOf(navArgument("documentId") { type = NavType.LongType })
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getLong("documentId") ?: -1L
            DocumentDetailScreen(documentId = documentId, viewModel = documentViewModel)
        }
    }
}
