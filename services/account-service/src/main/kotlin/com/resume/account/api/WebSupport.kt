package com.resume.account.api

import com.resume.account.config.AccountProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import java.time.Duration

/** `rb_session`: HttpOnly + Secure + SameSite=Lax; server-side bounds still govern (NFR-04). */
class SessionCookie(private val props: AccountProperties) {

    fun setHeader(sid: String): String =
        cookie(sid, Duration.ofDays(props.session.absoluteDays)).toString()

    /** Logout clears the cookie everywhere the browser shares it (all tabs). */
    fun clearHeader(): String = cookie("", Duration.ZERO).toString()

    private fun cookie(value: String, maxAge: Duration): ResponseCookie =
        ResponseCookie.from(props.session.cookieName, value)
            .httpOnly(true)
            .secure(props.session.cookieSecure)
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAge)
            .build()
}

/** Every callback answer is a 302; failure bodies never exist (no oracle). */
object Redirects {
    fun to(location: String): ResponseEntity<Void> =
        ResponseEntity.status(302).header(HttpHeaders.LOCATION, location).build()

    fun authError(): ResponseEntity<Void> = to("/auth/error")
}
