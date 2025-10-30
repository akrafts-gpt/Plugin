package io.github.remote.konfig.debug

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class EditorFieldTest {
    @Test
    fun setterProducesUpdatedInstance() {
        data class Welcome(val text: String, val enabled: Boolean)

        val field = EditorField<Welcome>(
            name = "text",
            type = "kotlin.String",
            getter = { it.text },
            setter = { config, value -> config.copy(text = value as String) }
        )

        val original = Welcome(text = "Hello", enabled = true)
        val updated = field.setter(original, "Howdy")

        assertEquals("text", field.name)
        assertEquals("kotlin.String", field.type)
        assertEquals("Howdy", field.getter(updated))
        assertSame(true, updated.enabled)
        // Ensure original instance was not mutated
        assertEquals("Hello", original.text)
    }
}
