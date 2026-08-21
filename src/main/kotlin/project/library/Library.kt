package org.example.project.library

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
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant

// --- Project 9: Book Lending Library API ---

@Serializable
data class Book(val id: Int, val title: String, val author: String, val isAvailable: Boolean)

@Serializable
data class BookRequest(val title: String, val author: String)

@Serializable
data class LendingRecord(
    val id: Int,
    val bookId: Int,
    val borrowerName: String,
    val checkoutDate: String,
    val returnDate: String?
)

@Serializable
data class CheckoutRequest(val borrowerName: String)

object Books : Table("library_books") {
    val id = integer("id").autoIncrement()
    val title = varchar("title", 250)
    val author = varchar("author", 200)
    val isAvailable = bool("is_available")
    override val primaryKey = PrimaryKey(id)
}

object LendingRecords : Table("library_lending_records") {
    val id = integer("id").autoIncrement()
    val bookId = integer("book_id").references(Books.id, onDelete = ReferenceOption.CASCADE)
    val borrowerName = varchar("borrower_name", 150)
    val checkoutDate = varchar("checkout_date", 40)

    // Null means the book is still out.
    val returnDate = varchar("return_date", 40).nullable()

    override val primaryKey = PrimaryKey(id)
}

class LendingRuleException(message: String, kind: Kind = Kind.CONFLICT) : BusinessRuleException(message, kind)

object BookRepository {

    private fun toBook(row: ResultRow) = Book(
        id = row[Books.id],
        title = row[Books.title],
        author = row[Books.author],
        isAvailable = row[Books.isAvailable]
    )

    fun getAll(): List<Book> = transaction { Books.selectAll().map(::toBook) }

    fun getById(id: Int): Book? = transaction {
        Books.selectAll().where { Books.id eq id }.map(::toBook).singleOrNull()
    }

    fun getAvailable(): List<Book> = transaction {
        Books.selectAll().where { Books.isAvailable eq true }.map(::toBook)
    }

    /** A newly added book is on the shelf, so availability is not client-supplied. */
    fun add(request: BookRequest): Book = transaction {
        if (request.title.isBlank()) throw LendingRuleException("title cannot be blank", Kind.INVALID)

        val newId = Books.insert {
            it[title] = request.title
            it[author] = request.author
            it[isAvailable] = true
        } get Books.id

        Book(newId, request.title, request.author, true)
    }

    /**
     * Editing a book never changes whether it is lent out - only checkout and
     * return may move that flag, so the two stay consistent.
     */
    fun update(id: Int, request: BookRequest): Book? = transaction {
        val existing = Books.selectAll().where { Books.id eq id }
            .map(::toBook).singleOrNull() ?: return@transaction null

        Books.update({ Books.id eq id }) {
            it[title] = request.title
            it[author] = request.author
        }
        existing.copy(title = request.title, author = request.author)
    }

    fun delete(id: Int): Boolean = transaction {
        val book = Books.selectAll().where { Books.id eq id }
            .map(::toBook).singleOrNull() ?: return@transaction false

        if (!book.isAvailable) {
            throw LendingRuleException("book $id is currently lent out and cannot be deleted")
        }
        Books.deleteWhere { Books.id eq id } > 0
    }
}

object LendingRepository {

    private fun toRecord(row: ResultRow) = LendingRecord(
        id = row[LendingRecords.id],
        bookId = row[LendingRecords.bookId],
        borrowerName = row[LendingRecords.borrowerName],
        checkoutDate = row[LendingRecords.checkoutDate],
        returnDate = row[LendingRecords.returnDate]
    )

    fun getAll(): List<LendingRecord> = transaction {
        LendingRecords.selectAll().map(::toRecord)
    }

    fun getById(id: Int): LendingRecord? = transaction {
        LendingRecords.selectAll().where { LendingRecords.id eq id }.map(::toRecord).singleOrNull()
    }

    fun getByBookId(bookId: Int): List<LendingRecord> = transaction {
        LendingRecords.selectAll().where { LendingRecords.bookId eq bookId }.map(::toRecord)
    }

