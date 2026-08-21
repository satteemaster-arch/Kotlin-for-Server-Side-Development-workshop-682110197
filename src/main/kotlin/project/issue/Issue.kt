package org.example.project.issue

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
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant

// --- Project 7: Simple Issue Tracker API ---

enum class IssueStatus { OPEN, IN_PROGRESS, CLOSED }

enum class IssuePriority { LOW, MEDIUM, HIGH }

@Serializable
data class Issue(
    val id: Int,
    val title: String,
    val description: String,
    val status: IssueStatus,
    val priority: IssuePriority,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class IssueRequest(
    val title: String,
    val description: String,
    val status: IssueStatus = IssueStatus.OPEN,
    val priority: IssuePriority = IssuePriority.MEDIUM
)

@Serializable
data class StatusChangeRequest(val status: IssueStatus)

object Issues : Table("issues") {
    val id = integer("id").autoIncrement()
    val title = varchar("title", 250)
    val description = text("description")

    // Enums are stored as their name, so the database stays readable and a
    // renamed constant fails loudly instead of silently shifting an ordinal.
    val status = varchar("status", 20)
    val priority = varchar("priority", 20)

    val createdAt = varchar("created_at", 40)
    val updatedAt = varchar("updated_at", 40)

    override val primaryKey = PrimaryKey(id)
}

class IssueRuleException(message: String, kind: Kind = Kind.CONFLICT) : BusinessRuleException(message, kind)

object IssueRepository {

    private fun toIssue(row: ResultRow) = Issue(
        id = row[Issues.id],
        title = row[Issues.title],
        description = row[Issues.description],
        status = IssueStatus.valueOf(row[Issues.status]),
        priority = IssuePriority.valueOf(row[Issues.priority]),
        createdAt = row[Issues.createdAt],
        updatedAt = row[Issues.updatedAt]
    )

    fun getAll(): List<Issue> = transaction { Issues.selectAll().map(::toIssue) }

    fun getById(id: Int): Issue? = transaction {
        Issues.selectAll().where { Issues.id eq id }.map(::toIssue).singleOrNull()
    }

    /**
     * Filters on either field, both, or neither.
     * Building the condition from Op.TRUE keeps one query instead of four branches.
     */
    fun filter(status: IssueStatus?, priority: IssuePriority?): List<Issue> = transaction {
        var condition: Op<Boolean> = Op.TRUE
        if (status != null) condition = condition and (Issues.status eq status.name)
        if (priority != null) condition = condition and (Issues.priority eq priority.name)

        Issues.selectAll()
            .where { condition }
            .orderBy(Issues.id to SortOrder.ASC)
            .map(::toIssue)
    }

    fun add(request: IssueRequest): Issue = transaction {
        if (request.title.isBlank()) throw IssueRuleException("title cannot be blank", Kind.INVALID)

        val now = Instant.now().toString()
        val newId = Issues.insert {
            it[title] = request.title
            it[description] = request.description
            it[status] = request.status.name
            it[priority] = request.priority.name
            it[createdAt] = now
            it[updatedAt] = now
        } get Issues.id

        Issue(newId, request.title, request.description, request.status, request.priority, now, now)
    }

    fun update(id: Int, request: IssueRequest): Issue? = transaction {
        if (request.title.isBlank()) throw IssueRuleException("title cannot be blank", Kind.INVALID)

        val existing = Issues.selectAll().where { Issues.id eq id }
            .map(::toIssue).singleOrNull() ?: return@transaction null

        val now = Instant.now().toString()
        Issues.update({ Issues.id eq id }) {
            it[title] = request.title
            it[description] = request.description
            it[status] = request.status.name
            it[priority] = request.priority.name
            it[updatedAt] = now
        }

        existing.copy(
            title = request.title,
            description = request.description,
            status = request.status,
            priority = request.priority,
            updatedAt = now
        )
    }

    /**
     * State transition endpoint. A closed issue is final here: reopening it
     * would need its own endpoint, so the rule is stated rather than implied.
     */
    fun changeStatus(id: Int, newStatus: IssueStatus): Issue? = transaction {
        val existing = Issues.selectAll().where { Issues.id eq id }
            .map(::toIssue).singleOrNull() ?: return@transaction null

        if (existing.status == IssueStatus.CLOSED && newStatus != IssueStatus.CLOSED) {
            throw IssueRuleException("issue $id is CLOSED and cannot go back to $newStatus")
        }

        val now = Instant.now().toString()
        Issues.update({ Issues.id eq id }) {
            it[status] = newStatus.name
            it[updatedAt] = now
        }
        existing.copy(status = newStatus, updatedAt = now)
    }

    fun delete(id: Int): Boolean = transaction { Issues.deleteWhere { Issues.id eq id } > 0 }
}

fun Route.issueRoutes() = route("/issues") {

    // Filtering lives on the collection itself: GET /issues?status=OPEN&priority=HIGH
    get {
        val statusRaw = call.request.queryParameters["status"]
        val priorityRaw = call.request.queryParameters["priority"]

        val status = statusRaw?.let {
            runCatching { IssueStatus.valueOf(it.uppercase()) }.getOrNull()
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    "status must be one of ${IssueStatus.entries.joinToString()}"
                )
        }
        val priority = priorityRaw?.let {
            runCatching { IssuePriority.valueOf(it.uppercase()) }.getOrNull()
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    "priority must be one of ${IssuePriority.entries.joinToString()}"
                )
        }

        call.respond(HttpStatusCode.OK, IssueRepository.filter(status, priority))
    }

    get("/{id}") {
        val id = call.intParam("id") ?: return@get call.badId()
        val issue = IssueRepository.getById(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, "issue $id not found")
        call.respond(HttpStatusCode.OK, issue)
    }

    post { call.respond(HttpStatusCode.Created, IssueRepository.add(call.receive())) }

    put("/{id}") {
        val id = call.intParam("id") ?: return@put call.badId()
        val updated = IssueRepository.update(id, call.receive())
            ?: return@put call.respond(HttpStatusCode.NotFound, "issue $id not found")
        call.respond(HttpStatusCode.OK, updated)
    }

    put("/{id}/status") {
        val id = call.intParam("id") ?: return@put call.badId()
        val request = call.receive<StatusChangeRequest>()
        val updated = IssueRepository.changeStatus(id, request.status)
            ?: return@put call.respond(HttpStatusCode.NotFound, "issue $id not found")
        call.respond(HttpStatusCode.OK, updated)
    }

    delete("/{id}") {
        val id = call.intParam("id") ?: return@delete call.badId()
        if (!IssueRepository.delete(id)) {
            return@delete call.respond(HttpStatusCode.NotFound, "issue $id not found")
        }
        call.respond(HttpStatusCode.NoContent)
    }
}
