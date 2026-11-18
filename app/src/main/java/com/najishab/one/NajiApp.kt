package com.najishab.one

import android.app.Application

/**
 * Exists purely so the crash reporter is installed before any other app code runs — including
 * in the VPN service process, which is where several of the native crashes have originated.
 */
class NajiApp : Application() {
    private val currentProcess: String? by lazy {
        if (android.os.Build.VERSION.SDK_INT >= 28) Application.getProcessName() else {
            val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            am.runningAppProcesses?.firstOrNull { it.pid == android.os.Process.myPid() }?.processName
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Started before the first unlock after a reboot (a Xiaomi, najishab/crashes #3220): every
        // setting lives in credential-encrypted storage, which does not exist until then, and
        // nothing of the app is declared to run that early.
        if (android.os.Build.VERSION.SDK_INT >= 24 &&
            getSystemService(android.os.UserManager::class.java)?.isUserUnlocked == false
        ) return
        CrashReporter.install(this)
        // A restore the user started is written here, before anything below reads a setting or
        // loads a store -- see backup/BackupStage. One file check when there is none.
        com.najishab.one.backup.BackupStage.onAppStart(this)
        // Before any engine can be loaded or started in this process: the store's copies are
        // found through this, and a JNI copy that crashed its last load is set aside here.
        com.najishab.one.store.StoreEngines.init(this)
        com.najishab.core.warp.WarpIdRelay.bind(this)
        com.najishab.one.data.CombineEngine.bind(this)
        val mainProcess = currentProcess == packageName
        // Off the main thread: the Keystore can take seconds to answer (it waits for its own
        // service after a reboot), and the app stood frozen at launch (ANR #542, #543).
        if (mainProcess) Thread({ runCatching { com.najishab.one.data.CfIdentityFiles.sealKnown(this) } }, "seal-identities").start()
        // Which Cloudflare edges answer on this network, for every caller (see CfEdge).
        com.najishab.one.data.CfEdge.init(this)
        // Read once, here, so the generator's copy is right before any screen or service can ask
        // it to build a config. Every later change goes through NetworkSettings.setTlsUnfilter.
        com.najishab.one.utils.NetworkSettings.tlsUnfilter(this)
        // Same arrangement for the Gemini / Google apps switch.
        com.najishab.one.utils.NetworkSettings.googleFix(this)
        com.najishab.one.utils.NetworkSettings.geminiExit(this)
        // Smart Iran route: the shipped lists in place, and today's from the daily build, once a day.
        if (mainProcess) Thread({
            runCatching {
                com.najishab.core.tunnel.SmartIranLists.ensureSeeded(this)
                if (com.najishab.core.tunnel.SmartIranRoute.enabled(this)) com.najishab.core.tunnel.SmartIranLists.refresh(this)
            }
        }, "smart-iran-lists").start()
        // Before any screen or engine asks for a string. AppLocaleManager has to be read first
        // or Loc would pin the device's language instead of the one the user chose.
        com.najishab.one.utils.AppLocaleManager.init(this)
        com.najishab.one.utils.Loc.bind(this)
        com.najishab.one.engines.cfnet.CfNetManager.bind(this)
        // The app's tunnels as the FALLBACK for deploying a Worker and taking configs when Cloudflare is
        // blocked (engines/cloud/CfTunnel.kt): direct stays first.
        com.najishab.one.engines.cloud.CfTunnel.bind(this)
        // Listen for the transport stack's status broadcasts from process start, not from the
        // first time a transport screen opens. The tunnel can be started by the Quick Settings
        // tile and restored by the boot receiver, and a screen opened afterwards would
        // otherwise show "آماده" over a tunnel that is carrying the device's traffic.
        // The main process only: the tunnel and every screen live there. Registered in each helper
        // process too, the tunnel's status and traffic broadcasts (every second or two while it is
        // up) were delivered to every one of them -- and a helper the system had frozen in the
        // background had them pile up until it was killed: "can't deliver broadcast" and
        // "excessive binder traffic during cached" (1.2.41).
        if (mainProcess) com.najishab.one.ui.tunnel.TunnelController.attach(this)
        // The home-screen widgets follow the connection from process start too, so a tunnel brought
        // up by the tile, the boot receiver or a widget is drawn on every widget at once. With no
        // widget placed this is a handful of state flows nobody writes to. Main process only, for
        // the same reason as the line above; after the language is bound (above), as they draw text.
        if (mainProcess) com.najishab.one.widget.WidgetUpdater.attach(this)
        // «کانفیگ استدیو» on the central Worker: the check that takes an ended term off while the app
        // is closed, scheduled while this phone runs the Studio on any account. One directory listing.
        if (mainProcess) Thread({ runCatching { com.najishab.one.data.studio.central.StudioCentralJob.ensure(this) } }, "studio-central-job").start()
        // Mirror that stack's connection log to a file. The in-memory ring holds a hundred
        // lines and dies with the process, which is exactly the wrong property for the case it
        // exists to serve: a tunnel that takes the app down with it leaves nothing to read. The
        // file is capped and truncates itself, so it cannot grow without bound.
        com.najishab.core.tunnel.ConnectionLog.bind(java.io.File(filesDir, "tunnel.log"))
        // Helper engines that only serve a screen go when the app leaves the screen: a child
        // process left working in a backgrounded app gets the whole app killed for its CPU use.
        if (mainProcess) registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(a: android.app.Activity) {
                started++
                com.najishab.one.utils.BackgroundGuard.onForeground()
            }
            override fun onActivityStopped(a: android.app.Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) {
                    Thread({ runCatching { com.najishab.core.geph.GephEngine.onAppBackground() } }, "app-background").start()
                    // and, two minutes on with no VPN up, whatever else is still working (BackgroundGuard)
                    com.najishab.one.utils.BackgroundGuard.onBackground(this@NajiApp)
                }
            }
            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
            override fun onActivityResumed(a: android.app.Activity) {}
            override fun onActivityPaused(a: android.app.Activity) {}
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) {}
        })
        // The Vercel tunnel is gone; its one stored switch ("vercel_enabled") lived alone in this
        // file. Deleting it keeps an old install from carrying a setting nothing reads any more.
        runCatching { deleteSharedPreferences("emergency_prefs") }
    }
}
