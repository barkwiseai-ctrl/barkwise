package com.petsocial.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.petsocial.app.ui.BarkWiseTheme
import com.petsocial.app.ui.PetSocialApp

class MainActivity : ComponentActivity() {
    private val deepLinkState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLinkState.value = intent?.dataString
        setContent {
            BarkWiseTheme {
                PetSocialApp(initialDeepLink = deepLinkState.value)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        deepLinkState.value = intent.dataString
    }
}
