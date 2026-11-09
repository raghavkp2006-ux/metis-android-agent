package dev.metis.agent

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class LauncherIconManifestTest {

    @Test fun manifestReferencesAdaptiveAndRoundLauncherIcons() {
        val manifest = readXml("src/main/AndroidManifest.xml")
        val appNodes = manifest.getElementsByTagName("application")
        assertEquals("Single application element expected", 1, appNodes.length)
        val app = appNodes.item(0) as Element

        val icon = app.getAttributeNS(ANDROID_NAMESPACE, "icon")
        val roundIcon = app.getAttributeNS(ANDROID_NAMESPACE, "roundIcon")

        assertEquals("@mipmap/ic_launcher", icon)
        assertEquals("@mipmap/ic_launcher_round", roundIcon)
    }

    @Test fun adaptiveXmlResourcesExistAndDeclareLayers() {
        val projectDir = File(requireNotNull(System.getProperty("metis.projectDir")))
        val anyDpiDir = File(projectDir, "src/main/res/mipmap-anydpi")

        val launcherXml = File(anyDpiDir, "ic_launcher.xml")
        val roundLauncherXml = File(anyDpiDir, "ic_launcher_round.xml")
        assertTrue("ic_launcher.xml must exist", launcherXml.exists())
        assertTrue("ic_launcher_round.xml must exist", roundLauncherXml.exists())

        listOf(launcherXml, roundLauncherXml).forEach { xmlFile ->
            val doc = readXmlFile(xmlFile)
            val adaptiveNodes = doc.getElementsByTagName("adaptive-icon")
            assertEquals(1, adaptiveNodes.length)
            val bgNodes = doc.getElementsByTagName("background")
            val fgNodes = doc.getElementsByTagName("foreground")
            val monoNodes = doc.getElementsByTagName("monochrome")
            assertEquals("background layer expected", 1, bgNodes.length)
            assertEquals("foreground layer expected", 1, fgNodes.length)
            assertEquals("monochrome layer expected for themed icons", 1, monoNodes.length)

            val bg = (bgNodes.item(0) as Element).getAttributeNS(ANDROID_NAMESPACE, "drawable")
            val fg = (fgNodes.item(0) as Element).getAttributeNS(ANDROID_NAMESPACE, "drawable")
            val mono = (monoNodes.item(0) as Element).getAttributeNS(ANDROID_NAMESPACE, "drawable")

            assertEquals("@color/ic_launcher_background", bg)
            assertEquals("@mipmap/ic_launcher_foreground", fg)
            assertEquals("@mipmap/ic_launcher_monochrome", mono)
        }
    }

    @Test fun launcherBackgroundColorIsDefined() {
        val colorsDoc = readXml("src/main/res/values/ic_launcher_colors.xml")
        val colorNodes = colorsDoc.getElementsByTagName("color")
        assertTrue("At least one color resource expected", colorNodes.length >= 1)

        val bgColor = (0 until colorNodes.length)
            .map { colorNodes.item(it) as Element }
            .firstOrNull { it.getAttribute("name") == "ic_launcher_background" }

        assertTrue("ic_launcher_background color must be present", bgColor != null)
        assertEquals("#2A1A25", bgColor?.textContent?.trim())
    }

    @Test fun densityMipmapFoldersContainAllRequiredPngAssets() {
        val projectDir = File(requireNotNull(System.getProperty("metis.projectDir")))
        val resDir = File(projectDir, "src/main/res")
        val densities = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
        val requiredDrawables = listOf(
            "ic_launcher.png",
            "ic_launcher_round.png",
            "ic_launcher_foreground.png",
            "ic_launcher_monochrome.png"
        )

        for (density in densities) {
            val densityFolder = File(resDir, "mipmap-$density")
            assertTrue("Folder mipmap-$density must exist", densityFolder.exists() && densityFolder.isDirectory)
            for (asset in requiredDrawables) {
                val assetFile = File(densityFolder, asset)
                assertTrue("Asset $asset in mipmap-$density must exist and be non-empty",
                    assetFile.exists() && assetFile.length() > 0)
            }
        }
    }

    private fun readXml(relativePath: String): org.w3c.dom.Document {
        val projectDir = requireNotNull(System.getProperty("metis.projectDir"))
        return readXmlFile(File(projectDir, relativePath))
    }

    private fun readXmlFile(file: File): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        return factory.newDocumentBuilder().parse(file)
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}

