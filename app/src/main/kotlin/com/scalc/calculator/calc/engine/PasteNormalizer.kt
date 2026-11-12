package com.scalc.calculator.calc.engine

/**
 * Converts clipboard text into the calculator's display syntax (see [CalculatorEngine.normalizePasted]).
 *
 * Rules, applied in order:
 * 1. Trim; remove all whitespace including NBSP, thin and zero-width spaces; strip leading and
 *    trailing '='.
 * 2. Commas. If the text has no '.' and exactly one comma, sitting between digits and followed by
 *    1-2 or 4+ digits ("3,5", "2,25", "1,2345"), or by exactly 3 digits after an integer part
 *    that starts with 0 ("0,500", never a thousands grouping), it is a decimal comma and becomes
 *    '.'. Otherwise commas inside a well-formed digit grouping (1-3 digits not starting with 0,
 *    then groups of exactly three: "1,234,567.5") are removed. Any other comma is left alone and
 *    makes the text invalid. So "1,234" is read as one thousand two hundred thirty-four.
 * 3. Words (case-insensitive): arcsin/asin/sin⁻¹ → asin (same for cos and tan), sin, cos, tan,
 *    log10/log → log, ln, sqrt → √, exp → e^, mod → %, pi → π.
 * 4. Symbols: '*', '∗', '·', '⋅' → '×'; '**' → '^'; '/' → '÷'; '-', '–', '—' → '−' (an exponent
 *    sign right after a number's 'E' stays ASCII '-'); '²', '³' → "^2", "^3".
 * 5. Scientific notation: 'e' directly after a digit and followed by digits or by an ASCII sign
 *    and digits is an exponent ("1e-7" → "1E-7", "2.5e3" → "2.5E3"); any other 'e' or a stray
 *    upper-case 'E' is Euler's number.
 */
internal object PasteNormalizer {
    private const val MAX_RAW_LENGTH = 10_000
    /** Zero-width space/joiners, word joiner and BOM: invisible, not whitespace for isWhitespace(). */
    private val ZERO_WIDTH_CODES = setOf(0x200B, 0x200C, 0x200D, 0x2060, 0xFEFF)

    private val WORDS = listOf(
        "arcsin" to "asin", "arccos" to "acos", "arctan" to "atan",
        "sin⁻¹" to "asin", "cos⁻¹" to "acos", "tan⁻¹" to "atan",
        "asin" to "asin", "acos" to "acos", "atan" to "atan",
        "sin" to "sin", "cos" to "cos", "tan" to "tan",
        "log10" to "log", "log" to "log", "ln" to "ln",
        "sqrt" to "√", "exp" to "e^", "mod" to "%", "pi" to "π",
    )

    /**
     * A digit run grouped with commas: 1-3 leading digits (never starting with 0), then one or
     * more ",ddd" groups.
     */
    private val GROUPED_NUMBER = Regex("""(?<![\d.,])[1-9]\d{0,2}(?:,\d{3})+(?![\d,])""")

    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_RAW_LENGTH) return null
        val compact = trimmed.filterNot { it.isWhitespace() || it.code in ZERO_WIDTH_CODES }
            .trimStart('=')
            .trimEnd('=')
        if (compact.isEmpty()) return null
        return mapSymbols(normalizeCommas(compact))
    }

    private fun normalizeCommas(text: String): String {
        if (',' !in text) return text
        val commaAt = text.indexOf(',')
        if ('.' !in text && text.lastIndexOf(',') == commaAt && commaAt > 0 && text[commaAt - 1].isAsciiDigit()) {
            var end = commaAt + 1
            while (end < text.length && text[end].isAsciiDigit()) end++
            val digitsAfter = end - commaAt - 1
            var start = commaAt - 1
            while (start > 0 && text[start - 1].isAsciiDigit()) start--
            // "0,500" cannot be a thousands grouping, so three digits after a leading 0 are decimals.
            val leadingZero = text[start] == '0'
            if (digitsAfter >= 1 && (digitsAfter != 3 || leadingZero)) {
                return text.replaceRange(commaAt, commaAt + 1, ".")
            }
        }
        return GROUPED_NUMBER.replace(text) { it.value.replace(",", "") }
    }

    private fun mapSymbols(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val word = WORDS.firstOrNull { text.regionMatches(i, it.first, 0, it.first.length, ignoreCase = true) }
            if (word != null) {
                out.append(word.second)
                i += word.first.length
                continue
            }
            when (val c = text[i]) {
                '*' -> if (text.getOrNull(i + 1) == '*') {
                    out.append('^')
                    i++
                } else {
                    out.append('×')
                }
                '∗', '·', '⋅', '×' -> out.append('×')
                '/', '∕', '÷' -> out.append('÷')
                '-', '–', '—', '‒', '−', '﹣', '－' -> out.append(if (isExponentSignPosition(out)) '-' else '−')
                '＋' -> out.append('+')
                '²' -> out.append("^2")
                '³' -> out.append("^3")
                'E' -> out.append(if (endsWithMantissa(out)) 'E' else 'e')
                'e' -> out.append(if (endsWithMantissa(out) && startsExponentDigits(text, i + 1)) 'E' else 'e')
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }

    private fun endsWithMantissa(out: StringBuilder) = out.isNotEmpty() && out.last().let { it.isAsciiDigit() || it == '.' }

    /** True when the output ends with a number's exponent marker, e.g. "1.5E". */
    private fun isExponentSignPosition(out: StringBuilder): Boolean {
        if (out.length < 2 || out.last() != 'E') return false
        val beforeE = out[out.length - 2]
        return beforeE.isAsciiDigit() || beforeE == '.'
    }

    private fun startsExponentDigits(text: String, index: Int): Boolean {
        val first = text.getOrNull(index) ?: return false
        if (first.isAsciiDigit()) return true
        return (first == '+' || first == '-') && text.getOrNull(index + 1)?.isAsciiDigit() == true
    }

    private fun Char.isAsciiDigit() = this in '0'..'9'
}
