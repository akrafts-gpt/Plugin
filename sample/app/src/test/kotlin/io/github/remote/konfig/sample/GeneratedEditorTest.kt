package io.github.remote.konfig.sample

import io.github.remote.konfig.generated.WelcomeConfigRemoteConfigEditor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneratedEditorTest {
    @Test
    fun generatedEditorExposesFields() {
        val editor = WelcomeConfigRemoteConfigEditor()
        val fields = editor.fields()

        assertEquals(2, fields.size)
        assertTrue(fields.any { it.name == "text" && it.type == "kotlin.String" })
        assertTrue(fields.any { it.name == "enabled" && it.type == "kotlin.Boolean" })

        val defaultConfig = editor.defaultInstance()
        val textField = fields.first { it.name == "text" }
        val updated = textField.setter(defaultConfig, "Hi!")

        assertEquals("Hi!", textField.getter(updated))
    }
}
