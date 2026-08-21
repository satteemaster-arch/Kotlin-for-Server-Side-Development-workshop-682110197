package org.example.project.expense

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.example.project.badId
import org.example.project.intParam
import org.example.project.BusinessRuleException
import org.example.project.BusinessRuleException.Kind
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

// --- Project 5: Expense Tracker API ---

enum class TransactionType { INCOME, EXPENSE }

@Serializable
data class ExpenseCategory(val id: Int, val name: String)

@Serializable
data class ExpenseCategoryRequest(val name: String)

@Serializable
data class Transaction(
    val id: Int,
    val description: String,
    val amount: Double,
    val type: TransactionType,
    val date: String,
    val categoryId: Int
)

@Serializable
data class TransactionRequest(
    val description: String,
    val amount: Double,
    val type: TransactionType,
    val date: String,
    val categoryId: Int
)

/** One line of the monthly report: how much a single category accounted for. */
@Serializable
data class CategoryTotal(
    val categoryId: Int,
    val categoryName: String,
    val total: Double
)

@Serializable
data class MonthlyReport(
    val year: Int,
    val month: Int,
    val totalIncome: Double,
    val totalExpense: Double,
    val balance: Double,
    val expenseByCategory: List<CategoryTotal>
)

object ExpenseCategories : Table("expense_categories") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 100)
    override val primaryKey = PrimaryKey(id)
}

object Transactions : Table("expense_transactions") {
    val id = integer("id").autoIncrement()
    val description = varchar("description", 250)
    val amount = double("amount")
    val type = varchar("type", 10)

    // ISO-8601 date, e.g. 2026-08-21, so "2026-08%" selects a whole month.
    val date = varchar("date", 10)

    val categoryId = integer("category_id")
        .references(ExpenseCategories.id, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(id)
}

class ExpenseRuleException(message: String, kind: Kind = Kind.CONFLICT) : BusinessRuleException(message, kind)

object ExpenseCategoryRepository {

    private fun toCategory(row: ResultRow) =
        ExpenseCategory(row[ExpenseCategories.id], row[ExpenseCategories.name])

    fun getAll(): List<ExpenseCategory> = transaction {
        ExpenseCategories.selectAll().map(::toCategory)
    }

    fun getById(id: Int): ExpenseCategory? = transaction {
        ExpenseCategories.selectAll().where { ExpenseCategories.id eq id }
            .map(::toCategory).singleOrNull()
    }

    fun add(request: ExpenseCategoryRequest): ExpenseCategory = transaction {
        val newId = ExpenseCategories.insert { it[name] = request.name } get ExpenseCategories.id
        ExpenseCategory(newId, request.name)
    }

    fun update(id: Int, request: ExpenseCategoryRequest): ExpenseCategory? = transaction {
        val changed = ExpenseCategories.update({ ExpenseCategories.id eq id }) {
            it[name] = request.name
        }
        if (changed == 0) null else ExpenseCategory(id, request.name)
    }

    fun delete(id: Int): Boolean = transaction {
        ExpenseCategories.deleteWhere { ExpenseCategories.id eq id } > 0
    }
}

object TransactionRepository {

    private fun toTransaction(row: ResultRow) = Transaction(
        id = row[Transactions.id],
        description = row[Transactions.description],
        amount = row[Transactions.amount],
        type = TransactionType.valueOf(row[Transactions.type]),
        date = row[Transactions.date],
        categoryId = row[Transactions.categoryId]
    )

    private fun requireValid(request: TransactionRequest) {
        if (request.amount <= 0) {
            // The sign lives in `type`, so the amount itself is always positive.
            throw ExpenseRuleException("amount must be greater than zero; use type to say income or expense", Kind.INVALID)
        }
        if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(request.date)) {
            throw ExpenseRuleException("date must look like 2026-08-21", Kind.INVALID)
        }
    }

    fun getAll(): List<Transaction> = transaction { Transactions.selectAll().map(::toTransaction) }

    fun getById(id: Int): Transaction? = transaction {
        Transactions.selectAll().where { Transactions.id eq id }.map(::toTransaction).singleOrNull()
    }

    fun getByCategory(categoryId: Int): List<Transaction> = transaction {
        Transactions.selectAll().where { Transactions.categoryId eq categoryId }.map(::toTransaction)
    }

    /** Null when the category does not exist. */
    fun add(request: TransactionRequest): Transaction? = transaction {
        requireValid(request)

        val categoryExists = ExpenseCategories.selectAll()
            .where { ExpenseCategories.id eq request.categoryId }.any()
        if (!categoryExists) return@transaction null

        val newId = Transactions.insert {
            it[description] = request.description
            it[amount] = request.amount
            it[type] = request.type.name
            it[date] = request.date
            it[categoryId] = request.categoryId
        } get Transactions.id

        Transaction(
            newId,
            request.description,
            request.amount,
            request.type,
            request.date,
            request.categoryId
        )
    }

