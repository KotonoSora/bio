package com.resume.account.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Feature 01 config surface (env-driven; no secrets in code).
 * Values that gate behaviour: session lifetimes (NFR-04), step-up freshness
 * (NFR-05), callback allowlist (BR-04), provider rollout flags (Q-01).
 */
@ConfigurationProperties(prefix = "account")
class AccountProperties {
    var auth = AuthSettings()
    var session = SessionSettings()
    var oauth = OAuthSettings()
    var services = ServiceEndpoints()
    var mail = MailSettings()
    var deletion = DeletionSettings()

    class AuthSettings {
        /** Same-origin allowlist for callbackURL/returnTo (relative paths only; BR-04). */
        var allowedReturnPrefixes: List<String> = listOf("/app", "/welcome", "/resumes", "/account")
        /** HMAC key for single-use recovery invitation tokens (BR-12); session-stable in dev when blank. */
        var recoverySigningKey: String = ""
        /** Auth state TTL seconds (BR-04: single-use, 10 min). */
        var stateTtlSeconds: Long = 600
        /** Recovery invitation TTL seconds (BR-12: single-use, 15 min). */
        var recoveryTtlSeconds: Long = 900
    }

    class SessionSettings {
        var idleDays: Long = 7
        var absoluteDays: Long = 90
        var quota: Int = 50
        var stepUpSeconds: Long = 600
        var cookieName: String = "rb_session"
        var cookieSecure: Boolean = true
        var sweepIntervalMs: Long = 60_000
    }

    class ServiceEndpoints {
        var profileBaseUrl: String = "http://localhost:8082"
        var billingBaseUrl: String = "http://localhost:8088"
        /** Consumer cache bound (BR-09: never longer than 30 s). */
        var entitlementCacheSeconds: Long = 30
        /** First-login handoff lookup cache (T-10: status cached 60 s). */
        var handoffCacheSeconds: Long = 60
        var timeoutMs: Long = 2_000
    }

    class MailSettings {
        /** log = no transport configured (dev); notices are never auto-verified in code. */
        var mode: String = "log"
    }

    /**
     * Feature 02 deletion settings (TL 01 D-4/D-6, TL 02). Grace/reminder day
     * counts are the decided product numbers (NFR-02/NFR-03); legal retention
     * floors and the buyer-email hashing policy are counsel-owned configuration
     * (Q-01) - blank means "not configured", never an invented default.
     */
    class DeletionSettings {
        /** Exactly 30 days from request to purge (NFR-02). */
        var graceDays: Long = 30
        /** Exactly one reminder at day 23 (NFR-03). */
        var reminderAfterDays: Long = 23
        var purgeCron: String = "0 30 3 * * *" // daily 03:30 UTC (Q-03)
        var reminderCron: String = "0 45 3 * * *"
        var probeCron: String = "0 0 4 * * 1" // weekly residual probe (BR-10)
        /** Certificate namespace root; account-service mounts its own volume here. */
        var storageRoot: String = "/srv/uploads"
        /** Legal-hold flag recorded honestly on the certificate (BR-05); policy is legal-owned. */
        var legalHold: Boolean = false
        var stuckAfterHours: Long = 24
        var participantRetries: Int = 3
        /** Registered purge participants (roster discipline; account-service itself is implicit). */
        var participants: List<Participant> = emptyList()
        /** Counsel-owned retention floors; blank = launch blocker Q-01 not yet resolved. */
        var retentionYears: String = ""
        /** Counsel-owned hashed-email allow-list policy (Q-01). */
        var hashBuyerEmail: Boolean = false
        /** Strict roster check at boot: registered-but-unreachable participant fails startup. */
        var strictParticipants: Boolean = false
    }

    class Participant {
        var name: String = ""
        var baseUrl: String = ""
    }

    class OAuthSettings {
        /** Public origin the IdP redirects back to; redirect URI = base + /api/auth/callback/<provider>. */
        var redirectBaseUrl: String = "http://localhost"
        var providers: Map<String, ProviderSettings> = emptyMap()
    }

    class ProviderSettings {
        var enabled: Boolean = true
        /** Button label shown on the login screen (config-driven, BR-01). */
        var label: String = ""
        var clientId: String = ""
        var clientSecret: String = ""
        var scopes: List<String> = listOf("openid", "profile", "email")
        var authorizeUri: String = ""
        var tokenUri: String = ""
        var userinfoUri: String = ""
        var jwkSetUri: String = ""
        var issuer: String = ""
        /** basic (default) or post. */
        var clientAuth: String = "basic"
        /** Claim carrying the stable subject in the userinfo response. */
        var subjectClaim: String = "sub"
    }
}
