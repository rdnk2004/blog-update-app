package com.example.data.markdown

import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles Unicode-safe slug generation and warm human-readable date formatting.
 */
object PostFormatter {

    private val NON_ASCII_COMBINING = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val NON_SLUG_CHARS = Regex("[^a-z0-9]+")
    private val LEADING_TRAILING_HYPHENS = Regex("^-+|-+$")

    fun todayIsoDate(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return formatter.format(Date())
    }

    /**
     * Generates a URL-safe slug from a title.
     * Falls back to a timestamp slug when the title produces an empty Latin slug
     * (for example, titles written purely in Indian languages).
     */
    fun generateSlug(title: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): String {
        val normalized = Normalizer.normalize(title.trim(), Normalizer.Form.NFD)
        val ascii = NON_ASCII_COMBINING.replace(normalized, "").lowercase(Locale.US)
        val slug = LEADING_TRAILING_HYPHENS
            .replace(NON_SLUG_CHARS.replace(ascii, "-"), "")
            .take(60)
            .trimEnd('-')

        return slug.ifBlank { "post-$nowEpochSeconds" }
    }

    fun formatDisplayDate(isoDate: String, longMonth: Boolean = false): String {
        if (isoDate.isBlank()) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            val parsed = parser.parse(isoDate.trim()) ?: return isoDate
            val pattern = if (longMonth) "d MMMM yyyy" else "d MMM yyyy"
            SimpleDateFormat(pattern, Locale.getDefault()).format(parsed)
        } catch (_: Exception) {
            isoDate
        }
    }
}
