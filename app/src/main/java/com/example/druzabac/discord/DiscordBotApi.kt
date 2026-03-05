package com.example.druzabac.discord

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Discord Bot API for managing event channels
 *
 * Setup instructions:
 * 1. Go to https://discord.com/developers/applications
 * 2. Create a new application
 * 3. Go to "Bot" section and create a bot
 * 4. Copy the bot token and paste it in BOT_TOKEN constant
 * 5. Enable these intents: SERVER MEMBERS INTENT, MESSAGE CONTENT INTENT
 * 6. Go to OAuth2 -> URL Generator
 * 7. Select scopes: "bot"
 * 8. Select permissions: "Manage Channels", "Manage Roles", "Send Messages"
 * 9. Copy the generated URL and add the bot to your Discord server
 * 10. Copy your Discord server (guild) ID and paste it in GUILD_ID constant
 */
object DiscordBotApi {
    private const val TAG = "DiscordBotApi"

    // TODO: Replace these with your actual values
    private const val BOT_TOKEN = "YOUR_DISCORD_BOT_TOKEN"
    const val GUILD_ID = "YOUR_DISCORD_GUILD_ID" // Made public for access from other classes
    private const val API_BASE = "https://discord.com/api/v10"

    // City channel IDs - map your city IDs to Discord channel IDs
    // Private event threads will be created in these channels
    private val CITY_CHANNELS = mapOf(
        "belgrade" to "YOUR_BELGRADE_CHANNEL_ID",
        "novi_sad" to "YOUR_NOVI_SAD_CHANNEL_ID",
        "kragujevac" to "YOUR_KRAGUJEVAC_CHANNEL_ID"
    )

    /**
     * Get Discord channel ID for a city
     */
    fun getChannelIdForCity(cityName: String): String? {
        val normalizedName = cityName.lowercase(java.util.Locale.ROOT).replace(" ", "_")
        Log.d(TAG, "Looking for channel: cityName='$cityName' -> normalized='$normalizedName'")
        val channelId = CITY_CHANNELS[normalizedName]
        if (channelId == null) {
            Log.e(TAG, "No channel ID found for normalized name: $normalizedName. Available: ${CITY_CHANNELS.keys}")
        } else {
            Log.d(TAG, "Found channel ID: $channelId for city: $cityName")
        }
        return channelId
    }


