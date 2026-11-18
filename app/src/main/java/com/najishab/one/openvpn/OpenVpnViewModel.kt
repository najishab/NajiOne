
package com.najishab.one.openvpn

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class CredentialPromptReason {
    GITHUB_UNAVAILABLE,
    AUTH_FAILED,
    MANUAL_ENTRY,
}

data class CredentialPrompt(
    val locationId: String,
    val transportMode: String,
    val reason: CredentialPromptReason,
    val prefillUsername: String = "",
)

class OpenVpnViewModel(app: Application) : AndroidViewModel(app) {
    private val state = MutableStateFlow<OpenVpnData?>(null)
    val data = state.asStateFlow()

    val message = MutableStateFlow<String?>(null)
    val fetchingCredentials = MutableStateFlow(false)
    val credentialPrompt = MutableStateFlow<CredentialPrompt?>(null)
    val testing = MutableStateFlow<Set<String>>(emptySet())
    val testTotal = MutableStateFlow(0)

    private var probes: Job? = null
    private var connectJob: Job? = null
    private var lastAttemptedUsername: String = ""
    private var lastHandledAuthErrorKey: String? = null

    private val repo get() = OpenVpnRepository.get(getApplication())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repo.data.collect { state.value = it }
        }
        viewModelScope.launch {
            OpenVpnRuntime.connection.collect { conn ->
                if (conn.phase == ConnectionPhase.ERROR && conn.error == "AUTH_FAILED") {
                    val key = "${conn.locationId}:${conn.profileId}:${conn.activeProtocol}:${conn.usingManualCredentials}:${System.currentTimeMillis() / 1000}"
                    if (lastHandledAuthErrorKey != key) {
                        lastHandledAuthErrorKey = key
                        val locId = conn.locationId ?: state.value?.selectedLocation ?: return@collect
                        val mode = state.value?.selectedMode ?: OpenVpnLocation.AUTO_MODE
                        credentialPrompt.value = CredentialPrompt(
                            locationId = locId,
                            transportMode = mode,
                            reason = CredentialPromptReason.AUTH_FAILED,
                            prefillUsername = lastAttemptedUsername.ifBlank { state.value?.lastManualUsername.orEmpty() },
                        )
                    }
                }
            }
        }
    }

    fun selectLocation(locationId: String) {
        viewModelScope.launch(Dispatchers.IO) { repo.selectLocation(locationId) }
    }

    fun selectMode(mode: String) {
        viewModelScope.launch(Dispatchers.IO) { repo.selectMode(mode) }
    }

    fun setSplitTcp(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { repo.setSplitTcp(enabled) }
    }

    fun setBlockIpv6(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { repo.setBlockIpv6(enabled) }
    }

    fun toggleFavorite(locationId: String) {
        viewModelScope.launch(Dispatchers.IO) { repo.toggleFavoriteLocation(locationId) }
    }

    /**
     * Step 1: Fetch username & password from GitHub DATA.txt.
     * Step 2: If valid -> start OpenVPN. If unavailable/empty/invalid -> show manual credentials popup.
     */
    fun connectWithGitHub(context: Context, locationId: String, modeOverride: String? = null) {
        cancelTests()
        connectJob?.cancel()
        message.value = null
        val d = state.value ?: return
        val mode = modeOverride ?: d.selectedMode

        connectJob = viewModelScope.launch {
            fetchingCredentials.value = true
            val result = withContext(Dispatchers.IO) { OpenVpnCredentials.fetchFromGitHub() }
            fetchingCredentials.value = false

            when (result) {
                is CredentialsFetchResult.Ok -> {
                    val creds = result.credentials
                    lastAttemptedUsername = creds.username
                    lastHandledAuthErrorKey = null
                    OpenVpnRuntime.connectLocation(
                        context = context,
                        locationId = locationId,
                        transportMode = mode,
                        username = creds.username,
                        password = creds.password,
                        manualCredentials = false,
                        splitTcp = d.splitTcp,
                        blockIpv6 = d.blockIpv6,
                    )
                }
                is CredentialsFetchResult.Failed -> {
                    credentialPrompt.value = CredentialPrompt(
                        locationId = locationId,
                        transportMode = mode,
                        reason = CredentialPromptReason.GITHUB_UNAVAILABLE,
                        prefillUsername = d.lastManualUsername,
                    )
                }
            }
        }
    }

    fun connectWithManual(context: Context, locationId: String, mode: String, username: String, password: String) {
        val cleanUser = username.trim()
        if (cleanUser.isBlank() || password.isEmpty()) return
        credentialPrompt.value = null
        message.value = null
        lastAttemptedUsername = cleanUser
        lastHandledAuthErrorKey = null
        val splitTcp = state.value?.splitTcp ?: true
        val blockIpv6 = state.value?.blockIpv6 ?: true
        viewModelScope.launch(Dispatchers.IO) {
            repo.rememberManualUsername(cleanUser)
        }
        OpenVpnRuntime.connectLocation(
            context = context,
            locationId = locationId,
            transportMode = mode,
            username = cleanUser,
            password = password,
            manualCredentials = true,
            splitTcp = splitTcp,
            blockIpv6 = blockIpv6,
        )
    }

    fun cancelConnecting(context: Context) {
        connectJob?.cancel()
        fetchingCredentials.value = false
        OpenVpnRuntime.disconnect(context)
    }

    fun openManualPrompt(locationId: String, mode: String) {
        credentialPrompt.value = CredentialPrompt(
            locationId = locationId,
            transportMode = mode,
            reason = CredentialPromptReason.MANUAL_ENTRY,
            prefillUsername = lastAttemptedUsername.ifBlank { state.value?.lastManualUsername.orEmpty() },
        )
    }

    fun dismissCredentialPrompt() {
        credentialPrompt.value = null
    }

    fun testAll(locations: List<OpenVpnLocation>, selectedMode: String) {
        if (probes?.isActive == true) return
        testing.value = locations.map { it.id }.toSet()
        testTotal.value = testing.value.size
        probes = viewModelScope.launch {
            val semaphore = Semaphore(8)
            try {
                coroutineScope {
                    locations.map { loc ->
                        launch {
                            semaphore.withPermit {
                                val result = OpenVpnLatency.measureLocation(loc, selectedMode)
                                withContext(Dispatchers.IO) { repo.updateLocationProbe(loc.id, result) }
                                testing.value = testing.value - loc.id
                            }
                        }
                    }.joinAll()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message.value = "TEST_FAILED"
            } finally {
                testing.value = emptySet()
            }
        }
    }

    fun cancelTests() {
        probes?.cancel()
    }
}