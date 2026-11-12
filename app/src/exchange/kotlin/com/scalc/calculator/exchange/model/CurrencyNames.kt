package com.scalc.calculator.exchange.model

import java.util.Locale
import java.util.Currency as JavaCurrency

/**
 * Currency names. The platform's `java.util.Currency` (ICU/CLDR data on Android) supplies names in
 * the user's language; the provider's English names fill the gaps (e.g. CNH, GGP or newly
 * introduced codes the platform does not know yet).
 */
object CurrencyNames {

    /**
     * English name for every code in [codes], as stored in a [RateTable]: the provider's name,
     * else the platform's English name, else the code itself.
     */
    fun englishNames(codes: Collection<String>, providerNames: Map<String, String>): Map<String, String> =
        codes.associateWith { code -> englishName(code, providerNames[code]) }

    /** The provider's [providerName] if not blank, else the platform's English name, else [code]. */
    fun englishName(code: String, providerName: String? = null): String =
        providerName?.trim()?.takeIf { it.isNotEmpty() } ?: platformName(code, Locale.ENGLISH) ?: code

    /**
     * Name of [currency] in [locale] for display: the platform's localized name when it knows a
     * real one (not just the code), else the English [Currency.name], else the code. The first
     * letter is capitalized for [locale] because the name is shown on its own (CLDR names are
     * lower case in several languages, e.g. "dólar estadounidense").
     */
    fun displayName(currency: Currency, locale: Locale): String {
        val name = platformName(currency.code, locale)
            ?: currency.name.trim().takeIf { it.isNotEmpty() }
            ?: currency.code
        return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    /** `java.util.Currency` display name in [locale], or null if the platform has no real name for [code]. */
    fun platformName(code: String, locale: Locale): String? =
        runCatching { JavaCurrency.getInstance(code).getDisplayName(locale) }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals(code, ignoreCase = true) }
}
