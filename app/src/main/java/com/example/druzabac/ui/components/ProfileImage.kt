package com.example.druzabac.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A composable that can display both regular URLs and Base64 data URLs.
 * Automatically detects if the URL is a base64 data URL and handles it accordingly.
 */
@Composable
fun ProfileImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    contentScale: ContentScale = ContentScale.Crop
) {
    if (imageUrl.isNullOrEmpty()) {
        // Show default person icon
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = contentDescription,
                modifier = Modifier.size(size / 2),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    } else if (imageUrl.startsWith("data:image")) {
        // Base64 data URL
        var imageBitmap by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }
        var isLoading by remember(imageUrl) { mutableStateOf(true) }

        LaunchedEffect(imageUrl) {
            isLoading = true
            imageBitmap = withContext(Dispatchers.IO) {
                try {
                    // Extract base64 part from data URL
                    val base64Part = imageUrl.substringAfter("base64,")
                    val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    bitmap?.asImageBitmap()
                } catch (e: Exception) {
                    android.util.Log.e("ProfileImage", "Failed to decode base64: ${e.message}")
                    null
                }
            }
            isLoading = false
        }

        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap!!,
                contentDescription = contentDescription,
                modifier = modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = contentScale
            )
        } else {
            // Fallback to person icon if decoding failed
            Box(
                modifier = modifier
                    .size(size)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(size / 2),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    } else {
        // Regular URL - use Coil with fallback
        var imageLoadFailed by remember(imageUrl) { mutableStateOf(false) }

        if (imageLoadFailed) {
            // Show default person icon if image failed to load
            Box(
                modifier = modifier
                    .size(size)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(size / 2),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                modifier = modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = contentScale,
                onError = { imageLoadFailed = true }
            )
        }
    }
}

