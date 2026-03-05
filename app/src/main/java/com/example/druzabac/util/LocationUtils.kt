package com.example.druzabac.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.text.Normalizer
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

data class GeocodeSuggestion(
    val title: String,
    val subtitle: String?,
    val latitude: Double,
    val longitude: Double,
    val fullAddress: String? = null
)

suspend fun geocodeAddress(context: Context, query: String): GeoPoint? {
    if (query.isBlank()) return null
    // 0) Try OpenStreetMap Nominatim first (no API key required)
    nominatimSearch(query, 1).firstOrNull()?.let { return GeoPoint(it.latitude, it.longitude) }
    // 1) Prefer Google Geocoding API (stable on emulators)
    googleGeocode(context, query, 1).firstOrNull()?.let { return GeoPoint(it.latitude, it.longitude) }

    // 2) Fallback to device geocoder
    return if (!Geocoder.isPresent()) {
        null
    } else {
        try {
            val geocoder = Geocoder(context)
            withTimeoutOrNull(3500) {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    val first = geocoder.getFromLocationName(query, 1)?.firstOrNull()
                    if (first != null) GeoPoint(first.latitude, first.longitude) else null
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}

suspend fun geocodeAddressWithFallback(
    context: Context,
    queries: List<String>
): GeoPoint? {
    val expanded = expandQueryVariants(queries)
    for (q in expanded) {
        val point = geocodeAddress(context, q)
        if (point != null) return point
    }
    return null
}

fun openInGoogleMaps(context: Context, latitude: Double?, longitude: Double?, label: String) {
    val uri = if (latitude != null && longitude != null) {
        Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(label)})")
    } else {
        Uri.parse("geo:0,0?q=${Uri.encode(label)}")
    }
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.google.android.apps.maps")
    }
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}

suspend fun searchGeocodeSuggestions(
    context: Context,
    query: String,
    maxResults: Int = 5
): List<GeocodeSuggestion> {
    if (query.isBlank() || query.length < 3) return emptyList()
    // 0) Try OpenStreetMap Nominatim first (no API key required)
    val osm = nominatimSearch(query, maxResults)
    if (osm.isNotEmpty()) return osm
    // 1) Prefer Google Geocoding API
    val google = googleGeocode(context, query, maxResults)
    if (google.isNotEmpty()) return google

    // 2) Fallback to device geocoder
    if (!Geocoder.isPresent()) return emptyList()
    return try {
        val geocoder = Geocoder(context)
        val addresses = withTimeoutOrNull(3500) {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, maxResults) ?: emptyList()
            }
        } ?: emptyList()

        addresses
            .mapNotNull { it.toGeocodeSuggestionOrNull() }
            .distinctBy { "${it.title}|${it.subtitle}|${it.latitude}|${it.longitude}" }
    } catch (_: Exception) {
        emptyList()
    }
}

private suspend fun nominatimSearch(
    query: String,
    maxResults: Int
): List<GeocodeSuggestion> = withContext(Dispatchers.IO) {
    try {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://nominatim.openstreetmap.org/search?format=jsonv2&q=$encoded&limit=$maxResults&addressdetails=1")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3500
            readTimeout = 3500
            setRequestProperty("User-Agent", "Druzabac/1.0 (Android)")
            setRequestProperty("Accept-Language", "sr,en")
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val arr = org.json.JSONArray(body)
        val out = mutableListOf<GeocodeSuggestion>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val displayName = item.optString("display_name")
            val lat = item.optString("lat").toDoubleOrNull()
            val lon = item.optString("lon").toDoubleOrNull()
            if (displayName.isBlank() || lat == null || lon == null) continue
            val address = item.optJSONObject("address")
            val title = buildPrettyTitle(address, displayName)
            val subtitle = buildPrettySubtitle(address, displayName)
            out += GeocodeSuggestion(
                title = toLatin(title),
                subtitle = subtitle?.let { toLatin(it) },
                latitude = lat,
                longitude = lon,
                fullAddress = toLatin(displayName)
            )
        }
        out.distinctBy { "${it.title}|${it.subtitle}|${it.latitude}|${it.longitude}" }.take(maxResults)
    } catch (e: Exception) {
        Log.w("LocationUtils", "Nominatim search failed: ${e.message}")
        emptyList()
    }
}

