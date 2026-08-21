package org.example.project

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
import org.example.project.blog.Comment
import org.example.project.blog.CommentRequest
import org.example.project.blog.Post
import org.example.project.blog.PostRequest
import org.example.project.blog.PostWithComments
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlogApiTest {

    @BeforeTest
    fun setUp() {
        DatabaseFactory.init("blogapitest")
        DatabaseFactory.clearAll()
    }

    private fun ApplicationTestBuilder.jsonClient(): HttpClient =
        createClient { install(ContentNegotiation) { json() } }

    @Test
    fun `POST post returns 201 with a generated id`() = testApplication {
        // Arrange
        application { module() }
        val client = jsonClient()

        // Act
        val response = client.post("/blog/posts") {
            contentType(ContentType.Application.Json)
            setBody(PostRequest("โพสต์แรก", "สวัสดีครับ"))
        }

        // Assert
        assertEquals(HttpStatusCode.Created, response.status)
        val created: Post = response.body()
        assertTrue(created.id > 0)
        assertEquals("โพสต์แรก", created.title)
    }

    @Test
    fun `GET posts returns 200 with every post`() = testApplication {
        // Arrange
        application { module() }
        val client = jsonClient()
        client.post("/blog/posts") {
            contentType(ContentType.Application.Json)
            setBody(PostRequest("หนึ่ง", "..."))
        }
        client.post("/blog/posts") {
            contentType(ContentType.Application.Json)
            setBody(PostRequest("สอง", "..."))
        }

        // Act
        val response = client.get("/blog/posts")

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        val posts: List<Post> = response.body()
        assertEquals(2, posts.size)
    }

    @Test
    fun `GET post by unknown id returns 404`() = testApplication {
        application { module() }
        assertEquals(HttpStatusCode.NotFound, jsonClient().get("/blog/posts/999").status)
    }

    @Test
    fun `GET post with a non numeric id returns 400`() = testApplication {
        application { module() }
        assertEquals(HttpStatusCode.BadRequest, jsonClient().get("/blog/posts/abc").status)
    }

    @Test
    fun `PUT post returns 200 with the updated post`() = testApplication {
        // Arrange
        application { module() }
        val client = jsonClient()
        val created: Post = client.post("/blog/posts") {
            contentType(ContentType.Application.Json)
            setBody(PostRequest("เดิม", "..."))
        }.body()

        // Act
        val response = client.put("/blog/posts/${created.id}") {
            contentType(ContentType.Application.Json)
            setBody(PostRequest("แก้แล้ว", "เนื้อหาใหม่"))
        }

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("แก้แล้ว", response.body<Post>().title)
    }

    @Test
    fun `DELETE post returns 204 and then 404`() = testApplication {
        // Arrange
        application { module() }
        val client = jsonClient()
        val created: Post = client.post("/blog/posts") {
            contentType(ContentType.Application.Json)
            setBody(PostRequest("จะลบ", "..."))
        }.body()

        // Act & Assert
        assertEquals(HttpStatusCode.NoContent, client.delete("/blog/posts/${created.id}").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/blog/posts/${created.id}").status)
    }

    @Test
    fun `POST comment on an unknown post returns 404`() = testApplication {
        application { module() }
        val response = jsonClient().post("/blog/posts/999/comments") {
            contentType(ContentType.Application.Json)
            setBody(CommentRequest("สมชาย", "ความเห็น"))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET post full nests its comments in the response`() = testApplication {
        // Arrange
        application { module() }
        val client = jsonClient()
        val post: Post = client.post("/blog/posts") {
            contentType(ContentType.Application.Json)
            setBody(PostRequest("มีคอมเมนต์", "..."))
        }.body()
        val comment: Comment = client.post("/blog/posts/${post.id}/comments") {
            contentType(ContentType.Application.Json)
            setBody(CommentRequest("สมหญิง", "ความเห็นแรก"))
        }.body<Comment>()

        // Act
        val response = client.get("/blog/posts/${post.id}/full")

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        val full: PostWithComments = response.body()
        assertEquals(1, full.comments.size)
        assertEquals(comment.id, full.comments.first().id)
    }

    @Test
    fun `POST post with a missing field returns 400 not 500`() = testApplication {
        // Arrange
        application { module() }
        val client = jsonClient()

        // Act - "content" is required but absent
        val response = client.post("/blog/posts") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"ไม่มี content"}""")
        }

        // Assert
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
