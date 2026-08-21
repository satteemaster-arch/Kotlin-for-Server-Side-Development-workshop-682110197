package org.example.project.recipe

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
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

// --- Project 3: Recipe Book API ---

@Serializable
data class Recipe(val id: Int, val name: String, val instructions: String)

@Serializable
data class RecipeRequest(val name: String, val instructions: String)

@Serializable
data class Ingredient(
    val id: Int,
    val recipeId: Int,
    val name: String,
    val quantity: Double,
    val unit: String
)

@Serializable
data class IngredientRequest(val name: String, val quantity: Double, val unit: String)

/** A recipe with its ingredients nested, so the client needs one call, not two. */
@Serializable
data class RecipeWithIngredients(
    val id: Int,
    val name: String,
    val instructions: String,
    val ingredients: List<Ingredient>
)

object Recipes : Table("recipes") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 200)
    val instructions = text("instructions")
    override val primaryKey = PrimaryKey(id)
}

object Ingredients : Table("ingredients") {
    val id = integer("id").autoIncrement()

    // One-to-many: a recipe owns its ingredients.
    val recipeId = integer("recipe_id")
        .references(Recipes.id, onDelete = ReferenceOption.CASCADE)

    val name = varchar("name", 150)
    val quantity = double("quantity")
    val unit = varchar("unit", 50)

    override val primaryKey = PrimaryKey(id)
}

object RecipeRepository {

    private fun toRecipe(row: ResultRow) =
        Recipe(row[Recipes.id], row[Recipes.name], row[Recipes.instructions])

    fun getAll(): List<Recipe> = transaction { Recipes.selectAll().map(::toRecipe) }

    fun getById(id: Int): Recipe? = transaction {
        Recipes.selectAll().where { Recipes.id eq id }.map(::toRecipe).singleOrNull()
    }

    fun getWithIngredients(id: Int): RecipeWithIngredients? = transaction {
        val recipe = Recipes.selectAll().where { Recipes.id eq id }
            .map(::toRecipe).singleOrNull() ?: return@transaction null

        RecipeWithIngredients(
            id = recipe.id,
            name = recipe.name,
            instructions = recipe.instructions,
            ingredients = IngredientRepository.getByRecipeId(id)
        )
    }

    /**
     * Finds every recipe that uses an ingredient whose name contains [ingredient].
     * Case-insensitive, and a recipe listing the same ingredient twice is
     * still returned once.
     */
    fun searchByIngredient(ingredient: String): List<Recipe> = transaction {
        val needle = "%${ingredient.lowercase()}%"

        val recipeIds = Ingredients
            .selectAll()
            .where { Ingredients.name.lowerCase() like needle }
            .map { it[Ingredients.recipeId] }
            .distinct()

        if (recipeIds.isEmpty()) return@transaction emptyList()

        recipeIds.mapNotNull { recipeId ->
            Recipes.selectAll().where { Recipes.id eq recipeId }.map(::toRecipe).singleOrNull()
        }
    }

    fun add(request: RecipeRequest): Recipe = transaction {
        val newId = Recipes.insert {
            it[name] = request.name
            it[instructions] = request.instructions
        } get Recipes.id
        Recipe(newId, request.name, request.instructions)
    }

    fun update(id: Int, request: RecipeRequest): Recipe? = transaction {
        val changed = Recipes.update({ Recipes.id eq id }) {
            it[name] = request.name
            it[instructions] = request.instructions
        }
        if (changed == 0) null else Recipe(id, request.name, request.instructions)
    }

    fun delete(id: Int): Boolean = transaction {
        Recipes.deleteWhere { Recipes.id eq id } > 0
    }
}

object IngredientRepository {

    private fun toIngredient(row: ResultRow) = Ingredient(
        id = row[Ingredients.id],
        recipeId = row[Ingredients.recipeId],
        name = row[Ingredients.name],
        quantity = row[Ingredients.quantity],
        unit = row[Ingredients.unit]
    )

    fun getByRecipeId(recipeId: Int): List<Ingredient> = transaction {
        Ingredients.selectAll().where { Ingredients.recipeId eq recipeId }.map(::toIngredient)
    }

    fun getById(id: Int): Ingredient? = transaction {
        Ingredients.selectAll().where { Ingredients.id eq id }.map(::toIngredient).singleOrNull()
    }