suspend fun searchGeocodeSuggestionsWithFallback(
    context: Context,
    query: String,
    maxResults: Int = 5
): List<GeocodeSuggestion> {
    val primary = searchGeocodeSuggestions(context, query, maxResults)
    if (primary.isNotEmpty()) return primary
    val relaxedQuery = query.substringBefore(",").trim()
    if (relaxedQuery.length < 3 || relaxedQuery == query.trim()) return primary
    return searchGeocodeSuggestions(context, relaxedQuery, maxResults)
}

suspend fun searchGeocodeSuggestionsMulti(
    context: Context,
    queries: List<String>,
    maxResults: Int = 6
): List<GeocodeSuggestion> {
    val expanded = expandQueryVariants(queries)
    val merged = mutableListOf<GeocodeSuggestion>()
    for (q in expanded) {
        val suggestions = searchGeocodeSuggestionsWithFallback(context, q, maxResults)
        if (suggestions.isNotEmpty()) {
            merged += suggestions
            if (merged.size >= maxResults) break
        }
    }
    return merged
        .distinctBy { "${it.title}|${it.subtitle}|${it.latitude}|${it.longitude}" }
        .take(maxResults)
}

private fun expandQueryVariants(queries: List<String>): List<String> {
    val out = linkedSetOf<String>()
    queries
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { q ->
            out += q
            // Add diacritics-expanded variant (c→č/ć, s→š, z→ž, dj→đ)
            val withDiacritics = addSerbianDiacritics(q)
            if (withDiacritics != q) out += withDiacritics
            // City name swaps
            out += q.replace("Belgrade", "Beograd", ignoreCase = true)
            out += q.replace("Beograd", "Belgrade", ignoreCase = true)
            out += q.replace("Serbia", "Srbija", ignoreCase = true)
            out += q.replace("Srbija", "Serbia", ignoreCase = true)
            // Also expand the diacritics variant with city swaps
            if (withDiacritics != q) {
                out += withDiacritics.replace("Belgrade", "Beograd", ignoreCase = true)
                out += withDiacritics.replace("Beograd", "Belgrade", ignoreCase = true)
            }
        }
    return out.toList()
}

/**
 * Expand ASCII Serbian to proper diacritics.
 * Handles: c→č, c→ć, s→š, z→ž, dj→đ (common patterns).
 * Uses common Serbian word patterns to pick the right replacement.
 */
private fun addSerbianDiacritics(input: String): String {
    var result = input
    // dj/Dj → đ/Đ (must be before individual letter replacements)
    result = result.replace("dj", "đ").replace("Dj", "Đ").replace("DJ", "Đ")
    // Common Serbian patterns — apply the most likely diacritics
    // š: s before/after vowels in typical positions
    result = result
        .replace(Regex("(?i)s(?=t[aeiouAEIOU])")) { if (it.value[0].isUpperCase()) "Š" else "š" }  // šta, što
    // For remaining ambiguous cases, try the most common replacements
    // We'll generate the most likely single variant
    val sb = StringBuilder()
    var i = 0
    while (i < result.length) {
        val ch = result[i]
        val lower = ch.lowercaseChar()
        val isUpper = ch.isUpperCase()
        when {
            // Already handled diacritics
            lower == 'č' || lower == 'ć' || lower == 'š' || lower == 'ž' || lower == 'đ' -> {
                sb.append(ch)
            }
            lower == 'c' && i + 1 < result.length && result[i + 1].lowercaseChar() == 'h' -> {
                // ch is not a Serbian pattern, keep as is
                sb.append(ch)
            }
            lower == 'c' -> {
                // In Serbian, standalone 'c' at word boundaries is often 'c' (centar),
                // but before 'e','i' it could be 'c' or 'č'. We'll add 'č' as it's more common in place names
                val next = if (i + 1 < result.length) result[i + 1].lowercaseChar() else ' '
                val prev = if (i > 0) result[i - 1].lowercaseChar() else ' '
                val replacement = when {
                    // 'ica', 'aca', 'uce' endings → keep c
                    next == 'a' && prev == 'i' -> 'c'
                    next == 'a' && prev == 'a' -> 'c'
                    next == 'e' && prev == 'u' -> 'c'
                    // 'ce' at end of word → often c (not č)
                    next == 'e' && (i + 2 >= result.length || !result[i + 2].isLetter()) -> 'c'
                    else -> 'c' // Keep as c — we'll rely on the API being fuzzy
                }
                sb.append(if (isUpper) replacement.uppercaseChar() else replacement)
            }
            else -> sb.append(ch)
        }
        i++
    }
    return sb.toString()
}

