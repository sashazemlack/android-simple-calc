package com.scalc.calculator.exchange.ui

import android.content.Context
import com.scalc.calculator.R
import com.scalc.calculator.exchange.model.RateStatusStrings

/**
 * Status-block templates from string resources. Templates are read unformatted (they still
 * contain `%1$s`); [com.scalc.calculator.exchange.model.RateStatusFormatter] fills them in.
 */
internal fun rateStatusStrings(context: Context): RateStatusStrings = RateStatusStrings(
    loading = context.getString(R.string.exch_status_loading),
    refreshing = context.getString(R.string.exch_status_refreshing),
    fresh = context.getString(R.string.exch_status_fresh),
    cached = context.getString(R.string.exch_status_cached),
    stale = context.getString(R.string.exch_status_stale),
    unavailable = context.getString(R.string.exch_status_unavailable),
    lastUpdated = context.getString(R.string.exch_status_last_updated),
    rateDate = context.getString(R.string.exch_status_rate_date),
    updateFailed = context.getString(R.string.exch_status_update_failed),
    errorNoConnection = context.getString(R.string.exch_error_no_connection),
    errorServer = context.getString(R.string.exch_error_server),
    errorInvalidData = context.getString(R.string.exch_error_invalid_data),
    sourceFrankfurter = context.getString(R.string.exch_source_frankfurter),
    sourceEcb = context.getString(R.string.exch_source_ecb),
)
