package com.example.keepyfitness

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.concurrent.Executor

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    private val maxAttempts = 5
    private val lockoutTime = 15 * 60 * 1000L // 15 minutes in milliseconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Check if user is already logged in
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null && currentUser.isEmailVerified) {
            // User is already logged in, go to HomeScreen
            val intent = Intent(this, HomeScreen::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        // Initialize FirebaseAuth and Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Setup biometric authentication
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Toast.makeText(this@LoginActivity, "Lỗi xác thực: $errString", Toast.LENGTH_SHORT).show()
                auth.signOut() // Sign out if biometric fails
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Toast.makeText(this@LoginActivity, "Xác thực thành công!", Toast.LENGTH_SHORT).show()

                // Get stored credentials and login
                val encryptedPrefs = getEncryptedPrefs()
                val email = encryptedPrefs.getString("email", null)
                val password = encryptedPrefs.getString("password", null)

                if (email != null && password != null) {
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val intent = Intent(this@LoginActivity, HomeScreen::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            } else {
                                Toast.makeText(this@LoginActivity, "Đăng nhập thất bại. Vui lòng thử lại.", Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    Toast.makeText(this@LoginActivity, "Không tìm thấy thông tin đăng nhập.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Toast.makeText(this@LoginActivity, "Xác thực thất bại. Thử lại.", Toast.LENGTH_SHORT).show()
            }
        })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Xác thực vân tay")
            .setSubtitle("Sử dụng vân tay để đăng nhập")
            .setNegativeButtonText("Hủy")
            .build()

        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val loginButton = findViewById<MaterialButton>(R.id.loginButton)
        val registerTextView = findViewById<TextView>(R.id.registerTextView)
        val forgotPasswordTextView = findViewById<TextView>(R.id.forgotPasswordTextView)
        forgotPasswordTextView.paint.isUnderlineText = true

        // Get biometric preferences
        val biometricPrefs = getSharedPreferences("reminder_settings", MODE_PRIVATE)
        val biometricEnabled = biometricPrefs.getBoolean("biometric_enabled", false)

        // Setup biometric login button - visible if device supports biometric
        val biometricLoginButton = findViewById<MaterialButton>(R.id.biometricLoginButton)
        biometricLoginButton.visibility = if (isBiometricSupported()) android.view.View.VISIBLE else android.view.View.GONE
        biometricLoginButton.setOnClickListener {
            // Reload biometric preferences to get latest status
            val currentBiometricPrefs = getSharedPreferences("reminder_settings", MODE_PRIVATE)
            val currentBiometricEnabled = currentBiometricPrefs.getBoolean("biometric_enabled", false)
            val hasCreds = hasBiometricCredentials()

            if (currentBiometricEnabled && hasCreds) {
                biometricPrompt.authenticate(promptInfo)
            } else {
                promptLoginForBiometric()
            }
        }

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Hãy điền đầy đủ thông tin yêu cầu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate email format
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Email không hợp lệ.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check for account lockout
            if (isAccountLocked()) {
                Toast.makeText(this, "Tài khoản của bạn đã bị khóa do nhiều lần đăng nhập thất bại. Vui lòng thử lại sau.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Disable button during authentication
            loginButton.isEnabled = false

            // Authenticate user
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    loginButton.isEnabled = true // Re-enable button

                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        if (user != null && user.isEmailVerified) {
                            // Tạo hoặc cập nhật dữ liệu user trong Firestore
                            checkAndUpdateUserDatabase(user.uid, email)
                            Toast.makeText(this, "Đăng nhập thành công.", Toast.LENGTH_SHORT).show()

                            // Reset failed attempts on successful login
                            resetFailedAttempts()

                            // Always store credentials for history access
                            val encryptedPrefs = getEncryptedPrefs()
                            encryptedPrefs.edit().apply {
                                putString("email", email)
                                putString("password", password)
                                commit()
                            }

                            // Go directly to HomeScreen
                            val intent = Intent(this, HomeScreen::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this, "Hãy xác minh email trước khi đăng nhập.", Toast.LENGTH_LONG).show()
                            auth.signOut()
                        }
                    } else {
                        val exception = task.exception
                        val message = when (exception) {
                            is FirebaseAuthInvalidUserException -> "Tài khoản không tồn tại."
                            is FirebaseAuthInvalidCredentialsException -> "Email hoặc mật khẩu không đúng."
                            else -> "Đăng nhập thất bại. Kiểm tra email và mật khẩu."
                        }
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        // Record the failed attempt
                        recordFailedAttempt()
                    }
                }
        }

        registerTextView.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        forgotPasswordTextView.setOnClickListener {
            startActivity(Intent(this, ResetPasswordActivity::class.java))
        }
    }

    private fun checkAndUpdateUserDatabase(uid: String, email: String) {
        val userRef = db.collection("users").document(uid)
        userRef.get().addOnSuccessListener { document: DocumentSnapshot ->
            if (!document.exists()) {
                val userData = hashMapOf(
                    "email" to email,
                    "role" to "user"
                )
                userRef.set(userData, SetOptions.merge()).addOnSuccessListener {
                    Toast.makeText(this, "Đã tạo CSDL user", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener { e: Exception ->
                    Toast.makeText(this, "Lỗi tạo CSDL: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.addOnFailureListener { e: Exception ->
            Toast.makeText(this, "Lỗi kiểm tra CSDL: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isAccountLocked(): Boolean {
        val prefs = getSharedPreferences("login_prefs", MODE_PRIVATE)
        val failedAttempts = prefs.getInt("failed_attempts", 0)
        val lastFailedTime = prefs.getLong("last_failed_time", 0)
        val currentTime = System.currentTimeMillis()
        if (failedAttempts >= maxAttempts && (currentTime - lastFailedTime) < lockoutTime) {
            return true
        }
        return false
    }

    private fun recordFailedAttempt() {
        val prefs = getSharedPreferences("login_prefs", MODE_PRIVATE)
        val currentAttempts = prefs.getInt("failed_attempts", 0)
        val editor = prefs.edit()
        editor.putInt("failed_attempts", currentAttempts + 1)
        editor.putLong("last_failed_time", System.currentTimeMillis())
        editor.apply()
    }

    private fun resetFailedAttempts() {
        val prefs = getSharedPreferences("login_prefs", MODE_PRIVATE).edit()
        prefs.putInt("failed_attempts", 0)
        prefs.putLong("last_failed_time", 0)
        prefs.apply()
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

    private fun isBiometricSupported(): Boolean {
        val biometricManager = BiometricManager.from(this)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun promptLoginForBiometric() {
        val biometricPrefs = getSharedPreferences("reminder_settings", MODE_PRIVATE)
        val biometricEnabled = biometricPrefs.getBoolean("biometric_enabled", false)
        val hasCredentials = hasBiometricCredentials()

        val message = when {
            !biometricEnabled && !hasCredentials -> "Đăng nhập bằng vân tay chưa được kích hoạt. Vui lòng vào cài đặt để kích hoạt."
            !biometricEnabled && hasCredentials -> "Vân tay đã bị tắt trong cài đặt. Vui lòng bật lại trong cài đặt."
            biometricEnabled && !hasCredentials -> "Thông tin đăng nhập chưa được lưu. Vui lòng đăng nhập bằng email/mật khẩu trước."
            else -> "Lỗi không xác định. Vui lòng thử lại."
        }

        AlertDialog.Builder(this)
            .setTitle("Kích hoạt vân tay")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}