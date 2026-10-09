package org.localar.navigator.packs

import android.content.Context
import org.localar.core.packs.PackValidator
import org.localar.core.packs.ValidationResult
import java.io.File
import java.io.FileOutputStream

class DistrictPackManager(private val context: Context) {

    private val validator = PackValidator()

    /**
     * Ensures the bundled default district pack is extracted, validated, and ready for use.
     * Returns the root directory of the extracted pack.
     */
    fun ensurePackExtracted(assetPackName: String = "synthetic-district-1.0.0.zip"): Result<File> {
        val packsRoot = File(context.filesDir, "packs")
        val districtDir = File(packsRoot, "synthetic-demo-district")
        val sqliteFile = File(districtDir, "data/region.sqlite")

        if (sqliteFile.exists() && sqliteFile.length() > 0) {
            return Result.success(districtDir)
        }

        // Unpack into staging directory first
        val stagingDir = File(context.cacheDir, "pack_staging_${System.currentTimeMillis()}")
        return try {
            val assetStream = context.assets.open("packs/$assetPackName")
            val validation = validator.unpackAndValidate(assetStream, stagingDir)

            when (validation) {
                is ValidationResult.Success -> {
                    if (districtDir.exists()) {
                        districtDir.deleteRecursively()
                    }
                    districtDir.mkdirs()
                    stagingDir.copyRecursively(districtDir, overwrite = true)
                    stagingDir.deleteRecursively()
                    Result.success(districtDir)
                }
                is ValidationResult.Failure -> {
                    stagingDir.deleteRecursively()
                    Result.failure(IllegalStateException("Pack validation failed: ${validation.reason}"))
                }
            }
        } catch (e: Exception) {
            stagingDir.deleteRecursively()
            Result.failure(e)
        }
    }
}
