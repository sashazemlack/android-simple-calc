package com.scalc.calculator.exchange.model

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Status texts for JVM tests. [ENGLISH] is read from the real default resources
 * (`src/exchange/res/values/strings_exchange.xml`), so tests check the texts users actually see;
 * [GERMAN] stands in for a translation to show that texts follow the language they are given.
 */
object TestStrings {

    private const val STRINGS_FILE = "res/values/strings_exchange.xml"

    /** All `<string>` resources of the Exchange flavor by name, with Android escapes resolved. */
    val resources: Map<String, String> by lazy {
        val file = listOf(File("src/exchange/$STRINGS_FILE"), File("app/src/exchange/$STRINGS_FILE")).firstOrNull { it.isFile }
            ?: throw AssertionError("src/exchange/$STRINGS_FILE not found from ${File("").absolutePath}")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        (0 until nodes.length).associate { i ->
            val element = nodes.item(i) as Element
            element.getAttribute("name") to unescape(element.textContent)
        }
    }

    fun string(name: String): String = resources[name] ?: throw AssertionError("Missing string resource $name")

    /** The English (default) status templates, exactly as in strings_exchange.xml. */
    val ENGLISH: RateStatusStrings by lazy {
        RateStatusStrings(
            loading = string("exch_status_loading"),
            refreshing = string("exch_status_refreshing"),
            fresh = string("exch_status_fresh"),
            cached = string("exch_status_cached"),
            stale = string("exch_status_stale"),
            unavailable = string("exch_status_unavailable"),
            lastUpdated = string("exch_status_last_updated"),
            rateDate = string("exch_status_rate_date"),
            rateDateEcb = string("exch_status_rate_date_ecb"),
            updateFailed = string("exch_status_update_failed"),
            errorNoConnection = string("exch_error_no_connection"),
            errorServer = string("exch_error_server"),
            errorInvalidData = string("exch_error_invalid_data"),
            sourceFrankfurterBlended = string("exch_source_frankfurter_blended"),
            sourceFrankfurterEcb = string("exch_source_frankfurter"),
            sourceEcb = string("exch_source_ecb"),
            joined = string("exch_status_joined"),
        )
    }

    /** Hand-written German templates (no translation exists yet); same placeholders as English. */
    val GERMAN = RateStatusStrings(
        loading = "Wechselkurse werden geladen …",
        refreshing = "Wechselkurse werden aktualisiert …",
        fresh = "Kurse zuletzt aktualisiert: %1\$s",
        cached = "Gespeicherte Kurse · Zuletzt aktualisiert: %1\$s",
        stale = "Gespeicherte Kurse (evtl. veraltet) · Zuletzt aktualisiert: %1\$s",
        unavailable = "Wechselkurse nicht verfügbar. Zum Wiederholen nach unten wischen.",
        lastUpdated = "Zuletzt aktualisiert: %1\$s",
        rateDate = "Wechselkurse vom %1\$s",
        rateDateEcb = "EZB-Referenzkurse vom %1\$s",
        updateFailed = "Aktualisierung fehlgeschlagen.",
        errorNoConnection = "Keine Internetverbindung.",
        errorServer = "Der Wechselkursdienst ist zurzeit nicht erreichbar.",
        errorInvalidData = "Der Wechselkursdienst hat ungültige Daten geliefert.",
        sourceFrankfurterBlended = "Quelle: Kurse von Zentralbanken und anderen amtlichen Stellen, gemittelt von frankfurter.dev",
        sourceFrankfurterEcb = "Quelle: Europäische Zentralbank über frankfurter.dev",
        sourceEcb = "Quelle: Europäische Zentralbank",
        joined = "%1\$s · %2\$s",
    )

    /** Resolves the escapes aapt2 resolves in string resources (`\'`, `\"`, `\\`, `\n`, `\t`, `\@`, `\?`). */
    private fun unescape(raw: String): String {
        val text = raw.trim().let { if (it.length >= 2 && it.startsWith('"') && it.endsWith('"')) it.substring(1, it.length - 1) else it }
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                out.append(
                    when (val next = text[i + 1]) {
                        'n' -> '\n'
                        't' -> '\t'
                        else -> next
                    },
                )
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
