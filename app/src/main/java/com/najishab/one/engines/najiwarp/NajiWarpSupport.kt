package com.najishab.one.engines.najiwarp

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.ErrnoException
import android.util.AtomicFile
import java.io.File
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.ByteBuffer
import java.security.KeyStore
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object NajiWarpIdentityVault {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "com.najishab.one.najiwarp.identity.v1"
    private const val DIR_NAME = "najiwarp_identity_v1"
    private const val FILE_SECRET = "warp_secret.bin"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val FORMAT_VERSION: Byte = 1
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val GCM_TAG_BYTES = 16

    private fun vaultFile(context: Context): File {
        val dir = File(context.applicationContext.noBackupFilesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, FILE_SECRET)
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return gen.generateKey()
    }

    @Synchronized
    fun saveSecret(context: Context, secret: ByteArray) {
        require(secret.isNotEmpty() && secret.size <= 128 * 1024)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(FILE_SECRET.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(secret)
        val encoded = ByteBuffer.allocate(2 + cipher.iv.size + ciphertext.size)
            .put(FORMAT_VERSION)
            .put(cipher.iv.size.toByte())
            .put(cipher.iv)
            .put(ciphertext)
            .array()
        val atomic = AtomicFile(vaultFile(context))
        val out = atomic.startWrite()
        try {
            out.write(encoded)
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        } finally {
            encoded.fill(0)
            ciphertext.fill(0)
        }
    }

    @Synchronized
    fun loadSecret(context: Context): ByteArray? {
        val file = vaultFile(context)
        if (!file.isFile) return null
        val encoded = runCatching { AtomicFile(file).readFully() }.getOrNull() ?: return null
        return try {
            if (encoded.size < 2 + GCM_IV_BYTES + GCM_TAG_BYTES) return null
            val buf = ByteBuffer.wrap(encoded)
            if (buf.get() != FORMAT_VERSION) return null
            val ivLen = buf.get().toInt() and 0xff
            if (ivLen != GCM_IV_BYTES || buf.remaining() <= GCM_TAG_BYTES) return null
            val iv = ByteArray(ivLen)
            buf.get(iv)
            val ct = ByteArray(buf.remaining())
            buf.get(ct)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(FILE_SECRET.toByteArray(Charsets.UTF_8))
            cipher.doFinal(ct)
        } catch (_: Exception) {
            null
        } finally {
            encoded.fill(0)
        }
    }

    @Synchronized
    fun hasIdentity(context: Context): Boolean {
        val secret = loadSecret(context) ?: return false
        val ok = secret.isNotEmpty()
        secret.fill(0)
        return ok
    }

    @Synchronized
    fun clear(context: Context) {
        val file = vaultFile(context)
        if (file.exists()) AtomicFile(file).delete()
    }
}

internal const val FAMILY_IPV4 = 0x1
internal const val FAMILY_IPV6 = 0x2
private const val ANDROID_ENONET = 64

internal data class RecoveryNetworkSnapshot(
    val generation: Long = 0L,
    val known: Boolean = false,
    val familyMask: Int = 0
) {
    fun toWire(): LongArray = longArrayOf(generation, if (known) 1L else 0L, familyMask.toLong())
}

internal data class PhysicalNetworkCandidate(
    val handle: Long,
    val score: Int,
    val familyMask: Int
)

internal data class PhysicalNetworkSelection(
    val handle: Long,
    val familyMask: Int
)

internal fun choosePhysicalNetwork(
    currentHandle: Long?,
    candidates: List<PhysicalNetworkCandidate>
): PhysicalNetworkSelection? {
    val usable = candidates.filter { it.familyMask != 0 }
    val maxScore = usable.maxOfOrNull(PhysicalNetworkCandidate::score) ?: return null
    val selected = usable.firstOrNull { it.handle == currentHandle && it.score == maxScore }
        ?: usable.asSequence().filter { it.score == maxScore }.minByOrNull(PhysicalNetworkCandidate::handle)
        ?: return null
    return PhysicalNetworkSelection(selected.handle, selected.familyMask)
}

internal class PhysicalNetworkMonitor(
    private val mainHandler: Handler,
    private val listener: Listener,
    private val selectionDelayMillis: Long = 100L
) {
    interface Listener {
        fun onUnderlyingNetworkChanged(
            selectedNetwork: Network?,
            selectedFamilyMask: Int,
            generation: Long
        )
    }

    private data class NetworkCandidate(
        val capabilities: NetworkCapabilities? = null,
        val linkProperties: LinkProperties? = null,
        val blocked: Boolean = false
    )

    private val availableNetworks = ConcurrentHashMap<Network, NetworkCandidate>()
    private val underlyingNetwork = AtomicReference<Network?>(null)
    private val underlyingFamilyMask = AtomicInteger(0)
    private val underlyingDnsServers = AtomicReference<List<InetAddress>>(emptyList())
    private val networkRestartGeneration = AtomicLong(0L)
    private val recoverySnapshot = AtomicReference(RecoveryNetworkSnapshot())
    private val generationHistory = ArrayDeque<Pair<Long, Network?>>(4)
    private val selectionTask = Runnable(::selectUnderlyingNetwork)
    private var connectivity: ConnectivityManager? = null

    private val periodicSelection = object : Runnable {
        override fun run() {
            val mgr = connectivity ?: return
            refreshCandidates(mgr)
            selectUnderlyingNetwork()
            mainHandler.postDelayed(this, 1_000L)
        }
    }

    @Suppress("DEPRECATION")
    private fun refreshCandidates(manager: ConnectivityManager) {
        val current = runCatching { manager.allNetworks.toSet() }.getOrNull() ?: return
        availableNetworks.keys.removeAll { it !in current }
        for (network in current) {
            val caps = runCatching { manager.getNetworkCapabilities(network) }.getOrNull()
            val props = runCatching { manager.getLinkProperties(network) }.getOrNull()
            availableNetworks.compute(network) { _, prev ->
                (prev ?: NetworkCandidate()).copy(capabilities = caps, linkProperties = props)
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            availableNetworks.putIfAbsent(network, NetworkCandidate())
            scheduleSelection()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            availableNetworks.compute(network) { _, prev ->
                (prev ?: NetworkCandidate()).copy(capabilities = networkCapabilities)
            }
            scheduleSelection()
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            availableNetworks.compute(network) { _, prev ->
                (prev ?: NetworkCandidate()).copy(linkProperties = linkProperties)
            }
            scheduleSelection()
        }

        override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
            availableNetworks.compute(network) { _, prev ->
                (prev ?: NetworkCandidate()).copy(blocked = blocked)
            }
            scheduleSelection()
        }

        override fun onLost(network: Network) {
            availableNetworks.remove(network)
            scheduleSelection()
        }
    }

    fun register(manager: ConnectivityManager) {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        connectivity = manager
        refreshCandidates(manager)
        selectUnderlyingNetwork()
        manager.registerNetworkCallback(request, networkCallback)
        mainHandler.removeCallbacks(periodicSelection)
        mainHandler.postDelayed(periodicSelection, 1_000L)
    }

    fun unregister(manager: ConnectivityManager) {
        connectivity = null
        mainHandler.removeCallbacks(periodicSelection)
        mainHandler.removeCallbacks(selectionTask)
        runCatching { manager.unregisterNetworkCallback(networkCallback) }
        synchronized(generationHistory) { generationHistory.clear() }
        recoverySnapshot.set(RecoveryNetworkSnapshot(generation = generation()))
    }

    fun underlyingNetwork(): Network? = underlyingNetwork.get()
    fun underlyingFamilyMask(): Int = underlyingFamilyMask.get()
    fun underlyingDnsServers(): List<InetAddress> = underlyingDnsServers.get()
    fun generation(): Long = networkRestartGeneration.get()
    fun recoverySnapshot(): LongArray = recoverySnapshot.get().toWire()

    fun bumpGeneration(): Long {
        val gen = networkRestartGeneration.incrementAndGet()
        recordGeneration(gen, underlyingNetwork.get())
        recoverySnapshot.updateAndGet { prev -> prev.copy(generation = maxOf(gen, prev.generation)) }
        return gen
    }

    fun networkForGeneration(gen: Long): Network? = synchronized(generationHistory) {
        generationHistory.firstOrNull { it.first == gen }?.second
    }

    private fun recordGeneration(gen: Long, network: Network?) {
        synchronized(generationHistory) {
            val newest = maxOf(gen, generationHistory.firstOrNull()?.first ?: gen)
            generationHistory.removeAll { it.first == gen || it.first < newest - 1 }
            if (gen < newest - 1) return
            if (gen == newest) generationHistory.addFirst(gen to network) else generationHistory.addLast(gen to network)
            while (generationHistory.size > 2) generationHistory.removeLast()
        }
    }

    private fun scheduleSelection() {
        mainHandler.removeCallbacks(selectionTask)
        mainHandler.postDelayed(selectionTask, selectionDelayMillis)
    }

    fun cancelScheduledSelection() {
        mainHandler.removeCallbacks(selectionTask)
    }

    fun awaitPhysicalNetwork(isCurrent: () -> Boolean, waitMillis: Long): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis)
        while (System.nanoTime() < deadline) {
            if (!isCurrent()) return false
            if (underlyingNetwork.get() != null) return true
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return underlyingNetwork.get() != null && isCurrent()
    }

    fun selectUnderlyingNetwork() {
        val candidates = availableNetworks.entries.mapNotNull { (network, candidate) ->
            val caps = candidate.capabilities ?: return@mapNotNull null
            if (candidate.blocked ||
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            ) return@mapNotNull null
            val mask = familyMask(candidate.linkProperties)
            if (mask == 0) return@mapNotNull null
            network to PhysicalNetworkCandidate(network.networkHandle, networkScore(caps), mask)
        }
        val current = underlyingNetwork.get()
        val selection = choosePhysicalNetwork(current?.networkHandle, candidates.map { it.second })
        val selectedNetwork = candidates.firstOrNull { it.second.handle == selection?.handle }?.first
        val selectedMask = selection?.familyMask ?: 0
        val selectedDns = selectedNetwork
            ?.let(availableNetworks::get)
            ?.linkProperties
            ?.dnsServers
            ?.distinctBy(::dnsKey)
            ?.sortedBy(::dnsKey)
            ?.take(8)
            ?: emptyList()

        val prevNet = underlyingNetwork.getAndSet(selectedNetwork)
        val prevMask = underlyingFamilyMask.getAndSet(selectedMask)
        val prevDns = underlyingDnsServers.getAndSet(selectedDns)

        val changed = prevNet?.networkHandle != selectedNetwork?.networkHandle ||
            prevMask != selectedMask ||
            prevDns.map(::dnsKey) != selectedDns.map(::dnsKey)

        val gen = if (changed) networkRestartGeneration.incrementAndGet() else networkRestartGeneration.get()
        val nextSnap = RecoveryNetworkSnapshot(gen, true, selectedMask)
        val snapChanged = run {
            var updated = false
            while (true) {
                val prev = recoverySnapshot.get()
                if (nextSnap.generation < prev.generation || nextSnap == prev) break
                if (recoverySnapshot.compareAndSet(prev, nextSnap)) {
                    updated = true
                    break
                }
            }
            updated
        }
        recordGeneration(gen, selectedNetwork)
        if (changed || snapChanged) {
            listener.onUnderlyingNetworkChanged(selectedNetwork, selectedMask, gen)
        }
    }

    private fun dnsKey(address: InetAddress): String = when (address) {
        is Inet6Address -> "${address.hostAddress?.substringBefore('%')}|${address.scopeId}"
        else -> "${address.hostAddress}|0"
    }

    private fun familyMask(linkProperties: LinkProperties?): Int {
        if (linkProperties == null) return FAMILY_IPV4 or FAMILY_IPV6
        var mask = 0
        for (route in linkProperties.routes) {
            if (!route.isDefaultRoute) continue
            when (route.destination.address) {
                is Inet4Address -> mask = mask or FAMILY_IPV4
                is Inet6Address -> mask = mask or FAMILY_IPV6
            }
        }
        return mask
    }

    private fun networkScore(caps: NetworkCapabilities): Int {
        var score = if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) 100 else 0
        score += when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 40
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 30
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 20
            else -> 10
        }
        return score
    }
}

