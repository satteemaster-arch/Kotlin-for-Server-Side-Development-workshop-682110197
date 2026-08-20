package org.example

// Workshop #3: Thai citizen ID validator (driven by tests)

fun validateCitizenId(id: String): Boolean {
    if (id.length != 13) return false
    if (!id.all { it.isDigit() }) return false

    // Each of the first 12 digits carries a weight running 13, 12, ... down to 2
    val weightedSum = (0..11).sumOf { i -> id[i].digitToInt() * (13 - i) }
    val checkDigit = (11 - weightedSum % 11) % 10

    return checkDigit == id[12].digitToInt()
}
