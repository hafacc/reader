package cc.hafa.reader.core

import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrderAndPicturesTest {
    private fun entry(id: String, published: Long, addedAt: Long, position: Int) = Entry(
        id = id, feedId = "f", guid = id, title = id, url = "", published = published, dated = true, author = "",
        imageUrl = "", excerpt = "", summary = "", categories = emptyList(), read = false, starred = false,
        addedAt = addedAt, position = position,
    )

    @Test
    fun `equal dates go by later sighting, then place in the feed, then id`() {
        val entries = listOf(
            entry("d", 100, 1, 0),
            entry("c", 100, 2, 1),
            entry("b", 100, 2, 0),
            entry("a", 200, 1, 5),
            entry("e", 100, 2, 1),
        )
        assertEquals(listOf("a", "b", "c", "e", "d"), entries.sortedWith(NEWEST_FIRST).map { item -> item.id })
        assertEquals(listOf("d", "e", "c", "b", "a"), entries.sortedWith(NEWEST_FIRST.reversed()).map { item -> item.id })
        assertEquals(entries.sortedWith(NEWEST_FIRST), entries.reversed().sortedWith(NEWEST_FIRST))
    }

    @Test
    fun `the same date, sighting and place fall back to guid and address before id`() {
        fun item(id: String, guid: String, url: String) =
            entry(id, 100, 1, 0).copy(guid = guid, url = url)
        val entries = listOf(item("a", "g2", "u1"), item("b", "g1", "u2"), item("c", "g1", "u1"))
        assertEquals(listOf("c", "b", "a"), entries.sortedWith(NEWEST_FIRST).map { found -> found.id })
    }

    @Test
    fun `a lead image the body already shows is hidden, however its address is written`() {
        val image = "https://i.example/x.png?w=1&#038;h=2"
        assertFalse(showsLeadImage(image, """<p><img src="https://i.example/x.png?w=1&amp;h=2"></p>"""))
        assertFalse(showsLeadImage("https://i.example/y.png", """<picture><source srcset="https://i.example/y.png 2x"></picture>"""))
        assertTrue(showsLeadImage(image, """<p><img src="https://i.example/other.png"></p>"""))
        assertTrue(showsLeadImage("https://i.example/z.png", ""))
        assertFalse(showsLeadImage("", "<p>text</p>"))
    }

    @Test
    fun `a lead image stored as the body picture's largest candidate is hidden`() {
        val body = sanitize(
            """<img width="300" src="/three-300.jpg" srcset="/three-300.jpg 300w, /three-1024.jpg 1024w">""",
            "https://pictures.example.com/wider-body",
        )
        assertFalse(showsLeadImage("https://pictures.example.com/three-1024.jpg", body))
    }

    @Test
    fun `the largest srcset picture is chosen`() {
        assertEquals("https://i.example/l.png", bestSrcsetCandidate("https://i.example/s.png 320w, https://i.example/l.png 1280w, https://i.example/m.png 640w"))
        assertEquals("b.png", bestSrcsetCandidate("a.png 1x, b.png 2x"))
        assertEquals("", bestSrcsetCandidate(""))
    }

    @Test
    fun `a picture's sources that fit every screen are weighed with its srcset`() {
        val body = """
            <picture>
              <source media="(min-width: 900px)" srcset="https://i.example/wide.jpg 2400w">
              <source type="image/jxl" srcset="https://i.example/big.jxl 2000w">
              <source type="image/webp" srcset="https://i.example/big.webp 1600w, https://i.example/small.webp 400w">
              <img src="https://i.example/small.jpg" srcset="https://i.example/medium.jpg 800w">
            </picture>
            <img src="https://i.example/plain.jpg" width="300" srcset="https://i.example/double.jpg 2x">
            <img src="https://i.example/only.jpg">
        """.trimIndent()
        val images = Jsoup.parseBodyFragment(body).select("img")
        assertEquals(
            listOf("https://i.example/big.webp", "https://i.example/double.jpg", "https://i.example/only.jpg"),
            images.map(::pictureAddress),
        )
    }

    @Test
    fun `a picture is sized by its declared width, else by its density`() {
        val body = """
            <img width="300" srcset="https://i.example/a.jpg 1x, https://i.example/a@2x.jpg 2x">
            <img width="100" src="https://i.example/800px.jpg">
            <img srcset="https://i.example/b.jpg 1x, https://i.example/b@2x.jpg 2x">
            <img srcset="https://i.example/c.jpg 640w">
            <img src="https://i.example/d.jpg">
            <img srcset="https://i.example/e.jpg 0x">
        """.trimIndent()
        val choices = Jsoup.parseBodyFragment(body).select("img").map(::pictureChoice)
        assertEquals(
            listOf(
                PictureChoice("https://i.example/a@2x.jpg", 300.0, 2.0),
                PictureChoice("https://i.example/800px.jpg", 100.0, 1.0),
                PictureChoice("https://i.example/b@2x.jpg", 0.0, 2.0),
                PictureChoice("https://i.example/c.jpg", 0.0, 1.0),
                PictureChoice("https://i.example/d.jpg", 0.0, 1.0),
                PictureChoice("https://i.example/e.jpg", 0.0, 1.0),
            ),
            choices,
        )
        assertEquals(listOf(300.0, 100.0, 300.0, 600.0, 600.0, 600.0), choices.map { choice -> choice.displayWidth(600.0) })
    }
}
