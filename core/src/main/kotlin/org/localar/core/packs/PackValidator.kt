package org.localar.core.packs

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

sealed class ValidationResult {
    data class Success(val manifest: PackManifest, val extractedDir: File) : ValidationResult()
    data class Failure(val reason: String, val details: String? = null) : ValidationResult()
}

/**
 * Validates and safely extracts offline region pack archives.
 * Implements Section 7.3 of spec.md.
 */
class PackValidator(
    private val maxFileCount: Int = 10_000,
    private val maxUncompressedBytes: Long = 500L * 1024L * 1024L // 500 MB limit
) {

    /**
     * Unpacks and validates a region pack zip from an input stream into the staging directory.
     */
    fun unpackAndValidate(zipStream: InputStream, stagingDir: File): ValidationResult {
        if (!stagingDir.exists()) {
            stagingDir.mkdirs()
        }

        var fileCount = 0
        var totalBytes = 0L
        val seenPaths = mutableSetOf<String>()

        try {
            ZipInputStream(zipStream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name.replace('\\', '/')

                    // 1. Safety check: Zip Slip / Directory traversal
                    if (entryName.startsWith("/") || entryName.contains("..")) {
                        stagingDir.deleteRecursively()
                        return ValidationResult.Failure("Malicious path detected in archive: $entryName")
                    }

                    // 2. Duplicate entry check
                    if (!seenPaths.add(entryName)) {
                        stagingDir.deleteRecursively()
                        return ValidationResult.Failure("Duplicate archive entry: $entryName")
                    }

                    val destFile = File(stagingDir, entryName)
                    // Ensure canonical path stays strictly inside staging directory
                    if (!destFile.canonicalPath.startsWith(stagingDir.canonicalPath)) {
                        stagingDir.deleteRecursively()
                        return ValidationResult.Failure("Path escapes staging directory: $entryName")
                    }

                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        destFile.outputStream().use { fos ->
                            val buffer = ByteArray(8192)
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                totalBytes += len
                                if (totalBytes > maxUncompressedBytes) {
                                    stagingDir.deleteRecursively()
                                    return ValidationResult.Failure("Archive exceeds uncompressed size limit ($maxUncompressedBytes bytes)")
                                }
                                fos.write(buffer, 0, len)
                            }
                        }

                        fileCount++
                        if (fileCount > maxFileCount) {
                            stagingDir.deleteRecursively()
                            return ValidationResult.Failure("Archive exceeds file count limit ($maxFileCount)")
                        }
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            stagingDir.deleteRecursively()
            return ValidationResult.Failure("Archive extraction failed: ${e.message}")
        }

        // 3. Validate manifest existence
        val manifestFile = File(stagingDir, "manifest.json")
        if (!manifestFile.exists()) {
            stagingDir.deleteRecursively()
            return ValidationResult.Failure("Missing required manifest.json in pack root")
        }

        val manifest: PackManifest
        try {
            val manifestJson = manifestFile.readText(Charsets.UTF_8)
            manifest = PackManifest.parse(manifestJson)
        } catch (e: Exception) {
            stagingDir.deleteRecursively()
            return ValidationResult.Failure("Failed to parse manifest.json: ${e.message}")
        }

        // 4. Verify file inventory hashes and sizes
        for (expectedFile in manifest.files) {
            val fileOnDisk = File(stagingDir, expectedFile.path)
            if (!fileOnDisk.exists()) {
                stagingDir.deleteRecursively()
                return ValidationResult.Failure("Manifest file missing on disk: ${expectedFile.path}")
            }

            if (fileOnDisk.length() != expectedFile.sizeBytes) {
                stagingDir.deleteRecursively()
                return ValidationResult.Failure(
                    "File size mismatch for ${expectedFile.path}: expected ${expectedFile.sizeBytes}, got ${fileOnDisk.length()}"
                )
            }

            val actualHash = computeSha256(fileOnDisk)
            if (!actualHash.equals(expectedFile.sha256, ignoreCase = true)) {
                stagingDir.deleteRecursively()
                return ValidationResult.Failure(
                    "SHA-256 hash mismatch for ${expectedFile.path}: expected ${expectedFile.sha256}, got $actualHash"
                )
            }
        }

        return ValidationResult.Success(manifest = manifest, extractedDir = stagingDir)
    }

    companion object {
        fun computeSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
