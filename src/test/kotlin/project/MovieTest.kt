package org.example.project

import org.example.project.movie.MovieRepository
import org.example.project.movie.MovieRequest
import org.example.project.movie.MovieRuleException
import org.example.project.movie.ReviewRepository
import org.example.project.movie.ReviewRequest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Project 6: average rating and search. */
class MovieTest {

    @BeforeTest
    fun setUp() {
        DatabaseFactory.init("movietest")
        DatabaseFactory.clearAll()
    }

    private fun aMovie(title: String = "Interstellar", director: String = "Christopher Nolan") =
        MovieRepository.add(MovieRequest(title, director, 2014)).id

    @Test
    fun `a movie with no reviews has a null average, not zero`() {
        // Arrange
        val id = aMovie()

        // Act
        val movie = MovieRepository.getWithRating(id)

        // Assert - "nobody rated it" and "everyone rated it 0" are different facts
        assertNull(movie?.averageRating)
        assertEquals(0, movie?.reviewCount)
    }

    @Test
    fun `average rating is the mean of every review`() {
        // Arrange
        val id = aMovie()
        ReviewRepository.add(id, ReviewRequest("สมชาย", 5, "ดีมาก"))
        ReviewRepository.add(id, ReviewRequest("สมหญิง", 4, "ดี"))
        ReviewRepository.add(id, ReviewRequest("สมศรี", 3, "พอใช้"))

        // Act
        val movie = MovieRepository.getWithRating(id)

        // Assert
        assertEquals(4.0, movie?.averageRating)
        assertEquals(3, movie?.reviewCount)
    }

    @Test
    fun `the average only counts reviews of that movie`() {
        // Arrange
        val interstellar = aMovie()
        val other = aMovie("Tenet", "Christopher Nolan")
        ReviewRepository.add(interstellar, ReviewRequest("สมชาย", 5, "ดีมาก"))
        ReviewRepository.add(other, ReviewRequest("สมหญิง", 1, "ไม่ชอบ"))

        // Assert
        assertEquals(5.0, MovieRepository.getWithRating(interstellar)?.averageRating)
        assertEquals(1.0, MovieRepository.getWithRating(other)?.averageRating)
    }

    @Test
    fun `a rating above 5 is rejected`() {
        val id = aMovie()
        assertFailsWith<MovieRuleException> { ReviewRepository.add(id, ReviewRequest("สมชาย", 6, "เกิน")) }
    }

    @Test
    fun `a rating of zero is rejected because the scale starts at 1`() {
        val id = aMovie()
        assertFailsWith<MovieRuleException> { ReviewRepository.add(id, ReviewRequest("สมชาย", 0, "ศูนย์")) }
    }

    @Test
    fun `ratings at both ends of the scale are accepted`() {
        val id = aMovie()
        ReviewRepository.add(id, ReviewRequest("ต่ำสุด", 1, "..."))
        ReviewRepository.add(id, ReviewRequest("สูงสุด", 5, "..."))
        assertEquals(3.0, MovieRepository.getWithRating(id)?.averageRating)
    }

    @Test
    fun `a review needs a movie that exists`() {
        assertNull(ReviewRepository.add(999, ReviewRequest("สมชาย", 5, "...")))
    }

    @Test
    fun `search by title ignores case and matches partially`() {
        aMovie("Interstellar", "Christopher Nolan")
        assertEquals(1, MovieRepository.search(title = "stellar", director = null).size)
        assertEquals(1, MovieRepository.search(title = "INTERSTELLAR", director = null).size)
    }

    @Test
    fun `search by director finds every film they made`() {
        aMovie("Interstellar", "Christopher Nolan")
        aMovie("Tenet", "Christopher Nolan")
        aMovie("Parasite", "Bong Joon-ho")

        assertEquals(2, MovieRepository.search(title = null, director = "nolan").size)
    }

    @Test
    fun `search with no terms at all is rejected`() {
        // Returning the whole library for an empty search would be a silent surprise.
        assertFailsWith<MovieRuleException> { MovieRepository.search(null, null) }
    }

    @Test
    fun `deleting a movie cascades to its reviews`() {
        // Arrange
        val id = aMovie()
        ReviewRepository.add(id, ReviewRequest("สมชาย", 5, "ดีมาก"))

        // Act
        MovieRepository.delete(id)

        // Assert
        assertTrue(ReviewRepository.getByMovieId(id).isEmpty())
    }
}