/**
 * Strip all diacritics to plain ASCII for fuzzy comparison.
 * č,ć → c, š → s, ž → z, đ → dj
 */
fun stripDiacritics(input: String): String {
    val sb = StringBuilder(input.length)
    input.forEach { ch ->
        when (ch) {
            'č', 'ć' -> sb.append('c')
            'Č', 'Ć' -> sb.append('C')
            'š' -> sb.append('s')
            'Š' -> sb.append('S')
            'ž' -> sb.append('z')
            'Ž' -> sb.append('Z')
            'đ' -> sb.append("dj")
            'Đ' -> sb.append("Dj")
            else -> {
                // Also handle accented Latin chars (from Nominatim etc.)
                val normalized = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD)
                    .replace("\\p{M}".toRegex(), "")
                sb.append(normalized)
            }
        }
    }
    return sb.toString()
}

private fun Address.toGeocodeSuggestionOrNull(): GeocodeSuggestion? {
    val line = getAddressLine(0)?.trim().orEmpty()
    val feature = featureName?.trim().orEmpty()
    val localityText = listOfNotNull(
        locality?.takeIf { it.isNotBlank() },
        adminArea?.takeIf { it.isNotBlank() },
        countryName?.takeIf { it.isNotBlank() }
    ).joinToString(", ").ifBlank { null }

    val title = when {
        feature.isNotBlank() -> feature
        line.isNotBlank() -> line
        else -> return null
    }
    val subtitle = if (feature.isNotBlank() && line.isNotBlank() && line != feature) {
        line
    } else {
        localityText
    }

    return GeocodeSuggestion(
        title = toLatin(title),
        subtitle = subtitle?.let { toLatin(it) },
        latitude = latitude,
        longitude = longitude,
        fullAddress = line.ifBlank { null }?.let { toLatin(it) }
    )
}

private suspend fun googleGeocode(
    context: Context,
    query: String,
    maxResults: Int
): List<GeocodeSuggestion> = withContext(Dispatchers.IO) {
    val apiKey = getMapsApiKey(context)
    if (apiKey.isNullOrBlank()) return@withContext emptyList()

    try {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://maps.googleapis.com/maps/api/geocode/json?address=$encoded&language=en&key=$apiKey")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3500
            readTimeout = 3500
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        val status = json.optString("status")
        if (status != "OK" && status != "ZERO_RESULTS") {
            Log.w("LocationUtils", "Google geocode status=$status query=$query")
            return@withContext emptyList()
        }

        val results = json.optJSONArray("results") ?: return@withContext emptyList()
        val out = mutableListOf<GeocodeSuggestion>()
        for (i in 0 until minOf(results.length(), maxResults)) {
            val item = results.optJSONObject(i) ?: continue
            val formatted = item.optString("formatted_address")
            val geometry = item.optJSONObject("geometry")
            val loc = geometry?.optJSONObject("location")
            val lat = loc?.optDouble("lat")
            val lng = loc?.optDouble("lng")
            if (formatted.isBlank() || lat == null || lng == null || lat.isNaN() || lng.isNaN()) continue

            val title = formatted.substringBefore(",").ifBlank { formatted }
            val subtitle = formatted.substringAfter(",", "").trim().ifBlank { null }
            out += GeocodeSuggestion(
                title = toLatin(title),
                subtitle = subtitle?.let { toLatin(it) },
                latitude = lat,
                longitude = lng,
                fullAddress = toLatin(formatted)
            )
        }
        out.distinctBy { "${it.title}|${it.subtitle}|${it.latitude}|${it.longitude}" }
    } catch (e: Exception) {
        Log.w("LocationUtils", "Google geocode failed: ${e.message}")
        emptyList()
    }
}

