@file:Suppress("MaxLineLength")

package dev.metis.agent.ml

import java.io.File
import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class NluModelParityTest {

    companion object {
        private lateinit var classifier: NluIntentClassifier
        private val referenceRows = mutableMapOf<String, List<Pair<String, Map<String, Float>>>>()

        @JvmStatic
        @BeforeClass
        fun setup() {
            val projectDir = System.getProperty("metis.projectDir")?.let { File(it) }
                ?: File(".").canonicalFile.let {
                    if (File(it, "app").exists()) File(it, "app") else it
                }
            val modelFile = File(projectDir, "src/main/assets/nlu_intent_model.json").let {
                if (it.exists()) it else File("app/src/main/assets/nlu_intent_model.json")
            }
            assertTrue("Model asset file must exist: ${modelFile.absolutePath}", modelFile.exists())
            classifier = NluIntentClassifier.fromInputStream(modelFile.inputStream())

            val refFile = File(projectDir, "src/test/assets/nlu_reference_predictions.json").let {
                if (it.exists()) it else File("app/src/test/assets/nlu_reference_predictions.json")
            }
            assertTrue("Reference test file must exist: ${refFile.absolutePath}", refFile.exists())
            parseReferenceFile(refFile)
        }

        @Suppress("NestedBlockDepth")
        private fun parseReferenceFile(file: File) {
            val json = file.readText()
            val classesStart = json.indexOf("\"classes\":[") + 10
            val classesEnd = json.indexOf(']', classesStart)
            val classes = json.substring(classesStart + 1, classesEnd)
                .split(',')
                .map { it.trim().trim('"') }

            val keys = listOf("test_csv", "raghav_test_100", "dev_clean")
            for (i in keys.indices) {
                val dKey = keys[i]
                val startTag = "\"$dKey\":["
                val startIdx = json.indexOf(startTag)
                if (startIdx < 0) continue
                val blockStart = startIdx + startTag.length
                val blockEnd = if (i < keys.size - 1) {
                    val nextTag = ",\"${keys[i + 1]}\":["
                    json.indexOf(nextTag, blockStart)
                } else {
                    json.lastIndexOf(']')
                }
                val block = json.substring(blockStart, blockEnd)

                val list = mutableListOf<Pair<String, Map<String, Float>>>()
                var idx = 0
                val textTag = "\"text\":\""
                while (idx < block.length) {
                    val tIdx = block.indexOf(textTag, idx)
                    if (tIdx < 0) break
                    var tEnd = tIdx + textTag.length
                    while (tEnd < block.length) {
                        if (block[tEnd] == '"' && block[tEnd - 1] != '\\') break
                        tEnd++
                    }
                    val text = block.substring(tIdx + textTag.length, tEnd)
                        .replace("\\\"", "\"").replace("\\\\", "\\")

                    val probTag = "\"probabilities\":["
                    val pIdx = block.indexOf(probTag, tEnd)
                    val pEnd = block.indexOf(']', pIdx)
                    val pList = block.substring(pIdx + probTag.length, pEnd)
                        .split(',')
                        .map { it.trim().toFloat() }

                    val probMap = classes.indices.associate { classes[it] to pList[it] }
                    list.add(text to probMap)
                    idx = pEnd + 1
                }
                referenceRows[dKey] = list
            }
        }
    }

    @Test
    fun `test parity on dev_clean rows`() {
        verifyParity("dev_clean", 317)
    }

    @Test
    fun `test parity on test_csv rows`() {
        verifyParity("test_csv", 128)
    }

    @Test
    fun `test parity on raghav_test_100 rows`() {
        verifyParity("raghav_test_100", 100)
    }

    private fun verifyParity(datasetName: String, expectedRows: Int) {
        val rows = referenceRows[datasetName]
        assertNotNull("Dataset $datasetName must be present", rows)
        assertEquals("Dataset $datasetName row count", expectedRows, rows!!.size)

        var maxDiff = 0.0f
        var diffCount = 0

        for ((text, expectedProbs) in rows) {
            val result = classifier.predict(text)
            for ((label, expectedP) in expectedProbs) {
                val actualP = result.probabilities[label] ?: 0.0f
                val diff = abs(actualP - expectedP)
                maxDiff = max(maxDiff, diff)
                if (diff > 1e-4f) {
                    diffCount++
                }
            }
        }

        println("Parity check on $datasetName ($expectedRows rows): max prob diff = $maxDiff (violations > 1e-4: $diffCount)")
        assertTrue("Max probability difference ($maxDiff) must be <= 1e-4 for $datasetName", maxDiff <= 1e-4f)
        assertEquals("No row should violate 1e-4 threshold on $datasetName", 0, diffCount)
    }
}
