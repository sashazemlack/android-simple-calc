package com.scalc.calculator.exchange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Themed launcher icons (Android 13+) tint the monochrome layer with SRC_IN, so only alpha
 * matters. The Exchange foreground draws colored arrows on an opaque white disc, which would
 * become a featureless solid disc; the Exchange flavor therefore uses a dedicated monochrome
 * drawable made of opaque white and transparent only.
 */
class LauncherIconResourcesTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    /** Unit tests run in the module directory; fall back to the project root just in case. */
    private fun exchangeFile(path: String): File =
        listOf(File("src/exchange/$path"), File("app/src/exchange/$path")).firstOrNull { it.isFile }
            ?: throw AssertionError("src/exchange/$path not found from ${File("").absolutePath}")

    private fun parse(path: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(exchangeFile(path)).documentElement
    }

    private fun Element.child(tag: String): Element {
        val nodes = getElementsByTagName(tag)
        assertEquals("exactly one <$tag>", 1, nodes.length)
        return nodes.item(0) as Element
    }

    private fun Element.drawable(tag: String): String = child(tag).getAttributeNS(androidNs, "drawable")

    @Test
    fun adaptiveIconsUseTheDedicatedMonochromeLayer() {
        for (name in listOf("ic_launcher.xml", "ic_launcher_round.xml")) {
            val icon = parse("res/mipmap-anydpi-v26/$name")
            assertEquals(name, "adaptive-icon", icon.tagName)
            assertEquals(name, "@drawable/ic_launcher_monochrome", icon.drawable("monochrome"))
            assertNotEquals(name, icon.drawable("foreground"), icon.drawable("monochrome"))
            // The colored icon stays the same as before.
            assertEquals(name, "@drawable/ic_launcher_foreground", icon.drawable("foreground"))
            assertEquals(name, "@color/launcher_background", icon.drawable("background"))
        }
    }

    @Test
    fun monochromeLayerIsOnlyOpaqueWhiteOrTransparent() {
        val vector = parse("res/drawable/ic_launcher_monochrome.xml")
        assertEquals("vector", vector.tagName)
        val paths = vector.getElementsByTagName("path")
        assertTrue("monochrome layer has paths", paths.length > 0)
        var visibleShapes = 0
        for (i in 0 until paths.length) {
            val path = paths.item(i) as Element
            for (attribute in listOf("fillColor", "strokeColor")) {
                val color = path.getAttributeNS(androidNs, attribute)
                if (color.isEmpty()) continue
                assertTrue(
                    "path $i $attribute=$color must be #FFFFFFFF or fully transparent",
                    color.equals("#FFFFFFFF", ignoreCase = true) || isFullyTransparent(color),
                )
                if (color.equals("#FFFFFFFF", ignoreCase = true)) visibleShapes++
            }
            // Colors from resources or theme attributes could hide a non-white color.
            assertTrue(path.getAttributeNS(androidNs, "fillColor").let { it.isEmpty() || it.startsWith("#") })
            assertTrue(path.getAttributeNS(androidNs, "strokeColor").let { it.isEmpty() || it.startsWith("#") })
            // A partly transparent path would read as a different gray once tinted.
            for (alpha in listOf("fillAlpha", "strokeAlpha")) {
                val value = path.getAttributeNS(androidNs, alpha)
                assertTrue("path $i $alpha=$value", value.isEmpty() || value.toFloat() == 1f)
            }
        }
        // Calculator outline, display, keys, badge ring and arrows.
        assertEquals(5, visibleShapes)
    }

    /** #ARGB / #AARRGGBB with alpha 0; #RGB / #RRGGBB are opaque. */
    private fun isFullyTransparent(color: String): Boolean {
        val hex = color.removePrefix("#")
        return when (hex.length) {
            4 -> hex[0] == '0'
            8 -> hex.startsWith("00")
            else -> false
        }
    }
}
