package com.example.druzabac.model

data class Game (
    val id: String,
    val name: String,
    val imageUrl: String?,
    val bggUrl: String? = null,
    // val language: String,
    // val categories: List<String>
)

