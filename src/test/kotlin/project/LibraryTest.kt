package org.example.project

import org.example.project.library.BookRepository
import org.example.project.library.BookRequest
import org.example.project.library.CheckoutRequest
import org.example.project.library.LendingRepository
import org.example.project.library.LendingRuleException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Project 9: two tables must change together or not at all. */
class LibraryTest {

    @BeforeTest
    fun setUp() {
        DatabaseFactory.init("librarytest")
        DatabaseFactory.clearAll()
    }

    private fun aBook(title: String = "Kotlin in Action") =
        BookRepository.add(BookRequest(title, "Dmitry Jemerov")).id

    @Test
    fun `a new book starts available`() {
        val id = aBook()
        assertTrue(BookRepository.getById(id)!!.isAvailable)
    }

    @Test
    fun `checking out marks the book unavailable and opens a record`() {
        // Arrange
        val id = aBook()

        // Act
        val record = LendingRepository.checkout(id, CheckoutRequest("สมชาย"))

        // Assert - both sides of the transaction landed
        assertNotNull(record)
        assertNull(record.returnDate, "an open loan has no return date yet")
        assertFalse(BookRepository.getById(id)!!.isAvailable)
    }

    @Test
    fun `a book that is already lent out cannot be lent again`() {
        // Arrange
        val id = aBook()
        LendingRepository.checkout(id, CheckoutRequest("สมชาย"))

        // Act & Assert
        val error = assertFailsWith<LendingRuleException> {
            LendingRepository.checkout(id, CheckoutRequest("สมหญิง"))
        }
        assertTrue(error.message!!.contains("already lent out"))
        assertEquals(1, LendingRepository.getByBookId(id).size, "the rejected loan must not be recorded")
    }

    @Test
    fun `returning stamps the date and puts the book back on the shelf`() {
        // Arrange
        val id = aBook()
        val record = LendingRepository.checkout(id, CheckoutRequest("สมชาย"))!!

        // Act
        val returned = LendingRepository.returnBook(record.id)

        // Assert
        assertNotNull(returned)
        assertNotNull(returned.returnDate)
        assertTrue(BookRepository.getById(id)!!.isAvailable)
    }

    @Test
    fun `a returned book can be borrowed again`() {
        // Arrange
        val id = aBook()
        val first = LendingRepository.checkout(id, CheckoutRequest("สมชาย"))!!
        LendingRepository.returnBook(first.id)

        // Act
        val second = LendingRepository.checkout(id, CheckoutRequest("สมหญิง"))

        // Assert
        assertNotNull(second)
        assertEquals(2, LendingRepository.getByBookId(id).size, "history keeps both loans")
    }

    @Test
    fun `returning the same record twice is rejected`() {
        // Arrange
        val id = aBook()
        val record = LendingRepository.checkout(id, CheckoutRequest("สมชาย"))!!
        LendingRepository.returnBook(record.id)

        // Act & Assert
        assertFailsWith<LendingRuleException> { LendingRepository.returnBook(record.id) }
    }

    @Test
    fun `editing a book does not change whether it is lent out`() {
        // Only checkout and return may move that flag.
        val id = aBook()
        LendingRepository.checkout(id, CheckoutRequest("สมชาย"))

        BookRepository.update(id, BookRequest("Kotlin in Action, 2nd Edition", "Dmitry Jemerov"))

        assertFalse(BookRepository.getById(id)!!.isAvailable)
    }

    @Test
    fun `a book that is lent out cannot be deleted`() {
        val id = aBook()
        LendingRepository.checkout(id, CheckoutRequest("สมชาย"))
        assertFailsWith<LendingRuleException> { BookRepository.delete(id) }
    }

    @Test
    fun `available filter hides books that are out`() {
        // Arrange
        val onShelf = aBook("อยู่บนชั้น")
        val lentOut = aBook("ถูกยืมไป")
        LendingRepository.checkout(lentOut, CheckoutRequest("สมชาย"))

        // Act
        val available = BookRepository.getAvailable()

        // Assert
        assertEquals(1, available.size)
        assertEquals(onShelf, available.first().id)
    }

    @Test
    fun `checking out a book that does not exist returns null`() {
        assertNull(LendingRepository.checkout(999, CheckoutRequest("สมชาย")))
    }

    @Test
    fun `a blank borrower name is rejected`() {
        val id = aBook()
        assertFailsWith<LendingRuleException> { LendingRepository.checkout(id, CheckoutRequest("  ")) }
    }
}
