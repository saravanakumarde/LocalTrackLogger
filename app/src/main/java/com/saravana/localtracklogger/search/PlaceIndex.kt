package com.saravana.localtracklogger.search

import com.saravana.localtracklogger.data.haversineMeters
import java.text.Normalizer

data class PlaceHit(
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double?
)

private val DIACRITICS = Regex("\\p{Mn}+")
private val SEPARATORS = Regex("[\\p{Punct}\\s]+")

/** Lower-cases, strips accents (ü to u, ß to ss) and unifies separators so "Fürth" matches "furth". */
fun normalizePlaceName(text: String): String {
    var plain = true
    for (c in text) {
        if (!(c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == ' ')) { plain = false; break }
    }
    if (plain && !text.contains("  ")) return text.trim().lowercase() // fast path: most names

    val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
    return DIACRITICS.replace(decomposed, "")
        .replace("ß", "ss").replace("ø", "o").replace("æ", "ae").replace("ł", "l").replace("đ", "d")
        .let { SEPARATORS.replace(it, " ") }
        .trim()
}

/** Local and English spellings of well-known cities, so both "München" and "Munich" are found. */
private val ALIAS_PAIRS = listOf(
    "munchen" to "munich", "koln" to "cologne", "nurnberg" to "nuremberg", "wien" to "vienna",
    "praha" to "prague", "roma" to "rome", "firenze" to "florence", "venezia" to "venice",
    "milano" to "milan", "napoli" to "naples", "torino" to "turin", "genova" to "genoa",
    "geneve" to "geneva", "genf" to "geneva", "bruxelles" to "brussels", "brussel" to "brussels",
    "warszawa" to "warsaw", "lisboa" to "lisbon", "kobenhavn" to "copenhagen", "athina" to "athens",
    "moskva" to "moscow", "braunschweig" to "brunswick", "bozen" to "bolzano", "brixen" to "bressanone",
    "meran" to "merano", "gent" to "ghent", "antwerpen" to "antwerp", "den haag" to "the hague",
    "sankt petersburg" to "saint petersburg", "frankfurt" to "frankfurt am main", "regensburg" to "ratisbon"
)

private val ALIASES: Map<String, List<String>> = buildMap<String, MutableList<String>> {
    ALIAS_PAIRS.forEach { (a, b) ->
        getOrPut(a) { mutableListOf() }.add(b)
        getOrPut(b) { mutableListOf() }.add(a)
    }
}

/**
 * In-memory offline place index. [lines] are "name<TAB>lat<TAB>lon<TAB>country".
 * Results rank by match quality (exact, prefix, word start, contains), then by distance from
 * the given reference point, so "Lindau" finds the one nearest to what you are looking at.
 */
class PlaceIndex(lines: Sequence<String>) {
    constructor(lines: List<String>) : this(lines.asSequence())

    private val names = ArrayList<String>()
    private val norms = ArrayList<String>()
    private val countries = ArrayList<String>()
    private var lats = DoubleArray(1024)
    private var lons = DoubleArray(1024)
    private var count = 0

    init {
        val codes = HashMap<String, String>()
        for (line in lines) {
            val parts = line.split('\t')
            if (parts.size < 4) continue
            val lat = parts[1].toDoubleOrNull() ?: continue
            val lon = parts[2].toDoubleOrNull() ?: continue
            if (count == lats.size) {
                lats = lats.copyOf(count * 2)
                lons = lons.copyOf(count * 2)
            }
            lats[count] = lat
            lons[count] = lon
            names.add(parts[0])
            norms.add(normalizePlaceName(parts[0]))
            countries.add(codes.getOrPut(parts[3]) { parts[3] })
            count++
        }
        names.trimToSize()
        norms.trimToSize()
        countries.trimToSize()
    }

    val size: Int get() = count

    fun search(query: String, nearLat: Double? = null, nearLon: Double? = null, limit: Int = 5): List<PlaceHit> {
        val base = normalizePlaceName(query)
        if (base.length < 2) return emptyList()
        val variants = listOf(base) + (ALIASES[base] ?: emptyList())

        class Match(val index: Int, val score: Int, val distance: Double?)
        val matches = ArrayList<Match>()
        for (i in norms.indices) {
            val norm = norms[i]
            var best = -1
            for (v in variants) {
                val s = score(norm, v)
                if (s >= 0 && (best < 0 || s < best)) best = s
            }
            if (best < 0) continue
            val distance = if (nearLat != null && nearLon != null) haversineMeters(nearLat, nearLon, lats[i], lons[i]) else null
            matches.add(Match(i, best, distance))
        }
        return matches
            .sortedWith(
                compareBy<Match> { it.score }
                    .thenBy { it.distance ?: 0.0 }
                    .thenBy { norms[it.index].length }
            )
            .take(limit)
            .map { PlaceHit(names[it.index], countries[it.index], lats[it.index], lons[it.index], it.distance?.div(1000)) }
    }

    private fun score(norm: String, q: String): Int = when {
        norm == q -> 0
        norm.startsWith(q) -> 1
        norm.contains(" $q") -> 2
        norm.contains(q) -> 3
        else -> -1
    }
}
