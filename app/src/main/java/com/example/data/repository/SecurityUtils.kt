package com.example.data.repository

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

object SecurityUtils {
    private val usernameRegex = Regex("^[a-z0-9_.]{3,24}$")
    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val hashtagRegex = Regex("#([A-Za-z0-9_]{1,40})")
    private val mentionRegex = Regex("@([a-z0-9_.]{3,24})")
    private val dobRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    private val rateLimits = mutableMapOf<String, MutableList<Long>>()

    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashPassword(password: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest("$salt:$password".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPassword(password: String, salt: String?, expectedHash: String?): Boolean {
        if (salt.isNullOrBlank() || expectedHash.isNullOrBlank()) return false
        return hashPassword(password, salt) == expectedHash
    }

    fun generateBackupCodes(count: Int = 6): List<String> {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return List(count) {
            val part1 = (1..4).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
            val part2 = (1..4).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
            "$part1-$part2"
        }
    }

    fun normalizeUsername(raw: String): String =
        raw.trim().removePrefix("@").lowercase(Locale.US)

    fun isValidUsername(username: String): Boolean =
        usernameRegex.matches(normalizeUsername(username))

    fun isValidEmail(email: String): Boolean =
        emailRegex.matches(email.trim())

    fun isValidDob(dob: String): Boolean {
        val trimmed = dob.trim()
        if (!dobRegex.matches(trimmed)) return false
        val parts = trimmed.split("-")
        val year = parts[0].toIntOrNull() ?: return false
        val month = parts[1].toIntOrNull() ?: return false
        val day = parts[2].toIntOrNull() ?: return false
        return year in 1920..2015 && month in 1..12 && day in 1..31
    }

    fun extractHashtags(text: String): List<String> =
        hashtagRegex.findAll(text).map { it.groupValues[1].lowercase(Locale.US) }.distinct().take(15).toList()

    fun extractMentions(text: String): List<String> =
        mentionRegex.findAll(text.lowercase(Locale.US)).map { it.groupValues[1] }.distinct().take(10).toList()

    fun checkRateLimit(actionKey: String, maxActions: Int = 15, windowMs: Long = 30_000L): Boolean {
        val now = System.currentTimeMillis()
        synchronized(rateLimits) {
            val timestamps = rateLimits.getOrPut(actionKey) { mutableListOf() }
            timestamps.removeAll { now - it > windowMs }
            if (timestamps.size >= maxActions) return false
            timestamps.add(now)
            return true
        }
    }

    fun buildConversationId(uidA: String, uidB: String): String =
        if (uidA <= uidB) "${uidA}__${uidB}" else "${uidB}__${uidA}"

    fun formatCompactCount(count: Long): String {
        return when {
            count >= 1_000_000L -> String.format(Locale.US, "%.1fM", count / 1_000_000.0).replace(".0M", "M")
            count >= 1_000L -> String.format(Locale.US, "%.1fK", count / 1_000.0).replace(".0K", "K")
            else -> count.toString()
        }
    }

    fun formatRelativeTime(timestampMs: Long): String {
        val diff = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
        val sec = diff / 1000L
        val min = sec / 60L
        val hr = min / 60L
        val day = hr / 24L
        return when {
            sec < 60L -> "Just now"
            min < 60L -> "${min}m"
            hr < 24L -> "${hr}h"
            day < 7L -> "${day}d"
            else -> "${day / 7L}w"
        }
    }
}
