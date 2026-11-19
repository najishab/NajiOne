package com.najishab.one.ui.skin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The two-skin contract, held the way this repo holds its other source contracts (SettingsIndexTest,
 * BackupCoverageTest): by reading the source. Freedom must stay the default, both skins must reach
 * the SAME catalog (so no destination becomes unreachable), Nexus must keep using the shared
 * connect controller, and Freedom must not invent engines the app does not have.
 */
class FreedomSkinContractTest {

    private val main = File("src/main/java")

    private fun src(rel: String): String = File(main, rel).readText()

    @Test
    fun freedomIsTheDefaultSkin() {
        assumeTrue("run from the app module", main.isDirectory)
        val s = src("com/najishab/one/ui/skin/AppSkin.kt")
        assertTrue("SkinPrefs must default to FREEDOM", s.contains("mutableStateOf(AppSkin.FREEDOM)"))
        assertTrue("the pref key must stay app_skin", s.contains("\"app_skin\""))
    }

    @Test
    fun bothSkinsShareOneCatalog() {
        assumeTrue("run from the app module", main.isDirectory)
        val shell = src("com/najishab/one/ui/freedom/FreedomShell.kt")
        assertTrue("Freedom Engines must come from NexusCatalog.methodGroups()", shell.contains("NexusCatalog.methodGroups()"))
        assertTrue("Freedom Tools must come from NexusCatalog.toolGroups()", shell.contains("NexusCatalog.toolGroups()"))
        val dir = src("com/najishab/one/ui/freedom/FreedomDirectory.kt")
        assertTrue("every row must open its destination", dir.contains("onOpen(app)"))
        assertFalse("the catalog must not be filtered by id", dir.contains("it.id !="))
    }

    @Test
    fun appScreenDispatchesBothSkinsAndBackHandlesFreedom() {
        assumeTrue("run from the app module", main.isDirectory)
        val app = src("com/najishab/one/ui/AppScreen.kt")
        assertTrue(app.contains("FreedomShell("))
        assertTrue(app.contains("NexusShell("))
        assertTrue("freedomPage must be saveable state", app.contains("freedomPage"))
        assertTrue("the settings tab must wear the active skin", app.contains("LocalFreedomSkin provides"))
    }

    @Test
    fun nexusHomeUsesTheSharedController() {
        assumeTrue("run from the app module", main.isDirectory)
        val home = src("com/najishab/one/ui/nexus/NexusHome.kt")
        assertTrue("NexusHome must call rememberLinkController", home.contains("rememberLinkController("))
        assertFalse("the connect logic must live in one place only", home.contains("VpnService.prepare"))
    }

    @Test
    fun theSkinRowIsSearchable() {
        assumeTrue("run from the app module", main.isDirectory)
        assertTrue(src("com/najishab/one/ui/settings/SettingsIndex.kt").contains("App skin"))
        assertTrue(src("com/najishab/one/ui/settings/SettingsScreen.kt").contains("SkinPickerPage("))
    }

    @Test
    fun freedomInventsNoEngines() {
        assumeTrue("run from the app module", main.isDirectory)
        val home = src("com/najishab/one/ui/freedom/FreedomHome.kt")
        listOf("Proton VPN", "Windscribe", "GitHub Tunnel", "Telegram Proxy", "Free Configs").forEach {
            assertFalse("the mockup's demo engine '$it' must not be ported", home.contains(it))
        }
    }
}