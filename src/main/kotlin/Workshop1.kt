package org.example

// Workshop #1: Simple Console Application - Unit Converter

fun main() {
    while (true) {
        println("===== Unit Converter =====")
        println("โปรดเลือกหน่วยที่ต้องการแปลง:")
        println("1. Celsius to Fahrenheit")
        println("2. Kilometers to Miles")
        println("พิมพ์ 'exit' เพื่อออกจากโปรแกรม")
        print("เลือกเมนู (1, 2, or exit): ")

        val choice = readln()

        // 3. ควบคุมการทำงานด้วย when expression
        when (choice.trim().lowercase()) {
            "1" -> convertCelsiusToFahrenheit()
            "2" -> convertKilometersToMiles()
            "exit" -> {
                println("ขอบคุณที่ใช้งาน โปรแกรมจบการทำงาน")
                return
            }
            else -> println("⚠️ ตัวเลือกไม่ถูกต้อง กรุณาเลือก 1, 2 หรือ exit")
        }

        println()
    }
}

// 4. ฟังก์ชันแปลงหน่วย
fun celsiusToFahrenheit(celsius: Double): Double = celsius * 9.0 / 5.0 + 32

fun kilometersToMiles(kilometers: Double): Double = kilometers * 0.621371

// ฟังก์ชันสำหรับจัดการกระบวนการแปลง Celsius to Fahrenheit ทั้งหมด
fun convertCelsiusToFahrenheit() {
    print("ป้อนค่าองศาเซลเซียส (Celsius): ")
    val input = readln()

    // 5. Null Safety ด้วย toDoubleOrNull() + Elvis operator
    val celsius = input.toDoubleOrNull() ?: run {
        println("⚠️ ข้อมูลไม่ถูกต้อง กรุณาป้อนตัวเลขเท่านั้น")
        return
    }

    val fahrenheitResult = celsiusToFahrenheit(celsius)

    // 6. แสดงผลลัพธ์ทศนิยม 2 ตำแหน่ง
    println("ผลลัพธ์: $celsius °C เท่ากับ ${"%.2f".format(fahrenheitResult)} °F")
}

// ฟังก์ชันสำหรับจัดการกระบวนการแปลง Kilometers to Miles ทั้งหมด
fun convertKilometersToMiles() {
    print("ป้อนค่ากิโลเมตร (Kilometers): ")
    val input = readln()

    val kilometers = input.toDoubleOrNull() ?: run {
        println("⚠️ ข้อมูลไม่ถูกต้อง กรุณาป้อนตัวเลขเท่านั้น")
        return
    }

    val milesResult = kilometersToMiles(kilometers)

    println("ผลลัพธ์: $kilometers km เท่ากับ ${"%.2f".format(milesResult)} miles")
}