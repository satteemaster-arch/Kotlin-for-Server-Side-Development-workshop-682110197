package org.example.project.inventory

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

// --- Project 1: Simple E-commerce Inventory API ---

@Serializable
data class Category(val id: Int, val name: String)

@Serializable
data class CategoryRequest(val name: String)

@Serializable
data class Product(
    val id: Int,
    val name: String,
    val description: String,
    val price: Double,
    val stockQuantity: Int,
    val categoryId: Int
)

@Serializable
data class ProductRequest(
    val name: String,
    val description: String,
    val price: Double,
    val stockQuantity: Int,
    val categoryId: Int
)

@Serializable
data class StockChangeRequest(val amount: Int)

object Categories : Table("inv_categories") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 100)
    override val primaryKey = PrimaryKey(id)
}

object Products : Table("inv_products") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 200)
    val description = text("description")
    val price = double("price")
    val stockQuantity = integer("stock_quantity")

    // Many-to-One: many products belong to one category.
    val categoryId = integer("category_id")
        .references(Categories.id, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(id)
}

/** Raised when a well-formed request breaks a business rule. */
class InventoryRuleException(message: String, kind: Kind = Kind.CONFLICT) : BusinessRuleException(message, kind)

object CategoryRepository {

    private fun toCategory(row: ResultRow) = Category(row[Categories.id], row[Categories.name])

    fun getAll(): List<Category> = transaction { Categories.selectAll().map(::toCategory) }

    fun getById(id: Int): Category? = transaction {
        Categories.selectAll().where { Categories.id eq id }.map(::toCategory).singleOrNull()
    }

    fun add(request: CategoryRequest): Category = transaction {
        val newId = Categories.insert { it[name] = request.name } get Categories.id
        Category(newId, request.name)
    }

    fun update(id: Int, request: CategoryRequest): Category? = transaction {
        val changed = Categories.update({ Categories.id eq id }) { it[name] = request.name }
        if (changed == 0) null else Category(id, request.name)
    }

    fun delete(id: Int): Boolean = transaction {
        Categories.deleteWhere { Categories.id eq id } > 0
    }
}

object ProductRepository {

    private fun toProduct(row: ResultRow) = Product(
        id = row[Products.id],
        name = row[Products.name],
        description = row[Products.description],
        price = row[Products.price],
        stockQuantity = row[Products.stockQuantity],
        categoryId = row[Products.categoryId]
    )

    fun getAll(): List<Product> = transaction { Products.selectAll().map(::toProduct) }

    fun getById(id: Int): Product? = transaction {
        Products.selectAll().where { Products.id eq id }.map(::toProduct).singleOrNull()
    }

    fun getByCategory(categoryId: Int): List<Product> = transaction {
        Products.selectAll().where { Products.categoryId eq categoryId }.map(::toProduct)
    }

    /** Null when the category does not exist. Throws when stock or price is negative. */
    fun add(request: ProductRequest): Product? = transaction {
        if (request.stockQuantity < 0) throw InventoryRuleException("stockQuantity cannot start negative", Kind.INVALID)
        if (request.price < 0) throw InventoryRuleException("price cannot be negative", Kind.INVALID)

        val categoryExists = Categories.selectAll().where { Categories.id eq request.categoryId }.any()
        if (!categoryExists) return@transaction null

        val newId = Products.insert {
            it[name] = request.name
            it[description] = request.description
            it[price] = request.price
            it[stockQuantity] = request.stockQuantity
            it[categoryId] = request.categoryId
        } get Products.id

        Product(
            newId,
            request.name,
            request.description,
            request.price,
            request.stockQuantity,
            request.categoryId
        )
    }

    fun update(id: Int, request: ProductRequest): Product? = transaction {
        if (request.stockQuantity < 0) throw InventoryRuleException("stockQuantity cannot be negative", Kind.INVALID)

        val changed = Products.update({ Products.id eq id }) {
            it[name] = request.name
            it[description] = request.description
            it[price] = request.price
            it[stockQuantity] = request.stockQuantity
            it[categoryId] = request.categoryId
        }
        if (changed == 0) null
        else Product(
            id,
            request.name,
            request.description,
            request.price,
            request.stockQuantity,
            request.categoryId
        )
    }

    fun delete(id: Int): Boolean = transaction {
        Products.deleteWhere { Products.id eq id } > 0
    }

    /**
     * Adds [amount] to the stock; a negative amount removes stock.
     * Read-check-write happens inside one transaction, so two callers cannot
     * both pass the check and drive the stock below zero.
     */
    fun changeStock(id: Int, amount: Int): Product? = transaction {
        val current = Products.selectAll().where { Products.id eq id }
            .map(::toProduct).singleOrNull() ?: return@transaction null

        val newQuantity = current.stockQuantity + amount
        if (newQuantity < 0) {
            throw InventoryRuleException(
                "stock cannot go negative: have ${current.stockQuantity}, requested change $amount"
            )
        }

        Products.update({ Products.id eq id }) { it[stockQuantity] = newQuantity }
        current.copy(stockQuantity = newQuantity)
    }
}

fun Route.inventoryRoutes() = route("/inventory") {

    get("/categories") { call.respond(HttpStatusCode.OK, CategoryRepository.getAll()) }

    get("/categories/{id}") {
        val id = call.intParam("id") ?: return@get call.badId()
        val category = CategoryRepository.getById(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, "category $id not found")
        call.respond(HttpStatusCode.OK, category)
    }

    post("/categories") {
        call.respond(HttpStatusCode.Created, CategoryRepository.add(call.receive()))
    }

    put("/categories/{id}") {
        val id = call.intParam("id") ?: return@put call.badId()
        val updated = CategoryRepository.update(id, call.receive())
            ?: return@put call.respond(HttpStatusCode.NotFound, "category $id not found")
        call.respond(HttpStatusCode.OK, updated)
    }

    delete("/categories/{id}") {
        val id = call.intParam("id") ?: return@delete call.badId()
        if (!CategoryRepository.delete(id)) {
            return@delete call.respond(HttpStatusCode.NotFound, "category $id not found")
        }
        call.respond(HttpStatusCode.NoContent)
    }

    get("/products") { call.respond(HttpStatusCode.OK, ProductRepository.getAll()) }

    get("/products/{id}") {
        val id = call.intParam("id") ?: return@get call.badId()
        val product = ProductRepository.getById(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, "product $id not found")
        call.respond(HttpStatusCode.OK, product)
    }

    post("/products") {
        val created = ProductRepository.add(call.receive())
            ?: return@post call.respond(HttpStatusCode.BadRequest, "category does not exist")
        call.respond(HttpStatusCode.Created, created)
    }

    put("/products/{id}") {
        val id = call.intParam("id") ?: return@put call.badId()
        val updated = ProductRepository.update(id, call.receive())
            ?: return@put call.respond(HttpStatusCode.NotFound, "product $id not found")
        call.respond(HttpStatusCode.OK, updated)
    }

    delete("/products/{id}") {
        val id = call.intParam("id") ?: return@delete call.badId()
        if (!ProductRepository.delete(id)) {
            return@delete call.respond(HttpStatusCode.NotFound, "product $id not found")
        }
        call.respond(HttpStatusCode.NoContent)
    }

    // Non-CRUD action: adjust stock without replacing the whole product.
    post("/products/{id}/add-stock") {
        val id = call.intParam("id") ?: return@post call.badId()
        val request = call.receive<StockChangeRequest>()
        val updated = ProductRepository.changeStock(id, request.amount)
            ?: return@post call.respond(HttpStatusCode.NotFound, "product $id not found")
        call.respond(HttpStatusCode.OK, updated)
    }
}