    fun update(id: Int, request: TransactionRequest): Transaction? = transaction {
        requireValid(request)
        val changed = Transactions.update({ Transactions.id eq id }) {
            it[description] = request.description
            it[amount] = request.amount
            it[type] = request.type.name
            it[date] = request.date
            it[categoryId] = request.categoryId
        }
        if (changed == 0) null
        else Transaction(
            id,
            request.description,
            request.amount,
            request.type,
            request.date,
            request.categoryId
        )
    }

    fun delete(id: Int): Boolean = transaction {
        Transactions.deleteWhere { Transactions.id eq id } > 0
    }

    /**
     * Monthly summary built with SUM + GROUP BY in the database rather than
     * by pulling every row into Kotlin and folding it there.
     */
    fun monthlyReport(year: Int, month: Int): MonthlyReport = transaction {
        if (month !in 1..12) throw ExpenseRuleException("month must be between 1 and 12", Kind.INVALID)

        val prefix = "%04d-%02d".format(year, month) + "%"
        val amountSum = Transactions.amount.sum()

        val perCategory = Transactions
            .select(Transactions.categoryId, amountSum)
            .where { (Transactions.date like prefix) and (Transactions.type eq TransactionType.EXPENSE.name) }
            .groupBy(Transactions.categoryId)
            .map { row -> row[Transactions.categoryId] to (row[amountSum] ?: 0.0) }

        val categoryNames = ExpenseCategories.selectAll()
            .associate { it[ExpenseCategories.id] to it[ExpenseCategories.name] }

        val expenseByCategory = perCategory.map { (categoryId, total) ->
            CategoryTotal(categoryId, categoryNames[categoryId] ?: "unknown", total)
        }.sortedByDescending { it.total }

        val totalExpense = expenseByCategory.sumOf { it.total }

        val totalIncome = Transactions
            .select(amountSum)
            .where { (Transactions.date like prefix) and (Transactions.type eq TransactionType.INCOME.name) }
            .firstOrNull()?.get(amountSum) ?: 0.0

        MonthlyReport(
            year = year,
            month = month,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            balance = totalIncome - totalExpense,
            expenseByCategory = expenseByCategory
        )
    }
}

fun Route.expenseRoutes() = route("/expenses") {

    get("/categories") { call.respond(HttpStatusCode.OK, ExpenseCategoryRepository.getAll()) }

    get("/categories/{id}") {
        val id = call.intParam("id") ?: return@get call.badId()
        val category = ExpenseCategoryRepository.getById(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, "category $id not found")
        call.respond(HttpStatusCode.OK, category)
    }

    post("/categories") {
        call.respond(HttpStatusCode.Created, ExpenseCategoryRepository.add(call.receive()))
    }

    put("/categories/{id}") {
        val id = call.intParam("id") ?: return@put call.badId()
        val updated = ExpenseCategoryRepository.update(id, call.receive())
            ?: return@put call.respond(HttpStatusCode.NotFound, "category $id not found")
        call.respond(HttpStatusCode.OK, updated)
    }

    delete("/categories/{id}") {
        val id = call.intParam("id") ?: return@delete call.badId()
        if (!ExpenseCategoryRepository.delete(id)) {
            return@delete call.respond(HttpStatusCode.NotFound, "category $id not found")
        }
        call.respond(HttpStatusCode.NoContent)
    }

    get("/transactions") { call.respond(HttpStatusCode.OK, TransactionRepository.getAll()) }

    get("/transactions/{id}") {
        val id = call.intParam("id") ?: return@get call.badId()
        val found = TransactionRepository.getById(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, "transaction $id not found")
        call.respond(HttpStatusCode.OK, found)
    }

    post("/transactions") {
        val created = TransactionRepository.add(call.receive())
            ?: return@post call.respond(HttpStatusCode.BadRequest, "category does not exist")
        call.respond(HttpStatusCode.Created, created)
    }

    put("/transactions/{id}") {
        val id = call.intParam("id") ?: return@put call.badId()
        val updated = TransactionRepository.update(id, call.receive())
            ?: return@put call.respond(HttpStatusCode.NotFound, "transaction $id not found")
        call.respond(HttpStatusCode.OK, updated)
    }

    delete("/transactions/{id}") {
        val id = call.intParam("id") ?: return@delete call.badId()
        if (!TransactionRepository.delete(id)) {
            return@delete call.respond(HttpStatusCode.NotFound, "transaction $id not found")
        }
        call.respond(HttpStatusCode.NoContent)
    }

    get("/reports/monthly") {
        val year = call.request.queryParameters["year"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "query parameter 'year' is required")
        val month = call.request.queryParameters["month"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "query parameter 'month' is required")

        call.respond(HttpStatusCode.OK, TransactionRepository.monthlyReport(year, month))
    }
}
