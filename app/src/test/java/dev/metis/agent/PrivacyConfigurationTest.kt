package dev.metis.agent

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class PrivacyConfigurationTest {
    @Test
    fun `only notification runtime permission is requested explicitly`() {
        val manifest = readXml("AndroidManifest.xml")
        val permissions = manifest.getElementsByTagName("uses-permission")
        assertEquals(1, permissions.length)
        assertEquals("android.permission.POST_NOTIFICATIONS",
            (permissions.item(0) as Element).getAttributeNS(ANDROID_NAMESPACE, "name"))
        assertEquals(0, manifest.getElementsByTagName("uses-permission-sdk-23").length)
    }

    @Test
    fun `backup and cleartext traffic are disabled`() {
        val app = readXml("AndroidManifest.xml")
            .getElementsByTagName("application").item(0) as Element
        assertEquals("false", app.getAttributeNS(ANDROID_NAMESPACE, "allowBackup"))
        assertEquals("false", app.getAttributeNS(ANDROID_NAMESPACE, "usesCleartextTraffic"))
        assertEquals("@xml/backup_rules", app.getAttributeNS(ANDROID_NAMESPACE, "fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", app.getAttributeNS(ANDROID_NAMESPACE, "dataExtractionRules"))
    }

    @Test
    fun `legacy backup excludes every personal storage domain`() {
        assertStorageExcluded(readXml("res/xml/backup_rules.xml"))
    }

    @Test
    fun `cloud backup and device transfer both exclude personal storage`() {
        val rules = readXml("res/xml/data_extraction_rules.xml")
        listOf("cloud-backup", "device-transfer").forEach { section ->
            val nodes = rules.getElementsByTagName(section)
            assertEquals(1, nodes.length)
            assertStorageExcluded(nodes.item(0) as Element)
        }
    }

    private fun readXml(path: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val projectDir = requireNotNull(System.getProperty("metis.projectDir"))
        return factory.newDocumentBuilder().parse(File(projectDir, "src/main/$path")).documentElement
    }

    private fun assertStorageExcluded(section: Element) {
        val excludes = section.getElementsByTagName("exclude")
        val excludedDomains = (0 until excludes.length).map { index ->
            val entry = excludes.item(index) as Element
            assertEquals(".", entry.getAttribute("path"))
            entry.getAttribute("domain")
        }.toSet()
        assertTrue("Every personal storage domain must be excluded", excludedDomains.containsAll(STORAGE_DOMAINS))
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        val STORAGE_DOMAINS = setOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        )
    }
}
