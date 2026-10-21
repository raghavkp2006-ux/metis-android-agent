package dev.metis.agent

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.w3c.dom.Element

class ContactManifestPermissionTest {
    @Test fun declaredPermissionSetIsExact() {
        val projectDir = requireNotNull(System.getProperty("metis.projectDir"))
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val manifest = factory.newDocumentBuilder().parse(File(projectDir, "src/main/AndroidManifest.xml"))
        val nodes = manifest.getElementsByTagName("uses-permission")
        val declared = (0 until nodes.length).map { index ->
            (nodes.item(index) as Element).getAttributeNS(ANDROID_NAMESPACE, "name")
        }.toSet()
        assertEquals(setOf("android.permission.POST_NOTIFICATIONS", "android.permission.RECORD_AUDIO",
            "android.permission.READ_CONTACTS"), declared)
        FORBIDDEN.forEach { assertFalse(it, it in declared) }
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        val FORBIDDEN = setOf("android.permission.INTERNET", "android.permission.CALL_PHONE",
            "android.permission.SEND_SMS", "android.permission.WRITE_CONTACTS")
    }
}
