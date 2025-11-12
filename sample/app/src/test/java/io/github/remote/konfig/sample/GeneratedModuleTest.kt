package io.github.remote.konfig.sample

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class GeneratedModuleTest {
    @Test
    fun generatedModuleAndEditorExist() {
        val module = File("build/generated/ksp/debug/kotlin/io/github/remote/konfig/sample/WelcomeExperienceConfigRemoteConfigModule.kt")
        val editor = File("build/generated/ksp/debug/kotlin/io/github/remote/konfig/generated/WelcomeExperienceConfigRemoteConfigEditor.kt")
        val screen = File("build/generated/ksp/debug/kotlin/io/github/remote/konfig/generated/WelcomeExperienceConfigRemoteConfigScreen.kt")

        assertTrue(module.exists(), "Expected generated module at ${module.path}")
        assertTrue(editor.exists(), "Expected generated editor at ${editor.path}")
        assertTrue(screen.exists(), "Expected generated screen at ${screen.path}")
    }
}
