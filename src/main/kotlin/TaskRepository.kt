package org.example

// --- 2. Data Layer (in-memory) ---

object TaskRepository {

    private val tasks = mutableListOf<Task>()

    fun getAll(): List<Task> = tasks.toList()

    fun getById(id: Int): Task? = tasks.find { it.id == id }

    fun add(task: Task) {
        tasks.add(task)
    }

    fun update(id: Int, updatedTask: Task): Boolean {
        val index = tasks.indexOfFirst { it.id == id }
        if (index == -1) return false
        tasks[index] = updatedTask.copy(id = id)
        return true
    }

    fun delete(id: Int): Boolean = tasks.removeIf { it.id == id }

    // Not part of the brief: lets each test start from a known, empty state.
    fun clear() {
        tasks.clear()
    }

    // Ids are handed out by the server, not by the client.
    fun nextId(): Int = (tasks.maxOfOrNull { it.id } ?: 0) + 1
}
