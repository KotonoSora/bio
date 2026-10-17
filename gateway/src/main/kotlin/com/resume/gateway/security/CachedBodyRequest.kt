package com.resume.gateway.security

import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

/**
 * Replayable request body: the CSRF double-submit form check and ServiceProxy
 * both need the bytes. The gateway buffers request bodies anyway (small
 * JSON/form payloads; ServiceProxy reads them whole), so this wrapper is the
 * single read, not an extra copy.
 */
class CachedBodyRequest(request: HttpServletRequest) : HttpServletRequestWrapper(request) {

    val body: ByteArray = request.inputStream.readBytes()

    override fun getInputStream(): ServletInputStream = ByteArrayServletInputStream(ByteArrayInputStream(body))

    override fun getReader(): BufferedReader =
        BufferedReader(InputStreamReader(ByteArrayInputStream(body), characterEncoding ?: Charsets.UTF_8.name()))

    override fun getContentLength(): Int = body.size

    override fun getContentLengthLong(): Long = body.size.toLong()

    private class ByteArrayServletInputStream(private val source: ByteArrayInputStream) : ServletInputStream() {
        override fun read(): Int = source.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = source.read(b, off, len)
        override fun available(): Int = source.available()
        override fun isFinished(): Boolean = source.available() == 0
        override fun isReady(): Boolean = true
        override fun setReadListener(listener: ReadListener?) = Unit
    }
}
