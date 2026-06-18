package com.example.birdfinder.classify

import android.content.Context
import java.io.BufferedReader

/**
 * Parses BirdNET-style label files where each line is `Scientific_Common`.
 * Some lines are non-bird tags (Dog, Engine, Fireworks, Environmental); they are
 * kept as-is so indices align with model outputs, and surfaced by [isBirdLike].
 */
class Labels private constructor(
    val entries: List<Label>,
) {
    data class Label(
        val scientific: String,
        val common: String,
    ) {
        /** Heuristic: real bird labels are formatted `Genus species_Common Name`. */
        val isBirdLike: Boolean
            get() = scientific.contains(' ') && !scientific.equals(common, ignoreCase = true)
    }

    val size: Int get() = entries.size
    operator fun get(index: Int): Label = entries[index]

    companion object {
        fun fromAssets(context: Context, fileName: String = "labels_en.txt"): Labels {
            val parsed = ArrayList<Label>(6600)
            context.assets.open(fileName).bufferedReader().use { r: BufferedReader ->
                r.lineSequence().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) return@forEach
                    val ix = trimmed.indexOf('_')
                    if (ix <= 0 || ix == trimmed.lastIndex) {
                        // Defensive: treat the whole line as both scientific & common.
                        parsed += Label(trimmed, trimmed)
                    } else {
                        parsed += Label(
                            scientific = trimmed.substring(0, ix),
                            common = trimmed.substring(ix + 1),
                        )
                    }
                }
            }
            return Labels(parsed)
        }
    }
}
