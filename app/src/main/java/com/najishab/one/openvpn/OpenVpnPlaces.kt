package com.najishab.one.openvpn

import com.najishab.one.utils.GeoLabel
import java.util.Locale

object OpenVpnPlaces {
    data class Place(
        val code: String?,
        val flag: String,
        val name: String,
        val countryName: String = name,
        val cityName: String? = null,
    )

    private val cache = HashMap<String, Place>()

    private val CITY_FA = mapOf(
        "melbourne" to "ملبورن",
        "sydney" to "سیدنی",
        "vienna" to "وین",
        "saopaulo" to "سائوپائولو",
        "sao paulo" to "سائوپائولو",
        "montreal" to "مونترال",
        "toronto" to "تورنتو",
        "copenhagen" to "کپنهاگ",
        "paris" to "پاریس",
        "frankfurt" to "فرانکفورت",
        "athens" to "آتن",
        "hongkong" to "هنگ‌کنگ",
        "hong kong" to "هنگ‌کنگ",
        "bangalore" to "بنگلور",
        "mumbai" to "بمبئی",
        "dublin" to "دوبلین",
        "milan" to "میلان",
        "tokyo" to "توکیو",
        "amsterdam" to "آمستردام",
        "auckland" to "اوکلند",
        "oslo" to "اسلو",
        "singapore" to "سنگاپور",
        "johannesburg" to "ژوهانسبورگ",
        "madrid" to "مادرید",
        "stockholm" to "استکهلم",
        "zurich" to "زوریخ",
        "istanbul" to "استانبول",
        "dubai" to "دبی",
        "london" to "لندن",
        "chicago" to "شیکاگو",
        "las vegas" to "لاس‌وگاس",
        "los angeles" to "لس‌آنجلس",
        "miami" to "میامی",
        "new york" to "نیویورک",
        "orlando" to "اورلاندو",
    )

    @Synchronized
    fun ofLocation(id: String, displayName: String, remotes: List<Remote>, fa: Boolean): Place {
        return cache.getOrPut("loc:$id:$fa") {
            resolvePlace(displayName, remotes, fa)
        }
    }

    @Synchronized
    fun of(profile: Profile, fa: Boolean): Place = cache.getOrPut("prof:${profile.id}:$fa") {
        resolvePlace(profile.name, profile.remotes, fa)
    }

    private fun resolvePlace(rawName: String, remotes: List<Remote>, fa: Boolean): Place {
        val bare = rawName.removeSuffix(".txt").removeSuffix(".ovpn").trim()
        val locale = if (fa) Locale("fa") else Locale.ENGLISH
        val country = GeoLabel.countryFromText(listOf(bare), locale)
            ?: hostCode(remotes)?.let { GeoLabel.countryFromCode(it, locale) }

        val rawCity = if ('-' in bare) bare.substringAfter('-').trim() else null
        val cityFormatted = rawCity?.let { formatCity(it, fa) }?.takeIf {
            country == null || !it.equals(country.name, ignoreCase = true)
        }

        if (country == null) {
            val clean = bare.replace('_', ' ').replace('-', ' ')
            return Place(null, "🌐", clean.ifBlank { rawName }, clean.ifBlank { rawName }, null)
        }

        val full = if (cityFormatted != null) "${country.name} · $cityFormatted" else country.name
        return Place(country.code, country.flag, full, country.name, cityFormatted)
    }

    private fun formatCity(raw: String, fa: Boolean): String {
        val m = Regex("^(.*?)(\\d+)$").find(raw.trim())
        val baseRaw = (m?.groupValues?.get(1) ?: raw).replace('_', ' ').trim()
        val num = m?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }

        val base = if (fa) {
            CITY_FA[baseRaw.lowercase(Locale.US)]
                ?: CITY_FA[baseRaw.lowercase(Locale.US).replace(" ", "")]
                ?: baseRaw
        } else {
            // Insert space in camelCase like SaoPaulo -> Sao Paulo, HongKong -> Hong Kong
            baseRaw.replace(Regex("([a-z])([A-Z])"), "$1 $2")
        }

        return if (num != null) {
            val n = if (fa) num.map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("") else num
            "$base $n"
        } else {
            base
        }
    }

    private fun hostCode(remotes: List<Remote>): String? =
        remotes.firstOrNull()?.host?.substringBefore('.')?.takeIf { it.length == 2 }?.uppercase(Locale.US)
}