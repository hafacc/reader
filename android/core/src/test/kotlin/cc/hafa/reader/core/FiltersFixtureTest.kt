package cc.hafa.reader.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** The sections, topic groups, hiding and toggling in `shared/fixtures/filters.json`, which every client's tests read. */
class FiltersFixtureTest {
    private val fixture: JsonObject =
        Json.parseToJsonElement(
            checkNotNull(javaClass.classLoader.getResource("filters.json")) { "missing fixture filters.json" }.readText(),
        ).jsonObject

    private data class Article(
        override val title: String,
        override val summary: String,
        override val categories: List<String>,
        override val categorySchemes: List<String>,
        override val url: String,
        override val sourceUrl: String,
    ) : FilterEntry

    private fun JsonElement.text(name: String): String = jsonObject[name]?.jsonPrimitive?.content ?: ""

    private fun JsonElement.texts(name: String): List<String> =
        jsonObject[name]?.jsonArray?.map { item -> item.jsonPrimitive.content } ?: emptyList()

    private fun JsonElement.items(name: String): List<JsonElement> = jsonObject.getValue(name).jsonArray.toList()

    private fun article(item: JsonElement): Article =
        Article(
            item.text("title"),
            item.text("summary"),
            item.texts("categories"),
            item.texts("categorySchemes"),
            item.text("url"),
            item.text("sourceUrl"),
        )

    private fun chip(item: JsonElement): SectionChip = SectionChip(item.text("label"), item.text("place"))

    @Test
    fun `sections and topic groups match the fixture`() {
        for (case in fixture.items("feeds")) {
            val name = case.text("name")
            val entries = case.items("input").map(::article)
            val output = case.jsonObject.getValue("output")
            val sections = feedSections(entries)
            assertEquals(output.items("sections").map { article -> article.jsonArray.map(::chip) }, sections, name)
            assertEquals(
                output.items("counts").map { item ->
                    CountedSection(item.text("label"), item.text("place"), item.jsonObject.getValue("count").jsonPrimitive.int)
                },
                sectionCounts(sections),
                name,
            )
            val groups = topicGroups(entries)
            assertEquals(
                output.items("groups").map { group ->
                    group.jsonArray.map { item -> TopicCount(item.text("topic"), item.jsonObject.getValue("count").jsonPrimitive.int) }
                },
                groups,
                name,
            )
            val order = GroupOrder(groups)
            assertEquals(
                output.items("articleTopics").map { rows -> rows.jsonArray.map { row -> row.jsonArray.map { topic -> topic.jsonPrimitive.content } } },
                entries.map { entry -> order.articleRows(entry.categories) },
                name,
            )
        }
    }

    @Test
    fun `names sort as the fixture says`() {
        val names = fixture.getValue("names")
        assertEquals(names.texts("output"), names.texts("input").sortedWith(::compareNames))
    }

    @Test
    fun `article places match the fixture`() {
        for (case in fixture.items("places")) {
            val input = case.jsonObject.getValue("input")
            val place = articlePlace(Article("", "", emptyList(), emptyList(), input.text("url"), input.text("sourceUrl")))
            assertEquals(case.text("output"), place, input.text("url"))
        }
    }

    @Test
    fun `turned-off sections and topics hide as the fixture says`() {
        for (case in fixture.items("hides")) {
            val input = case.jsonObject.getValue("input")
            val settings = ViewSettings(offTopics = input.texts("offTopics"))
            val entry = article(input.jsonObject.getValue("entry"))
            assertEquals(case.jsonObject.getValue("output").jsonPrimitive.boolean, !passes(settings, entry), settings.toString())
        }
    }

    @Test
    fun `tapping a section changes the stored places as the fixture says`() {
        for (case in fixture.items("toggles")) {
            val input = case.jsonObject.getValue("input")
            val output = case.jsonObject.getValue("output")
            val offTopics = input.texts("offTopics")
            val place = input.text("place")
            assertEquals(output.jsonObject.getValue("off").jsonPrimitive.boolean, sectionOff(offTopics, place), place)
            assertEquals(output.texts("offTopics"), toggledSection(offTopics, place), place)
        }
    }
}
