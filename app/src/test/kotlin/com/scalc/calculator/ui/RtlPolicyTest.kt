package com.scalc.calculator.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The app is English-only and its keypads, tab underline and numeric expressions are laid out
 * left-to-right. Under an RTL system locale the UI must not be mirrored or reordered.
 * (Unit tests run with the app module as working directory.)
 */
class RtlPolicyTest {

    @Test
    fun manifestDisablesRtlMirroring() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("manifest not found from ${File(".").absolutePath}", manifest.isFile)
        val text = manifest.readText()
        assertTrue(text.contains("android:supportsRtl=\"false\""))
        assertFalse(text.contains("android:supportsRtl=\"true\""))
    }

    @Test
    fun composeRootForcesLeftToRightLayout() {
        val root = File("src/main/kotlin/com/scalc/calculator/ui/AppRoot.kt")
        assertTrue(root.isFile)
        assertTrue(root.readText().contains("LocalLayoutDirection provides LayoutDirection.Ltr"))
    }
}
