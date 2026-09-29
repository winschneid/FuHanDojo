package com.winschneid.fuhandojo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.winschneid.fuhandojo.ui.navigation.NavGraph
import com.winschneid.fuhandojo.ui.theme.FuHanDojoTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FuHanDojoTheme {
                NavGraph()
            }
        }
    }
}
