package org.example

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

// Workshop #4: Task CRUD REST API with Ktor

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            // A client may post a full Task (id included); the id is ignored, not rejected.
            ignoreUnknownKeys = true
        })
    }

    routing {
        // --- 3. Routes ---

        get("/tasks") {
            call.respond(HttpStatusCode.OK, TaskRepository.getAll())
        }

        get("/tasks/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "id must be a number")

            val task = TaskRepository.getById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, "task $id not found")

            call.respond(HttpStatusCode.OK, task)
        }

        post("/tasks") {
            val request = call.receive<TaskRequest>()
            val task = Task(
                id = TaskRepository.nextId(),
                content = request.content,
                isDone = request.isDone
            )
            TaskRepository.add(task)
            call.respond(HttpStatusCode.Created, task)
        }

        put("/tasks/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, "id must be a number")

            val updatedTask = call.receive<Task>()
            if (!TaskRepository.update(id, updatedTask)) {
                return@put call.respond(HttpStatusCode.NotFound, "task $id not found")
            }

            call.respond(HttpStatusCode.OK, updatedTask.copy(id = id))
        }

        delete("/tasks/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "id must be a number")

            if (!TaskRepository.delete(id)) {
                return@delete call.respond(HttpStatusCode.NotFound, "task $id not found")
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
