package cc.hafa.reader.core

private val WIDTH_DESCRIPTOR = Regex("^(\\d+)w$", RegexOption.IGNORE_CASE)
private val DENSITY_DESCRIPTOR = Regex("^(\\d+(?:\\.\\d+)?|\\.\\d+)x$", RegexOption.IGNORE_CASE)
private val WHITESPACE = Regex("\\s+")

/** One picture a `srcset` offers: its address and its width or density descriptor. */
data class SrcsetCandidate(
    /** Picture address, which may hold commas (as `data:` addresses do). */
    val address: String,
    /** Width (`640w`) or density (`2x`); empty when none is given. */
    val descriptor: String,
)

/**
 * The candidates of a `srcset`, read as browsers do: an address is a run of
 * non-space characters (a trailing comma ends it), then an optional
 * descriptor up to the next comma.
 */
fun srcsetCandidates(srcset: String): List<SrcsetCandidate> {
    val found = mutableListOf<SrcsetCandidate>()
    var index = 0
    while (index < srcset.length) {
        while (index < srcset.length && (srcset[index].isWhitespace() || srcset[index] == ',')) {
            index += 1
        }
        val start = index
        while (index < srcset.length && !srcset[index].isWhitespace()) {
            index += 1
        }
        var address = srcset.substring(start, index)
        var descriptor = ""
        if (address.endsWith(",")) {
            address = address.trimEnd(',')
        } else {
            val end = srcset.indexOf(',', index).let { comma -> if (comma == -1) srcset.length else comma }
            descriptor = srcset.substring(index, end).trim()
            index = end
        }
        if (address.isNotEmpty()) {
            found.add(SrcsetCandidate(address, descriptor))
        }
    }
    return found
}

/** A `srcset` written back from its candidates. */
fun joinSrcset(candidates: List<SrcsetCandidate>): String =
    candidates.joinToString(", ") { candidate -> listOf(candidate.address, candidate.descriptor).filter(String::isNotEmpty).joinToString(" ") }

/** The addresses a `srcset` lists. */
fun srcsetAddresses(srcset: String): List<String> = srcsetCandidates(srcset).map { candidate -> candidate.address }

/** The largest picture a `srcset` offers, by its width, then its density; empty if none. */
fun bestSrcsetCandidate(srcset: String): String = largestCandidate(srcsetCandidates(srcset))

/**
 * The largest of [candidates] that loads from the web, by width, then
 * density, with [declaredWidth] (0 when unknown) turning densities into
 * widths; empty if none.
 */
fun largestCandidate(candidates: List<SrcsetCandidate>, declaredWidth: Double = 0.0): String =
    largestOf(candidates, declaredWidth)?.address ?: ""

/** The candidate [largestCandidate] picks; null if none loads from the web. */
fun largestOf(candidates: List<SrcsetCandidate>, declaredWidth: Double = 0.0): SrcsetCandidate? {
    var chosen: SrcsetCandidate? = null
    var chosenSize = CandidateSize(0.0, 0.0)
    for (candidate in candidates.filter { candidate -> !candidate.address.startsWith("data:") }) {
        val size = candidateSize(candidate.descriptor, declaredWidth)
        if (chosen == null || isLarger(size, chosenSize)) {
            chosen = candidate
            chosenSize = size
        }
    }
    return chosen
}

/** How large a picture is: its width in pixels when known, and its pixel density. */
data class CandidateSize(
    /** Width in pixels; 0 when neither a width nor a density with a known width gives it. */
    val width: Double,
    /** Pixel density, 1 unless an `x` descriptor says otherwise. */
    val density: Double,
)

/**
 * The size a candidate's [descriptors] give, with [declaredWidth] (the
 * image's `width` attribute, 0 when unknown) turning a density into a width.
 */
fun candidateSize(descriptors: String, declaredWidth: Double): CandidateSize {
    var width = 0.0
    var density = 1.0
    for (token in descriptors.split(WHITESPACE)) {
        val widthMatch = WIDTH_DESCRIPTOR.find(token)
        val densityMatch = DENSITY_DESCRIPTOR.find(token)
        if (widthMatch != null) {
            width = widthMatch.groupValues[1].toDouble()
        } else if (densityMatch != null) {
            density = densityMatch.groupValues[1].toDouble()
        }
    }
    return CandidateSize(if (width != 0.0) width else density * declaredWidth, density)
}

/** Whether a picture of size [next] is larger than one of size [current]: by width, then by density. */
fun isLarger(next: CandidateSize, current: CandidateSize): Boolean =
    if (next.width == current.width) next.density > current.density else next.width > current.width
