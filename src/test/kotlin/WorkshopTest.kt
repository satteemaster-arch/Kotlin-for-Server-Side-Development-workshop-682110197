package org.example

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkshopTest {

    // --- Tests for Workshop #1: Unit Converter ---

    @Test
    fun `test celsiusToFahrenheit with positive value`() {
        val celsiusInput = 20.0
        val expectedFahrenheit = 68.0
        val actualFahrenheit = celsiusToFahrenheit(celsiusInput)
        assertEquals(expectedFahrenheit, actualFahrenheit, 0.001, "20°C should be 68°F")
    }

    @Test
    fun `test celsiusToFahrenheit with zero`() {
        val celsiusInput = 0.0
        val expectedFahrenheit = 32.0
        val actualFahrenheit = celsiusToFahrenheit(celsiusInput)
        assertEquals(expectedFahrenheit, actualFahrenheit, 0.001, "0°C should be 32°F")
    }

    @Test
    fun `test celsiusToFahrenheit with negative value`() {
        val celsiusInput = -10.0
        val expectedFahrenheit = 14.0
        val actualFahrenheit = celsiusToFahrenheit(celsiusInput)
        assertEquals(expectedFahrenheit, actualFahrenheit, 0.001, "-10°C should be 14°F")
    }

    @Test
    fun `test kilometersToMiles with one kilometer`() {
        val kilometersInput = 1.0
        val expectedMiles = 0.621371
        val actualMiles = kilometersToMiles(kilometersInput)
        assertEquals(expectedMiles, actualMiles, 0.000001, "1 km should be approximately 0.621371 miles")
    }

    // --- Tests for Workshop #1: Unit Converter End ---


    // --- Tests for Workshop #2: Data Analysis Pipeline ---

    @Test
    fun `test calculateTotalElectronicsPriceOver500`() {
        // Arrange
        val products = listOf(
            Product("Laptop", 1200.0, "Electronics"),
            Product("Smartwatch", 500.0, "Electronics"), // 🚨 Edge case: 500 พอดี (ไม่ควรถูกนำมาบวก เพราะเงื่อนไขคือ > 500)
            Product("Mouse", 300.0, "Electronics"),
            Product("Smartphone", 800.0, "Electronics"),
            Product("Desk", 1000.0, "Furniture")
        )
        // ผลรวมที่คาดหวังคือ 1200.0 + 800.0 = 2000.0
        val expectedTotal = 2000.0

        // Act
        val actualTotal = calculateTotalElectronicsPriceOver500(products)

        // Assert
        assertEquals(expectedTotal, actualTotal, 0.001, "Total price should be 2000.0 (excluding exactly 500.0)")
    }

    @Test
    fun `test countElectronicsOver500`() {
        // Arrange
        val products = listOf(
            Product("Laptop", 1200.0, "Electronics"),
            Product("Smartwatch", 500.0, "Electronics"), // 🚨 Edge case: 500 พอดี (ไม่ควรถูกนับ)
            Product("Mouse", 300.0, "Electronics"),
            Product("Smartphone", 800.0, "Electronics"),
            Product("Desk", 1000.0, "Furniture")
        )
        // จำนวนที่คาดหวังคือ 2 ชิ้น (Laptop และ Smartphone)
        val expectedCount = 2

        // Act
        val actualCount = countElectronicsOver500(products)

        // Assert
        assertEquals(expectedCount, actualCount, "Count of Electronics over 500 should be 2 (excluding exactly 500.0)")
    }

    // --- Tests for Workshop #2: Data Analysis Pipeline End ---

    // --- Tests for Workshop #3: Citizen ID Validator ---

    @Test
    fun `valid id returns true`() {
        // Arrange
        val id = "3509900547250"

        // Act
        val result = validateCitizenId(id)

        // Assert
        assertTrue(result, "a well-formed 13-digit id should be accepted")
    }

    @Test
    fun `id with wrong length returns false`() {
        // Arrange
        val tooShort = "350990054725"    // 12 digits
        val tooLong = "35099005472500"   // 14 digits

        // Act & Assert
        assertFalse(validateCitizenId(tooShort), "12 digits is too short")
        assertFalse(validateCitizenId(tooLong), "14 digits is too long")
    }

    @Test
    fun `id with letters returns false`() {
        // Arrange
        val id = "12345678901AB"  // exactly 13 chars, but two of them are letters

        // Act
        val result = validateCitizenId(id)

        // Assert
        assertFalse(result, "an id containing letters should be rejected")
    }

    @Test
    fun `id with wrong checksum returns false`() {
        // หลักที่ 13 ต้องเป็น check digit ที่คำนวณจาก 12 หลักแรก
        // 110170018520 → check digit ที่ถูกต้องคือ 6
        assertFalse(validateCitizenId("1101700185207")) // หลักสุดท้ายผิด
        assertFalse(validateCitizenId("1234567890129")) // ที่ถูกคือ ...1

        // ใบที่ checksum ถูกต้อง ต้องยังผ่านอยู่
        assertTrue(validateCitizenId("3509900547250"))
        assertTrue(validateCitizenId("1234567890121"))
    }

    // --- Tests for Workshop #3: Citizen ID Validator End ---
}
