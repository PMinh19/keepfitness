package com.example.keepyfitness

object FoodCalorieData {
    // Dữ liệu calo cho 15 loại thực phẩm từ Food.AI model (calo/100g)
    private val caloriePer100gMap = mapOf(
        "Bread" to 2.65,      // 265 kcal/100g
        "Pancake" to 2.27,    // 227 kcal/100g
        "Waffle" to 2.91,     // 291 kcal/100g
        "Bagel" to 2.57,      // 257 kcal/100g
        "Muffin" to 3.77,     // 377 kcal/100g
        "Doughnut" to 4.52,   // 452 kcal/100g
        "Hamburger" to 2.95,  // 295 kcal/100g
        "Pizza" to 2.66,      // 266 kcal/100g
        "Sandwich" to 2.50,   // 250 kcal/100g
        "Hot dog" to 2.90,    // 290 kcal/100g
        "French fries" to 3.12, // 312 kcal/100g
        "Apple" to 0.52,      // 52 kcal/100g
        "Orange" to 0.47,     // 47 kcal/100g
        "Banana" to 0.89,     // 89 kcal/100g
        "Grape" to 0.69       // 69 kcal/100g
    )

    fun getCaloriesPerGram(foodName: String): Double {
        return caloriePer100gMap[foodName] ?: 2.0 // mặc định 2.0 kcal/g nếu không tìm thấy
    }

    fun getCalories(foodName: String, grams: Int): Int {
        val caloriesPerGram = getCaloriesPerGram(foodName)
        return (caloriesPerGram * grams).toInt()
    }

    fun getNutritionalInfo(foodName: String, grams: Int): String {
        val calories = getCalories(foodName, grams)
        val caloriesPerGram = getCaloriesPerGram(foodName)

        return """
            🍽️ Món ăn: $foodName
            ⚖️ Khối lượng: ${grams}g
            🔥 Calo: ~$calories kcal
            
            💡 Gợi ý: ${getAdvice(calories)}
        """.trimIndent()
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
