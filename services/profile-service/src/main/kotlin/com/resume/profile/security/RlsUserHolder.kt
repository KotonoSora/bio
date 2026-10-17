package com.resume.profile.security

/**
 * Server-set request scope for RLS. Written by RlsUserFilter (owner requests,
 * after the internal JWT is verified) or by internal handlers via [asService]
 * BEFORE their transaction borrows a connection; read by RlsAwareDataSource on
 * every borrowed connection.
 *
 * - `app.user_id`  owner predicate (`profiles.user_id` = app.user_id).
 * - `app.context`  'svc' mesh reads (feature 01 first-login handoff); the
 *                  handler still applies an explicit equality predicate -
 *                  context alone is never an authorization.
 * Both unset => deny-by-default. ThreadLocal: always cleared at request end.
 */
object RlsUserHolder {
    private val userSlot = ThreadLocal<String?>()
    private val contextSlot = ThreadLocal<String?>()

    fun set(userId: String) = userSlot.set(userId)
    fun get(): String? = userSlot.get()
    fun context(): String? = contextSlot.get()

    fun clear() {
        userSlot.remove()
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

    /** Mesh internal-handler scope: 'svc' (feature 01 T-10 by-user read). */
    fun <T> asService(block: () -> T): T = withContext("svc", block)

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
