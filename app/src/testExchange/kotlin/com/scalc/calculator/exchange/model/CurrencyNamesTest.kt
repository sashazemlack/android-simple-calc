package com.scalc.calculator.exchange.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class CurrencyNamesTest {

    private val usd = Currency("USD", "United States Dollar")
    private val czk = Currency("CZK", "Czech Koruna")

    @Test
    fun displayNamesFollowTheLanguage() {
        assertEquals("US Dollar", CurrencyNames.displayName(usd, Locale.ENGLISH))
        assertEquals("US-Dollar", CurrencyNames.displayName(usd, Locale.GERMAN))
        assertEquals("米ドル", CurrencyNames.displayName(usd, Locale.JAPANESE))
        assertEquals("美元", CurrencyNames.displayName(usd, Locale.forLanguageTag("zh-Hans")))
        assertEquals("Tschechische Krone", CurrencyNames.displayName(czk, Locale.GERMAN))
        assertEquals("チェコ コルナ", CurrencyNames.displayName(czk, Locale.JAPANESE))
    }

    @Test
    fun namesShownOnTheirOwnStartWithACapitalLetter() {
        // CLDR writes these in lower case for use inside sentences.
        assertEquals("Dólar estadounidense", CurrencyNames.displayName(usd, Locale.forLanguageTag("es")))
        assertEquals("Česká koruna", CurrencyNames.displayName(czk, Locale.forLanguageTag("cs")))
        assertEquals("Dollar des États-Unis", CurrencyNames.displayName(usd, Locale.FRENCH))
    }

    @Test
    fun unknownToThePlatformFallsBackToTheEnglishNameThenTheCode() {
        // COMESA Dollar and Guernsey Pound are not ISO 4217 codes.
        assertEquals("COMESA Dollar", CurrencyNames.displayName(Currency("CMD", "COMESA Dollar"), Locale.GERMAN))
        assertEquals("Guernsey Pound", CurrencyNames.displayName(Currency("GGP", " Guernsey Pound "), Locale.JAPANESE))
        assertEquals("CMD", CurrencyNames.displayName(Currency("CMD", ""), Locale.GERMAN))
        assertEquals("CMD", CurrencyNames.displayName(Currency("CMD", "CMD"), Locale.GERMAN))
    }

    @Test
    fun englishNamesPreferTheProvider() {
        assertEquals(
            mapOf("USD" to "United States Dollar", "PLN" to "Polish Zloty", "CMD" to "CMD", "CZK" to "Czech Koruna"),
            CurrencyNames.englishNames(listOf("USD", "PLN", "CMD", "CZK"), mapOf("USD" to "United States Dollar", "CZK" to " ", "PLN" to "")),
        )
        assertEquals("US Dollar", CurrencyNames.englishName("USD"))
    }

    @Test
    fun platformNamesAreOnlyRealNames() {
        assertEquals("Euro", CurrencyNames.platformName("EUR", Locale.ENGLISH))
        assertNull(CurrencyNames.platformName("CMD", Locale.ENGLISH))
        assertNull(CurrencyNames.platformName("usd!", Locale.ENGLISH))
        assertNull(CurrencyNames.platformName("", Locale.ENGLISH))
    }
}