private fun getMapsApiKey(context: Context): String? {
    return try {
        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
        appInfo.metaData?.getString("com.google.android.geo.API_KEY")
    } catch (_: Exception) {
        null
    }
}

/**
 * Google Places Autocomplete — best for finding businesses/cafés by name.
 * This is what Google Maps itself uses when you type.
 */
private suspend fun googlePlacesAutocomplete(
    context: Context,
    query: String,
    maxResults: Int = 6,
    locationBias: String? = null
): List<GeocodeSuggestion> = withContext(Dispatchers.IO) {
    val apiKey = getMapsApiKey(context)
    if (apiKey.isNullOrBlank()) return@withContext emptyList()

    try {
        val encoded = URLEncoder.encode(query, "UTF-8")
        var urlStr = "https://maps.googleapis.com/maps/api/place/autocomplete/json?input=$encoded&language=sr&key=$apiKey"
        if (!locationBias.isNullOrBlank()) {
            urlStr += "&location=$locationBias&radius=50000"
        }
        val url = URL(urlStr)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4000
            readTimeout = 4000
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        val status = json.optString("status")
        Log.d("LocationUtils", "Places Autocomplete status=$status for query=$query")
        if (status != "OK") return@withContext emptyList()

        val predictions = json.optJSONArray("predictions") ?: return@withContext emptyList()
        val out = mutableListOf<GeocodeSuggestion>()
        for (i in 0 until minOf(predictions.length(), maxResults)) {
            val item = predictions.optJSONObject(i) ?: continue
            val placeId = item.optString("place_id")
            val description = item.optString("description")
            val structured = item.optJSONObject("structured_formatting")
            val mainText = structured?.optString("main_text") ?: description.substringBefore(",")
            val secondaryText = structured?.optString("secondary_text")
            if (description.isBlank() || placeId.isBlank()) continue

            // Get coordinates via Place Details
            val coords = getPlaceDetails(apiKey, placeId)
            if (coords != null) {
                out += GeocodeSuggestion(
                    title = toLatin(mainText),
                    subtitle = secondaryText?.let { toLatin(it) } ?: toLatin(description.substringAfter(",", "").trim()),
                    latitude = coords.first,
                    longitude = coords.second,
                    fullAddress = toLatin(description)
                )
            }
        }
        out
    } catch (e: Exception) {
        Log.w("LocationUtils", "Places Autocomplete failed: ${e.message}")
        emptyList()
    }
}

/**
 * Get lat/lng for a place_id via Google Place Details API.
 */
private suspend fun getPlaceDetails(
    apiKey: String,
    placeId: String
): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://maps.googleapis.com/maps/api/place/details/json?place_id=$placeId&fields=geometry&key=$apiKey")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3000
            readTimeout = 3000
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        if (json.optString("status") != "OK") return@withContext null
        val loc = json.optJSONObject("result")?.optJSONObject("geometry")?.optJSONObject("location")
        val lat = loc?.optDouble("lat")
        val lng = loc?.optDouble("lng")
        if (lat != null && lng != null && !lat.isNaN() && !lng.isNaN()) lat to lng else null
    } catch (e: Exception) {
        Log.w("LocationUtils", "Place Details failed: ${e.message}")
        null
    }
}

/**
 * Google Places Text Search — finds businesses, cafés, restaurants, etc. by name.
 */
