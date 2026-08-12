package com.example

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.screens.SecurityScannerScreen
import com.example.ui.theme.MEDUSAALFHATheme
import com.example.ui.theme.NavyDark

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MEDUSAALFHATheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NavyDark
                ) {
                    SecurityScannerScreen()
                }
            }
        }
    }
}
