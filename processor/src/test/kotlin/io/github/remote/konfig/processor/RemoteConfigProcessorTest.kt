package io.github.remote.konfig.processor

import com.tschuchortdev.kotlin.compiletesting.KotlinCompilation
import com.tschuchortdev.kotlin.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteConfigProcessorTest {
    @Test
    fun generatesHiltModuleWithKey() {
        val source = SourceFile.kotlin(
            "Sample.kt",
            """
            package io.github.remote.konfig.sample

            import io.github.remote.konfig.HiltRemoteConfig
            import kotlinx.serialization.KSerializer

            @HiltRemoteConfig("x")
            data class SampleConfig(val value: String) {
                companion object {
                    fun serializer(): KSerializer<SampleConfig> =
                        error("Not used in test")
                }
            }
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source)
            inheritClassPath = true
            symbolProcessorProviders = listOf(RemoteConfigProcessorProvider())
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generated = result.kspSourcesDir.resolve("kotlin/io/github/remote/konfig/sample/SampleConfigRemoteConfigModule.kt")
        assertTrue(generated.exists(), "Generated module was not created")
        val contents = generated.readText()
        assertTrue("@Module" in contents)
        assertTrue("\"x\"" in contents)
    }
}
