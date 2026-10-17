package com.resume.profile.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Username retirement tombstone (feature 02, V5): permanently reserves the name
 * of a purged profile (BR-07). Written only by the purge handler, in the same
 * transaction as the profile delete; read by the availability checks.
 */
@Entity
@Table(name = "retired_usernames")
class RetiredUsername(
    @Id var username: String = "",
    @Column(name = "retired_at") var retiredAt: Instant = Instant.now(),
)

/** 422 `username_taken`: a live (or suspended, BR-07) profile owns the name. */
class UsernameTakenException : RuntimeException("That name is taken.")

/** 422 `username_retired`: a purged profile owned the name; reservation is permanent. */
class UsernameRetiredException : RuntimeException("That name was used before and stays reserved.")

/** 422 `username_reserved`: infrastructure slugs are never claimable. */
class UsernameReservedException : RuntimeException("That name is reserved.")