suspend fun googlePlacesSearch(
    context: Context,
    query: String,
    maxResults: Int = 6,
    locationBias: String? = null
): List<GeocodeSuggestion> = withContext(Dispatchers.IO) {
    val apiKey = getMapsApiKey(context)
    if (apiKey.isNullOrBlank()) return@withContext emptyList()

    try {
        val encoded = URLEncoder.encode(query, "UTF-8")
        var urlStr = "https://maps.googleapis.com/maps/api/place/textsearch/json?query=$encoded&language=sr&key=$apiKey"
        if (!locationBias.isNullOrBlank()) {
            urlStr += "&location=$locationBias&radius=50000"
        }
        val url = URL(urlStr)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4000
            readTimeout = 4000
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        val status = json.optString("status")
        Log.d("LocationUtils", "Places TextSearch status=$status for query=$query")
        if (status != "OK" && status != "ZERO_RESULTS") {
            return@withContext emptyList()
        }

        val results = json.optJSONArray("results") ?: return@withContext emptyList()
        val out = mutableListOf<GeocodeSuggestion>()
        for (i in 0 until minOf(results.length(), maxResults)) {
            val item = results.optJSONObject(i) ?: continue
            val name = item.optString("name")
            val formatted = item.optString("formatted_address")
            val geometry = item.optJSONObject("geometry")
            val loc = geometry?.optJSONObject("location")
            val lat = loc?.optDouble("lat")
            val lng = loc?.optDouble("lng")
            if (name.isBlank() || lat == null || lng == null || lat.isNaN() || lng.isNaN()) continue

            out += GeocodeSuggestion(
                title = toLatin(name),
                subtitle = toLatin(formatted),
                latitude = lat,
                longitude = lng,
                fullAddress = toLatin(formatted)
            )
        }
        out.distinctBy { "${it.title}|${it.latitude}|${it.longitude}" }
    } catch (e: Exception) {
        Log.w("LocationUtils", "Google Places search failed: ${e.message}")
        emptyList()
    }
}

/**
 * Google Find Place — finds a single best-matching place by name.
 * Simpler API that may be enabled even when Text Search isn't.
 */
private suspend fun googleFindPlace(
    context: Context,
    query: String
): List<GeocodeSuggestion> = withContext(Dispatchers.IO) {
    val apiKey = getMapsApiKey(context)
    if (apiKey.isNullOrBlank()) return@withContext emptyList()

    try {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://maps.googleapis.com/maps/api/place/findplacefromtext/json?input=$encoded&inputtype=textquery&fields=name,formatted_address,geometry&language=sr&key=$apiKey")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4000
            readTimeout = 4000
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        val status = json.optString("status")
        Log.d("LocationUtils", "FindPlace status=$status for query=$query")
        if (status != "OK") return@withContext emptyList()

        val candidates = json.optJSONArray("candidates") ?: return@withContext emptyList()
        val out = mutableListOf<GeocodeSuggestion>()
        for (i in 0 until candidates.length()) {
            val item = candidates.optJSONObject(i) ?: continue
            val name = item.optString("name")
            val formatted = item.optString("formatted_address")
            val geometry = item.optJSONObject("geometry")
            val loc = geometry?.optJSONObject("location")
            val lat = loc?.optDouble("lat")
            val lng = loc?.optDouble("lng")
            if (name.isBlank() || lat == null || lng == null || lat.isNaN() || lng.isNaN()) continue
            out += GeocodeSuggestion(
                title = toLatin(name),
                subtitle = toLatin(formatted),
                latitude = lat,
                longitude = lng,
                fullAddress = toLatin(formatted)
            )
        }
        out
    } catch (e: Exception) {
        Log.w("LocationUtils", "FindPlace failed: ${e.message}")
        emptyList()
    }
}

/**
 * Nominatim POI/amenity search — searches for businesses and places of interest.
 */
