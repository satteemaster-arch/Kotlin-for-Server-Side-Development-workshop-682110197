package org.example.project

import org.example.project.blog.CommentRepository
import org.example.project.blog.CommentRequest
import org.example.project.blog.PostRepository
import org.example.project.blog.PostRequest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlogRepositoryTest {

    @BeforeTest
    fun setUp() {
        DatabaseFactory.init("blogrepotest")
        DatabaseFactory.clearAll()
    }

    @Test
    fun `add assigns an id and stores the post`() {
        // Arrange
        val request = PostRequest("หัวข้อแรก", "เนื้อหาของโพสต์")

        // Act
        val created = PostRepository.add(request)

        // Assert
        assertTrue(created.id > 0, "the database should assign an id")
        assertEquals("หัวข้อแรก", created.title)
        assertEquals(1, PostRepository.getAll().size)
    }

    @Test
    fun `getById returns null for an id that was never created`() {
        assertNull(PostRepository.getById(999))
    }

    @Test
    fun `update changes updatedAt but keeps createdAt`() {
        // Arrange
        val created = PostRepository.add(PostRequest("เดิม", "เนื้อหาเดิม"))

        // Act
        val updated = PostRepository.update(created.id, PostRequest("ใหม่", "เนื้อหาใหม่"))

        // Assert
        assertEquals("ใหม่", updated?.title)
        assertEquals(created.createdAt, updated?.createdAt, "createdAt must never move")
        assertNotEquals(created.updatedAt, updated?.updatedAt, "updatedAt must move")
    }

    @Test
    fun `update returns null when the post does not exist`() {
        assertNull(PostRepository.update(999, PostRequest("x", "y")))
    }

    @Test
    fun `delete removes the post and reports whether anything was deleted`() {
        // Arrange
        val created = PostRepository.add(PostRequest("จะถูกลบ", "..."))

        // Act & Assert
        assertTrue(PostRepository.delete(created.id))
        assertNull(PostRepository.getById(created.id))
        assertTrue(!PostRepository.delete(created.id), "deleting twice must report false")
    }

    @Test
    fun `comment cannot be attached to a post that does not exist`() {
        // Act
        val orphan = CommentRepository.add(999, CommentRequest("สมชาย", "ความเห็น"))

        // Assert
        assertNull(orphan, "a comment needs a real parent post")
    }

    @Test
    fun `deleting a post cascades to its comments`() {
        // Arrange
        val post = PostRepository.add(PostRequest("มีคอมเมนต์", "..."))
        CommentRepository.add(post.id, CommentRequest("สมชาย", "ความเห็นที่ 1"))
        CommentRepository.add(post.id, CommentRequest("สมหญิง", "ความเห็นที่ 2"))
        assertEquals(2, CommentRepository.getByPostId(post.id).size)

        // Act
        PostRepository.delete(post.id)

        // Assert
        assertEquals(0, CommentRepository.getByPostId(post.id).size, "orphaned comments must not survive")
    }

    @Test
    fun `getWithComments nests every comment of that post only`() {
        // Arrange
        val postA = PostRepository.add(PostRequest("โพสต์ A", "..."))
        val postB = PostRepository.add(PostRequest("โพสต์ B", "..."))
        CommentRepository.add(postA.id, CommentRequest("คนที่ 1", "บน A"))
        CommentRepository.add(postA.id, CommentRequest("คนที่ 2", "บน A"))
        CommentRepository.add(postB.id, CommentRequest("คนที่ 3", "บน B"))

        // Act
        val full = PostRepository.getWithComments(postA.id)

        // Assert
        assertEquals(2, full?.comments?.size)
        assertTrue(full!!.comments.all { it.postId == postA.id })
    }
}