    /**
     * Create a PRIVATE thread for an event with the host in the city channel
     * Returns the thread ID if successful
     */
    suspend fun createPrivateEventThread(
        eventName: String,
        eventDate: String,
        eventTime: String,
        games: List<String>,
        hostName: String,
        hostDiscordId: String,
        location: String,
        cityName: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating PRIVATE event thread: cityName='$cityName', hostId='$hostDiscordId'")

            // Get city channel ID
            val channelId = getChannelIdForCity(cityName)
            if (channelId == null) {
                Log.e(TAG, "No Discord channel found for city: $cityName")
                return@withContext null
            }

            // Create thread name: "DD.MM - Game1, Game2"
            val threadName = "$eventDate - ${games.joinToString(", ").take(50)}"
            Log.d(TAG, "Creating PRIVATE thread in channel $channelId: $threadName")

            val url = URL("$API_BASE/channels/$channelId/threads")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bot $BOT_TOKEN")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val payload = JSONObject().apply {
                put("name", threadName.take(100)) // Max 100 chars
                put("type", 12) // 12 = PRIVATE_THREAD - only invited members can see
                put("auto_archive_duration", 10080) // 7 days
                put("invitable", false) // Only bot can invite members
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(response)
                val threadId = json.getString("id")

                Log.d(TAG, "Created PRIVATE thread: $threadId")

                // Send starter message with event details
                val starterMessage = buildString {
                    appendLine("🎮 **Event Details**")
                    appendLine()
                    appendLine("📅 **Date:** $eventDate")
                    appendLine("🕐 **Time:** $eventTime")
                    appendLine("🎲 **Games:** ${games.joinToString(", ")}")
                    appendLine("📍 **Location:** $location")
                    appendLine("🌆 **City:** $cityName")
                    appendLine("👤 **Host:** @$hostName")
                    appendLine()
                    appendLine("This is your private event thread. Accepted players will be added here!")
                }

                // Send starter message
                val messageSent = sendMessage(threadId, starterMessage)
                Log.d(TAG, "Starter message sent: $messageSent")

                // Add host to thread
                val hostAdded = addUserToThread(threadId, hostDiscordId)
                Log.d(TAG, "Host ($hostDiscordId) added to thread: $hostAdded")

                threadId
            } else {
                val error = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                Log.e(TAG, "Failed to create private thread: $responseCode - $error")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating private event thread", e)
            null
        }
    }

    /**
     * Add a user to a thread
     */
    suspend fun addUserToThread(threadId: String, userDiscordId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$API_BASE/channels/$threadId/thread-members/$userDiscordId")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "PUT"
            connection.setRequestProperty("Authorization", "Bot $BOT_TOKEN")
            connection.setRequestProperty("Content-Length", "0")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NO_CONTENT || responseCode == HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "Added user $userDiscordId to thread $threadId")
                true
            } else {
                val error = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                Log.e(TAG, "Failed to add user to thread: $responseCode - $error")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding user to thread", e)
            false
        }
    }

    /**
     * Generate invite link for a thread
     */
    suspend fun createThreadInvite(threadId: String): String? = withContext(Dispatchers.IO) {
        try {
            // For threads, we create a regular invite to the parent channel
            // Discord will redirect users to the thread
            val url = URL("$API_BASE/channels/$threadId/invites")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bot $BOT_TOKEN")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val payload = JSONObject().apply {
                put("max_age", 0) // Never expires
                put("max_uses", 0) // Unlimited uses
                put("unique", false)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(response)
                val code = json.getString("code")
                val inviteUrl = "https://discord.gg/$code"

                Log.d(TAG, "Created thread invite: $inviteUrl")
                inviteUrl
            } else {
                val error = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                Log.e(TAG, "Failed to create invite: $responseCode - $error")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating thread invite", e)
            null
        }
    }

    /**
     * Remove a user from a thread
     */
    suspend fun removeUserFromThread(threadId: String, userDiscordId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$API_BASE/channels/$threadId/thread-members/$userDiscordId")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Authorization", "Bot $BOT_TOKEN")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NO_CONTENT || responseCode == HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "Removed user $userDiscordId from thread $threadId")
                true
            } else {
                val error = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                Log.e(TAG, "Failed to remove user from thread: $responseCode - $error")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing user from thread", e)
            false
        }
    }

    /**
     * Archive/delete a thread
     */
    suspend fun deleteThread(threadId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$API_BASE/channels/$threadId")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Authorization", "Bot $BOT_TOKEN")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NO_CONTENT || responseCode == HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "Deleted thread $threadId")
                true
            } else {
                val error = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                Log.e(TAG, "Failed to delete thread: $responseCode - $error")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting thread", e)
            false
        }
    }

    /**
     * Send a message to a channel
     */
    suspend fun sendMessage(channelId: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$API_BASE/channels/$channelId/messages")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bot $BOT_TOKEN")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val payload = JSONObject().apply {
                put("content", content)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "Sent message to channel $channelId")
                true
            } else {
                val error = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                Log.e(TAG, "Failed to send message: $responseCode - $error")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            false
        }
    }

    /**
     * Create a DM channel with a user and start a thread
     * This creates a direct message thread between the bot and the user
     *
     * @param userId Discord user ID
     * @param threadName Name of the thread
     * @return Thread ID if successful, null otherwise
     */
    suspend fun createDMThreadWithUser(userId: String, threadName: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating DM thread with user: $userId, threadName: $threadName")

            // Step 1: Create DM channel with user
            val dmChannelId = createDMChannel(userId) ?: return@withContext null

            Log.d(TAG, "DM channel created: $dmChannelId")

            // Step 2: Create thread in DM channel
            val url = URL("$API_BASE/channels/$dmChannelId/threads")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bot $BOT_TOKEN")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val payload = JSONObject().apply {
                put("name", threadName.take(100))
                put("auto_archive_duration", 10080) // 7 days
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(response)
                val threadId = json.getString("id")

                Log.d(TAG, "DM thread created: $threadId")
                threadId
            } else {
                val error = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                Log.e(TAG, "Failed to create DM thread: $responseCode - $error")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating DM thread", e)
            null
        }
    }

    /**
     * Create a DM channel with a user
     *
     * @param userId Discord user ID
     * @return DM channel ID if successful, null otherwise
     */
    private suspend fun createDMChannel(userId: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$API_BASE/users/@me/channels")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bot $BOT_TOKEN")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val payload = JSONObject().apply {
                put("recipient_id", userId)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(response)
                json.getString("id")
            } else {
                val error = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                Log.e(TAG, "Failed to create DM channel: $responseCode - $error")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating DM channel", e)
            null
        }
    }

    suspend fun addUserToGuild(
        userDiscordId: String,
        userOAuthAccessToken: String,
        nick: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$API_BASE/guilds/$GUILD_ID/members/$userDiscordId")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "PUT"
            connection.setRequestProperty("Authorization", "Bot $BOT_TOKEN")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val payload = JSONObject().apply {
                put("access_token", userOAuthAccessToken)
                if (!nick.isNullOrBlank()) put("nick", nick)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = connection.responseCode
            when (code) {
                HttpURLConnection.HTTP_CREATED, HttpURLConnection.HTTP_NO_CONTENT, HttpURLConnection.HTTP_OK -> {
                    Log.d(TAG, "User $userDiscordId added to guild $GUILD_ID (code=$code)")
                    true
                }

                else -> {
                    val error =
                        connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                    Log.e(TAG, "Failed to add user to guild: $code - $error")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding user to guild", e)
            false
        }
    }
}

