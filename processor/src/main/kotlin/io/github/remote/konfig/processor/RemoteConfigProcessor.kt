package io.github.remote.konfig.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

class RemoteConfigProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return RemoteConfigProcessor(environment)
    }
}

private class RemoteConfigProcessor(
    environment: SymbolProcessorEnvironment
) : SymbolProcessor {
    private val logger: KSPLogger = environment.logger
    private val codeGenerator: CodeGenerator = environment.codeGenerator

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(HILT_REMOTE_CONFIG)
        val invalid = symbols.filterNot { it.validate() }.toList()

        symbols.filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .forEach { generateModule(it) }

        return invalid
    }

    private fun generateModule(classDeclaration: KSClassDeclaration) {
        val annotation = classDeclaration.annotations.firstOrNull {
            it.shortName.asString() == HILT_REMOTE_CONFIG_SIMPLE_NAME
        } ?: return

        val keyArgument = annotation.arguments.firstOrNull { it.name?.asString() == "key" || it.name == null }
        val key = keyArgument?.value as? String
        if (key == null) {
            logger.error("@HiltRemoteConfig requires a String key", classDeclaration)
            return
        }

        val packageName = classDeclaration.packageName.asString()
        val targetClass = classDeclaration.toClassName()
        val moduleName = "${targetClass.simpleName}RemoteConfigModule"
        val providerName = "provide${targetClass.simpleName}"

        val moduleType = TypeSpec.objectBuilder(moduleName)
            .addAnnotation(ClassName("dagger", "Module"))
            .addAnnotation(
                AnnotationSpec.builder(ClassName("dagger.hilt", "InstallIn"))
                    .addMember("%T::class", ClassName("dagger.hilt.components", "SingletonComponent"))
                    .build()
            )
            .addFunction(
                FunSpec.builder(providerName)
                    .addAnnotation(ClassName("dagger", "Provides"))
                    .returns(targetClass)
                    .addParameter("overrideStore", ClassName("io.github.remote.konfig", "OverrideStore"))
                    .addParameter("remoteConfigProvider", ClassName("io.github.remote.konfig", "RemoteConfigProvider"))
                    .addStatement("val key = %S", key)
                    .addStatement("val raw = overrideStore.get(key) ?: remoteConfigProvider.getRemoteConfig(key)")
                    .addStatement("requireNotNull(raw) { %P }", "Remote config for $key was null")
                    .addStatement(
                        "return %T { ignoreUnknownKeys = true }.decodeFromString(%T.serializer(), raw)",
                        ClassName("kotlinx.serialization.json", "Json"),
                        targetClass
                    )
                    .build()
            )
            .build()

        val source = classDeclaration.containingFile
        val dependencies = if (source != null) {
            Dependencies(aggregating = true, source)
        } else {
            Dependencies(aggregating = true)
        }

        FileSpec.builder(packageName, moduleName)
            .addImport("kotlinx.serialization", "decodeFromString")
            .addType(moduleType)
            .build()
            .writeTo(codeGenerator, dependencies)
    }

    override fun finish() = Unit

    override fun onError() = Unit

    companion object {
        private const val HILT_REMOTE_CONFIG = "io.github.remote.konfig.HiltRemoteConfig"
        private const val HILT_REMOTE_CONFIG_SIMPLE_NAME = "HiltRemoteConfig"
    }
}
