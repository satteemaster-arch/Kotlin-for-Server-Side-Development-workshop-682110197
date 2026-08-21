package org.example.project

import org.example.project.booking.AppointmentRepository
import org.example.project.booking.AppointmentRequest
import org.example.project.booking.BookingRuleException
import org.example.project.booking.ServiceRepository
import org.example.project.booking.ServiceRequest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Project 4: the same service cannot be booked twice at overlapping times. */
class BookingTest {

    @BeforeTest
    fun setUp() {
        DatabaseFactory.init("bookingtest")
        DatabaseFactory.clearAll()
    }

    /** A 60 minute service, so 10:00 runs until 11:00. */
    private fun aService(minutes: Int = 60) =
        ServiceRepository.add(ServiceRequest("ตัดผมชาย", "ตัด + สระ", minutes)).id

    private fun book(serviceId: Int, time: String, who: String = "สมชาย") =
        AppointmentRepository.add(AppointmentRequest(who, "$who@example.com", time, serviceId))

    @Test
    fun `a free slot can be booked`() {
        val serviceId = aService()
        assertNotNull(book(serviceId, "2026-08-25T10:00"))
    }

    @Test
    fun `booking the exact same time twice is rejected`() {
        // Arrange
        val serviceId = aService()
        book(serviceId, "2026-08-25T10:00")

        // Act & Assert
        val error = assertFailsWith<BookingRuleException> {
            book(serviceId, "2026-08-25T10:00", "สมหญิง")
        }
        assertTrue(error.message!!.contains("already booked"))
    }

    @Test
    fun `a booking that starts inside another one is rejected`() {
        // 10:00-11:00 is taken, so 10:30 lands in the middle of it.
        val serviceId = aService(minutes = 60)
        book(serviceId, "2026-08-25T10:00")

        assertFailsWith<BookingRuleException> { book(serviceId, "2026-08-25T10:30", "สมหญิง") }
    }

    @Test
    fun `a booking that ends inside another one is rejected`() {
        // 09:30-10:30 overlaps the front of the 10:00-11:00 slot.
        val serviceId = aService(minutes = 60)
        book(serviceId, "2026-08-25T10:00")

        assertFailsWith<BookingRuleException> { book(serviceId, "2026-08-25T09:30", "สมหญิง") }
    }

    @Test
    fun `a booking starting exactly when the previous one ends is allowed`() {
        // Ranges that merely touch do not overlap: 10:00-11:00 then 11:00-12:00.
        val serviceId = aService(minutes = 60)
        book(serviceId, "2026-08-25T10:00")

        assertNotNull(book(serviceId, "2026-08-25T11:00", "สมหญิง"))
    }

    @Test
    fun `the same time on a different day is free`() {
        val serviceId = aService()
        book(serviceId, "2026-08-25T10:00")
        assertNotNull(book(serviceId, "2026-08-26T10:00", "สมหญิง"))
    }

    @Test
    fun `two different services can be booked at the same time`() {
        // Double booking is per service, not per clock.
        val haircut = aService()
        val massage = ServiceRepository.add(ServiceRequest("นวดแผนไทย", "60 นาที", 60)).id

        book(haircut, "2026-08-25T10:00")
        assertNotNull(book(massage, "2026-08-25T10:00", "สมหญิง"))
    }

    @Test
    fun `updating an appointment does not clash with itself`() {
        // Arrange
        val serviceId = aService()
        val existing = book(serviceId, "2026-08-25T10:00")!!

        // Act - same slot, only the client name changes
        val updated = AppointmentRepository.update(
            existing.id,
            AppointmentRequest("สมชาย ใจดี", "somchai@example.com", "2026-08-25T10:00", serviceId)
        )

        // Assert
        assertEquals("สมชาย ใจดี", updated?.clientName)
    }

    @Test
    fun `an appointment for a service that does not exist returns null`() {
        assertNull(book(serviceId = 999, time = "2026-08-25T10:00"))
    }

    @Test
    fun `a badly formatted time is rejected`() {
        val serviceId = aService()
        assertFailsWith<BookingRuleException> { book(serviceId, "25/08/2026 10:00") }
    }

    @Test
    fun `a service duration of zero is rejected`() {
        assertFailsWith<BookingRuleException> {
            ServiceRepository.add(ServiceRequest("ไม่มีระยะเวลา", "...", 0))
        }
    }
}
