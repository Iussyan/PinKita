package org.localar.core

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.localar.core.packs.PackValidator
import org.localar.core.packs.ValidationResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun createZip(entries: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, data) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    @Test
    fun testValidPackSucceeds() {
        val testData = "test-sqlite-content".toByteArray(Charsets.UTF_8)
        val dataHash = sha256(testData)

        val manifestJson = """
        {
          "schema_version": 1,
          "region_id": "test-region",
          "display_name": "Test Region",
          "pack_version": "1.0.0",
          "built_at": "2026-10-09T00:00:00Z",
          "bounds": { "west": 120.9, "south": 14.5, "east": 121.0, "north": 14.6 },
          "routing_profile": "pedestrian-v1",
          "files": [
            {
              "path": "data/region.sqlite",
              "size_bytes": ${testData.size},
              "sha256": "$dataHash"
            }
          ]
        }
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val zipBytes = createZip(
            mapOf(
                "manifest.json" to manifestJson,
                "data/region.sqlite" to testData
            )
        )

        val stagingDir = File(tempFolder.root, "staging_valid")
        val validator = PackValidator()
        val result = validator.unpackAndValidate(ByteArrayInputStream(zipBytes), stagingDir)

        assertTrue("Expected validation to succeed", result is ValidationResult.Success)
        val success = result as ValidationResult.Success
        assertEquals("test-region", success.manifest.regionId)
        assertEquals("Test Region", success.manifest.displayName)
        assertTrue(File(stagingDir, "data/region.sqlite").exists())
    }

    @Test
    fun testZipSlipDirectoryTraversalRejected() {
        val zipBytes = createZip(
            mapOf(
                "../escape.txt" to "malicious".toByteArray(Charsets.UTF_8)
            )
        )

        val stagingDir = File(tempFolder.root, "staging_slip")
        val validator = PackValidator()
        val result = validator.unpackAndValidate(ByteArrayInputStream(zipBytes), stagingDir)

        assertTrue("Expected validation to fail on zip slip", result is ValidationResult.Failure)
        assertFalse("Staging dir should be deleted on failure", stagingDir.exists())
    }

    @Test
    fun testHashMismatchRejected() {
        val actualData = "real-data".toByteArray(Charsets.UTF_8)
        val fakeHash = "0000000000000000000000000000000000000000000000000000000000000000"

        val manifestJson = """
        {
          "schema_version": 1,
          "region_id": "test-region",
          "display_name": "Test Region",
          "pack_version": "1.0.0",
          "bounds": { "west": 120.9, "south": 14.5, "east": 121.0, "north": 14.6 },
          "files": [
            {
              "path": "data/region.sqlite",
              "size_bytes": ${actualData.size},
              "sha256": "$fakeHash"
            }
          ]
        }
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val zipBytes = createZip(
            mapOf(
                "manifest.json" to manifestJson,
                "data/region.sqlite" to actualData
            )
        )

        val stagingDir = File(tempFolder.root, "staging_hash_fail")
        val validator = PackValidator()
        val result = validator.unpackAndValidate(ByteArrayInputStream(zipBytes), stagingDir)

        assertTrue("Expected validation to fail on hash mismatch", result is ValidationResult.Failure)
        val failure = result as ValidationResult.Failure
        assertTrue("Error should mention hash mismatch", failure.reason.contains("SHA-256 hash mismatch"))
        assertFalse("Staging dir should be deleted on failure", stagingDir.exists())
    }

    @Test
    fun testMissingManifestRejected() {
        val zipBytes = createZip(
            mapOf("data/sample.txt" to "hello".toByteArray(Charsets.UTF_8))
        )

        val stagingDir = File(tempFolder.root, "staging_no_manifest")
        val validator = PackValidator()
        val result = validator.unpackAndValidate(ByteArrayInputStream(zipBytes), stagingDir)

        assertTrue("Expected failure when manifest is missing", result is ValidationResult.Failure)
        val failure = result as ValidationResult.Failure
        assertTrue(failure.reason.contains("Missing required manifest.json"))
    }

    @Test
    fun testGeneratedRealPackValidates() {
        val packFile = File("dist/synthetic-district-1.0.0.zip")
        if (!packFile.exists()) return

        val stagingDir = File(tempFolder.root, "staging_generated_pack")
        val validator = PackValidator()
        val result = validator.unpackAndValidate(packFile.inputStream(), stagingDir)

        assertTrue("Generated synthetic pack must validate successfully: $result", result is ValidationResult.Success)
        val success = result as ValidationResult.Success
        assertEquals("synthetic-demo-district", success.manifest.regionId)
        assertTrue(File(stagingDir, "data/region.sqlite").exists())
        assertTrue(File(stagingDir, "ai/embeddings.f32").exists())
    }
}
