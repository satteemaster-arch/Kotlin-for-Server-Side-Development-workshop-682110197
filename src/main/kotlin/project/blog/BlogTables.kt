package org.example.project.blog

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object Posts : Table("posts") {
    val id = integer("id").autoIncrement()
    val title = varchar("title", 200)
    val content = text("content")
    val createdAt = varchar("created_at", 40)
    val updatedAt = varchar("updated_at", 40)

    override val primaryKey = PrimaryKey(id)
}

object Comments : Table("comments") {
    val id = integer("id").autoIncrement()

    // One-to-many: deleting a post takes its comments with it.
    val postId = integer("post_id")
        .references(Posts.id, onDelete = ReferenceOption.CASCADE)

    val authorName = varchar("author_name", 100)
    val content = text("content")
    val createdAt = varchar("created_at", 40)

    override val primaryKey = PrimaryKey(id)
}
