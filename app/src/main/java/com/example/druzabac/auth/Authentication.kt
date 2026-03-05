package com.example.druzabac.auth

import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

class Authentication {

    companion object {
        private const val TAG = "Authentication"

        /**
         * Discord OAuth2 Login using WebView
         */
        @Composable
        fun DiscordAuthenticator(
            onLoginSuccess: () -> Unit,
            onLoginFailed: () -> Unit = {}
        ) {
            val context = LocalContext.current
            var launched by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                if (launched) return@LaunchedEffect
                launched = true

                try {
                    val intent = Intent(context, DiscordLoginActivity::class.java)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Discord auth failed", e)
                    onLoginFailed()
                }
            }
        }
    }
}