    /** Null when the parent recipe does not exist. */
    fun add(recipeId: Int, request: IngredientRequest): Ingredient? = transaction {
        val recipeExists = Recipes.selectAll().where { Recipes.id eq recipeId }.any()
        if (!recipeExists) return@transaction null

        val newId = Ingredients.insert {
            it[Ingredients.recipeId] = recipeId
            it[name] = request.name
            it[quantity] = request.quantity
            it[unit] = request.unit
        } get Ingredients.id

        Ingredient(newId, recipeId, request.name, request.quantity, request.unit)
    }

    fun update(id: Int, request: IngredientRequest): Ingredient? = transaction {
        val existing = Ingredients.selectAll().where { Ingredients.id eq id }
            .map(::toIngredient).singleOrNull() ?: return@transaction null

        Ingredients.update({ Ingredients.id eq id }) {
            it[name] = request.name
            it[quantity] = request.quantity
            it[unit] = request.unit
        }
        existing.copy(name = request.name, quantity = request.quantity, unit = request.unit)
    }

    fun delete(id: Int): Boolean = transaction {
        Ingredients.deleteWhere { Ingredients.id eq id } > 0
    }
}

fun Route.recipeRoutes() = route("/recipes") {

    // Declared before /{id} so "search" is never read as an id.
    get("/search") {
        val ingredient = call.request.queryParameters["ingredient"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "query parameter 'ingredient' is required")

        if (ingredient.isBlank()) {
            return@get call.respond(HttpStatusCode.BadRequest, "'ingredient' cannot be blank")
        }
        call.respond(HttpStatusCode.OK, RecipeRepository.searchByIngredient(ingredient))
    }

    get { call.respond(HttpStatusCode.OK, RecipeRepository.getAll()) }

    get("/{id}") {
        val id = call.intParam("id") ?: return@get call.badId()
        val recipe = RecipeRepository.getById(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, "recipe $id not found")
        call.respond(HttpStatusCode.OK, recipe)
    }

    get("/{id}/full") {
        val id = call.intParam("id") ?: return@get call.badId()
        val recipe = RecipeRepository.getWithIngredients(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, "recipe $id not found")
        call.respond(HttpStatusCode.OK, recipe)
    }

    post { call.respond(HttpStatusCode.Created, RecipeRepository.add(call.receive())) }

    put("/{id}") {
        val id = call.intParam("id") ?: return@put call.badId()
        val updated = RecipeRepository.update(id, call.receive())
            ?: return@put call.respond(HttpStatusCode.NotFound, "recipe $id not found")
        call.respond(HttpStatusCode.OK, updated)
    }

    delete("/{id}") {
        val id = call.intParam("id") ?: return@delete call.badId()
        if (!RecipeRepository.delete(id)) {
            return@delete call.respond(HttpStatusCode.NotFound, "recipe $id not found")
        }
        call.respond(HttpStatusCode.NoContent)
    }

    get("/{id}/ingredients") {
        val recipeId = call.intParam("id") ?: return@get call.badId()
        if (RecipeRepository.getById(recipeId) == null) {
            return@get call.respond(HttpStatusCode.NotFound, "recipe $recipeId not found")
        }
        call.respond(HttpStatusCode.OK, IngredientRepository.getByRecipeId(recipeId))
    }

    post("/{id}/ingredients") {
        val recipeId = call.intParam("id") ?: return@post call.badId()
        val created = IngredientRepository.add(recipeId, call.receive())
            ?: return@post call.respond(HttpStatusCode.NotFound, "recipe $recipeId not found")
        call.respond(HttpStatusCode.Created, created)
    }

    put("/ingredients/{ingredientId}") {
        val id = call.intParam("ingredientId") ?: return@put call.badId()
        val updated = IngredientRepository.update(id, call.receive())
            ?: return@put call.respond(HttpStatusCode.NotFound, "ingredient $id not found")
        call.respond(HttpStatusCode.OK, updated)
    }

    delete("/ingredients/{ingredientId}") {
        val id = call.intParam("ingredientId") ?: return@delete call.badId()
        if (!IngredientRepository.delete(id)) {
            return@delete call.respond(HttpStatusCode.NotFound, "ingredient $id not found")
        }
        call.respond(HttpStatusCode.NoContent)
    }
}
