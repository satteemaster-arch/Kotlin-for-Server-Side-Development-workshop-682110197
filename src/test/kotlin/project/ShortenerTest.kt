package org.example.project

import org.example.project.shortener.ShortUrlRepository
import org.example.project.shortener.ShortenRequest
import org.example.project.shortener.ShortenerRuleException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Project 2: unique codes and click counting. */
class ShortenerTest {

    @BeforeTest
    fun setUp() {
        DatabaseFactory.init("shortenertest")
        DatabaseFactory.clearAll()
    }

    @Test
    fun `shorten returns a code and a short url pointing at it`() {
        // Act
        val created = ShortUrlRepository.shorten(ShortenRequest("https://kotlinlang.org/docs/home.html"))

        // Assert
        assertEquals(7, created.shortCode.length)
        assertEquals("/s/${created.shortCode}", created.shortUrl)
        assertEquals(0, created.clickCount, "a fresh link has never been clicked")
    }

    @Test
    fun `two shortened urls never share a code`() {
        // Even the same long URL gets its own code, so their click counts stay separate.
        val first = ShortUrlRepository.shorten(ShortenRequest("https://example.com"))
        val second = ShortUrlRepository.shorten(ShortenRequest("https://example.com"))
        assertNotEquals(first.shortCode, second.shortCode)
    }

    @Test
    fun `a url without a scheme is rejected`() {
        val error = assertFailsWith<ShortenerRuleException> {
            ShortUrlRepository.shorten(ShortenRequest("example.com/no-scheme"))
        }
        assertTrue(error.message!!.contains("http"))
    }

    @Test
    fun `resolving a code counts the click`() {
        // Arrange
        val created = ShortUrlRepository.shorten(ShortenRequest("https://kotlinlang.org"))

        // Act
        ShortUrlRepository.resolveAndCount(created.shortCode)
        ShortUrlRepository.resolveAndCount(created.shortCode)
        val third = ShortUrlRepository.resolveAndCount(created.shortCode)

        // Assert
        assertEquals(3, third?.clickCount)
        assertEquals(3, ShortUrlRepository.getByCode(created.shortCode)?.clickCount)
    }

    @Test
    fun `resolving keeps pointing at the original long url`() {
        val target = "https://kotlinlang.org/docs/ktor.html"
        val created = ShortUrlRepository.shorten(ShortenRequest(target))
        assertEquals(target, ShortUrlRepository.resolveAndCount(created.shortCode)?.longUrl)
    }

    @Test
    fun `resolving an unknown code returns null`() {
        assertNull(ShortUrlRepository.resolveAndCount("nothere"))
    }

    @Test
    fun `a long url is trimmed before it is stored`() {
        val created = ShortUrlRepository.shorten(ShortenRequest("  https://example.com/path  "))
        assertEquals("https://example.com/path", created.longUrl)
    }
}
