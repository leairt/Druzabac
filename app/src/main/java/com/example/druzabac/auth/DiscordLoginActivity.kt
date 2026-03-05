package com.example.druzabac.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.example.druzabac.MainActivity
import com.example.druzabac.ui.theme.DruzabacTheme
import kotlinx.coroutines.launch

/**
 * Discord OAuth Login Activity using WebView
 * Intercepts the localhost redirect to get the auth code
 */
class DiscordLoginActivity : ComponentActivity() {
    companion object {
        private const val TAG = "DiscordLogin"
    }

    private var webView: WebView? = null

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val authUrl = DiscordConfig.buildAuthUrl()
        Log.d(TAG, "Auth URL: $authUrl")

        setContent {
            DruzabacTheme {
                var isLoading by remember { mutableStateOf(true) }
                var loadingProgress by remember { mutableStateOf(0) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Sign in with Discord") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }
                        )
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                WebView(context).apply {
                                    webView = this
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )

                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        databaseEnabled = true
                                        loadWithOverviewMode = true
                                        useWideViewPort = true
                                        builtInZoomControls = false
                                        displayZoomControls = false
                                        // Use desktop user agent for better Discord compatibility
                                        userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            val url = request?.url?.toString() ?: return false
                                            Log.d(TAG, "URL: $url")

                                                                                       // Intercept localhost/127.0.0.1 redirectss
                                            if (url.startsWith("discord-1468329659862225008://authorize") ||
                                                url.startsWith("http://localhost") ||
                                                url.startsWith("http://127.0.0.1")) {
                                                handleAuthCallback(url)
                                                return true
                                            }
                                            return false
                                        }

                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            super.onPageStarted(view, url, favicon)
                                            isLoading = true
                                            Log.d(TAG, "Page started: $url")

                                            // Also check here for redirect
                                            if (url?.startsWith("http://localhost") == true ||
                                                url?.startsWith("http://127.0.0.1") == true) {
                                                handleAuthCallback(url)
                                            }
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            isLoading = false
                                            Log.d(TAG, "Page finished: $url")
                                        }

                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: WebResourceError?
                                        ) {
                                            val url = request?.url?.toString() ?: ""
                                            Log.d(TAG, "Error for $url: ${error?.description}")

                                            // Localhost will error - that's expected, extract code from URL
                                            if (url.startsWith("http://localhost") ||
                                                url.startsWith("http://127.0.0.1")) {
                                                handleAuthCallback(url)
                                            }
                                        }
                                    }

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            loadingProgress = newProgress
                                            if (newProgress == 100) isLoading = false
                                        }
                                    }

                                    // Clear cookies for fresh login
                                    CookieManager.getInstance().removeAllCookies(null)

                                    loadUrl(authUrl)
                                }
                            }
                        )

                        // Loading indicator
                        if (isLoading) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                            ) {
                                LinearProgressIndicator(
                                    progress = { loadingProgress / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleAuthCallback(url: String) {
        Log.d(TAG, "Handling callback: $url")

        val uri = Uri.parse(url)

        // Check for error
        val error = uri.getQueryParameter("error")
        if (error != null) {
            val errorDesc = uri.getQueryParameter("error_description") ?: error
            Log.e(TAG, "Auth error: $errorDesc")
            Toast.makeText(this, "Login failed: $errorDesc", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Get authorization code
        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            Log.e(TAG, "No auth code in URL: $url")
            Toast.makeText(this, "Login failed: No authorization code", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d(TAG, "Got auth code, exchanging for token...")

        // Exchange code for token
        lifecycleScope.launch {
            try {
                val authManager = DiscordAuthManager(applicationContext)
                val success = authManager.handleAuthCode(code)

                if (success) {
                    Log.d(TAG, "Login successful!")
                    Toast.makeText(this@DiscordLoginActivity, "Login successful!", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e(TAG, "Token exchange failed")
                    Toast.makeText(this@DiscordLoginActivity, "Login failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login error", e)
                Toast.makeText(this@DiscordLoginActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }

            // Navigate back to MainActivity
            val intent = Intent(this@DiscordLoginActivity, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        webView?.destroy()
        super.onDestroy()
    }
}

