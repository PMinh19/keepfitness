package com.example.keepyfitness.security

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtil {
    // Thuật toán mã hóa
    private const val AES_ALGO = "AES"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128  // Độ dài tag xác thực
    private const val IV_BYTES = 12       // Độ dài IV (Initialization Vector)

    // Salt bí mật - QUAN TRỌNG: Thay đổi và lưu trong Android Keystore trong production
    private const val STATIC_SALT = "KeepyFitness2024SecretSalt"

    private val rng = SecureRandom()

    /**
     * Tạo khóa AES từ userId và salt bí mật
     * @param userId: UID của người dùng từ Firebase Auth
     * @return SecretKey: Khóa AES 256-bit
     */
    fun deriveKey(userId: String): SecretKey {
        // Kết hợp userId và salt
        val material = (userId + ":" + STATIC_SALT).toByteArray(StandardCharsets.UTF_8)
        // Băm SHA-256 để tạo khóa 256-bit
        val hash = MessageDigest.getInstance("SHA-256").digest(material)
        return SecretKeySpec(hash, AES_ALGO)
    }

    /**
     * Mã hóa chuỗi văn bản
     * @param plain: Chuỗi cần mã hóa
     * @param key: Khóa AES
     * @return String: Chuỗi đã mã hóa dạng Base64 (IV + ciphertext)
     */
    fun encrypt(plain: String, key: SecretKey): String {
        // Tạo IV ngẫu nhiên (quan trọng: mỗi lần mã hóa phải dùng IV khác nhau)
        val iv = ByteArray(IV_BYTES).also { rng.nextBytes(it) }

        // Khởi tạo Cipher với mode GCM
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

        // Mã hóa
        val cipherBytes = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))

        // Ghép IV + ciphertext và encode Base64
        val buf = ByteBuffer.allocate(iv.size + cipherBytes.size)
        buf.put(iv)
        buf.put(cipherBytes)
        return Base64.encodeToString(buf.array(), Base64.NO_WRAP)
    }

    /**
     * Giải mã chuỗi đã mã hóa
     * @param b64: Chuỗi Base64 (IV + ciphertext)
     * @param key: Khóa AES
     * @return String: Chuỗi văn bản gốc
     */
    fun decrypt(b64: String, key: SecretKey): String {
        // Decode Base64
        val data = Base64.decode(b64, Base64.NO_WRAP)

        // Tách IV và ciphertext
        val iv = data.copyOfRange(0, IV_BYTES)
        val cipherBytes = data.copyOfRange(IV_BYTES, data.size)

        // Khởi tạo Cipher với mode GCM
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

        // Giải mã
        val plain = cipher.doFinal(cipherBytes)
        return String(plain, StandardCharsets.UTF_8)
    }
}