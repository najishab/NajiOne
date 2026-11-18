package com.najishab.one.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One-time setup the Xray core needs before anything measures or connects through it: the geo
 * data files in the app's files dir, and the core's own environment.
 */
object XrayEnv {

    private const val TAG = "XrayEnv"

    @Volatile
    private var ready = false
    private val lock = Any()

    suspend fun ensure(context: Context) = withContext(Dispatchers.IO) {
        // Once per process, and never again.
        //
        // `initCoreEnv` sets up the core's shared global state and mints a new xudpBaseKey each
        // time, so re-running it while a dozen measurement cores are mid-flight re-initialises the
        // ground under them -- servers that were perfectly alive came back as failures, in numbers
        // large enough to look like the internet was down. The asset copy is also pure I/O that
        // only matters the first time.
        if (ready) return@withContext
        synchronized(lock) {
            if (ready) return@withContext
            ready = true
        }
        try {
            for (filename in listOf("geosite.dat", "geoip.dat")) {
                val destFile = java.io.File(context.filesDir, filename)
                if (!destFile.exists() || destFile.length() < 1000) {
                    context.assets.open(filename).use { input ->
                        java.io.FileOutputStream(destFile).use { output -> input.copyTo(output) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "asset copy failed: ${e.message}")
        }
        // The core itself never runs in the main process (XrayHosts, `:xmeasure`): nothing to set up here.
        if (com.najishab.core.warp.XrayHosts.inMainProcess(context)) return@withContext
        try {
            val keyBytes = ByteArray(32)
            java.security.SecureRandom().nextBytes(keyBytes)
            val flags = android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
            val xudpBaseKey = android.util.Base64.encodeToString(keyBytes, flags)
            libv2ray.Libv2ray.initCoreEnv(context.filesDir.absolutePath, xudpBaseKey)
        } catch (e: Throwable) {
            Log.w(TAG, "initCoreEnv failed: ${e.message}")
        }
    }
}
