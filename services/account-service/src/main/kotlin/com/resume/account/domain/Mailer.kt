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
}

@Component
class LoggingMailer(private val props: AccountProperties) : Mailer {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun sendContactCode(email: String, code: String) {
        if (props.mail.mode == "off") return
        val parts = email.split("@", limit = 2)
        val local = parts.firstOrNull() ?: ""
        val domain = parts.getOrNull(1) ?: ""
        val masked = "${local.firstOrNull() ?: '*'}***@$domain"
        // No code, no full address: the log proves the event, nothing more.
        log.info("contact_email_code_issued recipient={} transport=log", masked)
    }
}
