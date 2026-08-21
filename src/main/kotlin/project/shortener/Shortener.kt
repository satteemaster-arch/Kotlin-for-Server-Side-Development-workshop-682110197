package org.example.project.shortener

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.example.project.BusinessRuleException
import org.example.project.BusinessRuleException.Kind
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import kotlin.random.Random

// --- Project 2: URL Shortener Service ---

@Serializable
data class ShortUrl(
    val id: Int,
    val shortCode: String,
    val shortUrl: String,
    val longUrl: String,
    val clickCount: Int,
    val createdAt: String
)

@Serializable
data class ShortenRequest(val longUrl: String)

object ShortUrls : Table("short_urls") {
    val id = integer("id").autoIncrement()

    // Indexed and unique: every lookup in this service is by short code.
    val shortCode = varchar("short_code", 16).uniqueIndex()

    val longUrl = text("long_url")
    val clickCount = integer("click_count")
    val createdAt = varchar("created_at", 40)

    override val primaryKey = PrimaryKey(id)
}

class ShortenerRuleException(message: String, kind: Kind = Kind.CONFLICT) : BusinessRuleException(message, kind)

object ShortUrlRepository {

    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val CODE_LENGTH = 7

    /** Where a short code is served from, used to build the returned short URL. */
    const val BASE_PATH = "/s"

    private fun toShortUrl(row: ResultRow): ShortUrl {
        val code = row[ShortUrls.shortCode]
        return ShortUrl(
            id = row[ShortUrls.id],
            shortCode = code,
            shortUrl = "$BASE_PATH/$code",
            longUrl = row[ShortUrls.longUrl],
            clickCount = row[ShortUrls.clickCount],
            createdAt = row[ShortUrls.createdAt]
        )
    }

    private fun randomCode(): String =
        (1..CODE_LENGTH).map { ALPHABET[Random.nextInt(ALPHABET.length)] }.joinToString("")

    /** Retries on the (unlikely) collision instead of trusting randomness blindly. */
    private fun generateUniqueCode(): String {
        repeat(10) {
            val candidate = randomCode()
            val taken = ShortUrls.selectAll().where { ShortUrls.shortCode eq candidate }.any()
            if (!taken) return candidate
        }
        throw ShortenerRuleException("could not generate a unique short code")
    }

    fun getAll(): List<ShortUrl> = transaction { ShortUrls.selectAll().map(::toShortUrl) }

    fun getByCode(code: String): ShortUrl? = transaction {
        ShortUrls.selectAll().where { ShortUrls.shortCode eq code }.map(::toShortUrl).singleOrNull()
    }

    fun shorten(request: ShortenRequest): ShortUrl = transaction {
        val url = request.longUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw ShortenerRuleException("longUrl must start with http:// or https://", Kind.INVALID)
        }

        val code = generateUniqueCode()
        val now = Instant.now().toString()
        val newId = ShortUrls.insert {
            it[shortCode] = code
            it[longUrl] = url
            it[clickCount] = 0
            it[createdAt] = now
        } get ShortUrls.id

        ShortUrl(newId, code, "$BASE_PATH/$code", url, 0, now)
    }

    /** Resolves a code and counts the click in the same transaction. */
    fun resolveAndCount(code: String): ShortUrl? = transaction {
        val existing = ShortUrls.selectAll().where { ShortUrls.shortCode eq code }
            .map(::toShortUrl).singleOrNull() ?: return@transaction null

        ShortUrls.update({ ShortUrls.shortCode eq code }) {
            it[clickCount] = existing.clickCount + 1
        }
        existing.copy(clickCount = existing.clickCount + 1)
    }

    fun delete(code: String): Boolean = transaction {
        ShortUrls.deleteWhere { ShortUrls.shortCode eq code } > 0
    }
}

fun Route.shortenerRoutes() {

    post("/shorten") {
        val created = ShortUrlRepository.shorten(call.receive())
        call.respond(HttpStatusCode.Created, created)
    }

    route("/s") {
        // The brief asks for GET /{shortCode} at the root, but this project shares
        // one server with nine others, so a root wildcard would swallow their routes.
        // The redirect lives under /s/{shortCode} instead.
        get("/{shortCode}") {
            val code = call.parameters["shortCode"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "short code missing")

            val target = ShortUrlRepository.resolveAndCount(code)
                ?: return@get call.respond(HttpStatusCode.NotFound, "short code $code not found")

            // 302: the mapping could be deleted later, so this is not permanent.
            call.respondRedirect(target.longUrl, permanent = false)
        }

        get("/{shortCode}/stats") {
            val code = call.parameters["shortCode"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "short code missing")

            val found = ShortUrlRepository.getByCode(code)
                ?: return@get call.respond(HttpStatusCode.NotFound, "short code $code not found")
            call.respond(HttpStatusCode.OK, found)
        }

        delete("/{shortCode}") {
            val code = call.parameters["shortCode"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "short code missing")

            if (!ShortUrlRepository.delete(code)) {
                return@delete call.respond(HttpStatusCode.NotFound, "short code $code not found")
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }

    get("/shorten/all") { call.respond(HttpStatusCode.OK, ShortUrlRepository.getAll()) }
}
