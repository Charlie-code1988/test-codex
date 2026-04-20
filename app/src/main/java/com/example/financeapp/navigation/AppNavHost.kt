package com.example.financeapp.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.financeapp.data.DocumentRepository
import com.example.financeapp.ui.document.DocumentListScreen
import com.example.financeapp.ui.document.DocumentViewModel
import com.example.financeapp.ui.document.DocumentViewModelFactory
import com.example.financeapp.ui.document.ImportScreen
import com.example.financeapp.ui.screen.HomeScreen

object Routes {
    const val HOME = "home"
    const val IMPORT = "import"
    const val DOCUMENT_LIST = "document_list"
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
            DocumentListScreen(viewModel = documentViewModel)
        }
    }
}
