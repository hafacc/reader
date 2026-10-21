package cc.hafa.reader.core

import java.text.Normalizer

private val MARKS = Regex("\\p{M}")

/** A name with accents dropped and capitals ignored, for its first comparison. */
private fun plainName(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFD).replace(MARKS, "").lowercase()

/**
 * The order of two topic or section names, the same on every platform: by the names with accents and capitals
 * ignored, then with capitals ignored, then as written, each in UTF-16 code-unit order. A locale's collation is not
 * used, since engines order punctuation such as `7-1` and `7.1` apart.
 */
fun compareNames(first: String, second: String): Int {
    val plain = plainName(first).compareTo(plainName(second))
    val lower = first.lowercase().compareTo(second.lowercase())
    return if (plain != 0) plain else if (lower != 0) lower else first.compareTo(second)
}
