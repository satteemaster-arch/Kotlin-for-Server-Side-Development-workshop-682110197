package org.example

import kotlinx.serialization.Serializable

// --- 1. Data Modeling ---

@Serializable
data class Task(
    val id: Int,
    val content: String,
    val isDone: Boolean
)

// Body of a create request: the server assigns the id, so it is absent here.
@Serializable
data class TaskRequest(
    val content: String,
    val isDone: Boolean
)
