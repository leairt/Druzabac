package com.example.druzabac.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Utility functions for Discord integration
 */
object DiscordUtils {

    /**
     * Opens a direct message (DM) with a Discord user
     *
     * @param context Android context
     * @param discordUserId Discord user ID (18-digit string)
     */
    fun openDiscordDM(context: Context, discordUserId: String) {
        try {
            // Discord DM deeplink format that works better
            // Format: discord://discordapp.com/users/{userId}
            val discordIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://discordapp.com/users/$discordUserId")
                // Try to force Discord app if installed
                setPackage("com.discord")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            try {
                // Try with Discord package specified
                context.startActivity(discordIntent)
            } catch (e: Exception) {
                // If that fails, try without package specification (opens in browser/app chooser)
                val genericIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://discordapp.com/users/$discordUserId")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(genericIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Could not open Discord chat. Make sure Discord is installed.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Opens a Discord thread by ID using https:// URL
     * This works better than discord:// deeplink and opens in browser if Discord is not installed
     *
     * @param context Android context
     * @param guildId Discord server (guild) ID
     * @param threadId Discord thread ID
     */
    fun openDiscordThread(context: Context, guildId: String, threadId: String) {
        val url = "https://discord.com/channels/$guildId/$threadId"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            // Optional, but often helps when launching from non-Activity contexts
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // If there is no handler (Discord not installed), it will open in browser
        context.startActivity(intent)
    }

    /**
     * Checks if Discord app is installed
     *
     * @param context Android context
     * @return true if Discord is installed, false otherwise
     */
    fun isDiscordInstalled(context: Context): Boolean {
        // Try multiple package names as Discord has different variants
        val discordPackages = listOf(
            "com.discord",           // Standard Discord
            "com.discord.canary",    // Discord Canary
            "com.discord.ptb"        // Discord PTB (Public Test Build)
        )

        return discordPackages.any { packageName ->
            try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Gets the installed Discord package name, if any
     */
    private fun getDiscordPackageName(context: Context): String? {
        val discordPackages = listOf(
            "com.discord",
            "com.discord.canary",
            "com.discord.ptb"
        )

        return discordPackages.firstOrNull { packageName ->
            try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Alternative method: Opens Discord and shows instructions to user
     * This is more reliable than trying to deeplink to DM
     */
    fun openDiscordWithInstructions(context: Context, username: String) {
        val discordPackage = getDiscordPackageName(context)

        android.util.Log.d("DiscordUtils", "Attempting to open Discord. Package found: $discordPackage")

        if (discordPackage != null) {
            try {
                // Method 1: Try to open Discord app via launch intent
                val launchIntent = context.packageManager.getLaunchIntentForPackage(discordPackage)
                if (launchIntent != null) {
                    android.util.Log.d("DiscordUtils", "Method 1: Using getLaunchIntentForPackage")
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    context.startActivity(launchIntent)

                    // Show instructions with a slight delay so Discord has time to open
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        Toast.makeText(
                            context,
                            "Search for @$username in Discord to send a message",
                            Toast.LENGTH_LONG
                        ).show()
                    }, 500)
                    android.util.Log.d("DiscordUtils", "Discord opened successfully via Method 1")
                    return
                }

                android.util.Log.d("DiscordUtils", "Method 1 failed, trying Method 2")

                // Method 2: Try opening via intent
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(discordPackage)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)

                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    Toast.makeText(
                        context,
                        "Search for @$username in Discord to send a message",
                        Toast.LENGTH_LONG
                    ).show()
                }, 500)

                android.util.Log.d("DiscordUtils", "Discord opened successfully via Method 2")

            } catch (e: Exception) {
                android.util.Log.e("DiscordUtils", "Methods 1&2 failed: ${e.message}", e)
                // Method 3: Try web fallback
                try {
                    android.util.Log.d("DiscordUtils", "Trying Method 3: Web fallback")
                    val webIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://discord.com/app")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(webIntent)

                    Toast.makeText(
                        context,
                        "Opening Discord web. Search for @$username",
                        Toast.LENGTH_LONG
                    ).show()
                    android.util.Log.d("DiscordUtils", "Opened Discord web successfully")
                } catch (e2: Exception) {
                    android.util.Log.e("DiscordUtils", "All methods failed: ${e2.message}", e2)
                    Toast.makeText(
                        context,
                        "Could not open Discord",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            android.util.Log.w("DiscordUtils", "Discord package not detected, trying alternative methods")

            // Try to find Discord by searching all installed apps
            val packageManager = context.packageManager
            val allApps = packageManager.getInstalledApplications(0)
            val discordApp = allApps.find { app ->
                app.packageName.contains("discord", ignoreCase = true)
            }

            if (discordApp != null) {
                // Found Discord with different package name
                android.util.Log.d("DiscordUtils", "Found Discord with package: ${discordApp.packageName}")
                try {
                    val launchIntent = packageManager.getLaunchIntentForPackage(discordApp.packageName)
                    if (launchIntent != null) {
                        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(launchIntent)

                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            Toast.makeText(
                                context,
                                "Discord opened! Search for @$username to send a message",
                                Toast.LENGTH_LONG
                            ).show()
                        }, 500)
                        return
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DiscordUtils", "Failed to open Discord: ${e.message}")
                }
            }

            // If we still can't find Discord, show helpful message without opening Play Store
            Toast.makeText(
                context,
                "To message @$username:\n1. Open Discord app\n2. Search for @$username\n3. Send message",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Debug function to check Discord installation status
     * Shows detailed info about which Discord version is installed
     */
    fun getDiscordInstallationInfo(context: Context): String {
        val discordPackages = listOf(
            "com.discord" to "Standard Discord",
            "com.discord.canary" to "Discord Canary",
            "com.discord.ptb" to "Discord PTB"
        )

        val installedPackages = discordPackages.mapNotNull { (packageName, name) ->
            try {
                val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
                "$name (v${packageInfo.versionName})"
            } catch (e: Exception) {
                null
            }
        }

        if (installedPackages.isNotEmpty()) {
            return "Found: ${installedPackages.joinToString(", ")}"
        }

        // Search through all installed apps for Discord
        try {
            val packageManager = context.packageManager
            val allApps = packageManager.getInstalledApplications(0)
            val discordApps = allApps.filter { app ->
                app.packageName.contains("discord", ignoreCase = true)
            }

            if (discordApps.isNotEmpty()) {
                val foundPackages = discordApps.joinToString(", ") { it.packageName }
                return "Found Discord with unusual package: $foundPackages"
            }
        } catch (e: Exception) {
            android.util.Log.e("DiscordUtils", "Error searching for Discord: ${e.message}")
        }

        return "No Discord app found. Please check if Discord is installed."
    }
}

