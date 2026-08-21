package org.example.project

import org.example.project.expense.ExpenseCategoryRepository
import org.example.project.expense.ExpenseCategoryRequest
import org.example.project.expense.ExpenseRuleException
import org.example.project.expense.TransactionRepository
import org.example.project.expense.TransactionRequest
import org.example.project.expense.TransactionType
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Project 5: the monthly report built with SUM + GROUP BY. */
class ExpenseTest {

    @BeforeTest
    fun setUp() {
        DatabaseFactory.init("expensetest")
        DatabaseFactory.clearAll()
    }

    private fun spend(categoryId: Int, amount: Double, date: String) =
        TransactionRepository.add(
            TransactionRequest("ค่าใช้จ่าย", amount, TransactionType.EXPENSE, date, categoryId)
        )

    private fun earn(categoryId: Int, amount: Double, date: String) =
        TransactionRepository.add(
            TransactionRequest("รายรับ", amount, TransactionType.INCOME, date, categoryId)
        )

    @Test
    fun `monthly report groups expenses by category`() {
        // Arrange
        val food = ExpenseCategoryRepository.add(ExpenseCategoryRequest("อาหาร")).id
        val travel = ExpenseCategoryRepository.add(ExpenseCategoryRequest("เดินทาง")).id
        spend(food, 120.0, "2026-08-01")
        spend(food, 80.0, "2026-08-15")
        spend(travel, 300.0, "2026-08-20")

        // Act
        val report = TransactionRepository.monthlyReport(2026, 8)

        // Assert
        assertEquals(2, report.expenseByCategory.size)
        assertEquals(500.0, report.totalExpense, "120 + 80 food, plus 300 travel")
        // Sorted biggest first, so travel leads.
        assertEquals("เดินทาง", report.expenseByCategory.first().categoryName)
        assertEquals(300.0, report.expenseByCategory.first().total)
        assertEquals(200.0, report.expenseByCategory.last().total)
    }

    @Test
    fun `the report ignores transactions from other months`() {
        // Arrange
        val food = ExpenseCategoryRepository.add(ExpenseCategoryRequest("อาหาร")).id
        spend(food, 100.0, "2026-08-10")
        spend(food, 999.0, "2026-09-10")
        spend(food, 555.0, "2025-08-10")

        // Act
        val report = TransactionRepository.monthlyReport(2026, 8)

        // Assert
        assertEquals(100.0, report.totalExpense, "only August 2026 counts")
    }

    @Test
    fun `income and expense are summed separately and balanced`() {
        // Arrange
        val salary = ExpenseCategoryRepository.add(ExpenseCategoryRequest("เงินเดือน")).id
        val food = ExpenseCategoryRepository.add(ExpenseCategoryRequest("อาหาร")).id
        earn(salary, 25000.0, "2026-08-01")
        spend(food, 5000.0, "2026-08-05")

        // Act
        val report = TransactionRepository.monthlyReport(2026, 8)

        // Assert
        assertEquals(25000.0, report.totalIncome)
        assertEquals(5000.0, report.totalExpense)
        assertEquals(20000.0, report.balance)
    }

    @Test
    fun `a month with no data reports zeroes instead of failing`() {
        val report = TransactionRepository.monthlyReport(2026, 1)
        assertEquals(0.0, report.totalIncome)
        assertEquals(0.0, report.totalExpense)
        assertEquals(emptyList(), report.expenseByCategory)
    }

    @Test
    fun `a single digit month is zero padded when matching dates`() {
        // "2026-3%" would also match 2026-30-xx if the padding were missing.
        val food = ExpenseCategoryRepository.add(ExpenseCategoryRequest("อาหาร")).id
        spend(food, 50.0, "2026-03-09")

        assertEquals(50.0, TransactionRepository.monthlyReport(2026, 3).totalExpense)
    }

    @Test
    fun `month 13 is rejected`() {
        assertFailsWith<ExpenseRuleException> { TransactionRepository.monthlyReport(2026, 13) }
    }

    @Test
    fun `a negative amount is rejected because the sign lives in type`() {
        val food = ExpenseCategoryRepository.add(ExpenseCategoryRequest("อาหาร")).id
        assertFailsWith<ExpenseRuleException> { spend(food, -100.0, "2026-08-01") }
    }

    @Test
    fun `a malformed date is rejected`() {
        val food = ExpenseCategoryRepository.add(ExpenseCategoryRequest("อาหาร")).id
        assertFailsWith<ExpenseRuleException> { spend(food, 100.0, "21/08/2026") }
    }

    @Test
    fun `a transaction needs a category that exists`() {
        assertNull(spend(categoryId = 999, amount = 100.0, date = "2026-08-01"))
    }
}
