package cc.hafa.reader.core

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap

/** One section chip: the words it shows and the place turning it off stores. */
data class SectionChip(
    /** The folders below those every article of the feed shares, or the site. */
    val label: String,
    /** The site and every folder above and at the section, joined by `/`. */
    val place: String,
)

/** A section chip with how many articles it covers. */
data class CountedSection(
    /** The folders below those every article of the feed shares, or the site. */
    val label: String,
    /** The site and every folder above and at the section, joined by `/`. */
    val place: String,
    /** Articles of the view in the section. */
    val count: Int,
)

/** An entry of `offTopics` that holds a turned-off section's place rather than a topic. */
const val SECTION_PREFIX = "section:"

private val MONTHS = setOf(
    "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec",
    "january", "february", "march", "april", "june", "july", "august", "september", "october",
    "november", "december",
)
private val DIGIT = Regex("[0-9]")

/** Where an article sits: its site, then the folders of its address that can name a section. */
private data class Place(val domain: String, val parts: List<String>)

/** The address sections come from: the story's source when the feed names one, else its link. */
fun sectionAddress(entry: FilterEntry): String = entry.sourceUrl.ifEmpty { entry.url }

private fun placeOf(address: String): Place {
    val url = address.toHttpUrlOrNull()
    return if (url == null) {
        Place("", emptyList())
    } else {
        val segments = url.pathSegments.filter { segment -> segment.isNotEmpty() }
        Place(
            url.host.removePrefix("www."),
            segments
                .dropLast(1)
                .map { part -> part.lowercase() }
                .filter { part -> !(DIGIT.containsMatchIn(part) || part in MONTHS) },
        )
    }
}

private fun joined(place: Place): String = (listOf(place.domain) + place.parts).joinToString("/")

/** Addresses kept before the cache starts over. */
private const val PLACE_LIMIT = 100_000

/** Places already worked out, by address, since filters test every article. */
private val places = ConcurrentHashMap<String, String>()

/**
 * Where an article sits, as a turned-off section's place is stored: the host
 * without `www.`, then the folders of its address, leaving out its own name,
 * any with a digit and month names; empty when the address cannot be read.
 */
fun articlePlace(entry: FilterEntry): String {
    val address = sectionAddress(entry)
    return places[address] ?: run {
        val found = placeOf(address)
        val place = if (found.domain.isEmpty()) "" else joined(found)
        if (places.size >= PLACE_LIMIT) {
            places.clear()
        }
        places[address] = place
        place
    }
}

/** Whether [place] is [outer] or lies under it. */
private fun within(place: String, outer: String): Boolean = place == outer || place.startsWith("$outer/")

/** Whether any of the [stored] places holds [place]. */
fun placeHidden(stored: List<String>, place: String): Boolean =
    place.isNotEmpty() && stored.any { outer -> within(place, outer) }

/** The places of the turned-off sections among a view's [offTopics]. */
fun offPlaces(offTopics: List<String>): List<String> =
    offTopics.filter { topic -> topic.startsWith(SECTION_PREFIX) }.map { topic -> topic.removePrefix(SECTION_PREFIX) }

/** Whether a section is off: it or a section above it was turned off. */
fun sectionOff(offTopics: List<String>, place: String): Boolean = placeHidden(offPlaces(offTopics), place)

/**
 * The [offTopics] after tapping a section: an off section comes back on with
 * every section above it, and turning one off clears the sections below it.
 */
fun toggledSection(offTopics: List<String>, place: String): List<String> {
    val off = sectionOff(offTopics, place)
    val kept = offTopics.filter { topic ->
        if (topic.startsWith(SECTION_PREFIX)) {
            val stored = topic.removePrefix(SECTION_PREFIX)
            if (off) !within(place, stored) else !within(stored, place)
        } else {
            true
        }
    }
    return if (off) kept else kept + "$SECTION_PREFIX$place"
}

/** One folder in the tree of a feed's article folders. */
private class FolderNode {
    val articles = mutableListOf<Int>()
    val children = LinkedHashMap<String, FolderNode>()
}

private fun folderTree(paths: List<List<String>>): FolderNode {
    val root = FolderNode()
    for ((index, parts) in paths.withIndex()) {
        root.articles.add(index)
        var node = root
        for (part in parts) {
            node = node.children.getOrPut(part) { FolderNode() }
            node.articles.add(index)
        }
    }
    return root
}

