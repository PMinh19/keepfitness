package com.example.keepyfitness

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView

class UserActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_user)

        // Setup Bottom Navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_user

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, HomeScreen::class.java))
                    finish()
                    true
                }
                R.id.nav_user -> {
                    // Already on user, do nothing
                    true
                }
                else -> false
            }
        }

        // Hồ sơ người dùng button
        val btnUserProfile = findViewById<androidx.cardview.widget.CardView>(R.id.btnUserProfile)
        btnUserProfile.setOnClickListener {
            startActivity(Intent(this, UserProfileActivity::class.java))
        }

        // Cài đặt vân tay button
        val btnBiometric = findViewById<androidx.cardview.widget.CardView>(R.id.btnBiometric)
        btnBiometric.setOnClickListener {
            startActivity(Intent(this, NotificationSettingsActivity::class.java))
        }

        // Logout button
        val logoutButton = findViewById<CardView>(R.id.logoutButton)
        logoutButton.setOnClickListener {
            showLogoutConfirmation()
        }

        // Áp padding cho hệ thống bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun showLogoutConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Đăng xuất")
            .setMessage("Bạn có chắc chắn muốn đăng xuất không?")
            .setPositiveButton("Có") { dialog, which ->
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                // Clear encrypted shared preferences on logout
                val masterKeyAlias = androidx.security.crypto.MasterKey.Builder(this).setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM).build()
                val securePrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                    this,
                    "secure_prefs",
                    masterKeyAlias,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                securePrefs.edit().clear().apply()
                // Keep biometric enabled for future logins
                showCustomToast("Đã đăng xuất.")
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("Không", null)
            .setCancelable(true)
            .show()
    }

    private fun showCustomToast(message: String) {
        val inflater = layoutInflater
        val layout = inflater.inflate(R.layout.custom_toast, null)
        val text = layout.findViewById<android.widget.TextView>(R.id.toast_text)
        text.text = message
        val toast = android.widget.Toast(applicationContext)
        toast.duration = android.widget.Toast.LENGTH_LONG
        toast.view = layout
        toast.show()
    }
}
