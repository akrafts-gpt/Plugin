package io.github.remote.konfig.debug

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RemoteConfigDialogContentStateTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Serializable
    private data class Nested(val label: String, val enabled: Boolean)

    @Serializable
    private data class Wrapper(
        val title: String,
        val nested: Nested,
        val items: List<Nested>,
    )

    @Test
    fun nestedObjectIsSerializedForPreview() {
        val state = Wrapper(
            title = "Preview",
            nested = Nested(label = "Child", enabled = true),
            items = listOf(Nested("First", false))
        )

        val preview = fieldJsonValue(json, Wrapper.serializer(), state, "nested")

        assertTrue(preview?.contains("\"label\": \"Child\"") == true)
        assertTrue(preview?.contains("\"enabled\": true") == true)
    }

    @Test
    fun listValueIsSerializedForPreview() {
        val state = Wrapper(
            title = "Preview",
            nested = Nested(label = "Child", enabled = true),
            items = listOf(Nested("First", false))
        )

        val preview = fieldJsonValue(json, Wrapper.serializer(), state, "items")
        val expected = json.encodeToString(ListSerializer(Nested.serializer()), state.items)

        assertEquals(expected, preview)
    }

    @Test
    fun missingFieldReturnsNull() {
        val state = Wrapper(
            title = "Preview",
            nested = Nested(label = "Child", enabled = true),
            items = emptyList()
        )

        val preview = fieldJsonValue(json, Wrapper.serializer(), state, "unknown")

        assertNull(preview)
    }
}
