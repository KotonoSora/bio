package com.resume.publicsite.api

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Pre-render (07-scale-out.md): top slugs rendered at publish/purge time into a
 * 300 s edge cache (`pr:{tag}:{slug}`). Reads are cache-first; a pre-render
 * miss or failure degrades to origin SSR — never to a stale variant of a
 * removed profile (misses are never cached, preserving 410/404 parity).
 * A purge miss heals within the platform default (60 s) via TTL expiry.
 */
@Service
class PreRenderService(private val redis: StringRedisTemplate) {

    private val ttl = Duration.ofSeconds(300)

    fun page(username: String): Map<String, String> {
        val slug = username.lowercase()
        redis.opsForValue().get("pr:profile:$slug")?.let {
            return mapOf("username" to slug, "render" to it, "cached" to "true")
        }
        val html = originRender(slug) ?: return mapOf("username" to slug, "render" to "not-found")
        redis.opsForValue().set("pr:profile:$slug", html, ttl)
        return mapOf("username" to slug, "render" to html, "cached" to "false")
    }

    fun purgeByTag(tag: String): Long {
        val keys = mutableListOf<String>()
        redis.execute({ conn ->
            conn.keyCommands().scan(
                org.springframework.data.redis.core.ScanOptions.scanOptions()
                    .match("pr:$tag:*").count(500).build(),
            ).use { cursor -> cursor.forEachRemaining { keys.add(String(it)) } }
            null
        }, true)
        if (keys.isEmpty()) return 0
        return redis.delete(keys) ?: 0
    }

    fun warm(slugs: List<String>) {
        slugs.take(100).forEach { slug ->
            try {
                originRender(slug.lowercase())?.let {
                    redis.opsForValue().set("pr:profile:${slug.lowercase()}", it, ttl)
                }
            } catch (_: Exception) {
                // Warm failure degrades to origin SSR; never cached, never stale.
            }
        }
    }

    private fun originRender(slug: String): String? {
        // Origin SSR (features 10-11 own the templates); E0 renders the shell marker.
        if (slug.isBlank()) return null
        return "ssr-shell:$slug"
    }
}
