package com.example.druzabac.auth

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Discord API helper for OAuth2 operations
 */
object DiscordApi {
    private const val TAG = "DiscordApi"

    data class TokenResponse(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Int,
        val tokenType: String
    )

    data class DiscordUser(
        val id: String,
        val username: String,
        val discriminator: String,
        val email: String?,
        val avatar: String?,
        val globalName: String?
    ) {
        fun getAvatarUrl(): String {
            return if (avatar != null) {
                "https://cdn.discordapp.com/avatars/$id/$avatar.png"
            } else {
                // Default Discord avatar
                val defaultAvatarIndex = (id.toLongOrNull() ?: 0) % 5
                "https://cdn.discordapp.com/embed/avatars/$defaultAvatarIndex.png"
            }
        }

        fun getDisplayName(): String {
            return globalName ?: username
        }
    }

    /**
     * Exchange authorization code for access token
     */
    suspend fun exchangeCodeForToken(code: String): TokenResponse? = withContext(Dispatchers.IO) {
        try {
            val url = URL(DiscordConfig.TOKEN_URL)
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true

            val postData = buildString {
                append("client_id=${DiscordConfig.CLIENT_ID}")
                append("&client_secret=${DiscordConfig.CLIENT_SECRET}")
                append("&grant_type=authorization_code")
                append("&code=$code")
                append("&redirect_uri=${DiscordConfig.REDIRECT_URI}")
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(postData)
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(response)

                TokenResponse(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.getString("refresh_token"),
                    expiresIn = json.getInt("expires_in"),
                    tokenType = json.getString("token_type")
                )
            } else {
                val error = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                Log.e(TAG, "Token exchange failed: $responseCode - $error")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange error", e)
            null
        }
    }

    /**
     * Get Discord user info using access token
     */
    suspend fun getUserInfo(accessToken: String): DiscordUser? = withContext(Dispatchers.IO) {
        try {
            val url = URL(DiscordConfig.USER_INFO_URL)
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(response)

                DiscordUser(
                    id = json.getString("id"),
                    username = json.getString("username"),
                    discriminator = json.optString("discriminator", "0"),
                    email = json.optString("email", null),
                    avatar = json.optString("avatar", null),
                    globalName = json.optString("global_name", null)
                )
            } else {
                val error = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                Log.e(TAG, "Get user info failed: $responseCode - $error")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get user info error", e)
            null
        }
    }

    /**
     * Refresh access token using refresh token
     */
    suspend fun refreshToken(refreshToken: String): TokenResponse? = withContext(Dispatchers.IO) {
        try {
            val url = URL(DiscordConfig.TOKEN_URL)
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true

            val postData = buildString {
                append("client_id=${DiscordConfig.CLIENT_ID}")
                append("&client_secret=${DiscordConfig.CLIENT_SECRET}")
                append("&grant_type=refresh_token")
                append("&refresh_token=$refreshToken")
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(postData)
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(response)

                TokenResponse(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.getString("refresh_token"),
                    expiresIn = json.getInt("expires_in"),
                    tokenType = json.getString("token_type")
                )
            } else {
                Log.e(TAG, "Token refresh failed: $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh error", e)
            null
        }
    }
}

