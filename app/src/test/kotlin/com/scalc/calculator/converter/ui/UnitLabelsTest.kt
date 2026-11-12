package com.scalc.calculator.converter.ui

import com.scalc.calculator.converter.engine.UnitCatalog
import com.scalc.calculator.converter.engine.UnitCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Every catalog unit and category must have its own localized label and icon. */
class UnitLabelsTest {

    @Test
    fun everyUnitHasADistinctNameResource() {
        val units = UnitCategory.entries.flatMap { UnitCatalog.units(it) }
        val resources = units.map { it.nameRes }
        resources.forEach { assertNotEquals(0, it) }
        assertEquals(units.size, resources.toSet().size)
    }

    @Test
    fun everyCategoryHasADistinctTitleAndGlyph() {
        assertEquals(UnitCategory.entries.size, UnitCategory.entries.map { it.titleRes }.toSet().size)
        assertEquals(UnitCategory.entries.size, UnitCategory.entries.map { it.glyph }.toSet().size)
    }
}
