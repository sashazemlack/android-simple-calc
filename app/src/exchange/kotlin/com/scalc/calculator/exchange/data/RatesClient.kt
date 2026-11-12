package com.scalc.calculator.exchange.data

import com.scalc.calculator.exchange.model.RateTable

/** Source of reference rates. Implementations must not receive or send any user input. */
interface RatesClient {

    /**
     * Downloads the latest reference rates.
     *
     * @param etag validator of the cached table (HTTP `ETag`), or null to force a full download.
     * @return [FetchResult.NotModified] when the server confirmed that the cached table is current,
     *   otherwise the new table (with [RateTable.fetchedAtMillis] = 0; the caller stamps it).
     * @throws java.io.IOException on network problems (offline, DNS, timeout, TLS).
     * @throws RatesServerException when the provider answers with an HTTP error status.
     * @throws RatesFormatException when the response is empty, malformed or incomplete.
     */
    suspend fun fetch(etag: String?): FetchResult
}

/** Outcome of a successful [RatesClient.fetch]. */
sealed interface FetchResult {

    /** HTTP 304: the cached table (matching the sent ETag) is still current. */
    data object NotModified : FetchResult

    /** A complete new table; [etag] is the validator to send next time (null if unsupported). */
    data class Downloaded(val table: RateTable, val etag: String?) : FetchResult
}

/** The provider answered with a non-success HTTP status ([statusCode] is -1 for an invalid response). */
class RatesServerException(val statusCode: Int, message: String) : Exception(message)

/** The provider's response could not be used (empty, malformed, too large or missing currencies). */
class RatesFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)
