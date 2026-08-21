package org.example.project.blog

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.example.project.badId
import org.example.project.intParam

/**
 * Project 0: Personal Blog API.
 * Mounted under /blog so it cannot collide with the other nine projects.
 */
fun Route.blogRoutes() = route("/blog") {

    // --- Posts ---

    get("/posts") {
        call.respond(HttpStatusCode.OK, PostRepository.getAll())
    }

    get("/posts/{id}") {
        val id = call.intParam("id") ?: return@get call.badId()
        val post = PostRepository.getById(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, "post $id not found")
        call.respond(HttpStatusCode.OK, post)
    }

    // A post together with all of its comments (the one-to-many read).
    get("/posts/{id}/full") {
        val id = call.intParam("id") ?: return@get call.badId()
        val post = PostRepository.getWithComments(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, "post $id not found")
        call.respond(HttpStatusCode.OK, post)
    }

    post("/posts") {
        val request = call.receive<PostRequest>()
        call.respond(HttpStatusCode.Created, PostRepository.add(request))
    }

    put("/posts/{id}") {
        val id = call.intParam("id") ?: return@put call.badId()
        val updated = PostRepository.update(id, call.receive())
            ?: return@put call.respond(HttpStatusCode.NotFound, "post $id not found")
        call.respond(HttpStatusCode.OK, updated)
    }

    delete("/posts/{id}") {
        val id = call.intParam("id") ?: return@delete call.badId()
        if (!PostRepository.delete(id)) {
            return@delete call.respond(HttpStatusCode.NotFound, "post $id not found")
        }
        call.respond(HttpStatusCode.NoContent)
    }

    // --- Comments (nested under their post) ---

    get("/posts/{id}/comments") {
        val postId = call.intParam("id") ?: return@get call.badId()
        if (PostRepository.getById(postId) == null) {
            return@get call.respond(HttpStatusCode.NotFound, "post $postId not found")
        }
        call.respond(HttpStatusCode.OK, CommentRepository.getByPostId(postId))
    }

    post("/posts/{id}/comments") {
        val postId = call.intParam("id") ?: return@post call.badId()
        val comment = CommentRepository.add(postId, call.receive())
            ?: return@post call.respond(HttpStatusCode.NotFound, "post $postId not found")
        call.respond(HttpStatusCode.Created, comment)
    }

    put("/comments/{id}") {
        val id = call.intParam("id") ?: return@put call.badId()
        val updated = CommentRepository.update(id, call.receive())
            ?: return@put call.respond(HttpStatusCode.NotFound, "comment $id not found")
        call.respond(HttpStatusCode.OK, updated)
    }

    delete("/comments/{id}") {
        val id = call.intParam("id") ?: return@delete call.badId()
        if (!CommentRepository.delete(id)) {
            return@delete call.respond(HttpStatusCode.NotFound, "comment $id not found")
        }
        call.respond(HttpStatusCode.NoContent)
    }
}
