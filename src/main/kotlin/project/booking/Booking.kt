package org.example.project.booking

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
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

// --- Project 4: Appointment Booking System API ---

@Serializable
data class Service(
    val id: Int,
    val name: String,
    val description: String,
    val defaultDurationInMinutes: Int
)

@Serializable
data class ServiceRequest(
    val name: String,
    val description: String,
    val defaultDurationInMinutes: Int
)

@Serializable
data class Appointment(
    val id: Int,
    val clientName: String,
    val clientEmail: String,
    val appointmentTime: String,
    val serviceId: Int
)

@Serializable
data class AppointmentRequest(
    val clientName: String,
    val clientEmail: String,
    val appointmentTime: String,
    val serviceId: Int
)

object Services : Table("booking_services") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 150)
    val description = text("description")
    val defaultDurationInMinutes = integer("default_duration_minutes")
    override val primaryKey = PrimaryKey(id)
}

object Appointments : Table("booking_appointments") {
    val id = integer("id").autoIncrement()
    val clientName = varchar("client_name", 150)
    val clientEmail = varchar("client_email", 200)

    // ISO-8601 local date-time, e.g. 2026-08-25T10:00 - sorts and compares as text.
    val appointmentTime = varchar("appointment_time", 40)

    val serviceId = integer("service_id")
        .references(Services.id, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(id)
}

/** Raised when a request is well-formed but breaks a booking rule. */
class BookingRuleException(message: String, kind: Kind = Kind.CONFLICT) : BusinessRuleException(message, kind)

object ServiceRepository {

    private fun toService(row: ResultRow) = Service(
        id = row[Services.id],
        name = row[Services.name],
        description = row[Services.description],
        defaultDurationInMinutes = row[Services.defaultDurationInMinutes]
    )

    fun getAll(): List<Service> = transaction { Services.selectAll().map(::toService) }

    fun getById(id: Int): Service? = transaction {
        Services.selectAll().where { Services.id eq id }.map(::toService).singleOrNull()
    }

    fun add(request: ServiceRequest): Service = transaction {
        if (request.defaultDurationInMinutes <= 0) {
            throw BookingRuleException("defaultDurationInMinutes must be greater than zero", Kind.INVALID)
        }
        val newId = Services.insert {
            it[name] = request.name
            it[description] = request.description
            it[defaultDurationInMinutes] = request.defaultDurationInMinutes
        } get Services.id
        Service(newId, request.name, request.description, request.defaultDurationInMinutes)
    }

    fun update(id: Int, request: ServiceRequest): Service? = transaction {
        if (request.defaultDurationInMinutes <= 0) {
            throw BookingRuleException("defaultDurationInMinutes must be greater than zero", Kind.INVALID)
        }
        val changed = Services.update({ Services.id eq id }) {
            it[name] = request.name
            it[description] = request.description
            it[defaultDurationInMinutes] = request.defaultDurationInMinutes
        }
        if (changed == 0) null
        else Service(id, request.name, request.description, request.defaultDurationInMinutes)
    }

    fun delete(id: Int): Boolean = transaction {
        Services.deleteWhere { Services.id eq id } > 0
    }
}

object AppointmentRepository {

    private fun toAppointment(row: ResultRow) = Appointment(
        id = row[Appointments.id],
        clientName = row[Appointments.clientName],
        clientEmail = row[Appointments.clientEmail],
        appointmentTime = row[Appointments.appointmentTime],
        serviceId = row[Appointments.serviceId]
    )

    private fun parseTime(raw: String): LocalDateTime =
        try {
            LocalDateTime.parse(raw)
        } catch (e: DateTimeParseException) {
            throw BookingRuleException("appointmentTime must be ISO-8601, e.g. 2026-08-25T10:00", Kind.INVALID)
        }

    fun getAll(): List<Appointment> = transaction { Appointments.selectAll().map(::toAppointment) }

    fun getById(id: Int): Appointment? = transaction {
        Appointments.selectAll().where { Appointments.id eq id }.map(::toAppointment).singleOrNull()
    }

    fun getByService(serviceId: Int): List<Appointment> = transaction {
        Appointments.selectAll().where { Appointments.serviceId eq serviceId }.map(::toAppointment)
    }

    /**
     * Two bookings for the same service clash when their time ranges overlap.
     * Touching ranges do not clash: a 10:00-10:30 slot leaves 10:30 free.
     * [ignoreId] lets an update skip comparing an appointment against itself.
     */
    private fun findClash(
        serviceId: Int,
        start: LocalDateTime,
        durationMinutes: Int,
        ignoreId: Int?
    ): Appointment? {
        val end = start.plusMinutes(durationMinutes.toLong())

        return Appointments.selectAll()
            .where { Appointments.serviceId eq serviceId }
            .map(::toAppointment)
            .filter { it.id != ignoreId }
            .firstOrNull { existing ->
                val existingStart = LocalDateTime.parse(existing.appointmentTime)
                val existingEnd = existingStart.plusMinutes(durationMinutes.toLong())
                start.isBefore(existingEnd) && existingStart.isBefore(end)
            }
    }

