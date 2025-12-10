package com.example.keepyfitness

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class FruitCalo : AppCompatActivity() {

    private lateinit var classifier: Classifier
    private lateinit var imageView: ImageView
    private lateinit var txtResult: TextView
    private lateinit var btnGallery: LinearLayout
    private lateinit var btnCamera: LinearLayout

    // Khai báo launchers ở đây để tránh bị recreate
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val inputStream = contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                processImage(bitmap)
            } catch (e: Exception) {
                Log.e("FruitCalo", "Error loading image from gallery", e)
                txtResult.text = "❌ Không thể tải ảnh"
            }
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            try {
                val imageBitmap = result.data?.extras?.get("data") as? Bitmap
                imageBitmap?.let {
                    processImage(it)
                } ?: run {
                    txtResult.text = "❌ Không nhận được ảnh từ camera"
                }
            } catch (e: Exception) {
                Log.e("FruitCalo", "Error processing camera image", e)
                txtResult.text = "❌ Lỗi xử lý ảnh"
            }
        }
    }

    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fruitcalo)

        imageView = findViewById(R.id.imageView)
        txtResult = findViewById(R.id.txtResult)
        btnGallery = findViewById(R.id.button_gallery)
        btnCamera = findViewById(R.id.button_camera)

        // Load model food_detect.tflite
        try {
            classifier = Classifier(
                assets,
                "food_detect.tflite",      // model Food.AI với 15 classes
                "food_labelmap.txt",       // nhãn cho model Food.AI
                300                        // input size cho model Food.AI là 300x300
            )
        } catch (e: Exception) {
            Log.e("FruitCalo", "Error loading model", e)
            txtResult.text = "❌ Không thể tải model AI"
            return
        }

        btnGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        btnCamera.setOnClickListener {
            if (checkCameraPermission()) {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                cameraLauncher.launch(intent)
            }
        }
    }

    private fun checkCameraPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
            false
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Sau khi được cấp quyền, mở camera
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                cameraLauncher.launch(intent)
            } else {
                txtResult.text = "❌ Bạn cần cấp quyền camera để chụp ảnh"
            }
        }
    }

    private fun showWeightInputDialog(foodName: String, confidence: Int, onWeightEntered: (Int) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_weight_input, null)
        val editTextWeight = dialogView.findViewById<android.widget.EditText>(R.id.editTextWeight)
        val textViewFood = dialogView.findViewById<android.widget.TextView>(R.id.textViewFood)
        val textViewConfidence = dialogView.findViewById<android.widget.TextView>(R.id.textViewConfidence)

        textViewFood.text = "🍽️ $foodName"
        textViewConfidence.text = "📊 Độ tin cậy: $confidence%"

        // Đặt giá trị mặc định
        editTextWeight.setText("100")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Nhập khối lượng món ăn")
            .setView(dialogView)
            .setPositiveButton("Tính calo") { _, _ ->
                val weightText = editTextWeight.text.toString()
                val grams = weightText.toIntOrNull() ?: 100
                onWeightEntered(grams)
            }
            .setNegativeButton("Hủy", null)
            .setCancelable(false)
            .show()
    }

    private fun processMultipleFoods(recognitions: List<Recognition>, index: Int, weights: MutableList<Pair<String, Int>>) {
        if (index >= recognitions.size) {
            // Tất cả đã nhập xong, tính tổng calo
            var totalCalories = 0
            val resultText = StringBuilder()
            resultText.append("🎯 Phát hiện ${recognitions.size} món ăn:\n\n")

            weights.forEachIndexed { i, (foodName, grams) ->
                val calories = FoodCalorieData.getCalories(foodName, grams)
                totalCalories += calories
                val recognition = recognitions.find { it.title == foodName }
                val confidence = (recognition?.confidence ?: 0f * 100).toInt()

                resultText.append("${i + 1}. 🍽️ $foodName (${grams}g)\n")
                resultText.append("   🔥 Calo: $calories kcal\n")
                resultText.append("   📊 Độ tin cậy: $confidence%\n\n")

                Log.d("FruitCalo", "✅ Detected: $foodName - $grams g - $calories kcal")
            }

            // Hiển thị tổng calo
            resultText.append("━━━━━━━━━━━━━━━━━━━\n")
            resultText.append("🔥 Tổng calo: $totalCalories kcal\n")
            resultText.append("\n💡 ${getTotalCalorieAdvice(totalCalories)}")

            txtResult.text = resultText.toString()
            saveTotalCalories(totalCalories)
            return
        }

        val recognition = recognitions[index]
        val foodName = recognition.title
        val confidence = (recognition.confidence * 100).toInt()

        showWeightInputDialog(foodName, confidence) { grams ->
            weights.add(Pair(foodName, grams))
            processMultipleFoods(recognitions, index + 1, weights)
        }
    }

    private fun processImage(bitmap: Bitmap?) {
        bitmap?.let {
            try {
                imageView.setImageBitmap(it)

                // Sử dụng Object Detection để nhận diện nhiều món ăn
                val recognitions = classifier.recognizeImageMultiple(it)

                // Danh sách 15 món ăn từ Food.AI model
                val validFoods = listOf(
                    "Bread", "Pancake", "Waffle", "Bagel", "Muffin",
                    "Doughnut", "Hamburger", "Pizza", "Sandwich", "Hot dog",
                    "French fries", "Apple", "Orange", "Banana", "Grape"
                )

                // Lọc chỉ lấy món ăn hợp lệ
                val validRecognitions = recognitions.filter { it.title in validFoods }

                if (validRecognitions.isEmpty()) {
                    txtResult.text = """
                        🤖 Không phát hiện món ăn nào
                        
                        ℹ️ Model Food.AI nhận diện 15 loại thực phẩm:
                        🍞 Bread, Pancake, Waffle, Bagel, Muffin, Doughnut
                        🍔 Hamburger, Pizza, Sandwich, Hot dog, French fries
                        🍎 Apple, Orange, Banana, Grape
                        
                        💡 Hãy thử chụp rõ hơn hoặc chọn một trong những món trên!
                    """.trimIndent()
                    return
                }

                // Hiển thị kết quả cho nhiều món ăn
                val resultText = StringBuilder()
                var totalCalories = 0

                if (validRecognitions.size == 1) {
                    // Chỉ có 1 món - hiển thị chi tiết hơn và cho nhập khối lượng
                    val recognition = validRecognitions[0]
                    val foodName = recognition.title
                    val confidence = (recognition.confidence * 100).toInt()

                    // Hiển thị dialog nhập khối lượng
                    showWeightInputDialog(foodName, confidence) { grams ->
                        val calories = FoodCalorieData.getCalories(foodName, grams)
                        val nutritionalInfo = FoodCalorieData.getNutritionalInfo(foodName, grams)

                        runOnUiThread {
                            txtResult.text = nutritionalInfo
                            saveTotalCalories(calories)
                        }
                    }
                    return@let
                } else {
                    // Nhiều món - nhập khối lượng cho từng món
                    processMultipleFoods(validRecognitions, 0, mutableListOf())
                }

            } catch (e: Exception) {
                Log.e("FruitCalo", "Error processing image", e)
                txtResult.text = """
                    ❌ Lỗi nhận diện ảnh
                    
                    Chi tiết: ${e.message}
                    
                    💡 Vui lòng thử lại hoặc chụp ảnh rõ hơn
                """.trimIndent()
            }
        }
    }

    private fun saveTotalCalories(calories: Int) {
        try {
            val prefs = getSharedPreferences("health_data", MODE_PRIVATE)
            // Lưu calo NẠP VÀO từ thức ăn (calories consumed)
            prefs.edit().apply {
                putInt("calories_consumed_today", calories)
                putLong("last_food_scan_time", System.currentTimeMillis())
                apply()
            }

            Log.d("FruitCalo", "Saved calories consumed: $calories kcal (from food)")

            // Lưu lên Firestore nếu đã đăng nhập
            saveFoodCaloriesToFirestore(calories)
        } catch (e: Exception) {
            Log.e("FruitCalo", "Error saving calories", e)
        }
    }

    private fun saveFoodCaloriesToFirestore(calories: Int) {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.w("FruitCalo", "User not logged in, cannot save to Firestore")
            return
        }

        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val dayId = calendar.timeInMillis.toString()

        Log.d("FruitCalo", "Saving to Firestore - User: ${user.uid}, DayId: $dayId, Calories: $calories")

        val foodData = hashMapOf(
            "caloriesConsumed" to calories,
            "date" to System.currentTimeMillis(),
            "dayId" to dayId
        )

        db.collection("users").document(user.uid)
            .collection("foodIntake").document(dayId)
            .set(foodData)
            .addOnSuccessListener {
                Log.d("FruitCalo", "✅ Food calories saved to Firestore successfully: $calories kcal")
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@FruitCalo,
                        "✅ Đã lưu $calories calo vào hệ thống",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("FruitCalo", "❌ Error saving to Firestore: ${e.message}", e)
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@FruitCalo,
                        "⚠️ Không thể lưu vào cloud: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun getTotalCalorieAdvice(totalCalories: Int): String {
        return when {
            totalCalories < 300 -> "Bữa ăn nhẹ, phù hợp cho bữa phụ."
            totalCalories < 600 -> "Lượng calo vừa phải cho 1 bữa ăn."
            totalCalories < 900 -> "Bữa ăn đầy đủ, hãy vận động nhẹ sau ăn."
            else -> "Bữa ăn nhiều calo! Nên tăng cường tập luyện."
        }
    }
}
