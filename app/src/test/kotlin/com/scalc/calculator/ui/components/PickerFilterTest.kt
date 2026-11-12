package com.scalc.calculator.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickerFilterTest {

    private val pln = PickerItem("PLN", "PLN", "Polish Złoty")
    private val isk = PickerItem("ISK", "ISK", "Icelandic Króna")
    private val sek = PickerItem("SEK", "SEK", "Swedish Krona")
    private val items = listOf(pln, isk, sek)

    private fun keys(query: String) = filterPickerItems(items, query).map { it.key }

    @Test
    fun searchIgnoresAccentsAndCase() {
        assertEquals(listOf("PLN"), keys("zloty"))
        assertEquals(listOf("PLN"), keys("złoty"))
        assertEquals(listOf("PLN"), keys("ZŁOTY"))
        assertEquals(listOf("ISK", "SEK"), keys("krona"))
        assertEquals(listOf("ISK", "SEK"), keys("KRÓNA"))
        assertEquals(listOf("PLN"), keys("pln"))
        assertEquals(listOf("PLN"), keys("polish"))
    }

    @Test
    fun searchAlsoMatchesKeywords() {
        // Localized subtitle, English name as a hidden keyword.
        val usd = PickerItem("USD", "USD", "US-Dollar", keywords = listOf("United States Dollar"))
        val czk = PickerItem("CZK", "CZK", "Tschechische Krone", keywords = listOf("Czech Koruna"))
        val all = listOf(usd, czk, pln)
        fun search(query: String) = filterPickerItems(all, query).map { it.key }

        assertEquals(listOf("USD"), search("united states"))
        assertEquals(listOf("USD"), search("us-dollar"))
        assertEquals(listOf("CZK"), search("czech"))
        assertEquals(listOf("CZK"), search("tschechische"))
        assertEquals(listOf("CZK"), search("KORUNA"))
        assertEquals(listOf("USD", "PLN"), search("ol"))
        // Items without keywords behave as before.
        assertEquals(emptyList<String>(), pln.keywords)
    }

    @Test
    fun blankQueryReturnsEverythingAndUnknownNothing() {
        assertEquals(listOf("PLN", "ISK", "SEK"), keys("  "))
        assertEquals(listOf("PLN", "ISK", "SEK"), keys(""))
        assertEquals(emptyList<String>(), keys("xyz"))
    }

    @Test
    fun foldingCoversLettersThatDoNotDecompose() {
        assertEquals("zloty", foldForSearch("Złoty"))
        assertEquals("krone", foldForSearch("Krøne"))
        assertEquals("strasse", foldForSearch("Straße"))
        assertEquals("forint", foldForSearch("Forint"))
        assertEquals("lira", foldForSearch("Lİra"))
        assertEquals("", foldForSearch(""))
    }

    @Test
    fun initialScrollKeepsTwoRowsOfContextAboveTheSelection() {
        val many = (0 until 20).map { PickerItem("K$it", "Item $it") }
        assertNull(initialPickerScrollIndex(many, "K0"))
        assertNull(initialPickerScrollIndex(many, "K2"))
        assertEquals(1, initialPickerScrollIndex(many, "K3"))
        assertEquals(8, initialPickerScrollIndex(many, "K10"))
        assertNull(initialPickerScrollIndex(many, "missing"))
        assertNull(initialPickerScrollIndex(many, null))
    }

    @Test
    fun initialScrollIndexIsComputedOnTheFilteredList() {
        val currencies = (0 until 10).map { PickerItem("C$it", "C$it", "Currency $it") } + pln
        // Unfiltered, PLN is row 10; after filtering to [PLN] it is row 0: no scroll.
        assertEquals(8, initialPickerScrollIndex(currencies, "PLN"))
        assertNull(initialPickerScrollIndex(filterPickerItems(currencies, "zloty"), "PLN"))
    }
}
