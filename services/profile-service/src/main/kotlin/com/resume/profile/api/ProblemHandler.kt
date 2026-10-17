package com.resume.profile.api

import com.resume.profile.domain.AccountPurgeInProgressException
import com.resume.profile.domain.UsernameReservedException
import com.resume.profile.domain.UsernameRetiredException
import com.resume.profile.domain.UsernameTakenException
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

/**
 * RFC 9457 ProblemDetail for every error. Stable `code` strings (never renamed).
 * Bodies carry traceId only — no stack traces, payloads, PII, or money values.
 */
@RestControllerAdvice
class ProblemHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed")
        pd.type = URI.create("https://resume-builder.local/problems/validation-failed")
        pd.title = "Validation failed"
        pd.setProperty("code", "validation_failed")
        pd.setProperty("traceId", MDC.get("traceId") ?: "")
        pd.setProperty("errors", ex.bindingResult.fieldErrors.map {
            mapOf("field" to it.field, "code" to (it.code ?: "invalid"))
        })
        return pd
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Not found")
        pd.setProperty("code", "not_found")
        pd.setProperty("traceId", MDC.get("traceId") ?: "")
        return pd
    }

    @ExceptionHandler(ProfileAbsentException::class)
    fun handleProfileAbsent(ex: ProfileAbsentException): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Not found")
        pd.setProperty("code", "profile_absent")
        pd.setProperty("traceId", MDC.get("traceId") ?: "")
        return pd
    }

    /** Feature 02 (BR-07) + feature 06 availability codes: 422 with stable codes. */
    @ExceptionHandler(
        UsernameTakenException::class,
        UsernameRetiredException::class,
        UsernameReservedException::class,
        PurgeRequestValidationException::class,
    )
    fun handleUsername(ex: RuntimeException): ProblemDetail {
        val code = when (ex) {
            is UsernameTakenException -> "username_taken"
            is UsernameRetiredException -> "username_retired"
            is UsernameReservedException -> "username_reserved"
            else -> "validation_failed"
        }
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.message ?: "Unprocessable")
        pd.setProperty("code", code)
        pd.setProperty("traceId", MDC.get("traceId") ?: "")
        return pd
    }

    /** Feature 02 (S-3): a different active run holds the account's purge guard. */
    @ExceptionHandler(AccountPurgeInProgressException::class)
    fun handlePurgeInProgress(ex: AccountPurgeInProgressException): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Conflict")
        pd.setProperty("code", "purge_in_progress")
        pd.setProperty("traceId", MDC.get("traceId") ?: "")
        return pd
    }

    @ExceptionHandler(SecurityException::class)
    fun handleForbidden(ex: SecurityException): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.message ?: "Forbidden")
        pd.setProperty("code", "foreign_profile")
        pd.setProperty("traceId", MDC.get("traceId") ?: "")
        return pd
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleConflict(ex: IllegalStateException): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Conflict")
        pd.setProperty("code", "conflict")
        pd.setProperty("traceId", MDC.get("traceId") ?: "")
        return pd
    }
}
