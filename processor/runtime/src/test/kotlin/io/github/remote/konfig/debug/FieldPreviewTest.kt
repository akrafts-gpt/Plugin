package io.github.remote.konfig.debug

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class FieldPreviewTest {

    private val json = Json { prettyPrint = true }

    @Test
    fun returnsNullWhenFieldMissing() {
        val state = SampleConfig(title = "Title", enabled = true, nested = SampleNested("value", 7))

        val preview = buildFieldJsonPreview(
            fieldName = "unknown",
            state = state,
            serializer = SampleConfig.serializer(),
            json = json
        )

        assertNull(preview)
    }

    @Test
    fun returnsPrettyJsonForNestedObject() {
        val state = SampleConfig(title = "Title", enabled = true, nested = SampleNested("value", 7))

        val preview = buildFieldJsonPreview(
            fieldName = "nested",
            state = state,
            serializer = SampleConfig.serializer(),
            json = json
        )

        val expected = """
            {
                "text": "value",
                "count": 7
            }
        """.trimIndent()

        assertEquals(expected, preview)
    }

    @Test
    fun returnsRawStringForPrimitive() {
        val state = SampleConfig(title = "Title", enabled = true, nested = SampleNested("value", 7))

        val preview = buildFieldJsonPreview(
            fieldName = "title",
            state = state,
            serializer = SampleConfig.serializer(),
            json = json
        )

        assertEquals("Title", preview)
    }

    @Serializable
    private data class SampleConfig(
        val title: String,
        val enabled: Boolean,
        val nested: SampleNested,
    )

    @Serializable
    private data class SampleNested(
        val text: String,
        val count: Int,
    )
}
