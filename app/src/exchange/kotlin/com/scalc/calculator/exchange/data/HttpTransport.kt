package com.scalc.calculator.exchange.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Raw HTTP response: [body] is decoded as UTF-8 ("" for 304/204); [etag] is the `ETag` header. */
data class HttpResponse(val statusCode: Int, val body: String, val etag: String? = null)

/**
 * Minimal blocking HTTPS GET seam. It keeps [HttpRatesClient] testable on the JVM without a
 * network; production code uses [UrlConnectionTransport]. Callers run it on an I/O dispatcher.
 */
fun interface HttpTransport {
    /** @throws IOException on connection problems or when [url] is not HTTPS. */
    fun get(url: String, headers: Map<String, String>): HttpResponse
}

/**
 * [HttpTransport] on the platform's [HttpURLConnection] (no third-party networking library).
 * Only HTTPS is accepted, including after redirects; HTTP caching is disabled so conditional
 * requests are fully controlled by the caller. Success bodies above [maxBodyBytes] are rejected.
 */
class UrlConnectionTransport(
    private val connectTimeoutMillis: Int = CONNECT_TIMEOUT_MS,
    private val readTimeoutMillis: Int = READ_TIMEOUT_MS,
    private val maxBodyBytes: Int = MAX_BODY_BYTES,
) : HttpTransport {

    override fun get(url: String, headers: Map<String, String>): HttpResponse {
        val target = parseHttpsUrl(url)
        val connection = target.openConnection() as? HttpURLConnection
            ?: throw IOException("Unsupported connection type for $url")
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.useCaches = false
            connection.instanceFollowRedirects = true
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }

            val status = connection.responseCode
            if (!connection.url.protocol.equals(HTTPS, ignoreCase = true)) {
                throw IOException("Redirected to a non-HTTPS location")
            }
            val etag = connection.getHeaderField("ETag")
            val body = when {
                status == HttpURLConnection.HTTP_NOT_MODIFIED || status == HttpURLConnection.HTTP_NO_CONTENT -> ""
                status in 200..299 -> connection.inputStream.use { readUtf8(it, maxBodyBytes, failWhenTooLarge = true) }
                // Error bodies are only used for diagnostics: read a small prefix.
                else -> connection.errorStream?.use { readUtf8(it, MAX_ERROR_BODY_BYTES, failWhenTooLarge = false) }.orEmpty()
            }
            return HttpResponse(status, body, etag)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_BODY_BYTES = 1 shl 20
        private const val MAX_ERROR_BODY_BYTES = 4 * 1024
        private const val HTTPS = "https"

        /** Parses [url] and rejects anything that is not an absolute HTTPS URL. */
        internal fun parseHttpsUrl(url: String): URL {
            val parsed = try {
                URI(url).toURL()
            } catch (e: Exception) {
                throw IOException("Invalid URL: $url", e)
            }
            if (!parsed.protocol.equals(HTTPS, ignoreCase = true)) throw IOException("Only HTTPS is allowed: $url")
            return parsed
        }

        private fun readUtf8(input: InputStream, limit: Int, failWhenTooLarge: Boolean): String {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                val room = limit - out.size()
                if (read > room) {
                    if (failWhenTooLarge) throw RatesFormatException("Response exceeds $limit bytes")
                    out.write(buffer, 0, room)
                    break
                }
                out.write(buffer, 0, read)
            }
            return String(out.toByteArray(), Charsets.UTF_8)
        }
    }
}
