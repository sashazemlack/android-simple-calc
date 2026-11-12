package com.scalc.calculator.converter.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryGridTest {

    @Test
    fun twoColumnsOnTypicalPhonesAndOneMorePerExtra180Dp() {
        assertEquals(2, categoryColumns(320.dp))
        assertEquals(2, categoryColumns(360.dp))
        assertEquals(2, categoryColumns(411.dp))
        assertEquals(3, categoryColumns(540.dp))
        assertEquals(4, categoryColumns(720.dp))
    }

    @Test
    fun titleWidthSubtractsGutterGapsAndTileChrome() {
        // 360 dp: tile (360 - 2 x 12 - 8) / 2 = 164 dp; minus 3 + 12 + 12 padding, 24 icon, 8 gap.
        assertEquals(105f, categoryTitleWidth(360.dp, 2).value, 0.001f)
        // 540 dp, 3 columns: (540 - 24 - 16) / 3 = 166.67 dp tile.
        assertEquals(500f / 3 - 59f, categoryTitleWidth(540.dp, 3).value, 0.001f)
        // Never negative on absurdly narrow windows.
        assertEquals(0f, categoryTitleWidth(40.dp, 2).value, 0.001f)
    }

    @Test
    fun titleWordsAreTheUnbreakableUnitsOfEveryTitle() {
        assertEquals(
            listOf("Length", "Weight", "/", "Mass", "Temperature"),
            titleWords(listOf("Length", "Weight / Mass", " Temperature ", "Length")),
        )
        assertEquals(emptyList<String>(), titleWords(listOf("", "   ")))
    }
}
