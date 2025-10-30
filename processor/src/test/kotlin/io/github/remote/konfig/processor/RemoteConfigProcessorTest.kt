package io.github.remote.konfig.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspIncremental
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteConfigProcessorTest {

    @Test
    fun generatesModuleAndScreenForAnnotatedConfig() {
        val source = SourceFile.kotlin(
            "WelcomeCfg.kt",
            """
            package io.github.remote.konfig.sample

            import io.github.remote.konfig.HiltRemoteConfig
            import kotlinx.serialization.Serializable

            @Serializable
            @HiltRemoteConfig("welcome")
            data class WelcomeCfg(val text: String)
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            inheritClassPath = true
            sources = listOf(source)
            symbolProcessorProviders = listOf(RemoteConfigProcessorProvider())
            kspIncremental = false
        }

        val result = compilation.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val generatedFiles = compilation.kspSourcesDir
            .walkTopDown()
            .filter { it.isFile }
            .map { it.name }
            .toList()

        assertTrue(
            generatedFiles.any { it == "WelcomeCfgRemoteConfigModule.kt" },
            "Expected provider module to be generated, found: $generatedFiles",
        )
        assertTrue(
            generatedFiles.any { it == "WelcomeCfgRemoteConfigScreen.kt" },
            "Expected screen file to be generated, found: $generatedFiles",
        )
    }
}
