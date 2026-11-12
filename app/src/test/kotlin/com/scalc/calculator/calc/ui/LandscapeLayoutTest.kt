package com.scalc.calculator.calc.ui

import com.scalc.calculator.calc.engine.AngleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LandscapeLayoutTest {

    /** Dimensions at density 1 (px == dp), close to the real values at font scale 1.0. */
    private val dims = CalculatorDimensions(
        actionRow = 46,
        handle = 22,
        gap = 2,
        rowMin = 34,
        rowComfort = 44,
        sciRowMax = 44,
        displayNatural = 140,
        displayMin = 65,
    )

    private fun spec(viewport: Int) = LandscapeLayoutSpec(viewport, dims, separator = 1, bandMax = 120)

    @Test
    fun appliesOnlyToShortLandscapeViewports() {
        assertTrue("phone turned sideways", CalculatorLandscape.applies(765f, 306f))
        assertTrue("small phone sideways", CalculatorLandscape.applies(592f, 300f))
        assertFalse("phone portrait", CalculatorLandscape.applies(392f, 700f))
        assertFalse("tablet landscape keeps the portrait layout", CalculatorLandscape.applies(1280f, 700f))
        assertFalse("square", CalculatorLandscape.applies(500f, 500f))
    }

    @Test
    fun phoneLandscapeKeepsComfortableKeyRows() {
        val s = spec(306)
        assertEquals(73, s.band)
        assertEquals(232, s.keypad) // 5 rows of 44 + 6 gaps
        assertEquals(306, s.band + 1 + s.keypad)
    }

    @Test
    fun bandIsCappedAndTheKeysGetTheRest() {
        val s = spec(600)
        assertEquals(120, s.band)
        assertEquals(479, s.keypad)
    }

    @Test
    fun shortViewportsShrinkKeysThenScroll() {
        val tight = spec(250)
        assertEquals(65, tight.band) // display minimum
        assertEquals(184, tight.keypad)

        val tooShort = spec(200)
        assertEquals(65, tooShort.band)
        assertEquals(182, tooShort.keypad) // 5 rows of 34 + 6 gaps; the screen scrolls
        assertTrue(tooShort.band + 1 + tooShort.keypad > 200)
    }

    private fun names(rows: List<List<SciCell>>) = rows.map { row ->
        row.map { cell ->
            when (cell) {
                is SciCell.Key -> cell.spec.label
                SciCell.Inverse -> "inv"
                is SciCell.Angle -> cell.mode.name
            }
        }
    }

    @Test
    fun landscapeScientificKeysAreThreeColumnsByFiveRows() {
        assertEquals(
            listOf(
                listOf("inv", "RAD", "DEG"),
                listOf("sin", "cos", "tan"),
                listOf("ln", "log", "!"),
                listOf("π", "e", "^"),
                listOf("(", ")", "√"),
            ),
            names(scientificRows(inverse = false, landscape = true)),
        )
        assertEquals(
            listOf("sin⁻¹", "cos⁻¹", "tan⁻¹"),
            names(scientificRows(inverse = true, landscape = true))[1],
        )
        assertEquals(listOf("eˣ", "10ˣ", "!"), names(scientificRows(inverse = true, landscape = true))[2])
    }

    @Test
    fun portraitScientificKeysAreUnchanged() {
        assertEquals(
            listOf(
                listOf("sin", "cos", "tan", "log", "ln"),
                listOf("(", ")", "^", "√", "!"),
                listOf("π", "e", "inv", AngleMode.RAD.name, AngleMode.DEG.name),
            ),
            names(scientificRows(inverse = false, landscape = false)),
        )
    }

    @Test
    fun bothArrangementsContainTheSameFifteenKeys() {
        for (inverse in listOf(false, true)) {
            val portrait = names(scientificRows(inverse, landscape = false)).flatten()
            val landscape = names(scientificRows(inverse, landscape = true)).flatten()
            assertEquals(15, landscape.size)
            assertEquals(portrait.sorted(), landscape.sorted())
        }
    }
}
