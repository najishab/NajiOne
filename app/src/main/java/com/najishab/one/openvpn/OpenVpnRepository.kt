package com.najishab.one.openvpn

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class OpenVpnData(
    val accounts: List<Account> = emptyList(),
    val profiles: List<Profile> = emptyList(),
    val locations: List<OpenVpnLocation> = emptyList(),
    val availableModes: List<String> = emptyList(),
    val activeAccount: String? = null,
    val selectedProfile: String? = null,
    val selectedLocation: String? = null,
    val selectedMode: String = OpenVpnLocation.AUTO_MODE,
    val splitTcp: Boolean = true,
    val blockIpv6: Boolean = true,
    val lastManualUsername: String = "",
    val autoSwitch: Boolean = false,
    val storageError: Boolean = false,
)

class OpenVpnRepository private constructor(private val context: Context) {
    private val vault = OpenVpnVault(context)
    private val mutable = MutableStateFlow(OpenVpnData())
    val data = mutable.asStateFlow()

    private var customProfiles: List<Profile> = emptyList()
    private var providerProfiles: List<Profile> = emptyList()
    private var favoriteLocationIds: Set<String> = emptySet()
    private var locationProbes: Map<String, ProbeResult> = emptyMap()
    private var selectedLocationId: String? = null
    private var selectedMode: String = OpenVpnLocation.AUTO_MODE
    private var splitTcp: Boolean = true
    private var blockIpv6: Boolean = true
    private var lastManualUsername: String = ""
    private var vaultAvailable: Boolean = true

    private val localLocationsBase: List<OpenVpnLocation>
    private val discoveredModes: List<String>

    init {
        val (loadedLocations, modes) = loadBundledOvpnServer()
        localLocationsBase = loadedLocations
        discoveredModes = modes

        loadPersistedState()
        publish()
    }

    private fun loadBundledOvpnServer(): Pair<List<OpenVpnLocation>, List<String>> {
        val assets = context.assets
        val folders = runCatching { assets.list("ovpn_server")?.toList().orEmpty() }.getOrDefault(emptyList())
        val orderedFolders = buildList {
            for (p in OpenVpnLocation.PRIORITY_MODES) if (p in folders) add(p)
            for (f in folders.sorted()) if (f !in this) add(f)
        }

        val byLocation = linkedMapOf<String, MutableMap<String, Profile>>()
        for (folder in orderedFolders) {
            val files = runCatching { assets.list("ovpn_server/$folder")?.toList().orEmpty() }.getOrDefault(emptyList())
            for (fileName in files.sorted()) {
                if (!fileName.endsWith(".ovpn", ignoreCase = true) && !fileName.endsWith(".ovpn.txt", ignoreCase = true)) continue
                val locId = fileName.removeSuffix(".txt").removeSuffix(".ovpn").trim()
                if (locId.isEmpty()) continue
                val parsed = runCatching {
                    val text = assets.open("ovpn_server/$folder/$fileName").bufferedReader().use { it.readText() }
                    val raw = ProfileImporter.parse(locId, text, emptyMap())
                    Profile(
                        id = profileIdFor(folder, locId),
                        name = locId,
                        config = raw.config,
                        remotes = raw.remotes,
                        authenticatedControl = raw.authenticatedControl,
                    )
                }.onFailure {
                    Log.w(TAG, "Skipped $folder/$fileName: ${it.message}")
                }.getOrNull() ?: continue

                byLocation.getOrPut(locId) { linkedMapOf() }[folder] = parsed
            }
        }

        val locations = byLocation.entries.map { (locId, variants) ->
            OpenVpnLocation(id = locId, name = locId, variants = variants)
        }
        return locations to orderedFolders
    }

