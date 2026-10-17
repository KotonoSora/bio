package com.resume.account.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.resume.account.config.AccountProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant

/**
 * Certificate payload (TL 03 shape): ids, timestamps, verbatim participant
 * counts, and the honest legal-hold flag - nothing else (NFR-10/S-7).
 */
data class PurgeCertificate(
    val userId: String,
    val runId: String,
    val startedAt: Instant,
    val finishedAt: Instant,
    val tables: Map<String, Long>,
    val files: Long,
    val legalHold: Boolean,
)

/**
 * Local-volume certificate writer (TL 01 D-5, TL 04): write to `_tmp/`, parse
 * back to verify, then atomic-rename into `account/purge-certificates/{userId}/`.
 * `certificate_ref` is only referenced after a verified rename; failures leave
 * no final file, so a failed run never emits a half certificate. Server-side
 * reads only - no route, no Nginx location (S-4).
 */
@Service
class CertificateStore(
    private val props: AccountProperties,
    private val mapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val storageRoot: Path = Path.of(props.deletion.storageRoot).toAbsolutePath().normalize()
    private val root: Path = storageRoot.resolve("account").resolve("purge-certificates")

    /** Returns the storage-relative reference stored on the deletion row. */
    fun write(certificate: PurgeCertificate): String {
        val userDir = root.resolve(certificate.userId)
        val tmpDir = root.resolve("_tmp")
        Files.createDirectories(userDir)
        Files.createDirectories(tmpDir)
        val tmpFile = tmpDir.resolve("${certificate.runId}.json")
        val finalFile = userDir.resolve("${certificate.runId}.json")

        val bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(certificate)
        Files.write(
            tmpFile,
            bytes,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
        mapper.readValue(tmpFile.toFile(), PurgeCertificate::class.java) // verify, never trust the write
        Files.move(tmpFile, finalFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        return ref(certificate.userId, certificate.runId)
    }

    fun ref(userId: String, runId: String): String = "account/purge-certificates/$userId/$runId.json"

    /** Crash leftovers: `_tmp` files older than the sweep window never become final (TL 04). */
    fun sweepTemp(olderThan: Instant) {
        val tmpDir = root.resolve("_tmp")
        if (!Files.isDirectory(tmpDir)) return
        Files.newDirectoryStream(tmpDir).use { stream ->
            stream.forEach { path ->
                try {
                    if (Files.getLastModifiedTime(path).toInstant() < olderThan) Files.deleteIfExists(path)
                } catch (ex: Exception) {
                    log.warn("certificate tmp sweep skipped {}: {}", path.fileName, ex.message)
                }
            }
        }
    }

    /** Support read (audited caller only): refs are generated here; containment is a belt-and-braces check. */
    fun read(ref: String): PurgeCertificate? {
        val path = storageRoot.resolve(ref.removePrefix("/")).normalize()
        if (!path.startsWith(storageRoot) || !Files.isRegularFile(path)) return null
        return try {
            mapper.readValue(path.toFile(), PurgeCertificate::class.java)
        } catch (ex: Exception) {
            log.warn("certificate read failed ref={}: {}", ref, ex.message)
            null
        }
    }
}
