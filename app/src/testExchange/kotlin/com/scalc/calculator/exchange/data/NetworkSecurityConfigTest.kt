package com.scalc.calculator.exchange.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The ECB fallback host chains to "Sectigo Public Server Authentication Root E46", which Android
 * 7.0 to 13 do not trust. The Exchange flavor's network security config must trust the bundled
 * root for that host only, keep the system store and deny cleartext traffic everywhere.
 */
class NetworkSecurityConfigTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    /** Unit tests run in the module directory; fall back to the project root just in case. */
    private fun exchangeFile(path: String): File =
        listOf(File("src/exchange/$path"), File("app/src/exchange/$path")).firstOrNull { it.isFile }
            ?: throw AssertionError("src/exchange/$path not found from ${File("").absolutePath}")

    private fun parse(path: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(exchangeFile(path)).documentElement
    }

    private fun Element.children(tag: String): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).map { nodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }
    }

    private fun Element.trustAnchorSources(): Set<String> =
        children("trust-anchors").flatMap { it.children("certificates") }.map { it.getAttribute("src") }.toSet()

    @Test
    fun cleartextIsDeniedAndTheSystemStoreIsKept() {
        val config = parse("res/xml/network_security_config.xml")
        assertEquals("network-security-config", config.tagName)
        val base = config.children("base-config").single()
        assertEquals("false", base.getAttribute("cleartextTrafficPermitted"))
        assertEquals(setOf("system"), base.trustAnchorSources())
        config.children("domain-config").forEach { domainConfig ->
            assertEquals("false", domainConfig.getAttribute("cleartextTrafficPermitted"))
        }
    }

    @Test
    fun ecbHostTrustsTheBundledSectigoRootInAdditionToTheSystemStore() {
        val ecbHost = URI(HttpRatesClient.ECB_DAILY_URL).host
        val config = parse("res/xml/network_security_config.xml")
        val ecbConfigs = config.children("domain-config").filter { domainConfig ->
            domainConfig.children("domain").any { it.textContent.trim() == ecbHost }
        }
        val ecbConfig = ecbConfigs.single()
        assertEquals(
            setOf("system", "@raw/sectigo_public_server_authentication_root_e46"),
            ecbConfig.trustAnchorSources(),
        )
        // The extra root applies to this host only, not to the primary provider or subdomains.
        val domains = ecbConfig.children("domain")
        assertEquals(listOf(ecbHost), domains.map { it.textContent.trim() })
        assertEquals("false", domains.single().getAttribute("includeSubdomains"))
        val primaryHost = URI(HttpRatesClient.RATES_URL).host
        assertTrue(config.children("domain-config").none { dc -> dc.children("domain").any { it.textContent.trim() == primaryHost } })
    }

    @Test
    fun bundledRootIsTheSelfSignedSectigoE46Root() {
        val file = exchangeFile("res/raw/sectigo_public_server_authentication_root_e46.pem")
        val text = file.readText()
        assertTrue(text.trimStart().startsWith("-----BEGIN CERTIFICATE-----"))
        assertEquals(1, Regex("BEGIN CERTIFICATE").findAll(text).count())

        val certificate = file.inputStream().use {
            CertificateFactory.getInstance("X.509").generateCertificate(it)
        } as X509Certificate
        assertTrue(certificate.subjectX500Principal.name, certificate.subjectX500Principal.name.contains("CN=Sectigo Public Server Authentication Root E46"))
        assertEquals(certificate.subjectX500Principal, certificate.issuerX500Principal)
        certificate.verify(certificate.publicKey)
        assertTrue("must be a CA certificate", certificate.basicConstraints >= 0)

        val sha256 = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString(":") { "%02X".format(it) }
        assertEquals(
            "C9:0F:26:F0:FB:1B:40:18:B2:22:27:51:9B:5C:A2:B5:3E:2C:A5:B3:BE:5C:F1:8E:FE:1B:EF:47:38:0C:53:83",
            sha256,
        )
        assertEquals(Instant.parse("2046-03-21T23:59:59Z"), certificate.notAfter.toInstant())
    }

    @Test
    fun exchangeManifestUsesTheConfig() {
        val manifest = parse("AndroidManifest.xml")
        val application = manifest.children("application").single()
        assertEquals("@xml/network_security_config", application.getAttributeNS(androidNs, "networkSecurityConfig"))
    }
}
