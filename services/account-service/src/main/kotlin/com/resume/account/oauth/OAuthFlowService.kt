package com.resume.account.oauth

import com.resume.account.config.AccountProperties
import com.resume.account.data.SessionEntity
import com.resume.account.domain.AuditService
import com.resume.account.domain.HandoffStatus
import com.resume.account.domain.IdentityService
import com.resume.account.domain.IdpProfile
import com.resume.account.domain.LinkOutcome
import com.resume.account.domain.ProfileHandoffClient
import com.resume.account.domain.RecoveryService
import com.resume.account.domain.SessionService
import com.resume.account.domain.Tokens
import com.resume.account.security.RlsContext
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed interface CallbackOutcome {
    /** `sid` is set when an SSO dance started a session (login / recovery re-link). */
    data class Success(val redirectTo: String, val sid: String?) : CallbackOutcome
    /** Generic sign-in failure: no existence oracle, redirect to `/auth/error` (BR-04). */
    data object Failed : CallbackOutcome
}

sealed interface LinkStart {
    data class Redirect(val url: String) : LinkStart
    data object AlreadyLinked : LinkStart
}

/**
 * The SSO dance engine (T-03/T-04). `state`/`nonce`/PKCE live as single-use
 * Redis keys (D-4); every mode (login, step_up, link, recovery) shares the same
 * validated callback. Failures collapse to the generic `auth_failed` path.
 */
