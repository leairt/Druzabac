package com.example.druzabac.db

import android.util.Log
import com.example.druzabac.model.Game
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class GameDatabase {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val gamesCollection = firestore.collection("games")

    companion object {
        private const val TAG = "GameDatabase"
    }

    /**
     * Search games by name (case-insensitive partial match)
     * Returns games ordered by name
     */
    suspend fun searchByName(searchQuery: String): List<Game> {
        return try {
            if (searchQuery.isBlank()) {
                return emptyList()
            }

            val querySnapshot = gamesCollection
                .orderBy("name", Query.Direction.ASCENDING)
                .get()
                .await()

            // Firestore doesn't support case-insensitive or LIKE queries natively
            // Filter in code after retrieving documents
            val searchLower = searchQuery.lowercase()
            querySnapshot.documents.mapNotNull { document ->
                val name = document.getString("name") ?: ""
                val imageUrl = document.getString("imageUrl")

                if (name.lowercase().contains(searchLower)) {
                    Game(
                        id = document.id,
                        name = name,
                        imageUrl = imageUrl
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching games: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Get game by ID
     */
    suspend fun getById(gameId: String): Game? {
        return try {
            val document = gamesCollection
                .document(gameId)
                .get()
                .await()

            if (document.exists()) {
                Game(
                    id = document.id,
                    name = document.getString("name") ?: "",
                    imageUrl = document.getString("imageUrl"),
                    bggUrl = document.getString("bggUrl")
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting game by ID: ${e.message}", e)
            null
        }
    }

    /**
     * Get all games ordered by name
     */
    suspend fun getAllGames(): List<Game> {
        return try {
            val querySnapshot = gamesCollection
                .orderBy("name", Query.Direction.ASCENDING)
                .get()
                .await()

            querySnapshot.documents.mapNotNull { document ->
                Game(
                    id = document.id,
                    name = document.getString("name") ?: "",
                    imageUrl = document.getString("imageUrl"),
                    bggUrl = document.getString("bggUrl")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all games: ${e.message}", e)
            emptyList()
        }
    }
}