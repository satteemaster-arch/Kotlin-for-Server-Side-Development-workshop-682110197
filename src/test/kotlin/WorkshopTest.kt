package org.example
import kotlin.test.Test
import kotlin.test.assertEquals

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
}