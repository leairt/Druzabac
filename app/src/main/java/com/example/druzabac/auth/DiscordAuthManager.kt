package com.example.druzabac.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.example.druzabac.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.druzabac.discord.DiscordBotApi


/**
 * Discord OAuth2 Authentication Manager
 * Handles login flow, token storage, and user session
 */
class DiscordAuthManager(private val context: Context) {
    companion object {
        private const val TAG = "DiscordAuthManager"
        private const val PREFS_NAME = "discord_auth"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_DISCORD_USER_ID = "discord_user_id"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"

        private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    sealed class AuthState {
        object Unknown : AuthState()
        object NotAuthenticated : AuthState()
        object Authenticating : AuthState()
        data class Authenticated(val discordUserId: String) : AuthState()
        data class Error(val message: String) : AuthState()
    }

    init {
        // Check if we have a stored token
        checkStoredAuth()
    }

    private fun checkStoredAuth() {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val discordUserId = prefs.getString(KEY_DISCORD_USER_ID, null)

        if (accessToken != null && discordUserId != null) {
            _authState.value = AuthState.Authenticated(discordUserId)
        } else {
            _authState.value = AuthState.NotAuthenticated
        }
    }

    /**
     * Start Discord OAuth2 login flow
     * Opens Discord authorization page in Custom Chrome Tab
     */
    fun startLogin() {
        _authState.value = AuthState.Authenticating

        val authUrl = DiscordConfig.buildAuthUrl()
        Log.d(TAG, "Starting Discord login: $authUrl")

        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()

            customTabsIntent.launchUrl(context, Uri.parse(authUrl))
        } catch (e: Exception) {
            // Fallback to regular browser if Custom Tabs not available
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(browserIntent)
        }
    }

    /**
     * Handle OAuth2 callback with authorization code
     */
    suspend fun handleAuthCode(code: String): Boolean {
        Log.d(TAG, "Handling auth code")
        _authState.value = AuthState.Authenticating

        // Exchange code for token
        val tokenResponse = DiscordApi.exchangeCodeForToken(code)
        if (tokenResponse == null) {
            _authState.value = AuthState.Error("Failed to exchange code for token")
            return false
        }

        // Get user info
        val discordUser = DiscordApi.getUserInfo(tokenResponse.accessToken)
        if (discordUser == null) {
            _authState.value = AuthState.Error("Failed to get user info")
            return false
        }

        // Store tokens and user info
        saveAuthData(tokenResponse, discordUser)

        // Ensure user is in Druzabac Discord server
        val joined = DiscordBotApi.addUserToGuild(
            userDiscordId = discordUser.id,
            userOAuthAccessToken = tokenResponse.accessToken,
            nick = discordUser.getDisplayName()
        )
        Log.d(TAG, "Guild join result for ${discordUser.id}: $joined")


        // Create User object for the app
        val user = User(
            id = discordUser.id,  // Using Discord user ID as app user ID
            email = discordUser.email ?: "",
            displayName = discordUser.getDisplayName(),
            givenName = discordUser.getDisplayName(),
            familyName = "",
            photoUrl = discordUser.getAvatarUrl(),
            username = discordUser.username,
            cityId = ""
        )

        UserSession.setUser(user)
        _authState.value = AuthState.Authenticated(discordUser.id)

        Log.d(TAG, "Discord login successful: ${discordUser.username}")
        return true
    }

    private fun saveAuthData(token: DiscordApi.TokenResponse, user: DiscordApi.DiscordUser) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, token.accessToken)
            .putString(KEY_REFRESH_TOKEN, token.refreshToken)
            .putString(KEY_DISCORD_USER_ID, user.id)
            .putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + (token.expiresIn * 1000L))
            .apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getDiscordUserId(): String? = prefs.getString(KEY_DISCORD_USER_ID, null)

    fun isLoggedIn(): Boolean {
        return prefs.getString(KEY_ACCESS_TOKEN, null) != null &&
               prefs.getString(KEY_DISCORD_USER_ID, null) != null
    }

    fun signOut() {
        prefs.edit().clear().apply()
        UserSession.clear()
        _authState.value = AuthState.NotAuthenticated
        Log.d(TAG, "Signed out")
    }

    /**
     * Refresh token if needed
     */
    suspend fun refreshTokenIfNeeded(): Boolean {
        val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return false

        // Refresh if token expires in less than 5 minutes
        if (System.currentTimeMillis() > expiry - 300000) {
            val newToken = DiscordApi.refreshToken(refreshToken)
            if (newToken != null) {
                prefs.edit()
                    .putString(KEY_ACCESS_TOKEN, newToken.accessToken)
                    .putString(KEY_REFRESH_TOKEN, newToken.refreshToken)
                    .putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + (newToken.expiresIn * 1000L))
                    .apply()
                return true
            }
            return false
        }
        return true
    }
}

