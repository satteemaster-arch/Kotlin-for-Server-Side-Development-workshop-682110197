package org.example.project

import org.example.project.issue.IssuePriority
import org.example.project.issue.IssueRepository
import org.example.project.issue.IssueRequest
import org.example.project.issue.IssueRuleException
import org.example.project.issue.IssueStatus
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Project 7: enums, filtering and state transitions. */
class IssueTest {

    @BeforeTest
    fun setUp() {
        DatabaseFactory.init("issuetest")
        DatabaseFactory.clearAll()
    }

    private fun anIssue(
        title: String = "ปุ่มบันทึกกดไม่ได้",
        status: IssueStatus = IssueStatus.OPEN,
        priority: IssuePriority = IssuePriority.MEDIUM
    ) = IssueRepository.add(IssueRequest(title, "รายละเอียด", status, priority))

    @Test
    fun `a new issue defaults to OPEN and MEDIUM`() {
        val created = IssueRepository.add(IssueRequest("บั๊กใหม่", "..."))
        assertEquals(IssueStatus.OPEN, created.status)
        assertEquals(IssuePriority.MEDIUM, created.priority)
    }

    @Test
    fun `an enum survives the round trip through the database`() {
        // Stored as its name, so it must come back as the same constant.
        val created = anIssue(status = IssueStatus.IN_PROGRESS, priority = IssuePriority.HIGH)
        val loaded = IssueRepository.getById(created.id)

        assertEquals(IssueStatus.IN_PROGRESS, loaded?.status)
        assertEquals(IssuePriority.HIGH, loaded?.priority)
    }

    @Test
    fun `changing status moves updatedAt but not createdAt`() {
        // Arrange
        val created = anIssue()

        // Act
        val moved = IssueRepository.changeStatus(created.id, IssueStatus.IN_PROGRESS)

        // Assert
        assertEquals(IssueStatus.IN_PROGRESS, moved?.status)
        assertEquals(created.createdAt, moved?.createdAt)
    }

    @Test
    fun `a closed issue cannot be reopened through the status endpoint`() {
        // Arrange
        val created = anIssue()
        IssueRepository.changeStatus(created.id, IssueStatus.CLOSED)

        // Act & Assert
        val error = assertFailsWith<IssueRuleException> {
            IssueRepository.changeStatus(created.id, IssueStatus.OPEN)
        }
        assertTrue(error.message!!.contains("CLOSED"))
    }

    @Test
    fun `closing an already closed issue is allowed and stays closed`() {
        val created = anIssue()
        IssueRepository.changeStatus(created.id, IssueStatus.CLOSED)
        assertEquals(IssueStatus.CLOSED, IssueRepository.changeStatus(created.id, IssueStatus.CLOSED)?.status)
    }

    @Test
    fun `filter by status returns only that status`() {
        // Arrange
        anIssue("เปิดอยู่ 1")
        anIssue("เปิดอยู่ 2")
        anIssue("กำลังทำ", status = IssueStatus.IN_PROGRESS)

        // Act & Assert
        assertEquals(2, IssueRepository.filter(IssueStatus.OPEN, null).size)
        assertEquals(1, IssueRepository.filter(IssueStatus.IN_PROGRESS, null).size)
    }

    @Test
    fun `filter by status and priority applies both conditions`() {
        // Arrange
        anIssue("ด่วนและเปิด", status = IssueStatus.OPEN, priority = IssuePriority.HIGH)
        anIssue("ไม่ด่วนแต่เปิด", status = IssueStatus.OPEN, priority = IssuePriority.LOW)
        anIssue("ด่วนแต่ปิดแล้ว", status = IssueStatus.CLOSED, priority = IssuePriority.HIGH)

        // Act
        val found = IssueRepository.filter(IssueStatus.OPEN, IssuePriority.HIGH)

        // Assert
        assertEquals(1, found.size)
        assertEquals("ด่วนและเปิด", found.first().title)
    }

    @Test
    fun `filter with no conditions returns everything`() {
        anIssue("หนึ่ง")
        anIssue("สอง")
        assertEquals(2, IssueRepository.filter(null, null).size)
    }

    @Test
    fun `a blank title is rejected`() {
        assertFailsWith<IssueRuleException> { IssueRepository.add(IssueRequest("   ", "...")) }
    }

    @Test
    fun `changing the status of an issue that does not exist returns null`() {
        assertNull(IssueRepository.changeStatus(999, IssueStatus.CLOSED))
    }
}
