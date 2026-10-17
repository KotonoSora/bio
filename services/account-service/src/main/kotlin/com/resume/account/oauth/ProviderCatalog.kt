package com.resume.account.oauth

import com.resume.account.config.AccountProperties
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.stereotype.Component

/** Button payload for the login screen: enabled providers only (BR-01). */
data class ProviderView(val id: String, val label: String)

/**
 * The closed provider set (BR-01): Google, Apple, Facebook, LinkedIn plus a
 * generic OIDC adapter - configuration-only additions. Registrations are built
 * from `account.oauth.providers` (env/vault); no Spring Boot client auto-config
 * is used, so nothing exists outside this catalog.
 */
@Component
class ProviderCatalog(private val props: AccountProperties) {

    private val registrations: Map<String, ClientRegistration> = props.oauth.providers
        .filterValues { it.enabled && it.clientId.isNotBlank() && it.authorizeUri.isNotBlank() }
        .mapValues { (id, s) -> build(id, s) }

    fun enabled(): List<ProviderView> = props.oauth.providers
        .filterKeys { it in registrations }
        .map { (id, s) -> ProviderView(id, s.label.ifBlank { id.replaceFirstChar(Char::uppercase) }) }

    fun registration(provider: String): ClientRegistration? = registrations[provider.lowercase()]

    fun redirectUri(provider: String): String = "${props.oauth.redirectBaseUrl.trimEnd('/')}/api/auth/callback/$provider"

    fun subjectClaim(provider: String): String =
        props.oauth.providers[provider.lowercase()]?.subjectClaim?.ifBlank { "sub" } ?: "sub"

    private fun build(id: String, s: AccountProperties.ProviderSettings): ClientRegistration {
        val builder = ClientRegistration.withRegistrationId(id)
            .clientId(s.clientId)
            .clientSecret(s.clientSecret)
            .clientAuthenticationMethod(
                if (s.clientAuth.equals("post", ignoreCase = true)) {
                    ClientAuthenticationMethod.CLIENT_SECRET_POST
                } else {
                    ClientAuthenticationMethod.CLIENT_SECRET_BASIC
                },
            )
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(redirectUri(id))
            .scope(s.scopes)
            .authorizationUri(s.authorizeUri)
            .tokenUri(s.tokenUri)
            .userNameAttributeName(s.subjectClaim.ifBlank { "sub" })
            .clientName(s.label.ifBlank { id })
        if (s.jwkSetUri.isNotBlank()) builder.jwkSetUri(s.jwkSetUri)
        if (s.issuer.isNotBlank()) builder.issuerUri(s.issuer)
        if (s.userinfoUri.isNotBlank()) builder.userInfoUri(s.userinfoUri)
        return builder.build()
    }
}
