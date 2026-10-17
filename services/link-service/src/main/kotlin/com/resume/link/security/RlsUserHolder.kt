package com.resume.link.security

/**
 * Server-set request scope for RLS. Written by RlsUserFilter (in-chain, after the
 * internal JWT is verified) or by internal handlers via [asService]/[asOps]
 * BEFORE their transaction borrows a connection; read by RlsAwareDataSource on
 * every borrowed connection. ThreadLocal: always cleared at request end.
 *
 * - `app.user_id` owner predicate (`short_links.user_id` = app.user_id).
 * - `app.context` server-set scopes: 'svc' platform purge participant (feature
 *   02), 'ops' audited support reads. Handlers still apply an explicit equality
 *   predicate - context alone is never an authorization.
 * Unset scope = deny-by-default (policies return zero rows).
 */
object RlsUserHolder {
    private val current = ThreadLocal<String?>()
    private val contextSlot = ThreadLocal<String?>()

    fun set(userId: String) = current.set(userId)
    fun get(): String? = current.get()
    fun context(): String? = contextSlot.get()
    fun clear() {
        current.remove()
        contextSlot.remove()
    }

    inline fun <T> runAs(userId: String, block: () -> T): T {
        set(userId)
        try {
            return block()
        } finally {
            clear()
        }
    }

    /** Platform purge participant scope: 'svc' (feature 02). */
    fun <T> asService(block: () -> T): T = withContext("svc", block)

    /** Audited ops scope: 'ops'. */
    fun <T> asOps(block: () -> T): T = withContext("ops", block)

    private fun <T> withContext(ctx: String, block: () -> T): T {
        val previous = contextSlot.get()
        contextSlot.set(ctx)
        try {
            return block()
        } finally {
            if (previous == null) contextSlot.remove() else contextSlot.set(previous)
        }
    }
}