    /**
     * Checking out writes two things - a new record and the book's flag - and
     * both happen inside one transaction, so the database can never hold a
     * lending record for a book that still claims to be on the shelf.
     *
     * Null when the book does not exist. Throws when it is already lent out.
     */
    fun checkout(bookId: Int, request: CheckoutRequest): LendingRecord? = transaction {
        if (request.borrowerName.isBlank()) throw LendingRuleException("borrowerName cannot be blank", Kind.INVALID)

        val book = Books.selectAll().where { Books.id eq bookId }
            .map { it[Books.isAvailable] }.singleOrNull() ?: return@transaction null

        if (!book) throw LendingRuleException("book $bookId is already lent out")

        val now = Instant.now().toString()
        val newId = LendingRecords.insert {
            it[LendingRecords.bookId] = bookId
            it[borrowerName] = request.borrowerName
            it[checkoutDate] = now
            it[returnDate] = null
        } get LendingRecords.id

        Books.update({ Books.id eq bookId }) { it[isAvailable] = false }

        LendingRecord(newId, bookId, request.borrowerName, now, null)
    }

    /**
     * The mirror of checkout: stamp returnDate and put the book back on the
     * shelf together. Null when the record does not exist.
     */
    fun returnBook(recordId: Int): LendingRecord? = transaction {
        val record = LendingRecords.selectAll().where { LendingRecords.id eq recordId }
            .map(::toRecord).singleOrNull() ?: return@transaction null

        if (record.returnDate != null) {
            throw LendingRuleException("lending record $recordId was already returned on ${record.returnDate}")
        }

        val now = Instant.now().toString()
        LendingRecords.update({ LendingRecords.id eq recordId }) { it[returnDate] = now }
        Books.update({ Books.id eq record.bookId }) { it[isAvailable] = true }

        record.copy(returnDate = now)
    }

    fun delete(id: Int): Boolean = transaction {
        LendingRecords.deleteWhere { LendingRecords.id eq id } > 0
    }
}

fun Route.libraryRoutes() = route("/library") {

    get("/books") {
        val onlyAvailable = call.request.queryParameters["available"]?.toBooleanStrictOrNull()
        val books = if (onlyAvailable == true) BookRepository.getAvailable() else BookRepository.getAll()
        call.respond(HttpStatusCode.OK, books)
    }

    get("/books/{id}") {
        val id = call.intParam("id") ?: return@get call.badId()
        val book = BookRepository.getById(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, "book $id not found")
        call.respond(HttpStatusCode.OK, book)
    }

    post("/books") {
        call.respond(HttpStatusCode.Created, BookRepository.add(call.receive()))
    }

    put("/books/{id}") {
        val id = call.intParam("id") ?: return@put call.badId()
        val updated = BookRepository.update(id, call.receive())
            ?: return@put call.respond(HttpStatusCode.NotFound, "book $id not found")
        call.respond(HttpStatusCode.OK, updated)
    }

    delete("/books/{id}") {
        val id = call.intParam("id") ?: return@delete call.badId()
        if (!BookRepository.delete(id)) {
            return@delete call.respond(HttpStatusCode.NotFound, "book $id not found")
        }
        call.respond(HttpStatusCode.NoContent)
    }

    get("/books/{id}/history") {
        val bookId = call.intParam("id") ?: return@get call.badId()
        if (BookRepository.getById(bookId) == null) {
            return@get call.respond(HttpStatusCode.NotFound, "book $bookId not found")
        }
        call.respond(HttpStatusCode.OK, LendingRepository.getByBookId(bookId))
    }

    // Borrowing is an action on a book, not a plain create.
    post("/books/{id}/checkout") {
        val bookId = call.intParam("id") ?: return@post call.badId()
        val record = LendingRepository.checkout(bookId, call.receive())
            ?: return@post call.respond(HttpStatusCode.NotFound, "book $bookId not found")
        call.respond(HttpStatusCode.Created, record)
    }

    get("/lendings") { call.respond(HttpStatusCode.OK, LendingRepository.getAll()) }

    get("/lendings/{id}") {
        val id = call.intParam("id") ?: return@get call.badId()
        val record = LendingRepository.getById(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, "lending record $id not found")
        call.respond(HttpStatusCode.OK, record)
    }

    post("/lendings/{id}/return") {
        val id = call.intParam("id") ?: return@post call.badId()
        val record = LendingRepository.returnBook(id)
            ?: return@post call.respond(HttpStatusCode.NotFound, "lending record $id not found")
        call.respond(HttpStatusCode.OK, record)
    }

    delete("/lendings/{id}") {
        val id = call.intParam("id") ?: return@delete call.badId()
        if (!LendingRepository.delete(id)) {
            return@delete call.respond(HttpStatusCode.NotFound, "lending record $id not found")
        }
        call.respond(HttpStatusCode.NoContent)
    }
}
