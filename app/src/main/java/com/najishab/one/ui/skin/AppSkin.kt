package com.najishab.one.ui.skin

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager
import com.najishab.one.widget.WidgetMethods

/** The two skins the app ships with. FREEDOM is the default; NEXUS is the previous look, kept whole. */
enum class AppSkin { FREEDOM, NEXUS }

/**
 * Which skin the app wears, mirrored as Compose state the way [com.najishab.one.ui.home.Appearance]
 * mirrors its own settings: chosen from a page deep in Settings, read at the activity root, and a
 * composable cannot observe a SharedPreferences write. Default FREEDOM, for existing installs too.
 */
object SkinPrefs {
    private const val KEY = "app_skin"

    var skin by mutableStateOf(AppSkin.FREEDOM)
        private set

    /** Seeds from disk. Called once, at activity start, next to Appearance.init. */
    fun init(context: Context) {
        skin = when (PreferenceManager.getDefaultSharedPreferences(context).getString(KEY, null)) {
            "nexus" -> AppSkin.NEXUS
            "mosaic" -> AppSkin.MOSAIC
            else -> AppSkin.FREEDOM
        }
    }

    fun set(context: Context, value: AppSkin) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(KEY, value.name.lowercase()).apply()
        skin = value
    }
}

/**
 * Freedom's favourite protocol: the card tapped on Home is the method the next connect uses.
 * Only a real method id is ever stored ([WidgetMethods.isMethod]); anything else reads as null.
 */
object FreedomPrefs {
    private const val KEY = "freedom_preferred_method"
    private const val KEY_RECENT = "freedom_recent_methods"

    var preferredMethod by mutableStateOf<String?>(null)
        private set

    /** The three most recently connected methods, newest first. Drives the Home cards. */
    var recentMethods by mutableStateOf<List<String>>(emptyList())
        private set

    fun init(context: Context) {
        val p = PreferenceManager.getDefaultSharedPreferences(context)
        preferredMethod = p.getString(KEY, null)?.takeIf { WidgetMethods.isMethod(it) }
        recentMethods = p.getString(KEY_RECENT, null)
            ?.split(",")?.map { it.trim() }?.filter { WidgetMethods.isMethod(it) }
            ?.distinct()?.take(3) ?: emptyList()
    }

    fun set(context: Context, id: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(KEY, id).apply()
        preferredMethod = id
    }

    /** Recorded only when a method actually reaches CONNECTED -- a failed attempt is not "used". */
    fun noteUsed(context: Context, id: String) {
        if (!WidgetMethods.isMethod(id)) return
        val next = (listOf(id) + recentMethods).distinct().take(3)
        recentMethods = next
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(KEY_RECENT, next.joinToString(",")).apply()
    }
}

/**
 * The active skin, for surfaces that style themselves per skin. The default reads [SkinPrefs.skin]
 * (Compose state), so a reader recomposes on a switch even where no provider was set.
 */
val LocalAppSkin = compositionLocalOf { SkinPrefs.skin }