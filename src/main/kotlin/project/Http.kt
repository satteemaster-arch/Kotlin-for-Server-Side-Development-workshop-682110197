package org.example.project

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

/** Reads a path parameter as Int, or null when it is missing or not a number. */
fun ApplicationCall.intParam(name: String): Int? = parameters[name]?.toIntOrNull()

/** The 400 every route returns when a path id is not a number. */
suspend fun ApplicationCall.badId() =
    respond(HttpStatusCode.BadRequest, "path id must be a number")
