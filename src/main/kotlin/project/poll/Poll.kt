package org.example.project.poll

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.example.project.badId
import org.example.project.intParam
import org.example.project.BusinessRuleException
import org.example.project.BusinessRuleException.Kind
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

// --- Project 8: Simple Poll/Survey API ---

@Serializable
data class Poll(val id: Int, val question: String)

@Serializable
data class PollRequest(val question: String)

@Serializable
data class PollOption(val id: Int, val pollId: Int, val text: String, val voteCount: Int)

@Serializable
data class PollOptionRequest(val text: String)

/** A poll with its options and their current tallies, in one response. */
@Serializable
data class PollResult(
    val id: Int,
    val question: String,
    val totalVotes: Int,
    val options: List<PollOptionResult>
)

@Serializable
data class PollOptionResult(
    val id: Int,
    val text: String,
    val voteCount: Int,
    val percentage: Double
)

object Polls : Table("polls") {
    val id = integer("id").autoIncrement()
    val question = varchar("question", 500)
    override val primaryKey = PrimaryKey(id)
}

object PollOptions : Table("poll_options") {
    val id = integer("id").autoIncrement()
    val pollId = integer("poll_id").references(Polls.id, onDelete = ReferenceOption.CASCADE)
    val text = varchar("text", 300)
    val voteCount = integer("vote_count")
    override val primaryKey = PrimaryKey(id)
}

class PollRuleException(message: String, kind: Kind = Kind.CONFLICT) : BusinessRuleException(message, kind)

object PollRepository {

    private fun toPoll(row: ResultRow) = Poll(row[Polls.id], row[Polls.question])

    fun getAll(): List<Poll> = transaction { Polls.selectAll().map(::toPoll) }

    fun getById(id: Int): Poll? = transaction {
        Polls.selectAll().where { Polls.id eq id }.map(::toPoll).singleOrNull()
    }

    /** Poll, its options, and each option's share of the vote. */
    fun getResult(id: Int): PollResult? = transaction {
        val poll = Polls.selectAll().where { Polls.id eq id }
            .map(::toPoll).singleOrNull() ?: return@transaction null

        val options = PollOptionRepository.getByPollId(id)
        val totalVotes = options.sumOf { it.voteCount }

        PollResult(
            id = poll.id,
            question = poll.question,
            totalVotes = totalVotes,
            options = options.map { option ->
                PollOptionResult(
                    id = option.id,
                    text = option.text,
                    voteCount = option.voteCount,
                    // Guard the divide: a brand new poll has zero votes.
                    percentage = if (totalVotes == 0) 0.0
                    else option.voteCount * 100.0 / totalVotes
                )
            }
        )
    }

    fun add(request: PollRequest): Poll = transaction {
        if (request.question.isBlank()) throw PollRuleException("question cannot be blank", Kind.INVALID)
        val newId = Polls.insert { it[question] = request.question } get Polls.id
        Poll(newId, request.question)
    }

    fun update(id: Int, request: PollRequest): Poll? = transaction {
        if (request.question.isBlank()) throw PollRuleException("question cannot be blank", Kind.INVALID)
        val changed = Polls.update({ Polls.id eq id }) { it[question] = request.question }
        if (changed == 0) null else Poll(id, request.question)
    }

    fun delete(id: Int): Boolean = transaction { Polls.deleteWhere { Polls.id eq id } > 0 }
}

object PollOptionRepository {

    private fun toOption(row: ResultRow) = PollOption(
        id = row[PollOptions.id],
        pollId = row[PollOptions.pollId],
        text = row[PollOptions.text],
        voteCount = row[PollOptions.voteCount]
    )

    fun getByPollId(pollId: Int): List<PollOption> = transaction {
        PollOptions.selectAll().where { PollOptions.pollId eq pollId }.map(::toOption)
    }

    fun getById(id: Int): PollOption? = transaction {
        PollOptions.selectAll().where { PollOptions.id eq id }.map(::toOption).singleOrNull()
    }

