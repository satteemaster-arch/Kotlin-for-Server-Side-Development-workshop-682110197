package org.example.project.blog

import java.time.Instant
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

object PostRepository {

    private fun toPost(row: ResultRow) = Post(
        id = row[Posts.id],
        title = row[Posts.title],
        content = row[Posts.content],
        createdAt = row[Posts.createdAt],
        updatedAt = row[Posts.updatedAt]
    )

    fun getAll(): List<Post> = transaction {
        Posts.selectAll().map(::toPost)
    }

    fun getById(id: Int): Post? = transaction {
        Posts.selectAll().where { Posts.id eq id }.map(::toPost).singleOrNull()
    }

    fun getWithComments(id: Int): PostWithComments? = transaction {
        val post = Posts.selectAll().where { Posts.id eq id }.map(::toPost).singleOrNull()
            ?: return@transaction null

        val comments = CommentRepository.getByPostId(id)
        PostWithComments(
            id = post.id,
            title = post.title,
            content = post.content,
            createdAt = post.createdAt,
            updatedAt = post.updatedAt,
            comments = comments
        )
    }

    fun add(request: PostRequest): Post = transaction {
        val now = Instant.now().toString()
        val newId = Posts.insert {
            it[title] = request.title
            it[content] = request.content
            it[createdAt] = now
            it[updatedAt] = now
        } get Posts.id

        Post(newId, request.title, request.content, now, now)
    }

    /** Returns the updated post, or null when [id] does not exist. */
    fun update(id: Int, request: PostRequest): Post? = transaction {
        val existing = Posts.selectAll().where { Posts.id eq id }.map(::toPost).singleOrNull()
            ?: return@transaction null

        val now = Instant.now().toString()
        Posts.update({ Posts.id eq id }) {
            it[title] = request.title
            it[content] = request.content
            it[updatedAt] = now
        }

        // createdAt must survive an update; only updatedAt moves.
        existing.copy(title = request.title, content = request.content, updatedAt = now)
    }

    fun delete(id: Int): Boolean = transaction {
        Posts.deleteWhere { Posts.id eq id } > 0
    }
}

object CommentRepository {

    private fun toComment(row: ResultRow) = Comment(
        id = row[Comments.id],
        postId = row[Comments.postId],
        authorName = row[Comments.authorName],
        content = row[Comments.content],
        createdAt = row[Comments.createdAt]
    )

    fun getByPostId(postId: Int): List<Comment> = transaction {
        Comments.selectAll().where { Comments.postId eq postId }.map(::toComment)
    }

    fun getById(id: Int): Comment? = transaction {
        Comments.selectAll().where { Comments.id eq id }.map(::toComment).singleOrNull()
    }

    /** Returns null when the parent post does not exist. */
    fun add(postId: Int, request: CommentRequest): Comment? = transaction {
        val postExists = Posts.selectAll().where { Posts.id eq postId }.any()
        if (!postExists) return@transaction null

        val now = Instant.now().toString()
        val newId = Comments.insert {
            it[Comments.postId] = postId
            it[authorName] = request.authorName
            it[content] = request.content
            it[createdAt] = now
        } get Comments.id

        Comment(newId, postId, request.authorName, request.content, now)
    }

    fun update(id: Int, request: CommentRequest): Comment? = transaction {
        val existing = Comments.selectAll().where { Comments.id eq id }.map(::toComment).singleOrNull()
            ?: return@transaction null

        Comments.update({ Comments.id eq id }) {
            it[authorName] = request.authorName
            it[content] = request.content
        }

        existing.copy(authorName = request.authorName, content = request.content)
    }

    fun delete(id: Int): Boolean = transaction {
        Comments.deleteWhere { Comments.id eq id } > 0
    }
}
