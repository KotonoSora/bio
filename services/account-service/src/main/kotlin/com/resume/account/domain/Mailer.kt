package com.resume.account.domain

import com.resume.account.config.AccountProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Transactional notice transport port (feature 01). No SMTP/provider is
 * invented here: the default `log` mode records the event with a masked
 * recipient and never the code or the address (NFR-12). A real transport is
 * wired by the environment that owns mail (same port, no contract change).
 */
interface Mailer {
    fun sendContactCode(email: String, code: String)

    /**
     * Day-23 deletion reminder (feature 02 Q-04): exactly one transactional
     * email naming the purge date. Returns true when the transport accepted it
     * (bounces are surfaced to support by the transport owner, BR-12/S-10).
     */
    fun sendDeletionReminder(email: String, purgeDueAt: java.time.Instant): Boolean

    /**
     * Feature 05 (S-9): the signed-email approval link. The transport owns
     * delivery; the token itself is never logged here.
     */
    fun sendConciergeApprovalLink(email: String?, itemKind: String)
}

@Component
class LoggingMailer(private val props: AccountProperties) : Mailer {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun sendContactCode(email: String, code: String) {
        if (props.mail.mode == "off") return
        // No code, no full address: the log proves the event, nothing more.
        log.info("contact_email_code_issued recipient={} transport=log", mask(email))
    }

    override fun sendDeletionReminder(email: String, purgeDueAt: java.time.Instant): Boolean {
        if (props.mail.mode == "off") return false
        log.info("deletion_reminder_issued recipient={} purgeDueAt={} transport=log", mask(email), purgeDueAt)
        return true
    }

    override fun sendConciergeApprovalLink(email: String?, itemKind: String) {
        if (props.mail.mode == "off") return
        // The signed token is never logged (S-9); the event + masked recipient is
        // all the proof a dev run needs - a real transport delivers the link.
        log.info(
            "concierge_approval_link_issued recipient={} itemKind={} transport=log",
            email?.let(::mask) ?: "(none)",
            itemKind,
        )
    }

    private fun mask(email: String): String {
        val parts = email.split("@", limit = 2)
        val local = parts.firstOrNull() ?: ""
        val domain = parts.getOrNull(1) ?: ""
        return "${local.firstOrNull() ?: '*'}***@$domain"
    }
}
