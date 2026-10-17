package com.resume.resume.security

/**
 * Server-set request user for RLS. Written by RlsUserFilter (in-chain, after the
 * internal JWT is verified), read by RlsAwareDataSource on every borrowed connection.
 * ThreadLocal: always cleared at request end. Unset outside requests (jobs use runAs).
 * RLS denies by default when app.user_id is unset.
 */
object RlsUserHolder {
    private val current = ThreadLocal<String?>()

    fun set(userId: String) = current.set(userId)
    fun get(): String? = current.get()
    fun clear() = current.remove()

    inline fun <T> runAs(userId: String, block: () -> T): T {
        set(userId)
        try {
            return block()
        } finally {
            clear()
        }
    }
}
