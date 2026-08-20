package org.example

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaskApiTest {

    @BeforeTest
    fun resetRepository() {
        TaskRepository.clear()
    }

    private fun ApplicationTestBuilder.jsonClient(): HttpClient =
        createClient {
            install(ContentNegotiation) { json() }
        }

    @Test
    fun `GET tasks returns 200 with every stored task`() = testApplication {
        // Arrange
        application { module() }
        TaskRepository.add(Task(1, "อ่านหนังสือ", false))
        TaskRepository.add(Task(2, "ส่งการบ้าน", true))
        val client = jsonClient()

        // Act
        val response = client.get("/tasks")

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        val tasks: List<Task> = response.body()
        assertEquals(2, tasks.size)
        assertEquals("อ่านหนังสือ", tasks[0].content)
    }

    @Test
    fun `GET task by id returns 200 with that single task`() = testApplication {
        // Arrange
        application { module() }
        TaskRepository.add(Task(7, "ทบทวน Kotlin", false))
        val client = jsonClient()

        // Act
        val response = client.get("/tasks/7")

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        val task: Task = response.body()
        assertEquals(Task(7, "ทบทวน Kotlin", false), task)
    }

    @Test
    fun `GET task by unknown id returns 404`() = testApplication {
        // Arrange
        application { module() }
        val client = jsonClient()

        // Act
        val response = client.get("/tasks/999")

        // Assert
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST task returns 201 and stores it with a server assigned id`() = testApplication {
        // Arrange
        application { module() }
        val client = jsonClient()

        // Act
        val response = client.post("/tasks") {
            contentType(ContentType.Application.Json)
            setBody(TaskRequest("เขียนเทสต์", false))
        }

        // Assert
        assertEquals(HttpStatusCode.Created, response.status)
        val created: Task = response.body()
        assertEquals("เขียนเทสต์", created.content)
        assertEquals(1, created.id)
        assertEquals(1, TaskRepository.getAll().size)
    }

    @Test
    fun `PUT task replaces the stored task and returns 200`() = testApplication {
        // Arrange
        application { module() }
        TaskRepository.add(Task(1, "ยังไม่เสร็จ", false))
        val client = jsonClient()

        // Act
        val response = client.put("/tasks/1") {
            contentType(ContentType.Application.Json)
            setBody(Task(1, "เสร็จแล้ว", true))
        }

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(Task(1, "เสร็จแล้ว", true), TaskRepository.getById(1))
    }

    @Test
    fun `PUT on unknown id returns 404`() = testApplication {
        // Arrange
        application { module() }
        val client = jsonClient()

        // Act
        val response = client.put("/tasks/999") {
            contentType(ContentType.Application.Json)
            setBody(Task(999, "ไม่มีอยู่จริง", false))
        }

        // Assert
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `DELETE task returns 204 and removes it`() = testApplication {
        // Arrange
        application { module() }
        TaskRepository.add(Task(3, "ลบทิ้ง", false))
        val client = jsonClient()

        // Act
        val response = client.delete("/tasks/3")

        // Assert
        assertEquals(HttpStatusCode.NoContent, response.status)
        assertNull(TaskRepository.getById(3))
    }

    @Test
    fun `DELETE on unknown id returns 404`() = testApplication {
        // Arrange
        application { module() }
        val client = jsonClient()

        // Act
        val response = client.delete("/tasks/999")

        // Assert
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
