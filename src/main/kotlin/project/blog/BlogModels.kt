package org.example.project.blog

import kotlinx.serialization.Serializable

// --- Project 0: Personal Blog API ---

@Serializable
data class Post(
    val id: Int,
    val title: String,
    val content: String,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class PostRequest(
    val title: String,
    val content: String
)

@Serializable
data class Comment(
    val id: Int,
    val postId: Int,
    val authorName: String,
    val content: String,
    val createdAt: String
)

@Serializable
data class CommentRequest(
    val authorName: String,
    val content: String
)

/** A post together with every comment attached to it (one-to-many). */
@Serializable
data class PostWithComments(
    val id: Int,
    val title: String,
    val content: String,
    val createdAt: String,
    val updatedAt: String,
    val comments: List<Comment>
)
