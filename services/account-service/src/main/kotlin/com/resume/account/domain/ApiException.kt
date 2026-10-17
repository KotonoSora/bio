package com.resume.account.domain

import org.springframework.http.HttpStatus

/**
 * Problem-catalog exception (RFC 9457). `code` strings are stable contract
 * (TL 03 "Problem body and code catalog") and never renamed.
 */
class ApiException(
    val status: HttpStatus,
    val code: String,
    val detail: String,
    val extras: Map<String, Any?> = emptyMap(),
) : RuntimeException(detail) {

    companion object {
        fun stepUpRequired(): ApiException = ApiException(
            status = HttpStatus.FORBIDDEN,
            code = "step_up_required",
            detail = "Confirm it's you to continue.",
            extras = mapOf("reauthPath" to "/api/auth/step-up"),
        )

        fun validation(detail: String): ApiException =
            ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed", detail)

        fun unauthenticated(): ApiException =
            ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Authentication required.")

        fun notFound(code: String, detail: String): ApiException =
            ApiException(HttpStatus.NOT_FOUND, code, detail)

        fun conflict(code: String, detail: String): ApiException =
            ApiException(HttpStatus.CONFLICT, code, detail)

        fun invalidCode(): ApiException =
            ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "email_code_invalid", "That code didn't work. Check it or request a new one.")

        fun throttled(retryAfterSeconds: Long): ApiException = ApiException(
            status = HttpStatus.TOO_MANY_REQUESTS,
            code = "throttled",
            detail = "Too many attempts. Try again in ${retryAfterSeconds}s.",
            extras = mapOf("retryAfterSeconds" to retryAfterSeconds),
        )

        /** Feature 02 (BR-01): typed confirmation does not match the expected value. */
        fun confirmationMismatch(): ApiException = ApiException(
            status = HttpStatus.UNPROCESSABLE_ENTITY,
            code = "confirmation_mismatch",
            detail = "Type your username exactly (or DELETE) to continue.",
        )

        /** Feature 02: cancel without an active grace request. */
        fun deleteNotRequested(): ApiException =
            ApiException(HttpStatus.CONFLICT, "delete_not_requested", "There's nothing to cancel.")

        /** Feature 02: purge already completed; terminal. */
        fun purgeCompleted(): ApiException =
            ApiException(HttpStatus.GONE, "purge_completed", "This account has already been deleted.")

        /** Feature 02 (S-3): a different active run holds the account row guard. */
        fun purgeInProgress(): ApiException =
            ApiException(HttpStatus.CONFLICT, "purge_in_progress", "A purge run is already in progress.")
    }
}