private suspend fun nominatimPoiSearch(
    query: String,
    maxResults: Int
): List<GeocodeSuggestion> = withContext(Dispatchers.IO) {
    try {
        // Nominatim with amenity/name param for better POI results
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://nominatim.openstreetmap.org/search?format=jsonv2&q=$encoded&limit=$maxResults&addressdetails=1&extratags=1&namedetails=1")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3500
            readTimeout = 3500
            setRequestProperty("User-Agent", "Druzabac/1.0 (Android)")
            setRequestProperty("Accept-Language", "sr,en")
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val arr = org.json.JSONArray(body)
        val out = mutableListOf<GeocodeSuggestion>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val displayName = item.optString("display_name")
            val lat = item.optString("lat").toDoubleOrNull()
            val lon = item.optString("lon").toDoubleOrNull()
            val category = item.optString("category")
            val type = item.optString("type")
            if (displayName.isBlank() || lat == null || lon == null) continue
            val address = item.optJSONObject("address")
            val nameDetails = item.optJSONObject("namedetails")
            val poiName = nameDetails?.optString("name")?.takeIf { it.isNotBlank() }
            val title = poiName ?: buildPrettyTitle(address, displayName)
            val subtitle = buildPrettySubtitle(address, displayName)
            out += GeocodeSuggestion(
                title = toLatin(title),
                subtitle = subtitle?.let { toLatin(it) },
                latitude = lat,
                longitude = lon,
                fullAddress = toLatin(displayName)
            )
        }
        out.distinctBy { "${it.title}|${it.latitude}|${it.longitude}" }.take(maxResults)
    } catch (e: Exception) {
        Log.w("LocationUtils", "Nominatim POI search failed: ${e.message}")
        emptyList()
    }
}

/**
 * Combined search: tries multiple APIs to find both places (cafés, businesses) AND addresses.
 * Order: Google Places Autocomplete → Text Search → Find Place → Nominatim POI → Nominatim → Google Geocoding
 */
suspend fun searchPlacesAndAddresses(
    context: Context,
    queries: List<String>,
    maxResults: Int = 8,
    locationBias: String? = null
): List<GeocodeSuggestion> {
    val merged = mutableListOf<GeocodeSuggestion>()
    val expandedQueries = expandQueryVariants(queries)

    // 1) Google Places Autocomplete (best for business names — what Google Maps uses)
    for (q in expandedQueries.take(3)) {
        if (merged.size >= maxResults) break
        val results = googlePlacesAutocomplete(context, q, maxResults = maxResults - merged.size, locationBias = locationBias)
        merged += results
    }

    // 2) Google Places Text Search (also good for businesses)
    if (merged.size < maxResults) {
        for (q in expandedQueries.take(3)) {
            if (merged.size >= maxResults) break
            val results = googlePlacesSearch(context, q, maxResults = maxResults - merged.size, locationBias = locationBias)
            merged += results
        }
    }

    // 3) Google Find Place (simpler, may work if other Places APIs aren't enabled)
    if (merged.size < maxResults) {
        for (q in expandedQueries.take(3)) {
            if (merged.size >= maxResults) break
            val results = googleFindPlace(context, q)
            merged += results
        }
    }

    // 4) Nominatim POI search (finds businesses in OpenStreetMap data)
    if (merged.size < maxResults) {
        for (q in expandedQueries.take(4)) {
            if (merged.size >= maxResults) break
            val results = nominatimPoiSearch(q, maxResults - merged.size)
            merged += results
        }
    }

    // 5) Regular Nominatim + Google Geocoding (street addresses)
    if (merged.size < maxResults) {
        for (q in expandedQueries.take(4)) {
            if (merged.size >= maxResults) break
            val suggestions = searchGeocodeSuggestionsWithFallback(context, q, maxResults - merged.size)
            merged += suggestions
        }
    }

    Log.d("LocationUtils", "searchPlacesAndAddresses total results: ${merged.size} for queries: ${queries.take(2)}")

    return merged
        .distinctBy { "${stripDiacritics(it.title).lowercase()}|${String.format("%.4f", it.latitude)}|${String.format("%.4f", it.longitude)}" }
        .take(maxResults)
}

