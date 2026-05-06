package com.example.financeapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onNavigateImport: () -> Unit,
    onNavigateDocumentList: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Finance App MVP")

        Button(
            onClick = onNavigateImport,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(text = "导入单据")
        }

        Button(
            onClick = onNavigateDocumentList,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text(text = "单据列表")
        }
    }
}
