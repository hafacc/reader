package cc.hafa.reader.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Whether an article's lead image is shown above its body: only when the
 * body does not already show it, compared after decoding entities in both
 * the stored address and the body's picture addresses.
 */
fun showsLeadImage(imageUrl: String, bodyHtml: String): Boolean {
    val address = decodeHtmlEntities(imageUrl)
    return if (address.isEmpty()) {
        false
    } else {
        val written = address in bodyHtml || address.replace("&", "&amp;") in bodyHtml
        val pictures = Jsoup.parseBodyFragment(bodyHtml).select("img[src], source[srcset], img[srcset]")
            .flatMap { picture -> listOf(picture.attr("src")) + srcsetAddresses(picture.attr("srcset")) }
        !written && pictures.none { picture -> picture == address }
    }
}

// picture types every Android version shows; a `source` of another type may not decode
private val DECODABLE_TYPES = setOf("", "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif")

/** The picture Android loads for an `img`, with what sizes it as a browser would. */
data class PictureChoice(
    /** Picture address; empty when the image has none. */
    val address: String,
    /** The image's `width` attribute in CSS pixels; 0 when it has none. */
    val declaredWidth: Double,
    /** Pixel density of the chosen picture: its `x` descriptor, else 1. */
    val density: Double,
) {
    /**
     * Width to show the picture at in CSS pixels (dp), before fitting it to
     * the screen, given the loaded file's [naturalWidth] in pixels: the
     * declared width when there is one, else the natural width over the
     * density.
     */
    fun displayWidth(naturalWidth: Double): Double =
        if (declaredWidth > 0) declaredWidth else naturalWidth / density
}

/**
 * The picture Android loads for a sanitized `img`, since it cannot pick from
 * a `srcset` as a browser does: the largest candidate among the image's
 * `srcset` and those of its `picture`'s `source`s that apply on every screen
 * in a type Android shows, else its `src`.
 */
fun pictureChoice(image: Element): PictureChoice {
    val parent = image.parent()
    val sources = if (parent != null && parent.normalName() == "picture") {
        parent.children().filter { child ->
            child.normalName() == "source" &&
                !child.hasAttr("media") &&
                child.attr("type").trim().lowercase() in DECODABLE_TYPES
        }
    } else {
        emptyList()
    }
    val candidates = (sources + image).flatMap { picture -> srcsetCandidates(picture.attr("srcset")) }
    val declaredWidth = image.attr("width").trim().toIntOrNull()?.takeIf { width -> width > 0 }?.toDouble() ?: 0.0
    val chosen = largestOf(candidates, declaredWidth)
    return if (chosen != null) {
        val density = candidateSize(chosen.descriptor, declaredWidth).density.takeIf { density -> density > 0 } ?: 1.0
        PictureChoice(chosen.address, declaredWidth, density)
    } else {
        PictureChoice(image.absUrl("src").ifEmpty { image.attr("src") }, declaredWidth, 1.0)
    }
}

/** The address [pictureChoice] picks for a sanitized `img`. */
fun pictureAddress(image: Element): String = pictureChoice(image).address

/** Most pictures fetched ahead for one article, its lead picture included. */
const val PICTURES_PER_ARTICLE = 4

/**
 * The pictures an article shows first, at the addresses the article view
 * loads: its lead picture when the body does not show it, then the body's
 * first pictures (as [pictureAddress] picks them); https only,
 * at most [limit]. [body] must be the sanitized body the article shows.
 */
fun aheadPictures(imageUrl: String, body: String, limit: Int = PICTURES_PER_ARTICLE): List<String> {
    val lead = decodeHtmlEntities(imageUrl).takeIf { address -> showsLeadImage(imageUrl, body) && isSecure(address) }
    val pictures = Jsoup.parseBodyFragment(body).select("img").asSequence()
        .map(::pictureAddress)
        .filter(::isSecure)
    return (listOfNotNull(lead).asSequence() + pictures).take(limit).toList()
}

private fun isSecure(address: String): Boolean = address.startsWith("https://", ignoreCase = true)

/** The articles on either side of an open one. */
data class Neighbors(
    /** The next article in the way the reader is moving; null at the end. */
    val ahead: Entry?,
    /** The article on the other side; null at the end. */
    val behind: Entry?,
)

/** The rows on either side of [openId]; [heading] is 1 when moving down the list, -1 when moving up. */
fun neighborsOf(rows: List<Entry>, openId: String, heading: Int): Neighbors {
    val index = rows.indexOfFirst { row -> row.id == openId }
    return if (index == -1) {
        Neighbors(null, null)
    } else {
        Neighbors(rows.getOrNull(index + heading), rows.getOrNull(index - heading))
    }
}
