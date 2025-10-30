package io.github.remote.konfig.sample

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class GeneratedModuleTest {
    @Test
    fun generatedModuleAndEditorExist() {
        val module = File("build/generated/ksp/debug/kotlin/io/github/remote/konfig/sample/WelcomeConfigRemoteConfigModule.kt")
        val editor = File("build/generated/ksp/debug/kotlin/io/github/remote/konfig/generated/WelcomeConfigRemoteConfigEditor.kt")

        assertTrue(module.exists(), "Expected generated module at ${module.path}")
        assertTrue(editor.exists(), "Expected generated editor at ${editor.path}")
    }
}
