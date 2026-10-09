package org.localar.navigator.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SavedPlacesRepository(context: Context) {

    private val dbHelper = DatabaseHelper(context)

    fun isPlaceSaved(placeId: String): Boolean {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT 1 FROM saved_places WHERE place_id = ?", arrayOf(placeId))
        cursor.use {
            return it.moveToFirst()
        }
    }

    fun savePlace(place: Place) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("place_id", place.id)
            put("name", place.name)
            put("category", place.category)
            put("lat", place.lat)
            put("lon", place.lon)
            put("address", place.address ?: "")
            put("city", place.city ?: "")
            put("tag_feature", place.tagFeature ?: "")
            put("saved_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("saved_places", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun unsavePlace(placeId: String) {
        val db = dbHelper.writableDatabase
        db.delete("saved_places", "place_id = ?", arrayOf(placeId))
    }

    fun getAllSavedPlaces(): List<Place> {
        val list = mutableListOf<Place>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT place_id, name, category, lat, lon, address, city, tag_feature FROM saved_places ORDER BY saved_at DESC",
            null
        )
        cursor.use {
            val idIdx = cursor.getColumnIndexOrThrow("place_id")
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            val catIdx = cursor.getColumnIndexOrThrow("category")
            val latIdx = cursor.getColumnIndexOrThrow("lat")
            val lonIdx = cursor.getColumnIndexOrThrow("lon")
            val addrIdx = cursor.getColumnIndexOrThrow("address")
            val cityIdx = cursor.getColumnIndexOrThrow("city")
            val tagIdx = cursor.getColumnIndexOrThrow("tag_feature")

            while (cursor.moveToNext()) {
                list.add(
                    Place(
                        id = cursor.getString(idIdx),
                        osmRef = null,
                        name = cursor.getString(nameIdx),
                        normalizedName = cursor.getString(nameIdx).lowercase(),
                        category = cursor.getString(catIdx),
                        lat = cursor.getDouble(latIdx),
                        lon = cursor.getDouble(lonIdx),
                        buildingId = null,
                        recognitionSupported = true,
                        address = cursor.getString(addrIdx).takeIf { it.isNotBlank() },
                        city = cursor.getString(cityIdx).takeIf { it.isNotBlank() },
                        tagFeature = cursor.getString(tagIdx).takeIf { it.isNotBlank() },
                        isSaved = true
                    )
                )
            }
        }
        return list
    }

    private class DatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, "pinkita_user_places.db", null, 1) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS saved_places (
                    place_id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    category TEXT NOT NULL,
                    lat REAL NOT NULL,
                    lon REAL NOT NULL,
                    address TEXT,
                    city TEXT,
                    tag_feature TEXT,
                    saved_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS saved_places")
            onCreate(db)
        }
    }
}
