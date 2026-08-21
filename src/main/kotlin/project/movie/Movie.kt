package org.example.project.movie

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
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

// --- Project 6: Basic Movie Library API ---

@Serializable
data class Movie(val id: Int, val title: String, val director: String, val releaseYear: Int)

@Serializable
data class MovieRequest(val title: String, val director: String, val releaseYear: Int)

@Serializable
data class Review(
    val id: Int,
    val movieId: Int,
    val reviewerName: String,
    val rating: Int,
    val comment: String
)

@Serializable
data class ReviewRequest(val reviewerName: String, val rating: Int, val comment: String)

/**
 * A movie plus its rating summary.
 * [averageRating] is null - not 0.0 - when nobody has reviewed it yet:
 * "no opinion" and "everyone rated it zero" are different facts.
 */
@Serializable
data class MovieWithRating(
    val id: Int,
    val title: String,
    val director: String,
    val releaseYear: Int,
    val averageRating: Double?,
    val reviewCount: Int
)

object Movies : Table("movies") {
    val id = integer("id").autoIncrement()
    val title = varchar("title", 250)
    val director = varchar("director", 200)
    val releaseYear = integer("release_year")
    override val primaryKey = PrimaryKey(id)
}

object Reviews : Table("movie_reviews") {
    val id = integer("id").autoIncrement()
    val movieId = integer("movie_id").references(Movies.id, onDelete = ReferenceOption.CASCADE)
    val reviewerName = varchar("reviewer_name", 150)
    val rating = integer("rating")
    val comment = text("comment")
    override val primaryKey = PrimaryKey(id)
}

class MovieRuleException(message: String, kind: Kind = Kind.CONFLICT) : BusinessRuleException(message, kind)

object MovieRepository {

    private fun toMovie(row: ResultRow) = Movie(
        id = row[Movies.id],
        title = row[Movies.title],
        director = row[Movies.director],
        releaseYear = row[Movies.releaseYear]
    )

    fun getAll(): List<Movie> = transaction { Movies.selectAll().map(::toMovie) }

    fun getById(id: Int): Movie? = transaction {
        Movies.selectAll().where { Movies.id eq id }.map(::toMovie).singleOrNull()
    }

    fun getWithRating(id: Int): MovieWithRating? = transaction {
        val movie = Movies.selectAll().where { Movies.id eq id }
            .map(::toMovie).singleOrNull() ?: return@transaction null

        val ratings = Reviews.selectAll().where { Reviews.movieId eq id }.map { it[Reviews.rating] }

        MovieWithRating(
            id = movie.id,
            title = movie.title,
            director = movie.director,
            releaseYear = movie.releaseYear,
            averageRating = if (ratings.isEmpty()) null else ratings.average(),
            reviewCount = ratings.size
        )
    }

    /** Matches either field, case-insensitively; at least one term is required. */
    fun search(title: String?, director: String?): List<Movie> = transaction {
        if (title.isNullOrBlank() && director.isNullOrBlank()) {
            throw MovieRuleException("provide at least one of 'title' or 'director'", Kind.INVALID)
        }

        val titleNeedle = title?.takeIf { it.isNotBlank() }?.let { "%${it.lowercase()}%" }
        val directorNeedle = director?.takeIf { it.isNotBlank() }?.let { "%${it.lowercase()}%" }

        Movies.selectAll().where {
            when {
                titleNeedle != null && directorNeedle != null ->
                    (Movies.title.lowerCase() like titleNeedle) or (Movies.director.lowerCase() like directorNeedle)
                titleNeedle != null -> Movies.title.lowerCase() like titleNeedle
                else -> Movies.director.lowerCase() like directorNeedle!!
            }
        }.map(::toMovie)
    }

    fun add(request: MovieRequest): Movie = transaction {
        val newId = Movies.insert {
            it[title] = request.title
            it[director] = request.director
            it[releaseYear] = request.releaseYear
        } get Movies.id
        Movie(newId, request.title, request.director, request.releaseYear)
    }

    fun update(id: Int, request: MovieRequest): Movie? = transaction {
        val changed = Movies.update({ Movies.id eq id }) {
            it[title] = request.title
            it[director] = request.director
            it[releaseYear] = request.releaseYear
        }
        if (changed == 0) null else Movie(id, request.title, request.director, request.releaseYear)
    }

    fun delete(id: Int): Boolean = transaction { Movies.deleteWhere { Movies.id eq id } > 0 }
}

object ReviewRepository {

