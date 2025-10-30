package io.github.remote.konfig.sample

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class GeneratedModuleTest {
    @Test
    fun generatedModuleExists() {
        val generated = File("build/generated/ksp/debug/kotlin/io/github/remote/konfig/sample/WelcomeConfigRemoteConfigModule.kt")
        assertTrue(generated.exists(), "Expected generated module at \${generated.path}")
    }
}
