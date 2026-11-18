package io.github.georgexie2333.usque

import android.util.Log
import androidx.annotation.Keep

@Keep
object NativeEngine {
    private const val TAG = "NajiWarp.NativeEngine"

    val isLoaded: Boolean = try {
        System.loadLibrary("usque_android")
        Log.i(TAG, "libusque_android.so loaded successfully.")
        true
    } catch (t: Throwable) {
        Log.e(TAG, "Failed to load libusque_android.so: ${t.message}", t)
        false
    }

    fun isLibraryLoaded(): Boolean = isLoaded
    fun isReady(): Boolean = isLoaded && nativeIsReady()

    @JvmStatic external fun nativeIsReady(): Boolean
    @JvmStatic external fun nativeCapabilities(): String?
    @JvmStatic external fun nativeBuildInfo(): String?
    @JvmStatic external fun nativeConnectionTimeline(): String?

    @JvmStatic external fun nativeStart(
        tunFileDescriptor: Int,
        profileJson: String,
        warpSecret: ByteArray,
        proxyPassword: ByteArray,
        geoCacheDirectory: String,
        vpnService: Any
    ): Int

    @JvmStatic external fun nativeStartProxy(
        profileJson: String,
        warpSecret: ByteArray,
        proxyPassword: ByteArray,
        geoCacheDirectory: String,
        vpnService: Any
    ): Int

    @JvmStatic external fun nativeStop()
    @JvmStatic external fun nativeStopConfirmed(): Boolean
    @JvmStatic external fun nativeCancel()
    @JvmStatic external fun nativeResetProtonSelection()
    @JvmStatic external fun nativeNotifyNetworkChanged(generation: Long)
    @JvmStatic external fun nativeWakeRecovery()

    @JvmStatic external fun nativeReconfigure(profileJson: String, proxyPassword: ByteArray): Int
    @JvmStatic external fun nativeAttachTun(tunFileDescriptor: Int, profileJson: String, proxyPassword: ByteArray): Int
    @JvmStatic external fun nativeDetachTun(): Int
    @JvmStatic external fun nativeRejectFinalNetwork(): Int

    @JvmStatic external fun nativeValidateWarpSecret(secret: ByteArray): Int
    @JvmStatic external fun nativeInspectWarpSecret(secret: ByteArray): String?
    @JvmStatic external fun nativeSnapshot(): String?

    @JvmStatic external fun nativeRegisterConsumerWarp(locale: String): ByteArray?
    @JvmStatic external fun nativeRegisterConsumerWarpWithLicense(locale: String, licenseKey: String): ByteArray?
    @JvmStatic external fun nativeRegisterZeroTrustWarp(locale: String, team: String, callbackUri: String): ByteArray?
    @JvmStatic external fun nativeUnbindConsumerWarp(warpSecret: ByteArray): Int

    @JvmStatic external fun nativeCheckForUpdates(): String?
    @JvmStatic external fun nativeApplyProfileCommand(configPath: String, requestJson: String): String?
    @JvmStatic external fun nativeNetworkSettings(path: String, request: String): String?
    @JvmStatic external fun nativeScenarioCommand(path: String, request: String): String?

    const val OK = 0
    const val RECONFIGURE_NEED_COLD = 1
    const val RECONFIGURE_NEED_ATTACH = 2
    const val ERROR_NOT_LINKED = -2
    const val ERROR_INVALID_WARP_SECRET = -3
    const val ERROR_ALREADY_RUNNING = -4
    const val ERROR_INVALID_PROFILE = -5
    const val ERROR_PLATFORM_FAILURE = -6
    const val ERROR_TRANSPORT_FAILURE = -7
    const val ERROR_TUN_FAILURE = -8
    const val RECONFIGURE_NOT_RUNNING = -10
    const val RECONFIGURE_REJECTED = -11
}