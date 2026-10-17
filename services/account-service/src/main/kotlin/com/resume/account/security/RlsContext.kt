package com.resume.account.security

/**
 * Server-set request scope for RLS (TL 02 "RLS and access paths"). Written by
 * RlsUserFilter (owner requests) or by internal handlers via [asService]/[asOps]
 * BEFORE their transaction borrows a connection; read by RlsAwareDataSource.
 * Never client-influenced. ThreadLocal: always cleared at request end.
 *
 * - `app.user_id`  owner predicate (`users.id`/`user_id` = app.user_id).
 * - `app.context`  'svc' internal handlers (introspect/entitlement/purge; each
 *                  handler applies its own equality predicate), 'ops' audited
 *                  recovery-link route (existence + delivery target only).
 * Both unset => deny-by-default (policies return zero rows).
 */
object RlsContext {
    private val userId = ThreadLocal<String?>()
    private val context = ThreadLocal<String?>()

    fun setUser(userId: String?) = this.userId.set(userId)
    fun user(): String? = userId.get()
    fun context(): String? = context.get()
    fun clear() {
        userId.remove()
        context.remove()
    }

    /** Internal handler scope: 'svc'. */
    fun <T> asService(block: () -> T): T = withContext("svc", block)

    /** Audited ops scope: 'ops' (recovery-link route only). */
    fun <T> asOps(block: () -> T): T = withContext("ops", block)

    private fun <T> withContext(ctx: String, block: () -> T): T {
        val previous = context.get()
        context.set(ctx)
        try {
            return block()
        } finally {
            if (previous == null) context.remove() else context.set(previous)
        }
    }
}
