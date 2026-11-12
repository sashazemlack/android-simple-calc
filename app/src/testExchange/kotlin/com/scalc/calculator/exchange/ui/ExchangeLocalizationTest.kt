package com.scalc.calculator.exchange.ui

import com.scalc.calculator.exchange.data.FiatCurrencies
import com.scalc.calculator.exchange.data.FrankfurterParser
import com.scalc.calculator.exchange.model.Currency
import com.scalc.calculator.exchange.model.CurrencyNames
import com.scalc.calculator.exchange.model.LivePayloads
import com.scalc.calculator.exchange.model.RateSourceKind
import com.scalc.calculator.exchange.model.RateStatusInfo
import com.scalc.calculator.exchange.model.RateTable
import com.scalc.calculator.exchange.model.StatusKind
import com.scalc.calculator.exchange.model.TestStrings
import com.scalc.calculator.ui.components.filterPickerItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class ExchangeLocalizationTest {

    private val utc = TimeZone.getTimeZone("UTC")

    /** All fiat currencies of the saved live feed, in picker order. */
    private val liveCurrencies: List<Currency> by lazy {
        val parsed = FrankfurterParser.parseRates(LivePayloads.RATES_JSON)
        val catalog = FrankfurterParser.parseCurrencies(LivePayloads.CURRENCIES_JSON)
        val fiat = FiatCurrencies.select(parsed.rates.keys, catalog)
        val names = catalog.mapNotNull { (code, entry) -> entry.name?.let { code to it } }.toMap()
        val table = checkNotNull(
            RateTable.create(
                parsed.rates.filterKeys { it in fiat },
                parsed.rateDates,
                CurrencyNames.englishNames(fiat, names),
                RateSourceKind.FRANKFURTER_BLENDED,
            ),
        )
        CurrencyOrdering.sort(table.currencies)
    }

    private fun search(locale: Locale, query: String): List<String> =
        filterPickerItems(currencyPickerItems(liveCurrencies, locale), query).map { it.key }

    @Test
    fun pickerKeepsPinnedCurrenciesFirstThenSortsByCode() {
        val codes = liveCurrencies.map { it.code }
        assertEquals(161, codes.size)
        assertEquals(CurrencyOrdering.PINNED, codes.take(7))
        assertEquals(codes.drop(7).sorted(), codes.drop(7))
    }

    @Test
    fun pickerShowsLocalizedNamesAndKeepsEnglishOnesSearchable() {
        val german = currencyPickerItems(liveCurrencies, Locale.GERMAN).associateBy { it.key }
        assertEquals("US-Dollar", german.getValue("USD").subtitle)
        assertEquals(listOf("United States Dollar"), german.getValue("USD").keywords)
        // Unknown to the platform: the provider's English name is shown, no duplicate keyword.
        assertEquals("COMESA Dollar", german.getValue("CMD").subtitle)
        assertEquals(emptyList<String>(), german.getValue("CMD").keywords)
        assertEquals("米ドル", currencyPickerItems(liveCurrencies, Locale.JAPANESE).first { it.key == "USD" }.subtitle)
    }

    @Test
    fun pickerSearchMatchesCodeLocalizedNameAndEnglishName() {
        // Code.
        assertEquals(listOf("CZK"), search(Locale.GERMAN, "czk"))
        // Localized name, accent- and case-insensitive.
        assertEquals(listOf("CZK"), search(Locale.GERMAN, "tschechische"))
        assertTrue("USD" in search(Locale.GERMAN, "us-dollar"))
        assertEquals(listOf("CZK"), search(Locale.forLanguageTag("cs"), "ceska koruna"))
        assertTrue("PLN" in search(Locale.forLanguageTag("pl"), "złoty"))
        assertTrue("USD" in search(Locale.JAPANESE, "米ドル"))
        // English provider name, in any language.
        assertEquals(listOf("USD"), search(Locale.GERMAN, "united states"))
        assertEquals(listOf("USD"), search(Locale.JAPANESE, "united states dollar"))
        assertEquals(listOf("CZK"), search(Locale.JAPANESE, "Czech"))
        assertTrue("CHF" in search(Locale.GERMAN, "swiss franc"))
        // Crypto-assets are not offered at all.
        assertEquals(emptyList<String>(), search(Locale.ENGLISH, "bitcoin"))
    }

    @Test
    fun localizeUsesTheGivenLanguageForNamesStatusAndDates() {
        val state = ExchangeUiState(
            loaded = true,
            amountText = "1",
            from = Currency("EUR", "Euro"),
            to = Currency("TTD", "Trinidad and Tobago Dollar"),
            convertedText = "7.50",
            conversionAvailable = true,
            unitRateText = "1 EUR = 7.4987 TTD",
            inverseRateText = "1 TTD = 0.133356 EUR",
            pairRateDate = "2026-11-11",
            status = RateStatusInfo(
                StatusKind.Fresh,
                fetchedAtMillis = 1_794_000_000_000L,
                rateDate = "2026-11-12",
                source = RateSourceKind.FRANKFURTER_BLENDED,
            ),
            refreshing = false,
            currencies = emptyList(),
        )
        val english = state.localize(ExchangeLocalization(Locale.ENGLISH, TestStrings.ENGLISH), utc)
        assertEquals("Trinidad & Tobago Dollar", english.toName)
        assertEquals("Rates last updated: 6 Nov 2026, 21:20", english.status.text)
        assertEquals("Exchange rates of 12 Nov 2026", english.status.detail)
        assertEquals("11 Nov 2026", english.pairRateDate)

        val german = state.localize(ExchangeLocalization(Locale.GERMAN, TestStrings.GERMAN), utc)
        assertEquals("Euro", german.fromName)
        assertEquals("Trinidad-und-Tobago-Dollar", german.toName)
        assertEquals("Kurse zuletzt aktualisiert: 6 Nov. 2026, 21:20", german.status.text)
        assertEquals("Wechselkurse vom 12 Nov. 2026", german.status.detail)

        assertNull(state.copy(pairRateDate = null).localize(ExchangeLocalization(Locale.ENGLISH, TestStrings.ENGLISH), utc).pairRateDate)
    }

    @Test
    fun pairDateTemplateIsAResource() {
        assertEquals("%1\$s (as of %2\$s)", TestStrings.string("exch_rate_dated"))
        assertEquals(
            "1 EUR = 7.4987 TTD (as of 11 Nov 2026)",
            String.format(Locale.ENGLISH, TestStrings.string("exch_rate_dated"), "1 EUR = 7.4987 TTD", "11 Nov 2026"),
        )
    }
}
