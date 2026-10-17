package com.resume.account.oauth

import com.resume.account.domain.IdpProfile
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

/**
 * Server-side code exchange + IdP profile read (BR-04). Token exchange runs
 * through the Spring Security OAuth2 client; `id_token` verification (signature
 * via JWKS, issuer, audience) uses Spring's OIDC decoder factory, with the
 * nonce compared against the single-use server state. Provider errors never
 * pass through - any failure resolves to null and the caller answers the generic
 * `auth_failed` (no oracle).
 */
@Service
class TokenClient(private val catalog: ProviderCatalog) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tokenClient: OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> =
        RestClientAuthorizationCodeTokenResponseClient()
    private val decoderFactory = OidcIdTokenDecoderFactory()
    private val http: RestClient = RestClient.builder()
        .requestFactory(
            org.springframework.http.client.JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
            ).apply { setReadTimeout(Duration.ofSeconds(10)) },
        )
        .build()

    fun exchange(
        provider: String,
        code: String,
        state: String,
        redirectUri: String,
        codeVerifier: String,
        nonce: String,
    ): IdpProfile? = try {
        val registration = catalog.registration(provider) ?: return null
        val authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri(registration.providerDetails.authorizationUri ?: return null)
            .clientId(registration.clientId)
            .redirectUri(redirectUri)
            .state(state)
            .attributes(mapOf("code_verifier" to codeVerifier))
            .build()
        val authorizationResponse = OAuth2AuthorizationResponse.success(code)
            .redirectUri(redirectUri)
            .state(state)
            .build()
        val grant = OAuth2AuthorizationCodeGrantRequest(
            registration,
            OAuth2AuthorizationExchange(authorizationRequest, authorizationResponse),
        )
        val tokenResponse = tokenClient.getTokenResponse(grant)
        val accessToken = tokenResponse.accessToken.tokenValue
        val idToken = (tokenResponse.additionalParameters?.get("id_token")) as? String
        val claims = if (idToken != null) {
            val decoded = decoderFactory.createDecoder(registration).decode(idToken)
            if (decoded.claims["nonce"] != nonce) {
                log.warn("id_token nonce mismatch provider={}", provider)
                return null
            }
            decoded.claims
        } else {
            null
        }
        val userinfo = if (claims == null || subject(claims, provider) == null) {
            fetchUserinfo(registration.providerDetails.userInfoEndpoint.uri, accessToken)
        } else {
            emptyMap()
        }
        build(provider, claims, userinfo) ?: null
    } catch (ex: Exception) {
        log.warn("auth code exchange failed provider={} status={}", provider, ex.message)
        null
    }

    private fun fetchUserinfo(uri: String?, accessToken: String): Map<String, Any?> {
        if (uri.isNullOrBlank()) return emptyMap()
        @Suppress("UNCHECKED_CAST")
        return http.get().uri(uri)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .retrieve()
            .body(Map::class.java) as? Map<String, Any?> ?: emptyMap()
    }

    private fun subject(claims: Map<String, Any?>, provider: String): String? =
        (claims[catalog.subjectClaim(provider)] as? String)?.ifBlank { null }
            ?: (claims["sub"] as? String)?.ifBlank { null }

    private fun build(
        provider: String,
        claims: Map<String, Any?>?,
        userinfo: Map<String, Any?>,
    ): IdpProfile? {
        val subject = claims?.let { subject(it, provider) }
            ?: (userinfo[catalog.subjectClaim(provider)] as? String)?.ifBlank { null }
        if (subject.isNullOrBlank()) return null
        val email = ((claims?.get("email") as? String) ?: (userinfo["email"] as? String))?.ifBlank { null }
        val emailVerified = when {
            claims?.containsKey("email_verified") == true -> claims["email_verified"] == true
            userinfo.containsKey("email_verified") -> userinfo["email_verified"] == true
            // Facebook userinfo carries the provider-verified primary email; a denied
            // email permission yields no value at all (BR-03 still creates the account).
            provider == "facebook" && email != null -> true
            else -> false
        }
        val name = (claims?.get("name") as? String) ?: (userinfo["name"] as? String)
        return IdpProfile(provider, subject, email, emailVerified, name)
    }
}
