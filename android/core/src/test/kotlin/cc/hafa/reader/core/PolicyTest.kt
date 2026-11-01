package cc.hafa.reader.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolicyTest {
    private fun policy(name: String): List<PolicyBlock> =
        parsePolicy(checkNotNull(javaClass.getResource("/$name.html")).readText())

    @Test
    fun readsTheTermsAsTheWebShowsThem() {
        val terms = policy("terms")
        assertEquals(PolicyBlock.Paragraph(listOf(PolicyRun("Last updated October 12, 2026")), updated = true), terms[0])
        val body = terms[1].runs
        assertTrue(body.first().text.startsWith("Reader is a free app for reading the feeds you choose. The feeds"))
        assertEquals(PolicyRun("privacy policy", "$SITE_URL/privacy"), body[1])
        assertEquals(PolicyRun("support@reader.hafa.cc", "mailto:support@reader.hafa.cc"), body[3])
        assertEquals(PolicyRun("contact page", "$SITE_URL/contact"), body[5])
        assertEquals(PolicyRun("."), body.last())
    }

    @Test
    fun readsThePrivacyPolicysHeadingsAndLists() {
        val privacy = policy("privacy")
        assertEquals(PolicyBlock.Heading(listOf(PolicyRun("What Reader reads"))), privacy[2])
        assertTrue(privacy[3] is PolicyBlock.Bullet)
        val links = privacy.flatMap { block -> block.runs }.map { run -> run.href }.filter { href -> href.isNotEmpty() }
        assertTrue("$SITE_URL/delete" in links)
        assertTrue("https://policies.google.com/privacy" in links)
        assertTrue(privacy.all { block -> block.runs.all { run -> "  " !in run.text && "\n" !in run.text } })
    }
}
