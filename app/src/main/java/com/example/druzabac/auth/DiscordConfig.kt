package com.example.druzabac.auth

/**
 * Discord OAuth2 Configuration
 *
 * SETUP na Discord Developer Portal (https://discord.com/developers/applications):
 *
 * 1. Idi na https://discord.com/developers/applications
 * 2. Klikni "New Application" i daj ime (npr. "Druzabac")
 * 3. U levom meniju klikni "OAuth2"
 * 4. Pod "Client Information" vidiš:
 *    - CLIENT ID - kopiraj i stavi dole
 *    - CLIENT SECRET - klikni "Reset Secret", kopiraj i stavi dole
 * 5. Pod "Redirects" klikni "Add Redirect" i dodaj:
 *    http://127.0.0.1/callback
 * 6. Sačuvaj promene
 *
 * VAŽNO: CLIENT_SECRET čuvaj privatno! Za produkciju koristi backend server.
 */
object DiscordConfig {
    const val CLIENT_ID = "YOUR_DISCORD_CLIENT_ID"
    const val CLIENT_SECRET = "YOUR_DISCORD_CLIENT_SECRET"

    // Discord supports http://127.0.0.1 for native apps
    const val REDIRECT_URI = "http://localhost/authorize/callback"

    const val SCOPES = "identify email guilds.join"

    const val AUTHORIZE_URL = "https://discord.com/oauth2/authorize"
    const val TOKEN_URL = "https://discord.com/api/oauth2/token"
    const val USER_INFO_URL = "https://discord.com/api/users/@me"

    fun buildAuthUrl(): String {
        val encodedRedirect = java.net.URLEncoder.encode(REDIRECT_URI, "UTF-8")
        val encodedScopes = java.net.URLEncoder.encode(SCOPES, "UTF-8")
        return "$AUTHORIZE_URL?" +
                "client_id=$CLIENT_ID&" +
                "redirect_uri=$encodedRedirect&" +
                "response_type=code&" +
                "scope=$encodedScopes"
    }
}