/** Each path cut at its first folder fewer than two articles share under the same parents. */
private fun sharedPaths(paths: List<List<String>>): List<List<String>> {
    val root = folderTree(paths)
    return paths.map { parts ->
        val kept = mutableListOf<String>()
        var node: FolderNode? = root
        for (part in parts) {
            node = node?.children?.get(part)
            if (node == null || node.articles.size < 2) {
                break
            }
            kept.add(part)
        }
        kept
    }
}

/** How many leading folders every path has in common. */
private fun commonDepth(paths: List<List<String>>): Int {
    var depth = 0
    while (paths.isNotEmpty() && paths.all { parts -> parts.size > depth } && paths.map { parts -> parts[depth] }.toSet().size == 1) {
        depth += 1
    }
    return depth
}

/** Section chips by folder, for articles that share one site. */
private fun folderSections(domain: String, paths: List<List<String>>): List<List<SectionChip>> {
    val result = paths.map { mutableListOf<SectionChip>() }
    val common = commonDepth(paths)
    fun walk(node: FolderNode, depth: Int, trail: List<String>) {
        val stops = node.articles.any { index -> paths[index].size == depth }
        val splits = node.children.size > 1 || (stops && node.children.size == 1)
        for ((part, child) in node.children) {
            val here = trail + part
            if (splits && depth >= common) {
                val chip = SectionChip(here.drop(common).joinToString("/"), (listOf(domain) + here).joinToString("/"))
                for (index in child.articles) {
                    result[index].add(chip)
                }
            }
            walk(child, depth + 1, here)
        }
    }
    walk(folderTree(paths), 0, emptyList())
    return result
}

/**
 * Each article's section chips, outermost first, worked out from all the
 * articles of one feed.
 *
 * When the articles' addresses ([sectionAddress]) span more than one site,
 * each article's only section is its site. Otherwise sections come from the
 * folders of the addresses ([articlePlace]): a folder counts only when two
 * articles have it under the same parents, folders every article shares are
 * skipped, and each folder that splits the articles gives a chip, so
 * `/us/politics/` gives `us` and `us/politics`.
 */
fun feedSections(entries: List<FilterEntry>): List<List<SectionChip>> {
    val found = entries.map { entry -> placeOf(sectionAddress(entry)) }
    val domains = found.map { place -> place.domain }.filter { domain -> domain.isNotEmpty() }.toSet()
    return if (domains.size > 1) {
        found.map { place -> if (place.domain.isEmpty()) emptyList() else listOf(SectionChip(place.domain, place.domain)) }
    } else {
        val domain = domains.firstOrNull() ?: ""
        val kept = found.withIndex().filter { (_, place) -> place.domain.isNotEmpty() }
        val chips = folderSections(domain, sharedPaths(kept.map { (_, place) -> place.parts }))
        val result = MutableList(found.size) { emptyList<SectionChip>() }
        for ((position, item) in kept.withIndex()) {
            result[item.index] = chips[position]
        }
        result
    }
}

/** The shorter label, then the first in code-unit order, so every device names a place alike. */
private fun preferredLabel(first: String, second: String): String =
    if (second.length < first.length || (second.length == first.length && second < first)) second else first

/** The nearest section above [place] among the [counted] ones. */
private fun parentPlace(place: String, counted: Map<String, CountedSection>): String? {
    var outer = place
    var parent: String? = null
    while (parent == null && '/' in outer) {
        outer = outer.substringBeforeLast('/')
        parent = outer.takeIf { candidate -> candidate in counted }
    }
    return parent
}

/**
 * A view's section chips with how many of its articles each covers, from
 * each article's chips ([feedSections]): the most articles first, then by
 * name, each followed by the sections under it in the same order.
 */
fun sectionCounts(articles: Collection<List<SectionChip>>): List<CountedSection> {
    val counted = LinkedHashMap<String, CountedSection>()
    for (chips in articles) {
        for (chip in chips.associateBy { chip -> chip.place }.values) {
            val known = counted[chip.place]
            counted[chip.place] = CountedSection(
                if (known == null) chip.label else preferredLabel(known.label, chip.label),
                chip.place,
                (known?.count ?: 0) + 1,
            )
        }
    }
    val children = counted.values.groupBy { section -> parentPlace(section.place, counted) }
    val ordered = mutableListOf<CountedSection>()
    fun add(parent: String?) {
        val sorted = (children[parent] ?: emptyList()).sortedWith { first, second ->
            val byCount = second.count - first.count
            if (byCount != 0) byCount else compareNames(first.label, second.label)
        }
        for (section in sorted) {
            ordered.add(section)
            add(section.place)
        }
    }
    add(null)
    return ordered
}
