package com.scalc.calculator.ui.theme

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluentIndicationTest {

    @Test
    fun focusIsTracked() {
        val counters = IndicationCounters()
        val focus = FocusInteraction.Focus()
        assertTrue(counters.apply(focus))
        assertEquals(1, counters.focused)
        assertTrue(counters.apply(FocusInteraction.Unfocus(focus)))
        assertEquals(0, counters.focused)
        counters.apply(FocusInteraction.Unfocus(focus))
        assertEquals(0, counters.focused)
    }

    @Test
    fun pressAndHoverAreCountedAsBefore() {
        val counters = IndicationCounters()
        val press = PressInteraction.Press(Offset.Zero)
        counters.apply(press)
        assertEquals(1, counters.pressed)
        counters.apply(PressInteraction.Release(press))
        assertEquals(0, counters.pressed)
        counters.apply(press)
        counters.apply(PressInteraction.Cancel(press))
        assertEquals(0, counters.pressed)
        counters.apply(PressInteraction.Release(press))
        assertEquals(0, counters.pressed)

        val enter = HoverInteraction.Enter()
        counters.apply(enter)
        assertEquals(1, counters.hovered)
        counters.apply(HoverInteraction.Exit(enter))
        assertEquals(0, counters.hovered)
        assertEquals(0, counters.focused)
    }

    @Test
    fun unrelatedInteractionsDoNotRedraw() {
        assertFalse(IndicationCounters().apply(DragInteraction.Start()))
    }

    @Test
    fun focusColorTakesPartInEquality() {
        val a = FluentIndication(Color.Gray, Color.DarkGray, Color.Blue)
        assertEquals(a, FluentIndication(Color.Gray, Color.DarkGray, Color.Blue))
        assertEquals(a.hashCode(), FluentIndication(Color.Gray, Color.DarkGray, Color.Blue).hashCode())
        assertNotEquals(a, FluentIndication(Color.Gray, Color.DarkGray, Color.Red))
        assertNotEquals(a, FluentIndication(Color.Gray, Color.DarkGray))
    }
}