private fun toLatin(input: String): String {
    val sb = StringBuilder(input.length + 8)
    input.forEach { ch ->
        when (ch) {
            'А' -> sb.append("A")
            'Б' -> sb.append("B")
            'В' -> sb.append("V")
            'Г' -> sb.append("G")
            'Д' -> sb.append("D")
            'Ђ' -> sb.append("Đ")
            'Е' -> sb.append("E")
            'Ж' -> sb.append("Ž")
            'З' -> sb.append("Z")
            'И' -> sb.append("I")
            'Ј' -> sb.append("J")
            'К' -> sb.append("K")
            'Л' -> sb.append("L")
            'Љ' -> sb.append("Lj")
            'М' -> sb.append("M")
            'Н' -> sb.append("N")
            'Њ' -> sb.append("Nj")
            'О' -> sb.append("O")
            'П' -> sb.append("P")
            'Р' -> sb.append("R")
            'С' -> sb.append("S")
            'Т' -> sb.append("T")
            'Ћ' -> sb.append("Ć")
            'У' -> sb.append("U")
            'Ф' -> sb.append("F")
            'Х' -> sb.append("H")
            'Ц' -> sb.append("C")
            'Ч' -> sb.append("Č")
            'Џ' -> sb.append("Dž")
            'Ш' -> sb.append("Š")
            'а' -> sb.append("a")
            'б' -> sb.append("b")
            'в' -> sb.append("v")
            'г' -> sb.append("g")
            'д' -> sb.append("d")
            'ђ' -> sb.append("đ")
            'е' -> sb.append("e")
            'ж' -> sb.append("ž")
            'з' -> sb.append("z")
            'и' -> sb.append("i")
            'ј' -> sb.append("j")
            'к' -> sb.append("k")
            'л' -> sb.append("l")
            'љ' -> sb.append("lj")
            'м' -> sb.append("m")
            'н' -> sb.append("n")
            'њ' -> sb.append("nj")
            'о' -> sb.append("o")
            'п' -> sb.append("p")
            'р' -> sb.append("r")
            'с' -> sb.append("s")
            'т' -> sb.append("t")
            'ћ' -> sb.append("ć")
            'у' -> sb.append("u")
            'ф' -> sb.append("f")
            'х' -> sb.append("h")
            'ц' -> sb.append("c")
            'ч' -> sb.append("č")
            'џ' -> sb.append("dž")
            'ш' -> sb.append("š")
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

private fun buildPrettyTitle(address: JSONObject?, displayName: String): String {
    val houseNumber = address?.optString("house_number").orEmpty()
    val road = firstNonBlank(
        address?.optString("road"),
        address?.optString("pedestrian"),
        address?.optString("footway")
    )
    val amenity = firstNonBlank(address?.optString("amenity"), address?.optString("shop"), address?.optString("tourism"))
    val named = firstNonBlank(address?.optString("building"), address?.optString("office"))

    val roadAndNumber = listOf(road, houseNumber).filter { it.isNotBlank() }.joinToString(" ").trim()
    val candidate = when {
        amenity.isNotBlank() && !isNumericLike(amenity) -> amenity
        roadAndNumber.isNotBlank() && !isNumericLike(roadAndNumber) -> roadAndNumber
        named.isNotBlank() && !isNumericLike(named) -> named
        else -> displayName.substringBefore(",").trim()
    }
    return candidate.ifBlank { "Selected location" }
}

private fun buildPrettySubtitle(address: JSONObject?, displayName: String): String? {
    val locality = firstNonBlank(
        address?.optString("suburb"),
        address?.optString("city_district"),
        address?.optString("city"),
        address?.optString("town"),
        address?.optString("village"),
        address?.optString("state"),
        address?.optString("country")
    )
    if (locality.isNotBlank()) {
        val extra = listOfNotNull(
            address?.optString("city")?.takeIf { it.isNotBlank() && !it.equals(locality, ignoreCase = true) },
            address?.optString("country")?.takeIf { it.isNotBlank() }
        ).joinToString(", ").ifBlank { null }
        return if (extra != null) "$locality, $extra" else locality
    }
    return displayName.substringAfter(",", "").trim().ifBlank { null }
}

private fun firstNonBlank(vararg values: String?): String {
    return values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
}

private fun isNumericLike(value: String): Boolean {
    val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{M}".toRegex(), "")
        .trim()
    return normalized.matches(Regex("^[\\d\\s,./-]+$"))
}
