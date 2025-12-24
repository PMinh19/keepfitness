package com.example.keepyfitness.security

import android.text.Html
import android.text.Spanned
import java.util.regex.Pattern

object XSSProtection {

    /**
     * Sanitize input bằng cách loại bỏ các ký tự nguy hiểm
     */
    fun sanitizeInput(input: String?): String {
        if (input.isNullOrBlank()) return ""

        return input
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;")
            .replace("&", "&amp;")
            .trim()
    }

    /**
     * Validate email format
     */
    fun isValidEmail(email: String): Boolean {
        val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
        return Pattern.compile(emailPattern).matcher(email).matches()
    }

    /**
     * Validate số điện thoại
     */
    fun isValidPhoneNumber(phone: String): Boolean {
        val phonePattern = "^[0-9]{10,11}$"
        return Pattern.compile(phonePattern).matcher(phone).matches()
    }

    /**
     * Chỉ cho phép ký tự chữ và số
     */
    fun sanitizeAlphanumeric(input: String?): String {
        if (input.isNullOrBlank()) return ""
        return input.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
    }

    /**
     * Sanitize số (chỉ cho phép số và dấu chấm)
     */
    fun sanitizeNumeric(input: String?): String {
        if (input.isNullOrBlank()) return ""
        return input.replace(Regex("[^0-9.]"), "").trim()
    }

    /**
     * Decode HTML entities an toàn
     */
    fun decodeHtml(html: String?): Spanned {
        if (html.isNullOrBlank()) return Html.fromHtml("", Html.FROM_HTML_MODE_LEGACY)
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
    }

    /**
     * Kiểm tra script injection
     */
    fun containsScriptTag(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        val scriptPattern = "(?i)<script[^>]*>.*?</script>"
        return Pattern.compile(scriptPattern).matcher(input).find()
    }
}