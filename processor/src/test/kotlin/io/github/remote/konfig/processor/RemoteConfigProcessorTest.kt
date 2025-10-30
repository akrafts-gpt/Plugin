package io.github.remote.konfig.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteConfigProcessorTest {
    @Test
    fun generatesModuleAndEditor() {
        val configSource = SourceFile.kotlin(
            name = "WelcomeConfig.kt",
            contents = """
                package test

                import io.github.remote.konfig.HiltRemoteConfig
                import kotlinx.serialization.Serializable

                @Serializable
                @HiltRemoteConfig("welcome")
                data class WelcomeConfig(val text: String)
            """.trimIndent()
        )
        val serializableStub = SourceFile.kotlin(
            name = "Serializable.kt",
            contents = """
                package kotlinx.serialization

                annotation class Serializable
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(configSource, serializableStub)
            inheritClassPath = true
            symbolProcessorProviders = listOf(RemoteConfigProcessorProvider())
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedDir = result.kspSourcesDir.resolve("kotlin")
        val generatedFiles = generatedDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        assertEquals(2, generatedFiles.size, "Expected two generated files (module + editor)")

        val contents = generatedFiles.associate { it.name to it.readText() }
        assertTrue(contents.values.any { "@Module" in it }, "Expected generated module to contain @Module annotation")
        assertTrue(contents.values.any { "RemoteConfigEditor" in it }, "Expected generated editor to reference RemoteConfigEditor")
    }
}
