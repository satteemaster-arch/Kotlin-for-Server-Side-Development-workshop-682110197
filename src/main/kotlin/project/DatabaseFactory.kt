package org.example.project

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.example.project.blog.Comments
import org.example.project.inventory.Categories
import org.example.project.inventory.Products
import org.example.project.recipe.Ingredients
import org.example.project.recipe.Recipes
import org.example.project.booking.Appointments
import org.example.project.booking.Services
import org.example.project.expense.ExpenseCategories
import org.example.project.expense.Transactions
import org.example.project.movie.Movies
import org.example.project.movie.Reviews
import org.example.project.issue.Issues
import org.example.project.poll.PollOptions
import org.example.project.poll.Polls
import org.example.project.library.Books
import org.example.project.library.LendingRecords
import org.example.project.shortener.ShortUrls
import org.example.project.blog.Posts

object DatabaseFactory {

    // Every table in the project, so schema creation stays in one place.
    private val allTables = arrayOf(
        Posts, Comments,
        Categories, Products,
        ShortUrls,
        Recipes, Ingredients,
        Services, Appointments,
        ExpenseCategories, Transactions,
        Movies, Reviews,
        Issues,
        Polls, PollOptions,
        Books, LendingRecords
    )

    /**
     * H2 keeps the database alive only while a connection is open, so
     * DB_CLOSE_DELAY=-1 makes it live for the whole JVM instead.
     * [name] lets each test get its own isolated database.
     */
    fun init(name: String = "workshop") {
        Database.connect(
            url = "jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.create(*allTables)
        }
    }

    /** Wipes every row. Used by tests so each one starts from a known state. */
    fun clearAll() = transaction {
        SchemaUtils.drop(*allTables)
        SchemaUtils.create(*allTables)
    }
}