internal object UnderlyingSocketBindingResult {
    const val BOUND = 0
    const val STALE = 1
    const val REJECTED = 2
}

internal class UnderlyingSocketBinding<N : Any, D : Any>(
    private val destroyed: () -> Boolean,
    private val currentGeneration: () -> Long,
    private val networkForGeneration: (Long) -> N?,
    private val networkHandle: (N) -> Long,
    private val protect: (Int) -> Boolean,
    private val duplicate: (Int) -> D,
    private val bindToNetwork: (N, D) -> Unit,
    private val close: (D) -> Unit
) {
    fun bind(descriptor: Int, expectedGeneration: Long, requireVpnProtection: Boolean): Int {
        if (descriptor < 0 || expectedGeneration < 0 || destroyed()) return UnderlyingSocketBindingResult.REJECTED
        if (currentGeneration() != expectedGeneration) return UnderlyingSocketBindingResult.STALE
        val network = networkForGeneration(expectedGeneration) ?: return UnderlyingSocketBindingResult.STALE
        if (requireVpnProtection) {
            val ok = try { protect(descriptor) } catch (_: Exception) { false }
            if (!ok) return UnderlyingSocketBindingResult.REJECTED
        }
        val statusAfterProtect = status(expectedGeneration, network)
        if (statusAfterProtect != UnderlyingSocketBindingResult.BOUND) return statusAfterProtect
        val copy = try { duplicate(descriptor) } catch (_: Exception) { return UnderlyingSocketBindingResult.REJECTED }
        var result: Int
        try {
            result = status(expectedGeneration, network)
            if (result == UnderlyingSocketBindingResult.BOUND) {
                result = try {
                    bindToNetwork(network, copy)
                    status(expectedGeneration, network)
                } catch (error: Exception) {
                    val s = status(expectedGeneration, network)
                    when {
                        s != UnderlyingSocketBindingResult.BOUND -> s
                        isUnderlyingNetworkGone(error) -> UnderlyingSocketBindingResult.STALE
                        else -> UnderlyingSocketBindingResult.REJECTED
                    }
                }
            }
        } finally {
            try { close(copy) } catch (_: Exception) { result = UnderlyingSocketBindingResult.REJECTED }
        }
        if (destroyed()) return UnderlyingSocketBindingResult.REJECTED
        return if (result == UnderlyingSocketBindingResult.BOUND) status(expectedGeneration, network) else result
    }

    private fun status(expectedGeneration: Long, network: N): Int = when {
        destroyed() -> UnderlyingSocketBindingResult.REJECTED
        currentGeneration() != expectedGeneration -> UnderlyingSocketBindingResult.STALE
        networkForGeneration(expectedGeneration)?.let(networkHandle) != networkHandle(network) -> UnderlyingSocketBindingResult.STALE
        else -> UnderlyingSocketBindingResult.BOUND
    }

    private fun isUnderlyingNetworkGone(error: Throwable): Boolean {
        val visited = IdentityHashMap<Throwable, Boolean>()
        var cause: Throwable? = error
        while (cause != null && visited.put(cause, true) == null) {
            if (cause is ErrnoException) return cause.errno == ANDROID_ENONET
            cause = cause.cause
        }
        return false
    }
}