    /** Null when the poll does not exist. A new option starts at zero votes. */
    fun add(pollId: Int, request: PollOptionRequest): PollOption? = transaction {
        if (request.text.isBlank()) throw PollRuleException("option text cannot be blank", Kind.INVALID)

        val pollExists = Polls.selectAll().where { Polls.id eq pollId }.any()
        if (!pollExists) return@transaction null

        val newId = PollOptions.insert {
            it[PollOptions.pollId] = pollId
            it[text] = request.text
            it[voteCount] = 0
        } get PollOptions.id

        PollOption(newId, pollId, request.text, 0)
    }

    /** Only the text is editable; voteCount is never set directly by a client. */
    fun updateText(id: Int, request: PollOptionRequest): PollOption? = transaction {
        if (request.text.isBlank()) throw PollRuleException("option text cannot be blank", Kind.INVALID)

        val existing = PollOptions.selectAll().where { PollOptions.id eq id }
            .map(::toOption).singleOrNull() ?: return@transaction null

        PollOptions.update({ PollOptions.id eq id }) { it[text] = request.text }
        existing.copy(text = request.text)
    }

    /**
     * The vote action: read and increment inside one transaction so two
     * simultaneous votes cannot read the same count and both write count + 1.
     */
    fun vote(optionId: Int): PollOption? = transaction {
        val existing = PollOptions.selectAll().where { PollOptions.id eq optionId }
            .map(::toOption).singleOrNull() ?: return@transaction null

        val newCount = existing.voteCount + 1
        PollOptions.update({ PollOptions.id eq optionId }) { it[voteCount] = newCount }
        existing.copy(voteCount = newCount)
    }

    fun delete(id: Int): Boolean = transaction {
        PollOptions.deleteWhere { PollOptions.id eq id } > 0
    }
}

fun Route.pollRoutes() = route("/polls") {

    get { call.respond(HttpStatusCode.OK, PollRepository.getAll()) }

    get("/{id}") {
        val id = call.intParam("id") ?: return@get call.badId()
        val result = PollRepository.getResult(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, "poll $id not found")
        call.respond(HttpStatusCode.OK, result)
    }

    post { call.respond(HttpStatusCode.Created, PollRepository.add(call.receive())) }

    put("/{id}") {
        val id = call.intParam("id") ?: return@put call.badId()
        val updated = PollRepository.update(id, call.receive())
            ?: return@put call.respond(HttpStatusCode.NotFound, "poll $id not found")
        call.respond(HttpStatusCode.OK, updated)
    }

    delete("/{id}") {
        val id = call.intParam("id") ?: return@delete call.badId()
        if (!PollRepository.delete(id)) {
            return@delete call.respond(HttpStatusCode.NotFound, "poll $id not found")
        }
        call.respond(HttpStatusCode.NoContent)
    }

    get("/{id}/options") {
        val pollId = call.intParam("id") ?: return@get call.badId()
        if (PollRepository.getById(pollId) == null) {
            return@get call.respond(HttpStatusCode.NotFound, "poll $pollId not found")
        }
        call.respond(HttpStatusCode.OK, PollOptionRepository.getByPollId(pollId))
    }

    post("/{id}/options") {
        val pollId = call.intParam("id") ?: return@post call.badId()
        val created = PollOptionRepository.add(pollId, call.receive())
            ?: return@post call.respond(HttpStatusCode.NotFound, "poll $pollId not found")
        call.respond(HttpStatusCode.Created, created)
    }

    put("/options/{optionId}") {
        val id = call.intParam("optionId") ?: return@put call.badId()
        val updated = PollOptionRepository.updateText(id, call.receive())
            ?: return@put call.respond(HttpStatusCode.NotFound, "option $id not found")
        call.respond(HttpStatusCode.OK, updated)
    }

    delete("/options/{optionId}") {
        val id = call.intParam("optionId") ?: return@delete call.badId()
        if (!PollOptionRepository.delete(id)) {
            return@delete call.respond(HttpStatusCode.NotFound, "option $id not found")
        }
        call.respond(HttpStatusCode.NoContent)
    }

    // Not standard CRUD: an action on a resource, so POST rather than PUT.
    post("/options/{optionId}/vote") {
        val id = call.intParam("optionId") ?: return@post call.badId()
        val voted = PollOptionRepository.vote(id)
            ?: return@post call.respond(HttpStatusCode.NotFound, "option $id not found")
        call.respond(HttpStatusCode.OK, voted)
    }
}
