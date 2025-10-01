package com.example.keepyfitness.Model

import com.google.firebase.firestore.PropertyName
import java.io.Serializable

data class HeartRateData(
    @PropertyName("id") val id: String = System.currentTimeMillis().toString(),
    @PropertyName("bpm") val bpm: Int = 0,
    @PropertyName("status") val status: String = "",
    @PropertyName("suggestion") val suggestion: String = "",
    @PropertyName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    @PropertyName("duration") val duration: Long = 0L
) : Serializable