    private fun toReview(row: ResultRow) = Review(
        id = row[Reviews.id],
        movieId = row[Reviews.movieId],
        reviewerName = row[Reviews.reviewerName],
        rating = row[Reviews.rating],
        comment = row[Reviews.comment]
    )

    fun getByMovieId(movieId: Int): List<Review> = transaction {
        Reviews.selectAll().where { Reviews.movieId eq movieId }.map(::toReview)
    }

    fun getById(id: Int): Review? = transaction {
        Reviews.selectAll().where { Reviews.id eq id }.map(::toReview).singleOrNull()
    }

    /** Null when the movie does not exist. Throws when the rating is outside 1..5. */
    fun add(movieId: Int, request: ReviewRequest): Review? = transaction {
        if (request.rating !in 1..5) throw MovieRuleException("rating must be between 1 and 5", Kind.INVALID)

        val movieExists = Movies.selectAll().where { Movies.id eq movieId }.any()
        if (!movieExists) return@transaction null

        val newId = Reviews.insert {
            it[Reviews.movieId] = movieId
            it[reviewerName] = request.reviewerName
            it[rating] = request.rating
            it[comment] = request.comment
        } get Reviews.id

        Review(newId, movieId, request.reviewerName, request.rating, request.comment)
    }

    fun update(id: Int, request: ReviewRequest): Review? = transaction {
        if (request.rating !in 1..5) throw MovieRuleException("rating must be between 1 and 5", Kind.INVALID)

        val existing = Reviews.selectAll().where { Reviews.id eq id }
            .map(::toReview).singleOrNull() ?: return@transaction null

        Reviews.update({ Reviews.id eq id }) {
            it[reviewerName] = request.reviewerName
            it[rating] = request.rating
            it[comment] = request.comment
        }
        existing.copy(
            reviewerName = request.reviewerName,
            rating = request.rating,
            comment = request.comment
        )
    }

    fun delete(id: Int): Boolean = transaction { Reviews.deleteWhere { Reviews.id eq id } > 0 }
}

fun Route.movieRoutes() = route("/movies") {

    // Before /{id} so "search" is never parsed as an id.
    get("/search") {
        val title = call.request.queryParameters["title"]
        val director = call.request.queryParameters["director"]
        call.respond(HttpStatusCode.OK, MovieRepository.search(title, director))
    }

    get { call.respond(HttpStatusCode.OK, MovieRepository.getAll()) }

    get("/{id}") {
        val id = call.intParam("id") ?: return@get call.badId()
        val movie = MovieRepository.getWithRating(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, "movie $id not found")
        call.respond(HttpStatusCode.OK, movie)
    }

    post { call.respond(HttpStatusCode.Created, MovieRepository.add(call.receive())) }

    put("/{id}") {
        val id = call.intParam("id") ?: return@put call.badId()
        val updated = MovieRepository.update(id, call.receive())
            ?: return@put call.respond(HttpStatusCode.NotFound, "movie $id not found")
        call.respond(HttpStatusCode.OK, updated)
    }

    delete("/{id}") {
        val id = call.intParam("id") ?: return@delete call.badId()
        if (!MovieRepository.delete(id)) {
            return@delete call.respond(HttpStatusCode.NotFound, "movie $id not found")
        }
        call.respond(HttpStatusCode.NoContent)
    }

    get("/{id}/reviews") {
        val movieId = call.intParam("id") ?: return@get call.badId()
        if (MovieRepository.getById(movieId) == null) {
            return@get call.respond(HttpStatusCode.NotFound, "movie $movieId not found")
        }
        call.respond(HttpStatusCode.OK, ReviewRepository.getByMovieId(movieId))
    }

    post("/{id}/reviews") {
        val movieId = call.intParam("id") ?: return@post call.badId()
        val created = ReviewRepository.add(movieId, call.receive())
            ?: return@post call.respond(HttpStatusCode.NotFound, "movie $movieId not found")
        call.respond(HttpStatusCode.Created, created)
    }

    put("/reviews/{reviewId}") {
        val id = call.intParam("reviewId") ?: return@put call.badId()
        val updated = ReviewRepository.update(id, call.receive())
            ?: return@put call.respond(HttpStatusCode.NotFound, "review $id not found")
        call.respond(HttpStatusCode.OK, updated)
    }

    delete("/reviews/{reviewId}") {
        val id = call.intParam("reviewId") ?: return@delete call.badId()
        if (!ReviewRepository.delete(id)) {
            return@delete call.respond(HttpStatusCode.NotFound, "review $id not found")
        }
        call.respond(HttpStatusCode.NoContent)
    }
}
