package com.resume.profile.api

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