    private fun loadPersistedState() {
        try {
            vault.read("ovpn_prefs_v2")?.let { raw ->
                val j = JSONObject(raw)
                selectedLocationId = j.stringOrNull("selectedLocation")
                selectedMode = j.stringOrNull("selectedMode") ?: OpenVpnLocation.AUTO_MODE
                splitTcp = if (j.has("splitTcp")) j.optBoolean("splitTcp", true) else true
                blockIpv6 = if (j.has("blockIpv6")) j.optBoolean("blockIpv6", true) else true
                lastManualUsername = j.optString("lastManualUser", "")
                favoriteLocationIds = j.optJSONArray("favorites")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }.toSet()
                } ?: emptySet()
                locationProbes = j.optJSONObject("probes")?.let { obj ->
                    obj.keys().asSequence().mapNotNull { k ->
                        val p = obj.optJSONObject(k) ?: return@mapNotNull null
                        val ips = p.optJSONArray("ips")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                        k to ProbeResult(p.longOrNull("ms"), p.getLong("checked"), p.getString("method"), p.stringOrNull("error"), ips)
                    }.toMap()
                } ?: emptyMap()
            }

            // Read legacy/backup catalog for user-imported profiles (ignoring TunnelBear)
            vault.read("catalog")?.let { raw ->
                val j = JSONObject(raw)
                val loadedProfiles = j.optJSONArray("profiles")?.objects().orEmpty().mapNotNull { p ->
                    runCatching {
                        val name = p.getString("name")
                        val config = p.getString("config")
                        if (name.startsWith("TunnelBear", ignoreCase = true) || config.contains("lazerpenguin.com", ignoreCase = true)) {
                            return@mapNotNull null
                        }
                        ProfileImporter.parse(name, config, emptyMap()).withFavorite(p.optBoolean("favorite"))
                    }.getOrNull()
                }
                customProfiles = loadedProfiles
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vault read warning: ${e.message}")
            vaultAvailable = false
        }
    }

    @Synchronized
    private fun saveState() {
        if (!vaultAvailable) return
        runCatching {
            val prefsJson = JSONObject().apply {
                put("version", 2)
                put("selectedLocation", selectedLocationId)
                put("selectedMode", selectedMode)
                put("splitTcp", splitTcp)
                put("blockIpv6", blockIpv6)
                put("lastManualUser", lastManualUsername)
                put("favorites", JSONArray(favoriteLocationIds.toList()))
                put("probes", JSONObject().apply {
                    locationProbes.forEach { (k, pr) ->
                        put(k, JSONObject()
                            .put("ms", pr.millis)
                            .put("checked", pr.checkedAt)
                            .put("method", pr.method)
                            .put("error", pr.error)
                            .put("ips", JSONArray(pr.healthy)))
                    }
                })
            }
            vault.write("ovpn_prefs_v2", prefsJson.toString())

            val allLegacy = customProfiles + providerProfiles
            val catalog = JSONObject().apply {
                put("version", 1)
                put("active", JSONObject.NULL)
                put("selected", selectedProfileId())
                put("auto", false)
                put("accounts", JSONArray())
                put("profiles", JSONArray(allLegacy.map { p ->
                    JSONObject().apply {
                        put("name", p.name)
                        put("config", p.config)
                        put("favorite", p.favorite)
                    }
                }))
            }
            vault.write("catalog", catalog.toString())
        }.onFailure {
            Log.w(TAG, "Vault write failed: ${it.message}")
        }
    }

    private fun selectedProfileId(): String? {
        val loc = resolvedLocations().firstOrNull { it.id == selectedLocationId }
            ?: resolvedLocations().firstOrNull()
        return loc?.profileFor(selectedMode)?.id
    }

    private fun resolvedLocations(): List<OpenVpnLocation> {
        return localLocationsBase.map { base ->
            val fav = base.id in favoriteLocationIds
            val probe = locationProbes[base.id]
            val updatedVariants = base.variants.mapValues { (_, p) ->
                var out = p.withFavorite(fav)
                if (probe != null) out = out.withProbe(probe)
                out
            }
            base.copy(variants = updatedVariants, favorite = fav, probe = probe)
        }
    }

    @Synchronized
    private fun publish() {
        val locations = resolvedLocations()
        val chosenLocation = locations.firstOrNull { it.id == selectedLocationId }?.id
            ?: locations.firstOrNull()?.id
        selectedLocationId = chosenLocation

        val validMode = if (selectedMode == OpenVpnLocation.AUTO_MODE || selectedMode in discoveredModes) {
            selectedMode
        } else {
            OpenVpnLocation.AUTO_MODE
        }
        selectedMode = validMode

        val activeModeProfiles = locations.mapNotNull { it.profileFor(validMode) }
        val allVariantProfiles = locations.flatMap { it.variants.values }
        val combinedProfiles = (activeModeProfiles + customProfiles + providerProfiles + allVariantProfiles)
            .distinctBy { it.id }

        val selProf = locations.firstOrNull { it.id == chosenLocation }?.profileFor(validMode)?.id

        mutable.value = OpenVpnData(
            accounts = emptyList(),
            profiles = combinedProfiles,
            locations = locations,
            availableModes = discoveredModes,
            activeAccount = null,
            selectedProfile = selProf,
            selectedLocation = chosenLocation,
            selectedMode = validMode,
            splitTcp = splitTcp,
            blockIpv6 = blockIpv6,
            lastManualUsername = lastManualUsername,
            autoSwitch = false,
            storageError = false,
        )
    }

    @Synchronized
    fun selectLocation(locationId: String) {
        if (localLocationsBase.none { it.id == locationId }) return
        selectedLocationId = locationId
        saveState()
        publish()
    }

    @Synchronized
    fun selectMode(mode: String) {
        selectedMode = if (mode == OpenVpnLocation.AUTO_MODE || mode in discoveredModes) mode else OpenVpnLocation.AUTO_MODE
        saveState()
        publish()
    }

    @Synchronized
    fun setSplitTcp(enabled: Boolean) {
        splitTcp = enabled
        saveState()
        publish()
    }

    @Synchronized
    fun setBlockIpv6(enabled: Boolean) {
        blockIpv6 = enabled
        saveState()
        publish()
    }

    @Synchronized
    fun toggleFavoriteLocation(locationId: String) {
        favoriteLocationIds = if (locationId in favoriteLocationIds) {
            favoriteLocationIds - locationId
        } else {
            favoriteLocationIds + locationId
        }
        saveState()
        publish()
    }

    @Synchronized
    fun updateLocationProbe(locationId: String, result: ProbeResult) {
        locationProbes = locationProbes + (locationId to result)
        saveState()
        publish()
    }

    @Synchronized
    fun rememberManualUsername(username: String) {
        val clean = username.trim()
        if (clean.isBlank() || clean == lastManualUsername) return
        lastManualUsername = clean
        saveState()
        publish()
    }

    fun locationById(id: String): OpenVpnLocation? = mutable.value.locations.firstOrNull { it.id == id }

    fun findProfileById(id: String): Profile? =
        mutable.value.profiles.firstOrNull { it.id == id }

    // ── Profile management hooks ──────────────────────────────────────────────

    @Synchronized
    fun removeProfile(id: String) {
        check(OpenVpnRuntime.connection.value.profileId != id || !OpenVpnRuntime.connection.value.active)
        providerProfiles = providerProfiles.filterNot { it.id == id }
        customProfiles = customProfiles.filterNot { it.id == id }
        saveState()
        publish()
    }

    @Synchronized
    fun updateProfile(id: String, change: (Profile) -> Profile) {
        val loc = localLocationsBase.firstOrNull { l -> l.variants.values.any { it.id == id } }
        if (loc != null) {
            val sample = loc.variants.values.first { it.id == id }
            val updated = change(sample)
            updated.probe?.let { updateLocationProbe(loc.id, it) }
            if (updated.favorite != (loc.id in favoriteLocationIds)) {
                toggleFavoriteLocation(loc.id)
            }
            return
        }
        customProfiles = customProfiles.map { if (it.id == id) change(it) else it }
        saveState()
        publish()
    }

    fun updateAccount(id: String, update: (Account) -> Account) = Unit
    fun refreshAccount(id: String) = Unit
    fun password(id: String): String = ""

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.stringOrNull(key: String) = if (isNull(key)) null else getString(key)
    private fun JSONObject.longOrNull(key: String) = if (isNull(key)) null else getLong(key)

    companion object {
        private const val TAG = "OpenVpnRepository"
        @Volatile private var instance: OpenVpnRepository? = null

        fun profileIdFor(folder: String, locationId: String) = "ovpn_${folder}_${locationId}"

        fun get(context: Context): OpenVpnRepository = instance ?: synchronized(this) {
            instance ?: OpenVpnRepository(context.applicationContext).also { instance = it }
        }
    }
}