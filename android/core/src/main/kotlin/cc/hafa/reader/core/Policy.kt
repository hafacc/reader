package cc.hafa.reader.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** The site the policies are on, which their links by path point into. */
const val SITE_URL = "https://reader.hafa.cc"

/** A stretch of a policy's text, a link to [href] when it has one. */
data class PolicyRun(
    /** The text. */
    val text: String,
    /** Where the text links to, in full; empty for plain text. */
    val href: String = "",
)

/** What a policy's text is made of, in order. */
sealed interface PolicyBlock {
    /** The block's text. */
    val runs: List<PolicyRun>

    /** A heading. */
    data class Heading(override val runs: List<PolicyRun>) : PolicyBlock

    /** A paragraph; the date it was last changed when [updated]. */
    data class Paragraph(override val runs: List<PolicyRun>, val updated: Boolean = false) : PolicyBlock

    /** An item of a bulleted list. */
    data class Bullet(override val runs: List<PolicyRun>) : PolicyBlock
}

/**
 * The blocks of a policy written as HTML in `shared/policy`, as the web shows it: headings, paragraphs and list items
 * with their links, resolved against [SITE_URL], and runs of white space as single spaces.
 */
fun parsePolicy(html: String): List<PolicyBlock> =
    Jsoup.parseBodyFragment(html, SITE_URL).body().select("h2, p, li").map { element ->
        val runs = runsOf(element)
        when (element.tagName()) {
            "h2" -> PolicyBlock.Heading(runs)
            "li" -> PolicyBlock.Bullet(runs)
            else -> PolicyBlock.Paragraph(runs, updated = element.hasClass("updated"))
        }
    }

private fun runsOf(element: Element): List<PolicyRun> {
    val runs = mutableListOf<PolicyRun>()
    fun add(text: String, href: String) {
        val last = runs.lastOrNull()
        if (last != null && last.href == href) {
            runs[runs.lastIndex] = last.copy(text = last.text + text)
        } else {
            runs += PolicyRun(text, href)
        }
    }
    fun walk(node: Node, href: String) {
        when (node) {
            is TextNode -> add(node.wholeText.replace(WHITE_SPACE, " "), href)
            is Element -> {
                val link = if (node.tagName() == "a") node.absUrl("href") else href
                node.childNodes().forEach { child -> walk(child, link) }
            }
        }
    }
    element.childNodes().forEach { child -> walk(child, "") }
    if (runs.isNotEmpty()) {
        runs[0] = runs[0].copy(text = runs[0].text.trimStart())
        runs[runs.lastIndex] = runs.last().copy(text = runs.last().text.trimEnd())
    }
    return runs.filter { run -> run.text.isNotEmpty() }
}

private val WHITE_SPACE = Regex("\\s+")