internal data class CidrBlock(val address: InetAddress, val prefixLength: Int) {
    val isIpv4: Boolean get() = address is Inet4Address
    val isIpv6: Boolean get() = address is Inet6Address
    val key: String get() = "${address.hostAddress}/$prefixLength"

    fun subtract(exclusion: CidrBlock): List<CidrBlock> {
        if (!contains(exclusion) && !exclusion.contains(this)) return listOf(this)
        if (exclusion.contains(this)) return emptyList()
        val retained = mutableListOf<CidrBlock>()
        var current = this
        while (current.prefixLength < exclusion.prefixLength) {
            val (left, right) = current.split()
            if (left.contains(exclusion)) {
                retained += right
                current = left
            } else {
                retained += left
                current = right
            }
        }
        return retained
    }

    fun contains(other: CidrBlock): Boolean {
        if (address.address.size != other.address.address.size || prefixLength > other.prefixLength) return false
        return prefixMatches(address.address, other.address.address, prefixLength)
    }

    private fun split(): Pair<CidrBlock, CidrBlock> {
        val leftBytes = address.address.copyOf()
        val rightBytes = leftBytes.copyOf()
        val byteIdx = prefixLength / 8
        val bitMask = 1 shl (7 - (prefixLength % 8))
        rightBytes[byteIdx] = (rightBytes[byteIdx].toInt() or bitMask).toByte()
        return create(leftBytes, prefixLength + 1) to create(rightBytes, prefixLength + 1)
    }

