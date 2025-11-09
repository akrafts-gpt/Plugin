package io.github.remote.konfig.debug

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorFieldTest {
    @Test
    fun setterProducesUpdatedInstance() {
        data class Welcome(val text: String, val enabled: Boolean)

        val field = StringFieldEditor(
            label = "Text",
            getter = { (it as Welcome).text },
            setter = { config, value -> (config as Welcome).copy(text = value as String) }
        )

        val original = Welcome(text = "Hello", enabled = true)
        val updated = field.setter(original, "Howdy")

        assertEquals("Text", field.label)
        assertEquals("Howdy", field.getter(updated))
        assertTrue(updated.enabled)
        // Ensure original instance was not mutated
        assertEquals("Hello", original.text)
    }
}
