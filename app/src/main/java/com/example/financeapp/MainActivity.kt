package com.example.financeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.room.Room
import com.example.financeapp.data.DocumentRepository
import com.example.financeapp.db.AppDatabase
import com.example.financeapp.navigation.AppNavHost

class MainActivity : ComponentActivity() {

    private val db by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "finance_mvp.db"
        ).fallbackToDestructiveMigration().build()
    }

    private val repository by lazy {
        DocumentRepository(db.simpleRecordDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db.openHelper.writableDatabase

        setContent {
            MaterialTheme {
                Surface {
                    AppNavHost(repository = repository)
                }
            }
        }
    }
}