    /** Null when the service does not exist. Throws on a double booking. */
    fun add(request: AppointmentRequest): Appointment? = transaction {
        val service = Services.selectAll().where { Services.id eq request.serviceId }
            .map { it[Services.defaultDurationInMinutes] }.singleOrNull()
            ?: return@transaction null

        val start = parseTime(request.appointmentTime)
        val clash = findClash(request.serviceId, start, service, ignoreId = null)
        if (clash != null) {
            throw BookingRuleException(
                "service ${request.serviceId} is already booked at ${clash.appointmentTime}"
            )
        }

        val newId = Appointments.insert {
            it[clientName] = request.clientName
            it[clientEmail] = request.clientEmail
            it[appointmentTime] = request.appointmentTime
            it[serviceId] = request.serviceId
        } get Appointments.id

        Appointment(
            newId,
            request.clientName,
            request.clientEmail,
            request.appointmentTime,
            request.serviceId
        )
    }

    fun update(id: Int, request: AppointmentRequest): Appointment? = transaction {
        val exists = Appointments.selectAll().where { Appointments.id eq id }.any()
        if (!exists) return@transaction null

        val service = Services.selectAll().where { Services.id eq request.serviceId }
            .map { it[Services.defaultDurationInMinutes] }.singleOrNull()
            ?: throw BookingRuleException("service ${request.serviceId} does not exist", Kind.INVALID)

        val start = parseTime(request.appointmentTime)
        val clash = findClash(request.serviceId, start, service, ignoreId = id)
        if (clash != null) {
            throw BookingRuleException(
                "service ${request.serviceId} is already booked at ${clash.appointmentTime}"
            )
        }

        Appointments.update({ Appointments.id eq id }) {
            it[clientName] = request.clientName
            it[clientEmail] = request.clientEmail
            it[appointmentTime] = request.appointmentTime
            it[serviceId] = request.serviceId
        }

        Appointment(
            id,
            request.clientName,
            request.clientEmail,
            request.appointmentTime,
            request.serviceId
        )
    }

    fun delete(id: Int): Boolean = transaction {
        Appointments.deleteWhere { Appointments.id eq id } > 0
    }
}

fun Route.bookingRoutes() = route("/booking") {

    get("/services") { call.respond(HttpStatusCode.OK, ServiceRepository.getAll()) }

    get("/services/{id}") {
        val id = call.intParam("id") ?: return@get call.badId()
        val service = ServiceRepository.getById(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, "service $id not found")
        call.respond(HttpStatusCode.OK, service)
    }

    post("/services") {
        call.respond(HttpStatusCode.Created, ServiceRepository.add(call.receive()))
    }

    put("/services/{id}") {
        val id = call.intParam("id") ?: return@put call.badId()
        val updated = ServiceRepository.update(id, call.receive())
            ?: return@put call.respond(HttpStatusCode.NotFound, "service $id not found")
        call.respond(HttpStatusCode.OK, updated)
    }

    delete("/services/{id}") {
        val id = call.intParam("id") ?: return@delete call.badId()
        if (!ServiceRepository.delete(id)) {
            return@delete call.respond(HttpStatusCode.NotFound, "service $id not found")
        }
        call.respond(HttpStatusCode.NoContent)
    }

    get("/appointments") { call.respond(HttpStatusCode.OK, AppointmentRepository.getAll()) }

    get("/appointments/{id}") {
        val id = call.intParam("id") ?: return@get call.badId()
        val appointment = AppointmentRepository.getById(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, "appointment $id not found")
        call.respond(HttpStatusCode.OK, appointment)
    }

    post("/appointments") {
        val created = AppointmentRepository.add(call.receive())
            ?: return@post call.respond(HttpStatusCode.BadRequest, "service does not exist")
        call.respond(HttpStatusCode.Created, created)
    }

    put("/appointments/{id}") {
        val id = call.intParam("id") ?: return@put call.badId()
        val updated = AppointmentRepository.update(id, call.receive())
            ?: return@put call.respond(HttpStatusCode.NotFound, "appointment $id not found")
        call.respond(HttpStatusCode.OK, updated)
    }

    delete("/appointments/{id}") {
        val id = call.intParam("id") ?: return@delete call.badId()
        if (!AppointmentRepository.delete(id)) {
            return@delete call.respond(HttpStatusCode.NotFound, "appointment $id not found")
        }
        call.respond(HttpStatusCode.NoContent)
    }
}
