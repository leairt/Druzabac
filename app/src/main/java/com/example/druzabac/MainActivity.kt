package com.example.druzabac

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import com.example.druzabac.auth.*
import com.example.druzabac.db.*
import com.example.druzabac.ui.screens.*
import com.example.druzabac.ui.theme.DruzabacTheme
import com.example.druzabac.ui.theme.ThemeManager

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val authRepo = LocalAuthRepository(this)
        val discordAuthManager = DiscordAuthManager(this)

        // Initialize ThemeManager
        ThemeManager.init(this)

        // UserSession <-> SharedPrefs sync
        UserSession.init { user ->
            if (user == null) {
                authRepo.signOut()
            } else {
                authRepo.cacheUser(user)
            }
        }

        // Restore local user
        authRepo.currentUser?.let { UserSession.setUser(it) }

        setContent {
            val userDb = remember { UserDatabase() }

            // Observe theme state
            val isDarkTheme by ThemeManager.isDarkTheme

            // Observe UserSession state
            val currentUser = UserSession.userInfo

            // Check Discord auth state
            val authState by discordAuthManager.authState.collectAsState()
            val isLoggedIn = currentUser != null || authState is DiscordAuthManager.AuthState.Authenticated

            // App flow state
            var showCityGate by remember { mutableStateOf(false) }
            var isCheckingDb by remember { mutableStateOf(false) }

            // Helper: full sign out
            fun fullSignOut() {
                discordAuthManager.signOut()
                UserSession.clear()
                showCityGate = false
            }

            // Check user in DB when logged in
            LaunchedEffect(currentUser?.id, authState) {
                val userId = currentUser?.id
                    ?: (authState as? DiscordAuthManager.AuthState.Authenticated)?.discordUserId

                if (userId == null) {
                    showCityGate = false
                    return@LaunchedEffect
                }

                isCheckingDb = true
                val dbUser = userDb.getById(userId)
                if (dbUser == null) {
                    showCityGate = true
                } else {
                    UserSession.setUser(dbUser)
                    showCityGate = dbUser.cityId.isBlank()
                }
                isCheckingDb = false
            }


            DruzabacTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        !isLoggedIn -> {
                            var startLogin by remember { mutableStateOf(false) }

                            StartScreen(
                                onContinueWithDiscord = { startLogin = true }
                            )

                            if (startLogin) {
                                Authentication.DiscordAuthenticator(
                                    onLoginSuccess = { startLogin = false },
                                    onLoginFailed = { startLogin = false }
                                )
                            }
                        }



                        isCheckingDb -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }

                        showCityGate -> {
                            CityGateScreen(
                                onEnabledCityComplete = {
                                    // Nakon izbora enabled grada, CityGate kreira user doc i setuje session.
                                    showCityGate = false
                                },
                                onBackToStart = {
                                    // Ako grad nije dostupan -> vrati na start screen
                                    fullSignOut()
                                }
                            )
                        }

                        else -> {
                            // MAIN APP
                            MainNavigationScreen(
                                onSignOut = { fullSignOut() }
                            )
                        }
                    }
                }
            }
        }
    }
}
