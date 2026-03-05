package com.example.druzabac.db

import android.util.Log
import com.example.druzabac.model.City
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class CityDatabase {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val citiesCollection = firestore.collection("cities")

    companion object {
        private const val TAG = "CityDatabase"
    }

    /**
     * Get all cities ordered by name, then by country
     */
    suspend fun getAllCities(): List<City> {
        return try {
            val querySnapshot = citiesCollection
                .orderBy("name", Query.Direction.ASCENDING)
                // .orderBy("country", Query.Direction.ASCENDING)
                .get()
                .await()

            querySnapshot.documents.mapNotNull { document ->
                City(
                    id = document.id,
                    name = document.getString("name") ?: "",
                    districts = document.get("districts") as? List<String> ?: emptyList(),
                    country = document.getString("country") ?: "",
                    enabled = document.getBoolean("enabled") ?: false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cities: ${e.message}", e)
            emptyList()
        }
    }
}