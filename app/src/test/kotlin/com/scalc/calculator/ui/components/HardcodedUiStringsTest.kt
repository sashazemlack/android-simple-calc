package com.scalc.calculator.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * User-visible and accessibility strings of the shared UI live in string resources.
 * (Unit tests run with the app module as working directory.)
 */
class HardcodedUiStringsTest {

    private val roots = listOf(
        "src/main/kotlin/com/scalc/calculator/ui",
        "src/main/kotlin/com/scalc/calculator/settings",
    )

    private val patterns = listOf(
        Regex("contentDescription\\s*=\\s*\"[^\"]"),
        Regex(":\\s*String\\s*=\\s*\"[A-Za-z]"),
        // Former literals of the shared keypad and picker.
        Regex("\"(Toggle sign|Double zero|Decimal point|Clear|Backspace|Search|No matches|Clear search|Close)\""),
    )

    @Test
    fun sharedUiHasNoHardcodedLabels() {
        val files = roots.map(::File).flatMap { dir ->
            assertTrue("missing ${dir.absolutePath}", dir.isDirectory)
            dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
        assertTrue(files.any { it.name == "NumericKeypad.kt" } && files.any { it.name == "PickerDialog.kt" })
        val hits = files.flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> patterns.any { it.containsMatchIn(line) } }
                .map { (i, line) -> "${file.name}:${i + 1}: ${line.trim()}" }
        }
        assertTrue("Hardcoded UI strings:\n" + hits.joinToString("\n"), hits.isEmpty())
    }
}
