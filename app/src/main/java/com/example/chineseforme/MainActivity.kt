package com.example.chineseforme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.chineseforme.ui.ChineseForMeNav
import com.example.chineseforme.ui.theme.ChineseForMeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChineseForMeTheme {
                ChineseForMeNav()
            }
        }
    }
}
