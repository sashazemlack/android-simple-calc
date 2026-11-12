package com.scalc.calculator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scalc.calculator.ui.theme.FluentTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * Simple custom-drawn line icons on a 24-unit grid. No icon packs, no Material icons.
 */
enum class Glyph {
    History, Paste, Copy, Backspace, Swap, Settings, Back, ChevronDown, ChevronUp,
    Search, Refresh, Delete, Check, Close,
    Length, Area, Volume, Speed, Mass, Temperature, Power, Pressure,
}

@Composable
fun GlyphIcon(
    glyph: Glyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = FluentTheme.colors.textPrimary,
    size: Dp = 20.dp,
) {
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            this.role = Role.Image
        }
    } else {
        Modifier
    }
    Canvas(modifier.size(size).then(semanticsModifier)) { drawGlyph(glyph, tint) }
}

fun DrawScope.drawGlyph(glyph: Glyph, color: Color) {
    val u = size.minDimension / 24f
    val ox = (size.width - 24f * u) / 2f
    val oy = (size.height - 24f * u) / 2f
    val sw = 1.6f * u
    val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun p(x: Float, y: Float) = Offset(ox + x * u, oy + y * u)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, p(x1, y1), p(x2, y2), sw, StrokeCap.Round)
    fun rect(l: Float, t: Float, r: Float, b: Float, radius: Float = 1f) =
        drawRoundRect(color, p(l, t), Size((r - l) * u, (b - t) * u), CornerRadius(radius * u), style = stroke)
    fun circle(cx: Float, cy: Float, r: Float, fill: Boolean = false) =
        if (fill) drawCircle(color, r * u, p(cx, cy)) else drawCircle(color, r * u, p(cx, cy), style = stroke)
    fun arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float) =
        drawArc(color, start, sweep, false, p(cx - r, cy - r), Size(2 * r * u, 2 * r * u), style = stroke)
    fun poly(closed: Boolean, vararg pts: Float) {
        val path = Path()
        path.moveTo(ox + pts[0] * u, oy + pts[1] * u)
        var i = 2
        while (i < pts.size) {
            path.lineTo(ox + pts[i] * u, oy + pts[i + 1] * u)
            i += 2
        }
        if (closed) path.close()
        drawPath(path, color, style = stroke)
    }
    fun arrowHead(tipX: Float, tipY: Float, dirX: Float, dirY: Float, len: Float = 4f) {
        // Two strokes from the tip, ±35° from the reverse of the travel direction.
        val back = Math.atan2(-dirY.toDouble(), -dirX.toDouble())
        val a1 = back + Math.toRadians(35.0)
        val a2 = back - Math.toRadians(35.0)
        line(tipX, tipY, tipX + (len * cos(a1)).toFloat(), tipY + (len * sin(a1)).toFloat())
        line(tipX, tipY, tipX + (len * cos(a2)).toFloat(), tipY + (len * sin(a2)).toFloat())
    }

    when (glyph) {
        Glyph.History -> {
            arc(12f, 12f, 8.5f, 200f, 300f)
            // arrow at the open end of the clock rim (counter-clockwise "back in time" cue)
            val endAngle = Math.toRadians(200.0)
            val ex = 12f + 8.5f * cos(endAngle).toFloat()
            val ey = 12f + 8.5f * sin(endAngle).toFloat()
            arrowHead(ex, ey, sin(endAngle).toFloat(), -cos(endAngle).toFloat(), 3.5f)
            line(12f, 12f, 12f, 7.5f)
            line(12f, 12f, 15f, 14f)
        }
        Glyph.Paste -> {
            rect(5f, 4.5f, 19f, 21f)
            rect(9f, 2.5f, 15f, 6.5f)
            line(8.5f, 11f, 15.5f, 11f)
            line(8.5f, 15f, 13.5f, 15f)
        }
        Glyph.Copy -> {
            poly(false, 9f, 7.5f, 9f, 3.5f, 20f, 3.5f, 20f, 15f, 16f, 15f)
            rect(4f, 8f, 15.5f, 20.5f)
        }
        Glyph.Backspace -> {
            poly(true, 8f, 5.5f, 21f, 5.5f, 21f, 18.5f, 8f, 18.5f, 2.5f, 12f)
            line(11.5f, 9f, 17.5f, 15f)
            line(17.5f, 9f, 11.5f, 15f)
        }
        Glyph.Swap -> {
            line(8f, 19.5f, 8f, 4.5f)
            arrowHead(8f, 4.5f, 0f, -1f)
            line(16f, 4.5f, 16f, 19.5f)
            arrowHead(16f, 19.5f, 0f, 1f)
        }
        Glyph.Settings -> {
            circle(12f, 12f, 3f)
            circle(12f, 12f, 6.5f)
            for (k in 0 until 8) {
                val a = Math.toRadians(k * 45.0)
                line(
                    12f + 6.5f * cos(a).toFloat(), 12f + 6.5f * sin(a).toFloat(),
                    12f + 9.5f * cos(a).toFloat(), 12f + 9.5f * sin(a).toFloat(),
                )
            }
        }
        Glyph.Back -> {
            line(20f, 12f, 4f, 12f)
            poly(false, 10f, 6f, 4f, 12f, 10f, 18f)
        }
        Glyph.ChevronDown -> poly(false, 6f, 9f, 12f, 15f, 18f, 9f)
        Glyph.ChevronUp -> poly(false, 6f, 15f, 12f, 9f, 18f, 15f)
        Glyph.Search -> {
            circle(10.5f, 10.5f, 6.5f)
            line(15.5f, 15.5f, 20.5f, 20.5f)
        }
        Glyph.Refresh -> {
            arc(12f, 12f, 7.5f, -80f, 300f)
            val endAngle = Math.toRadians(220.0)
            val ex = 12f + 7.5f * cos(endAngle).toFloat()
            val ey = 12f + 7.5f * sin(endAngle).toFloat()
            // clockwise tangent in screen coordinates
            arrowHead(ex, ey, -sin(endAngle).toFloat(), cos(endAngle).toFloat(), 4f)
        }
        Glyph.Delete -> {
            line(4f, 6f, 20f, 6f)
            poly(false, 9f, 6f, 9f, 3.5f, 15f, 3.5f, 15f, 6f)
            poly(false, 6f, 6f, 7f, 21f, 17f, 21f, 18f, 6f)
            line(10f, 10f, 10f, 17f)
            line(14f, 10f, 14f, 17f)
        }
        Glyph.Check -> poly(false, 5f, 12.5f, 10f, 17.5f, 19f, 7f)
        Glyph.Close -> {
            line(6f, 6f, 18f, 18f)
            line(18f, 6f, 6f, 18f)
        }
        Glyph.Length -> {
            rect(2f, 8f, 22f, 16f, 0.5f)
            line(6f, 8f, 6f, 12f)
            line(10f, 8f, 10f, 10.5f)
            line(14f, 8f, 14f, 12f)
            line(18f, 8f, 18f, 10.5f)
        }
        Glyph.Area -> {
            rect(4f, 4f, 20f, 20f, 0.5f)
            line(4f, 12f, 12f, 4f)
            line(4f, 20f, 20f, 4f)
            line(12f, 20f, 20f, 12f)
        }
        Glyph.Volume -> {
            rect(4f, 9f, 15f, 20f, 0.5f)
            poly(false, 4f, 9f, 9f, 4f, 20f, 4f, 15f, 9f)
            poly(false, 20f, 4f, 20f, 15f, 15f, 20f)
        }
        Glyph.Speed -> {
            arc(12f, 15f, 8.5f, 180f, 180f)
            line(3.5f, 15f, 20.5f, 15f)
            line(12f, 15f, 16.5f, 9f)
            circle(12f, 15f, 1.2f, fill = true)
            line(7f, 9.5f, 8f, 10.5f)
            line(12f, 7f, 12f, 8.5f)
        }
        Glyph.Mass -> {
            poly(true, 5.5f, 9f, 18.5f, 9f, 21f, 20.5f, 3f, 20.5f)
            circle(12f, 5.8f, 2.4f)
        }
        Glyph.Temperature -> {
            poly(false, 10f, 14.6f, 10f, 5f)
            arc(12f, 5f, 2f, 180f, 180f)
            poly(false, 14f, 5f, 14f, 14.6f)
            arc(12f, 17.8f, 3.8f, -58f, 296f)
            circle(12f, 17.8f, 1.8f, fill = true)
            line(12f, 17.8f, 12f, 9f)
        }
        Glyph.Power -> poly(true, 13.5f, 2f, 5f, 13.5f, 11.5f, 13.5f, 10f, 22f, 19f, 10f, 12.5f, 10f)
        Glyph.Pressure -> {
            line(7f, 3f, 7f, 12f)
            arrowHead(7f, 12f, 0f, 1f, 3f)
            line(12f, 3f, 12f, 12f)
            arrowHead(12f, 12f, 0f, 1f, 3f)
            line(17f, 3f, 17f, 12f)
            arrowHead(17f, 12f, 0f, 1f, 3f)
            line(3f, 16f, 21f, 16f)
            line(5f, 20.5f, 8f, 16f)
            line(10f, 20.5f, 13f, 16f)
            line(15f, 20.5f, 18f, 16f)
        }
    }
}
