package io.github.remote.konfig.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspIncremental
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val STUB_SOURCES = listOf(
    SourceFile.kotlin(
        "ApiStubs.kt",
        """
        package io.github.remote.konfig

        @Target(AnnotationTarget.CLASS)
        annotation class HiltRemoteConfig(val key: String)

        class OverrideStore {
            fun get(key: String): String? = null
        }

        interface RemoteConfigProvider {
            fun getRemoteConfig(key: String): String? = null
        }

        interface RemoteConfigScreen {
            val id: String
            val title: String
            fun show(fragmentManager: androidx.fragment.app.FragmentManager)
        }

        interface RemoteConfigScreenProvider {
            fun screens(): Set<RemoteConfigScreen> = emptySet()
        }
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "AndroidOsBundleStub.kt",
        """
        package android.os

        class Bundle {
            private val values = mutableMapOf<String, Any?>()

            fun putString(key: String, value: String?) {
                values[key] = value
            }

            fun getString(key: String): String? = values[key] as? String
        }
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "AndroidContentContextStub.kt",
        """
        package android.content

        open class Context
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "AndroidAppDialogStub.kt",
        """
        package android.app

        open class Dialog
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "AndroidRStub.kt",
        """
        @file:Suppress("ClassName")

        package android

        object R {
            object string {
                const val ok: Int = 1
            }
        }
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "AndroidxCoreBundleOfStub.kt",
        """
        package androidx.core.os

        import android.os.Bundle

        fun bundleOf(vararg pairs: Pair<String, Any?>): Bundle {
            val bundle = Bundle()
            for ((key, value) in pairs) {
                bundle.putString(key, value as? String)
            }
            return bundle
        }
        """.trimIndent(),
    SourceFile.kotlin(
        "AndroidxAppCompatAlertDialogStub.kt",
        """
        package androidx.appcompat.app

        import android.app.Dialog
        import android.content.Context

        open class AlertDialog : Dialog()

        class Builder(private val context: Context) {
            fun setTitle(title: CharSequence?): Builder = this
            fun setMessage(message: CharSequence?): Builder = this
            fun setPositiveButton(textId: Int, listener: Any?): Builder = this
            fun create(): AlertDialog = AlertDialog()
        }
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "DaggerStubs.kt",
        """
        package dagger

        import kotlin.reflect.KClass

        @Target(AnnotationTarget.CLASS)
        annotation class Module

        @Target(AnnotationTarget.FUNCTION)
        annotation class Provides

        @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
        annotation class Component(val value: KClass<*>)

        @Target(AnnotationTarget.FUNCTION)
        annotation class Binds
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "DaggerHiltStubs.kt",
        """
        package dagger.hilt

        import kotlin.reflect.KClass

        @Target(AnnotationTarget.CLASS)
        annotation class InstallIn(vararg val value: KClass<*>)
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "HiltAndroidEntryPointStub.kt",
        """
        package dagger.hilt.android

        @Target(AnnotationTarget.CLASS)
        annotation class AndroidEntryPoint
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "DaggerHiltComponentsStub.kt",
        """
        package dagger.hilt.components

        interface SingletonComponent
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "DaggerMultibindingsStub.kt",
        """
        package dagger.multibindings

        @Target(AnnotationTarget.FUNCTION)
        annotation class IntoSet
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "JavaxInjectStub.kt",
        """
        package javax.inject

        @Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
        annotation class Inject
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "KotlinxSerializableStub.kt",
        """
        package kotlinx.serialization

        @Target(AnnotationTarget.CLASS)
        annotation class Serializable

        interface KSerializer<T>
        """.trimIndent(),
    ),
)

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
            sources = STUB_SOURCES + source
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
