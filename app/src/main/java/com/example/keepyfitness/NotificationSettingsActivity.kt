package com.example.keepyfitness

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import org.mindrot.jbcrypt.BCrypt

class NotificationSettingsActivity : AppCompatActivity() {

    private lateinit var switchBiometric: Switch
    private lateinit var btnSaveSettings: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_settings)

        auth = FirebaseAuth.getInstance()

        switchBiometric = findViewById(R.id.switchBiometric)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)

        loadSettings()

        switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !hasBiometricCredentials()) {
                // Prompt to enable biometric
                enableBiometric()
            } else if (isChecked && hasBiometricCredentials()) {
                // Credentials exist, prompt biometric to confirm
                promptBiometricForConfirmation()
            } else if (!isChecked && hasBiometricCredentials()) {
                // User disabled biometric, clear stored credentials
                clearBiometricCredentials()
                Toast.makeText(this, "Đã xóa thông tin đăng nhập vân tay", Toast.LENGTH_SHORT).show()
            }
        }

        btnSaveSettings.setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("reminder_settings", MODE_PRIVATE)
        val enabled = prefs.getBoolean("biometric_enabled", false)
        val hasCreds = hasBiometricCredentials()
        switchBiometric.isChecked = enabled || hasCreds
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("reminder_settings", MODE_PRIVATE).edit()
        prefs.putBoolean("biometric_enabled", switchBiometric.isChecked)
        prefs.apply()

        Toast.makeText(this, "Cài đặt đã lưu", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun enableBiometric() {
        // First, show dialog to enter current password
        val passwordInput = EditText(this)
        passwordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        passwordInput.hint = "Nhập mật khẩu hiện tại"

        AlertDialog.Builder(this)
            .setTitle("Xác nhận mật khẩu")
            .setMessage("Vui lòng nhập mật khẩu hiện tại để kích hoạt đăng nhập bằng vân tay.")
            .setView(passwordInput)
            .setPositiveButton("Xác nhận") { _, _ ->
                val password = passwordInput.text.toString()
                if (password.isEmpty()) {
                    Toast.makeText(this, "Vui lòng nhập mật khẩu", Toast.LENGTH_SHORT).show()
                    switchBiometric.isChecked = false
                    return@setPositiveButton
                }

                // Verify password with Firebase before enabling biometric
                val currentUser = auth.currentUser
                val email = currentUser?.email

                if (email == null) {
                    Toast.makeText(this, "Không tìm thấy thông tin người dùng", Toast.LENGTH_SHORT).show()
                    switchBiometric.isChecked = false
                    return@setPositiveButton
                }

                // Re-authenticate user with Firebase to verify password
                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        // Password is correct, now prompt biometric
                        promptBiometric(password)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Mật khẩu không đúng: ${e.message}", Toast.LENGTH_SHORT).show()
                        switchBiometric.isChecked = false
                    }
            }
            .setNegativeButton("Hủy") { _, _ ->
                switchBiometric.isChecked = false
            }
            .show()
    }

    private fun promptBiometric(password: String) {
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                // Proceed with biometric prompt
                promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Kích hoạt đăng nhập bằng vân tay")
                    .setSubtitle("Quét vân tay để kích hoạt")
                    .setNegativeButtonText("Hủy")
                    .build()

                biometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        // Save credentials
                        saveBiometricCredentials(password)
                        // Set biometric enabled
                        val prefs = getSharedPreferences("reminder_settings", MODE_PRIVATE).edit()
                        prefs.putBoolean("biometric_enabled", true)
                        prefs.apply()
                        Toast.makeText(applicationContext, "Đã kích hoạt đăng nhập bằng vân tay!", Toast.LENGTH_SHORT).show()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        Toast.makeText(applicationContext, "Lỗi: $errString", Toast.LENGTH_SHORT).show()
                        switchBiometric.isChecked = false
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Toast.makeText(applicationContext, "Xác thực thất bại", Toast.LENGTH_SHORT).show()
                        switchBiometric.isChecked = false
                    }
                })

                biometricPrompt.authenticate(promptInfo)
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Toast.makeText(this, "Thiết bị này không hỗ trợ vân tay.", Toast.LENGTH_SHORT).show()
                switchBiometric.isChecked = false
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Toast.makeText(this, "Vân tay hiện không khả dụng.", Toast.LENGTH_SHORT).show()
                switchBiometric.isChecked = false
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Toast.makeText(this, "Vui lòng thiết lập vân tay trong cài đặt.", Toast.LENGTH_SHORT).show()
                switchBiometric.isChecked = false
            }
        }
    }

    private fun saveBiometricCredentials(password: String) {
        try {
            val email = auth.currentUser?.email ?: run {
                Toast.makeText(this, "Không tìm thấy email người dùng", Toast.LENGTH_SHORT).show()
                return
            }

            // Hash mật khẩu bằng bcrypt trước khi lưu
            val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())

            val prefs = getEncryptedPrefs()
            prefs.edit().apply {
                putString("email", email)
                putString("password", hashedPassword) // Lưu password đã hash
                commit()
            }

            Toast.makeText(this, "Đã lưu thông tin đăng nhập an toàn", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi lưu credentials: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getEncryptedPrefs() = getSharedPreferences("biometric_prefs", MODE_PRIVATE)

    private fun hasBiometricCredentials(): Boolean {
        return try {
            val prefs = getEncryptedPrefs()
            val email = prefs.getString("email", null)
            val password = prefs.getString("password", null)
            !email.isNullOrEmpty() && !password.isNullOrEmpty()
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi kiểm tra credentials: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun clearBiometricCredentials() {
        val prefs = getEncryptedPrefs()
        prefs.edit().apply {
            remove("email")
            remove("password")
            commit()
        }
    }

    private fun promptBiometricForConfirmation() {
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                // Proceed with biometric prompt for confirmation
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Xác nhận vân tay")
                    .setSubtitle("Quét vân tay để xác nhận")
                    .setNegativeButtonText("Hủy")
                    .build()

                val biometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        // Set biometric enabled
                        val prefs = getSharedPreferences("reminder_settings", MODE_PRIVATE).edit()
                        prefs.putBoolean("biometric_enabled", true)
                        prefs.apply()
                        Toast.makeText(applicationContext, "Đã kích hoạt đăng nhập bằng vân tay!", Toast.LENGTH_SHORT).show()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        Toast.makeText(applicationContext, "Lỗi: $errString", Toast.LENGTH_SHORT).show()
                        switchBiometric.isChecked = false
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Toast.makeText(applicationContext, "Xác thực thất bại", Toast.LENGTH_SHORT).show()
                        switchBiometric.isChecked = false
                    }
                })

                biometricPrompt.authenticate(promptInfo)
            }
            else -> {
                Toast.makeText(this, "Vân tay không khả dụng", Toast.LENGTH_SHORT).show()
                switchBiometric.isChecked = false
            }
        }
    }

    private fun getBiometricPassword(): String? {
        return try {
            val prefs = getEncryptedPrefs()
            prefs.getString("password", null)
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi lấy thông tin đăng nhập: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }
}
