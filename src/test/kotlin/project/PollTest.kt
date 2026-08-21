package org.example.project

import org.example.project.poll.PollOptionRepository
import org.example.project.poll.PollOptionRequest
import org.example.project.poll.PollRepository
import org.example.project.poll.PollRequest
import org.example.project.poll.PollRuleException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Project 8: voting, an action that is not plain CRUD. */
class PollTest {

    @BeforeTest
    fun setUp() {
        DatabaseFactory.init("polltest")
        DatabaseFactory.clearAll()
    }

    private fun aPoll() = PollRepository.add(PollRequest("ภาษาไหนดีที่สุด?")).id

    @Test
    fun `a new option starts with zero votes`() {
        val pollId = aPoll()
        val option = PollOptionRepository.add(pollId, PollOptionRequest("Kotlin"))
        assertEquals(0, option?.voteCount)
    }

    @Test
    fun `each vote adds exactly one`() {
        // Arrange
        val pollId = aPoll()
        val option = PollOptionRepository.add(pollId, PollOptionRequest("Kotlin"))!!

        // Act
        PollOptionRepository.vote(option.id)
        PollOptionRepository.vote(option.id)
        val third = PollOptionRepository.vote(option.id)

        // Assert
        assertEquals(3, third?.voteCount)
        assertEquals(3, PollOptionRepository.getById(option.id)?.voteCount)
    }

    @Test
    fun `voting for one option leaves the others untouched`() {
        // Arrange
        val pollId = aPoll()
        val kotlin = PollOptionRepository.add(pollId, PollOptionRequest("Kotlin"))!!
        val java = PollOptionRepository.add(pollId, PollOptionRequest("Java"))!!

        // Act
        PollOptionRepository.vote(kotlin.id)

        // Assert
        assertEquals(1, PollOptionRepository.getById(kotlin.id)?.voteCount)
        assertEquals(0, PollOptionRepository.getById(java.id)?.voteCount)
    }

    @Test
    fun `the result carries the total and each option's share`() {
        // Arrange
        val pollId = aPoll()
        val kotlin = PollOptionRepository.add(pollId, PollOptionRequest("Kotlin"))!!
        val java = PollOptionRepository.add(pollId, PollOptionRequest("Java"))!!
        repeat(3) { PollOptionRepository.vote(kotlin.id) }
        PollOptionRepository.vote(java.id)

        // Act
        val result = PollRepository.getResult(pollId)

        // Assert
        assertEquals(4, result?.totalVotes)
        assertEquals(75.0, result?.options?.first { it.id == kotlin.id }?.percentage)
        assertEquals(25.0, result?.options?.first { it.id == java.id }?.percentage)
    }

    @Test
    fun `a poll with no votes reports zero percent instead of dividing by zero`() {
        // Arrange
        val pollId = aPoll()
        PollOptionRepository.add(pollId, PollOptionRequest("Kotlin"))

        // Act
        val result = PollRepository.getResult(pollId)

        // Assert
        assertEquals(0, result?.totalVotes)
        assertEquals(0.0, result?.options?.first()?.percentage)
    }

    @Test
    fun `editing an option's text does not reset its votes`() {
        // voteCount is never something a client sets directly.
        val pollId = aPoll()
        val option = PollOptionRepository.add(pollId, PollOptionRequest("Kotlin"))!!
        PollOptionRepository.vote(option.id)

        val renamed = PollOptionRepository.updateText(option.id, PollOptionRequest("Kotlin 2.4"))

        assertEquals("Kotlin 2.4", renamed?.text)
        assertEquals(1, renamed?.voteCount)
    }

    @Test
    fun `an option needs a poll that exists`() {
        assertNull(PollOptionRepository.add(999, PollOptionRequest("ลอย")))
    }

    @Test
    fun `voting for an option that does not exist returns null`() {
        assertNull(PollOptionRepository.vote(999))
    }

    @Test
    fun `a blank question is rejected`() {
        assertFailsWith<PollRuleException> { PollRepository.add(PollRequest("  ")) }
    }

    @Test
    fun `deleting a poll cascades to its options`() {
        val pollId = aPoll()
        PollOptionRepository.add(pollId, PollOptionRequest("Kotlin"))
        PollRepository.delete(pollId)
        assertEquals(0, PollOptionRepository.getByPollId(pollId).size)
    }
}
