package io.github.remote.konfig.sample

import io.github.remote.konfig.debug.BooleanFieldEditor
import io.github.remote.konfig.debug.StringFieldEditor
import io.github.remote.konfig.generated.WelcomeExperienceConfigRemoteConfigEditor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneratedEditorTest {
    @Test
    fun generatedEditorExposesFields() {
        val editor = WelcomeExperienceConfigRemoteConfigEditor()
        val fields = editor.fields()

        assertEquals(2, fields.size)
        assertTrue(fields.any { it.label == "Text" && it is StringFieldEditor })
        assertTrue(fields.any { it.label == "Enabled" && it is BooleanFieldEditor })

        val defaultConfig = editor.defaultInstance()
        val textField = fields.filterIsInstance<StringFieldEditor>().first { it.label == "Text" }
        val updated = textField.setter(defaultConfig, "Hi!")

        assertEquals("Hi!", textField.getter(updated))
    }
}
