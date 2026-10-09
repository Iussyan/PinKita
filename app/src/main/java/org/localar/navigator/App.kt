package org.localar.navigator

import android.app.Application
import org.maplibre.android.MapLibre

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize MapLibre Native SDK once per process
        MapLibre.getInstance(this)
    }
}
