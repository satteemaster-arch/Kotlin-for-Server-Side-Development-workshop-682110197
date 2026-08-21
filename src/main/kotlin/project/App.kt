package org.example.project

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.example.project.blog.blogRoutes
import org.example.project.inventory.inventoryRoutes
import org.example.project.booking.bookingRoutes
import org.example.project.expense.expenseRoutes
import org.example.project.issue.issueRoutes
import org.example.project.library.libraryRoutes
import org.example.project.movie.movieRoutes
import org.example.project.poll.pollRoutes
import org.example.project.recipe.recipeRoutes
import org.example.project.shortener.shortenerRoutes

// Final project: ten REST APIs in one Ktor server, each under its own route prefix.

fun main() {
    DatabaseFactory.init()
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        })
    }

    // A malformed or incomplete JSON body is the client's fault, so answer
    // 400 instead of letting the exception surface as a 500.
    install(StatusPages) {
        // A broken business rule is the client's fault too, but which kind matters:
        // nonsense input is 400, while a well-formed request that the current
        // state cannot satisfy is 409.
        exception<BusinessRuleException> { call, cause ->
            val status = when (cause.kind) {
                BusinessRuleException.Kind.INVALID -> HttpStatusCode.BadRequest
                BusinessRuleException.Kind.CONFLICT -> HttpStatusCode.Conflict
            }
            call.respond(status, cause.message ?: "request rejected by a business rule")
        }
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause.message ?: "malformed request body")
        }
        exception<SerializationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause.message ?: "malformed JSON")
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause.message ?: "invalid request")
        }
    }

    routing {
        blogRoutes()
        inventoryRoutes()
        shortenerRoutes()
        recipeRoutes()
        bookingRoutes()
        expenseRoutes()
        movieRoutes()
        issueRoutes()
        pollRoutes()
        libraryRoutes()
    }
}
