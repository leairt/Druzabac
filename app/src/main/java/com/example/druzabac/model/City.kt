package com.example.druzabac.model

data class City(
    val id: String,
    val name: String,
    val districts: List<String>,
    val country: String,
    val enabled: Boolean
)
