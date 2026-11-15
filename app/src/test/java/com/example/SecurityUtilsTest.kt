package com.example

import com.example.data.repository.SecurityUtils
import org.junit.Assert.*
import org.junit.Test

class SecurityUtilsTest {

    @Test
    fun `password hashing verifies matching password and rejects wrong password`() {
        val salt = SecurityUtils.generateSalt()
        val hash = SecurityUtils.hashPassword("ScrollDown#2026", salt)
        assertTrue(SecurityUtils.verifyPassword("ScrollDown#2026", salt, hash))
        assertFalse(SecurityUtils.verifyPassword("WrongPassword", salt, hash))
    }

    @Test
    fun `extractHashtags and extractMentions parse social tokens accurately`() {
        val caption = "Golden hour with @utkarshrai and @Riya_Art #ScrollDown #LuxuryDark #scrolldown"
        val tags = SecurityUtils.extractHashtags(caption)
        val mentions = SecurityUtils.extractMentions(caption)
        assertEquals(listOf("scrolldown", "luxurydark"), tags)
        assertEquals(listOf("utkarshrai", "riya_art"), mentions)
    }

    @Test
    fun `username and email validation enforce security rules`() {
        assertTrue(SecurityUtils.isValidUsername("@utkarshrai"))
        assertTrue(SecurityUtils.isValidUsername("alex_m.01"))
        assertFalse(SecurityUtils.isValidUsername("ab"))
        assertFalse(SecurityUtils.isValidUsername("bad user!"))
        assertTrue(SecurityUtils.isValidEmail("utkarshgaming8029@gmail.com"))
        assertFalse(SecurityUtils.isValidEmail("not-an-email"))
    }

    @Test
    fun `formatCompactCount formats thousands and millions cleanly`() {
        assertEquals("300K", SecurityUtils.formatCompactCount(300_000L))
        assertEquals("1.5M", SecurityUtils.formatCompactCount(1_500_000L))
        assertEquals("42", SecurityUtils.formatCompactCount(42L))
    }
}
