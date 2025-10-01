//package com.example.keepyfitness
//
//import android.Manifest
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.graphics.Bitmap
//import android.graphics.BitmapFactory
//import android.os.Bundle
//import android.provider.MediaStore
//import android.util.Log
//import android.widget.Button
//import android.widget.ImageView
//import android.widget.TextView
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.app.ActivityCompat
//import androidx.core.content.ContextCompat
//
//class fruitcalo : AppCompatActivity() {
//
//    private lateinit var classifier: Classifier
//    private lateinit var imageView: ImageView
//    private lateinit var txtResult: TextView
//    private lateinit var btnGallery: Button
//    private lateinit var btnCamera: Button
//
//    companion object {
//        private const val CAMERA_PERMISSION_CODE = 100
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_fruitcalo)
//
//        imageView = findViewById(R.id.imageView)
//        txtResult = findViewById(R.id.txtResult)
//        btnGallery = findViewById(R.id.button_gallery)
//        btnCamera = findViewById(R.id.button_camera)
//
//        // Load model Food11
//        classifier = Classifier(
//            assets,
//            "food101.tflite", // model đã copy vào assets
//            "food101.txt",    // nhãn đã copy vào assets
//            224              // input size
//        )
//
//        // chọn ảnh từ gallery
//        val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
//            uri?.let {
//                val inputStream = contentResolver.openInputStream(it)
//                val bitmap = BitmapFactory.decodeStream(inputStream)
//                processImage(bitmap)
//            }
//        }
//
//        // chụp ảnh bằng camera
//        val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//            if (result.resultCode == RESULT_OK) {
//                val extras = result.data?.extras
//                val bitmap = extras?.get("data") as? Bitmap
//                bitmap?.let { processImage(it) }
//            }
//        }
//
//        btnGallery.setOnClickListener {
//            galleryLauncher.launch("image/*")
//        }
//
//        btnCamera.setOnClickListener {
//            if (checkCameraPermission()) {
//                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
//                cameraLauncher.launch(intent)
//            }
//        }
//    }
//
//    private fun checkCameraPermission(): Boolean {
//        return if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
//            != PackageManager.PERMISSION_GRANTED
//        ) {
//            ActivityCompat.requestPermissions(
//                this,
//                arrayOf(Manifest.permission.CAMERA),
//                CAMERA_PERMISSION_CODE
//            )
//            false
//        } else {
//            true
//        }
//    }
//
//    override fun onRequestPermissionsResult(
//        requestCode: Int,
//        permissions: Array<out String>,
//        grantResults: IntArray
//    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == CAMERA_PERMISSION_CODE) {
//            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
//                // mở lại camera ngay sau khi user cho phép
//                registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//                    if (result.resultCode == RESULT_OK) {
//                        val extras = result.data?.extras
//                        val bitmap = extras?.get("data") as? Bitmap
//                        bitmap?.let { processImage(it) }
//                    }
//                }.launch(intent)
//            } else {
//                txtResult.text = "❌ Bạn cần cấp quyền camera để chụp ảnh"
//            }
//        }
//    }
//
//    private fun processImage(bitmap: Bitmap?) {
//        bitmap?.let {
//            imageView.setImageBitmap(it)
//            val (label, prob) = classifier.recognizeImage(it)
//            txtResult.text = "🍽️ Món ăn: $label\n📊 Độ tin cậy: ${(prob * 100).toInt()}%"
//            Log.d("fruitcalo", "Predict: $label - $prob")
//        }
//    }
//}
