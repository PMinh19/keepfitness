package com.example.keepyfitness.Model

data class Schedule(
    val exercise: String,
    val time: String,
    val days: List<String>,
    val quantity: Int
)
