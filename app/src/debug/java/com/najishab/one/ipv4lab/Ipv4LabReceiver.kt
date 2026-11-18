package com.najishab.one.ipv4lab

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.najishab.one.MyVpnService
import com.najishab.one.data.*
import com.najishab.one.engines.cfdoctor.NativeProbe
import com.najishab.one.engines.cfnet.*
import com.najishab.one.utils.*
import kotlinx.coroutines.*
import org.json.JSONObject

/** Debug-only, shell permission required. Uses saved configs internally; logs no credentials. */
class Ipv4LabReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("stop", false)) { job?.cancel(); return }
        if (job?.isActive == true) { log("busy"); return }
        val app = context.applicationContext
        val mode = intent.getStringExtra("mode") ?: "cf"
        val count = intent.getIntExtra("count", 3).coerceIn(1, 12)
        job = scope.launch {
            try {
                if (mode == "speed") { speed(intent.getStringExtra("url") ?: "https://speed.cloudflare.com/__down?bytes=10000000"); log("DONE speed"); return@launch }
                if (MyVpnService.isRunning) { log("VPN active: test skipped"); return@launch }
                log("start mode=$mode")
                when (mode) {
                    "firewall" -> log("firewall=${CfNetManager.get(app).firewall()?.key}")
                    else -> cf(app, count, intent.getBooleanExtra("diagnose", false))
                }
                log("DONE $mode")
            } catch (e: CancellationException) { log("cancelled") } catch (e: Throwable) { log("ERROR ${e.javaClass.simpleName}: ${e.message?.take(160)}") }
        }
    }

    /**
     * Throughput and latency the way an app sees them: this app's own traffic, which is inside the
     * tunnel when one is up and goes direct otherwise.
     */
    private fun speed(url: String) {
        repeat(3) { i ->
            val t0 = System.nanoTime()
            val c = runCatching { java.net.URL("https://www.gstatic.com/generate_204").openConnection() as java.net.HttpURLConnection }.getOrNull()
            val code = runCatching { c?.connectTimeout = 10_000; c?.readTimeout = 10_000; c?.responseCode }.getOrNull()
            c?.disconnect()
            log("latency #$i code=$code ms=${(System.nanoTime() - t0) / 1_000_000}")
        }
        repeat(2) { i ->
            val t0 = System.nanoTime()
            var n = 0L
            var ttfb = 0L
            val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            try {
                c.connectTimeout = 15_000; c.readTimeout = 20_000
                c.inputStream.use { inp ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val r = inp.read(buf)
                        if (r < 0) break
                        if (ttfb == 0L) ttfb = (System.nanoTime() - t0) / 1_000_000
                        n += r
                    }
                }
            } catch (e: Exception) { log("speed #$i error ${e.javaClass.simpleName}") } finally { c.disconnect() }
            val ms = ((System.nanoTime() - t0) / 1_000_000).coerceAtLeast(1)
            log("speed #$i bytes=$n ttfb=$ttfb ms=$ms mbps=${"%.2f".format(n * 8.0 / 1000.0 / ms)}")
        }
    }

    private suspend fun cf(app: Context, count: Int, diagnose: Boolean) {
        val groups = GroupManager(app)
        groups.loadCloudGroups(); groups.loadScannerGroupsPublic()
        val configs = (groups.scannerGroupsFlow.value.flatMap { it.nodes } + groups.cloudGroupsFlow.value.flatMap { it.nodes })
            .filter { node -> VpnConfig.parseUri(node.uri)?.let { CfUnfilter.isCloudflareFronted(it) && !CfUnfilter.isLocalFront(it) } == true }
            .distinctBy { it.uri }.take(count)
        val manager = CfNetManager.get(app)
        XrayJsonGenerator.tlsUnfilter = NetworkSettings.tlsUnfilter(app)
        CombineEngine.ensureCoreEnv(app)
        log("CF nodes=${configs.size} scan=${manager.saved(CfStage.SCAN)?.id} connect=${manager.saved(CfStage.CONNECT)?.id}")
        val native = NativeProbe(app)
        try {
            for ((i, node) in configs.withIndex()) {
                val c = VpnConfig.parseUri(node.uri) ?: continue
                if (diagnose && manager.saved(CfStage.SCAN, c) == null) {
                    val r = manager.diagnose(CfStage.SCAN, c, maxMs = 150_000)
                    log("CF diagnosis winner=${r.winner?.id}")
                }
                val scan = XrayJsonGenerator.generateSpeedtestConfig(c, CfStage.SCAN)
                val health = XrayJsonGenerator.generateSpeedtestConfig(c)
                val live = XrayJsonGenerator.generateConfig(c, 10809, includeTun = false)
                fun endpoint(json: String): String {
                    val settings = JSONObject(json).getJSONArray("outbounds").getJSONObject(0).getJSONObject("settings")
                    val s = (settings.optJSONArray("vnext") ?: settings.getJSONArray("servers")).getJSONObject(0)
                    return "${s.optString("address")}:${s.optInt("port")}" // compare only; never logged
                }
                log("CF row=$i v4=${!c.address.contains(':')} route=${manager.runtimeStrategy(CfStage.CONNECT, c)?.id} endpointPreserved=${endpoint(scan) == endpoint(health) && endpoint(scan) == endpoint(live)}")
                val before = CombineEngine.measureDelay(XrayJsonGenerator.generateSpeedtestConfig(c, null), timeoutMs = 12_000)
                val scanned = CombineEngine.measureDelay(scan, timeoutMs = 12_000)
                val healthy = CombineEngine.measureUri(app, node.uri)
                log("CF row=$i bareMs=$before scanMs=$scanned healthMs=$healthy")
                if (healthy > 0) {
                    val (state, result) = native.withCore(live) { port -> CfDataProbe.validate(port) }
                    log("CF row=$i live=$state transfer=${result?.ok} detail=${result?.detail}")
                }
            }
        } finally { native.release() }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var job: Job? = null
        private fun log(text: String) { Log.i("Ipv4Lab", text) }
    }
}
