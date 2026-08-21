package org.example.project

/**
 * Base type for every business rule each of the ten APIs enforces.
 *
 * A rule can be broken in two different ways, and they are different HTTP
 * answers: the request itself can be nonsense ([Kind.INVALID] -> 400), or the
 * request can be perfectly well-formed but impossible against the current
 * state of the data ([Kind.CONFLICT] -> 409). "rating must be 1..5" is the
 * first; "that book is already lent out" is the second.
 */
open class BusinessRuleException(
    message: String,
    val kind: Kind = Kind.CONFLICT
) : Exception(message) {

    enum class Kind { INVALID, CONFLICT }
}
