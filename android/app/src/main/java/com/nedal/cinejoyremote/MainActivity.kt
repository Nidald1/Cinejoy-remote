package com.nedal.cinejoyremote

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nedal.cinejoyremote.ui.CinejoyApp
import com.nedal.cinejoyremote.ui.theme.CinejoyTheme
import com.nedal.cinejoyremote.ui.theme.ThemeController

class CinejoyApplication : Application()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeController.load(applicationContext)
        setContent {
            CinejoyTheme(ThemeController.mode) { CinejoyApp() }
        }
    }
}