    companion object {
        fun parse(value: String): CidrBlock {
            val (host, prefixText) = value.trim().split('/', limit = 2)
            val bytes = InetAddress.getByName(host).address
            return create(bytes, prefixText.toInt())
        }

        private fun create(source: ByteArray, prefixLength: Int): CidrBlock {
            val net = source.copyOf()
            val fullBytes = prefixLength / 8
            val remBits = prefixLength % 8
            if (remBits != 0 && fullBytes < net.size) {
                net[fullBytes] = (net[fullBytes].toInt() and (0xff shl (8 - remBits))).toByte()
            }
            val clearFrom = fullBytes + if (remBits == 0) 0 else 1
            for (i in clearFrom until net.size) net[i] = 0
            return CidrBlock(InetAddress.getByAddress(net), prefixLength)
        }

        private fun prefixMatches(left: ByteArray, right: ByteArray, prefixLength: Int): Boolean {
            val wholeBytes = prefixLength / 8
            for (i in 0 until wholeBytes) if (left[i] != right[i]) return false
            val remBits = prefixLength % 8
            if (remBits == 0) return true
            val mask = 0xff shl (8 - remBits)
            return (left[wholeBytes].toInt() and mask) == (right[wholeBytes].toInt() and mask)
        }
    }
}

internal data class RoutePlan(val included: List<CidrBlock>, val excluded: List<CidrBlock>)

internal object VpnRoutePlanner {
    private val lanExclusions = listOf(
        CidrBlock.parse("10.0.0.0/8"),
        CidrBlock.parse("172.16.0.0/12"),
        CidrBlock.parse("192.168.0.0/16"),
        CidrBlock.parse("169.254.0.0/16"),
        CidrBlock.parse("fc00::/7"),
        CidrBlock.parse("fe80::/10")
    )

    fun plan(allowLan: Boolean, supportsRouteExclusion: Boolean): RoutePlan {
        val defaults = listOf(CidrBlock.parse("0.0.0.0/0"), CidrBlock.parse("::/0"))
        val exclusions = if (allowLan) lanExclusions else emptyList()
        if (supportsRouteExclusion) return RoutePlan(defaults, exclusions)
        var included = defaults
        for (ex in exclusions) {
            included = included.flatMap { it.subtract(ex) }
        }
        return RoutePlan(included, emptyList())
    }
}