@Service
class OAuthFlowService(
    private val catalog: ProviderCatalog,
    private val states: AuthStateStore,
    private val tokens: TokenClient,
    private val identities: IdentityService,
    private val sessions: SessionService,
    private val recovery: RecoveryService,
    private val handoff: ProfileHandoffClient,
    private val audit: AuditService,
    private val props: AccountProperties,
    meters: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val failed = meters.counter("auth.failed")

    fun startLogin(provider: String, callbackURL: String): String {
        val target = ReturnTargets.sanitize(callbackURL, props.auth.allowedReturnPrefixes, DEFAULT_APP)
        return begin(provider, mode = "login", callbackURL = target)
    }

    /** Step-up re-dance: caller names the provider; the session's `sid` binds the result. */
    fun startStepUp(userId: String, sid: String, provider: String, returnTo: String): String {
        val target = ReturnTargets.sanitize(returnTo, props.auth.allowedReturnPrefixes, DEFAULT_APP)
        val url = begin(provider, mode = "step_up", callbackURL = target, userId = userId, sid = sid)
        audit.record(userId = userId, action = "step_up", target = "challenge_issued")
        return url
    }

    fun startLink(userId: String, provider: String, returnTo: String): LinkStart {
        if (identities.linkedProviders(userId).any { it.providerId == provider.lowercase() }) {
            return LinkStart.AlreadyLinked
        }
        val target = ReturnTargets.sanitize(returnTo, props.auth.allowedReturnPrefixes, DEFAULT_APP)
        return LinkStart.Redirect(begin(provider, mode = "link", callbackURL = target, userId = userId))
    }

    /** Recovery landing -> provider choreography; null means invalid/expired/reused token. */
    fun startRecovery(token: String, provider: String): String? {
        val invitation = recovery.inspect(token) ?: return null
        return begin(provider, mode = "recovery", callbackURL = "/app/account/settings", userId = invitation.userId, recoveryToken = token)
    }

    fun callback(provider: String, code: String?, state: String?, deviceLabel: String?, ipClass: String?): CallbackOutcome {
        if (code.isNullOrBlank() || state.isNullOrBlank()) return fail()
        val flowState = states.consume(state) ?: return fail()
        if (flowState.provider != provider.lowercase()) return fail()
        val redirectUri = catalog.redirectUri(provider.lowercase())
        val profile = tokens.exchange(provider.lowercase(), code, state, redirectUri, flowState.codeVerifier, flowState.nonce)
            ?: return fail()
        return try {
            when (flowState.mode) {
                "login" -> login(profile, flowState.callbackURL, deviceLabel, ipClass)
                "step_up" -> stepUp(flowState, ipClass)
                "link" -> linkProviders(profile, flowState, ipClass)
                "recovery" -> recoveryLink(profile, flowState, deviceLabel, ipClass)
                else -> fail()
            }
        } catch (ex: Exception) {
            log.warn("callback completion failed provider={} mode={} ({})", provider, flowState.mode, ex.message)
            fail()
        }
    }

    private fun login(profile: IdpProfile, callbackURL: String, deviceLabel: String?, ipClass: String?): CallbackOutcome {
        val (userId, created) = RlsContext.asService {
            val (user, created) = identities.resolve(profile, ipClass)
            user.id to created
        }
        val session = RlsContext.asService { sessions.issue(userId, deviceLabel, ipClass) }
        val redirect = when {
            created -> CHOOSER
            else -> when (handoff.lookup(userId)) {
                HandoffStatus.HasProfile -> callbackURL
                HandoffStatus.NoProfile -> CHOOSER
                HandoffStatus.Unknown -> DEFAULT_APP
            }
        }
        return CallbackOutcome.Success(redirect, session.sid)
    }

    private fun stepUp(flowState: AuthState, ipClass: String?): CallbackOutcome {
        val userId = flowState.userId ?: return fail()
        val sid = flowState.sid ?: return fail()
        val refreshed: SessionEntity? = RlsContext.asService { sessions.refreshAuthTime(sid, userId) }
        if (refreshed == null) return fail()
        audit.record(userId = userId, action = "step_up", target = "completed", ipClass = ipClass)
        return CallbackOutcome.Success(flowState.callbackURL, null)
    }

    private fun linkProviders(profile: IdpProfile, flowState: AuthState, ipClass: String?): CallbackOutcome {
        val userId = flowState.userId ?: return fail()
        val outcome = RlsContext.asService { identities.link(userId, profile, ipClass) }
        return when (outcome) {
            is LinkOutcome.Linked, LinkOutcome.AlreadyLinked -> CallbackOutcome.Success(flowState.callbackURL, null)
            LinkOutcome.ForeignSubject -> fail()
        }
    }

    private fun recoveryLink(
        profile: IdpProfile,
        flowState: AuthState,
        deviceLabel: String?,
        ipClass: String?,
    ): CallbackOutcome {
        val token = flowState.recoveryToken ?: return fail()
        val invitation = recovery.consume(token) ?: return fail()
        val userId = invitation.userId
        val outcome = RlsContext.asService { identities.link(userId, profile, ipClass) }
        if (outcome == LinkOutcome.ForeignSubject) return fail()
        // The provider proof is the same class as a fresh login; grant a session so the
        // member regains access immediately (no credential is ever set or shown).
        val session = RlsContext.asService { sessions.issue(userId, deviceLabel, ipClass) }
        audit.record(userId = userId, action = "login", target = "recovery", ipClass = ipClass)
        return CallbackOutcome.Success("/app/account/settings", session.sid)
    }

    private fun fail(): CallbackOutcome {
        failed.increment()
        return CallbackOutcome.Failed
    }

    private fun begin(
        provider: String,
        mode: String,
        callbackURL: String,
        userId: String? = null,
        sid: String? = null,
        recoveryToken: String? = null,
    ): String {
        val normalized = provider.lowercase()
        val registration = catalog.registration(normalized)
            ?: throw com.resume.account.domain.ApiException.validation("Sign-in isn't available right now.")
        val state = Tokens.random(24)
        val nonce = Tokens.random(24)
        val verifier = Pkce.verifier()
        states.put(
            state,
            AuthState(normalized, nonce, verifier, callbackURL, mode, userId, sid, recoveryToken),
        )
        val params = linkedMapOf(
            "response_type" to "code",
            "client_id" to registration.clientId,
            "redirect_uri" to catalog.redirectUri(normalized),
            "scope" to registration.scopes.joinToString(" "),
            "state" to state,
            "nonce" to nonce,
            "code_challenge" to Pkce.challenge(verifier),
            "code_challenge_method" to "S256",
        )
        if (normalized == "apple") params["response_mode"] = "form_post"
        val query = params.entries.joinToString("&") { (k, v) -> "$k=${url(v)}" }
        val base = registration.providerDetails.authorizationUri
            ?: throw com.resume.account.domain.ApiException.validation("Sign-in isn't available right now.")
        val separator = if (base.contains("?")) "&" else "?"
        return base + separator + query
    }

    private fun url(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    companion object {
        const val DEFAULT_APP = "/app"
        const val CHOOSER = "/app/welcome/username"
    }
}
