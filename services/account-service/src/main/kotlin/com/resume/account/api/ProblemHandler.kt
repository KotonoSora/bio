package com.resume.account.api

import com.resume.account.domain.ApiException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * RFC 9457 problem bodies with the stable feature-01 code catalog (G4:
 * codes are rename-frozen). No stack traces, no PII, no existence oracles;
 * `throttled` carries `Retry-After` (NFR-09).
 */
@RestControllerAdvice
class ProblemHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun apiException(ex: ApiException): ResponseEntity<Map<String, Any?>> {
        val body = mutableMapOf<String, Any?>(
            "type" to "https://api.example.com/problems/${ex.code}",
            "title" to title(ex),
            "status" to ex.status.value(),
            "code" to ex.code,
            "detail" to ex.detail,
        )
        ex.extras.forEach { (k, v) -> body[k] = v }
        traceId()?.let { body["traceId"] = it }
        val builder = ResponseEntity.status(ex.status).contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
        if (ex.code == "throttled") {
            val retry = (ex.extras["retryAfterSeconds"] as? Number)?.toLong() ?: 60
            builder.header(HttpHeaders.RETRY_AFTER, retry.toString())
        }
        return builder.body(body)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidBody(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, Any?>> =
        validation("Check the form and try again.")

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class,
        IllegalArgumentException::class,
    )
    fun invalidRequest(ex: Exception): ResponseEntity<Map<String, Any?>> =
        validation("Check the request and try again.")

    @ExceptionHandler(NoResourceFoundException::class)
    fun notFound(ex: NoResourceFoundException): ResponseEntity<Map<String, Any?>> =
        problem(HttpStatus.NOT_FOUND, "not_found", "Page not found")

    @ExceptionHandler(Exception::class)
    fun unexpected(ex: Exception): ResponseEntity<Map<String, Any?>> {
        log.error("unhandled error", ex)
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Something went wrong.")
    }

    private fun validation(detail: String): ResponseEntity<Map<String, Any?>> =
        problem(HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed", detail)

    private fun problem(status: HttpStatus, code: String, detail: String): ResponseEntity<Map<String, Any?>> {
        val body = mutableMapOf<String, Any?>(
            "type" to "https://api.example.com/problems/$code",
            "title" to code,
            "status" to status.value(),
            "code" to code,
            "detail" to detail,
        )
        traceId()?.let { body["traceId"] = it }
        return ResponseEntity.status(status).contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(body)
    }

    private fun title(ex: ApiException): String = when (ex.code) {
        "step_up_required" -> "Re-authentication required"
        "unauthenticated" -> "Authentication required"
        "throttled" -> "Too many attempts"
        "validation_failed", "email_code_invalid" -> "Check the input"
        "email_conflict", "already_linked", "last_link_forbidden" -> "Conflict"
        "session_not_found", "not_linked", "user_absent" -> "Not found"
        else -> ex.status.reasonPhrase
    }

    private fun traceId(): String? = MDC.get("traceId")?.takeIf { it.isNotBlank() }
}
