package org.localar.navigator

import android.app.Application
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize MapLibre Native SDK once per process
        MapLibre.getInstance(this)

        // Configure compliant User-Agent for OpenStreetMap tile requests (policy requirement, zero API key)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "LocalAR-Navigator/1.0 (Android; OpenStreetMap)")
                    .build()
                chain.proceed(request)
            }
            .build()
        HttpRequestUtil.setOkHttpClient(client)
    }
}
