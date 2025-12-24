package com.example.keepyfitness

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object FoodCalorieData {
    private val db = FirebaseFirestore.getInstance()
    private val foodsCollection = db.collection("foods")

    // Cache local để tránh fetch nhiều lần
    private var cachedFoods: Map<String, Double>? = null

    suspend fun getCaloriesPerGram(foodName: String): Double {
        if (cachedFoods == null) {
            loadFoodsFromFirestore()
        }
        return cachedFoods?.get(foodName) ?: 2.0 // mặc định 2.0 kcal/g nếu không tìm thấy
    }

    fun getCaloriesPerGramSync(foodName: String): Double {
        return cachedFoods?.get(foodName) ?: 2.0
    }

    suspend fun getCalories(foodName: String, grams: Int): Int {
        val caloriesPerGram = getCaloriesPerGram(foodName)
        return (caloriesPerGram * grams).toInt()
    }

    fun getCaloriesSync(foodName: String, grams: Int): Int {
        val caloriesPerGram = getCaloriesPerGramSync(foodName)
        return (caloriesPerGram * grams).toInt()
    }

    suspend fun getNutritionalInfo(foodName: String, grams: Int): String {
        val calories = getCalories(foodName, grams)
        val caloriesPerGram = getCaloriesPerGram(foodName)

        return """
            🍽️ Món ăn: $foodName
            ⚖️ Khối lượng: ${grams}g
            🔥 Calo: ~$calories kcal
            
            💡 Gợi ý: ${getAdvice(calories)}
        """.trimIndent()
    }

    fun getNutritionalInfoSync(foodName: String, grams: Int): String {
        val calories = getCaloriesSync(foodName, grams)
        val caloriesPerGram = getCaloriesPerGramSync(foodName)

        return """
            🍽️ Món ăn: $foodName
            ⚖️ Khối lượng: ${grams}g
            🔥 Calo: ~$calories kcal
            
            💡 Gợi ý: ${getAdvice(calories)}
        """.trimIndent()
    }

    private suspend fun loadFoodsFromFirestore() {
        try {
            val snapshot = foodsCollection.get().await()
            cachedFoods = snapshot.documents.associate { doc ->
                val name = doc.getString("name") ?: ""
                val calories = doc.getDouble("caloriesPer100g") ?: 2.0
                name to calories
            }
        } catch (e: Exception) {
            // Fallback nếu lỗi
            cachedFoods = emptyMap()
        }
    }

    private fun getAdvice(calories: Int): String {
        return when {
            calories < 100 -> "Món ăn nhẹ, giàu vitamin và chất xơ. Tốt cho sức khỏe!"
            calories < 250 -> "Lượng calo vừa phải, tốt cho bữa ăn cân đối."
            calories < 400 -> "Lượng calo cao, nên kết hợp với rau xanh và vận động."
            else -> "Món ăn nhiều calo, nên ăn vừa phải và tăng cường tập luyện."
        }
    }
}
