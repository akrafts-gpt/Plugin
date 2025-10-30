package io.github.remote.konfig.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import com.squareup.kotlinpoet.parameterizedBy

private const val HILT_REMOTE_CONFIG_ANNOTATION = "io.github.remote.konfig.HiltRemoteConfig"
private const val SERIALIZABLE_ANNOTATION = "kotlinx.serialization.Serializable"
private const val GENERATED_PACKAGE = "io.github.remote.konfig.generated"

private val REMOTE_CONFIG_PROVIDER = ClassName("io.github.remote.konfig", "RemoteConfigProvider")
private val OVERRIDE_STORE = ClassName("io.github.remote.konfig", "OverrideStore")
private val JSON = ClassName("kotlinx.serialization.json", "Json")
private val MODULE = ClassName("dagger", "Module")
private val INSTALL_IN = ClassName("dagger.hilt", "InstallIn")
private val SINGLETON_COMPONENT = ClassName("dagger.hilt.components", "SingletonComponent")
private val PROVIDES = ClassName("dagger", "Provides")
private val JVM_STATIC = ClassName("kotlin.jvm", "JvmStatic")
private val KCLASS = ClassName("kotlin.reflect", "KClass")
private val LIST = ClassName("kotlin.collections", "List")
private val PAIR = ClassName("kotlin", "Pair")

private data class RemoteConfigEntry(
    val key: String,
    val targetType: ClassName,
    val sourceFile: KSFile
)

class RemoteKonfigProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    private val processed = mutableSetOf<String>()
    private val entries = mutableListOf<RemoteConfigEntry>()
    private val sourceFiles = mutableSetOf<KSFile>()
    private val seenKeys = mutableSetOf<String>()
    private var registryGenerated = false
    private var hadError = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(HILT_REMOTE_CONFIG_ANNOTATION)
        val deferred = mutableListOf<KSAnnotated>()

        symbols.forEach { symbol ->
            if (symbol !is KSClassDeclaration) {
                logger.error("@HiltRemoteConfig can only be applied to classes.", symbol)
                hadError = true
                return@forEach
            }

            if (!symbol.validate()) {
                deferred += symbol
                return@forEach
            }

            val qualifiedName = symbol.qualifiedName?.asString()
            if (qualifiedName == null) {
                logger.error("Unable to determine qualified name for @HiltRemoteConfig target.", symbol)
                hadError = true
                return@forEach
            }

            if (!processed.add(qualifiedName)) {
                return@forEach
            }

            val annotation = symbol.annotations.firstOrNull {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == HILT_REMOTE_CONFIG_ANNOTATION
            }

            val key = annotation?.arguments?.firstOrNull { it.name?.asString() == "key" }?.value as? String
            if (key.isNullOrBlank()) {
                logger.error("@HiltRemoteConfig key cannot be null or blank.", symbol)
                hadError = true
                return@forEach
            }

            if (!seenKeys.add(key)) {
                logger.error("Duplicate @HiltRemoteConfig key '$key' detected.", symbol)
                hadError = true
                return@forEach
            }

            if (!symbol.hasSerializableAnnotation()) {
                logger.error(
                    "${symbol.simpleName.asString()} must be annotated with @Serializable to use @HiltRemoteConfig.",
                    symbol
                )
                hadError = true
                return@forEach
            }

            val targetClassName = symbol.toClassName()
            val containingFile = symbol.containingFile
            if (containingFile == null) {
                logger.error("@HiltRemoteConfig targets must be declared in source files.", symbol)
                hadError = true
                return@forEach
            }

            val entry = RemoteConfigEntry(key, targetClassName, containingFile)
            generateModuleFor(entry)
            entries += entry
            sourceFiles += containingFile
        }

        return deferred
    }

    override fun finish() {
        if (registryGenerated || hadError) return
        generateRegistry()
        registryGenerated = true
    }

    private fun generateModuleFor(entry: RemoteConfigEntry) {
        val simpleNames = entry.targetType.simpleNames
        val moduleClassName = simpleNames.joinToString(separator = "_") + "RemoteConfigModule"
        val providerFunctionName = "provide" + simpleNames.joinToString(separator = "")

        val moduleType = TypeSpec.objectBuilder(moduleClassName)
            .addAnnotation(MODULE)
            .addAnnotation(
                AnnotationSpec.builder(INSTALL_IN)
                    .addMember("%T::class", SINGLETON_COMPONENT)
                    .build()
            )
            .addFunction(
                FunSpec.builder(providerFunctionName)
                    .addAnnotation(JVM_STATIC)
                    .addAnnotation(PROVIDES)
                    .addParameter("provider", REMOTE_CONFIG_PROVIDER)
                    .returns(entry.targetType.copy(nullable = true))
                    .addStatement(
                        "val json = %T.getOverride(%S) ?: provider.getRemoteConfig(%S)",
                        OVERRIDE_STORE,
                        entry.key,
                        entry.key
                    )
                    .addStatement(
                        "return json?.let { %T.decodeFromString(%T.serializer(), it) }",
                        JSON,
                        entry.targetType
                    )
                    .build()
            )
            .build()

        val fileSpec = FileSpec.builder(GENERATED_PACKAGE, moduleClassName)
            .addFileComment("Generated by RemoteKonfigProcessor. Do not modify.")
            .addType(moduleType)
            .build()

        fileSpec.writeTo(codeGenerator, Dependencies(false, entry.sourceFile))
    }

    private fun generateRegistry() {
        val sortedEntries = entries.sortedBy { it.key }
        val kClassStar = KCLASS.parameterizedBy(STAR)
        val pairType = PAIR.parameterizedBy(STRING, kClassStar)
        val listType = LIST.parameterizedBy(pairType)

        val entriesInitializer = if (sortedEntries.isEmpty()) {
            CodeBlock.of("emptyList()")
        } else {
            CodeBlock.builder().apply {
                add("listOf(\n")
                sortedEntries.forEachIndexed { index, entry ->
                    add("  %S to %T::class", entry.key, entry.targetType)
                    if (index != sortedEntries.lastIndex) {
                        add(",")
                    }
                    add("\n")
                }
                add(")")
            }.build()
        }

        val registryObject = TypeSpec.objectBuilder("RemoteKonfigRegistry")
            .addKdoc("Registry of generated remote config bindings. Generated for debugging purposes.")
            .addProperty(
                PropertySpec.builder("entries", listType)
                    .addKdoc("List of registered remote config entries keyed by remote config key.")
                    .initializer(entriesInitializer)
                    .build()
            )
            .addFunction(
                FunSpec.builder("find")
                    .addKdoc("Returns the [KClass] associated with [key], or null if not present.")
                    .addParameter("key", STRING)
                    .returns(kClassStar.copy(nullable = true))
                    .addStatement("return entries.firstOrNull { it.first == key }?.second")
                    .build()
            )
            .build()

        val dependencies = if (sourceFiles.isEmpty()) {
            Dependencies(true)
        } else {
            Dependencies(true, *sourceFiles.toTypedArray())
        }

        FileSpec.builder(GENERATED_PACKAGE, "RemoteKonfigRegistry")
            .addFileComment("Generated by RemoteKonfigProcessor. Do not modify.")
            .addImport("kotlin.reflect", "KClass")
            .addType(registryObject)
            .build()
            .writeTo(codeGenerator, dependencies)
    }

    private fun KSClassDeclaration.hasSerializableAnnotation(): Boolean {
        return annotations.any {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == SERIALIZABLE_ANNOTATION
        }
    }
}

class RemoteKonfigProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return RemoteKonfigProcessor(environment.codeGenerator, environment.logger)
    }

    override val isIncremental: Boolean = true
    override val incrementalType: SymbolProcessorProvider.IncrementalType =
        SymbolProcessorProvider.IncrementalType.AGGREGATING
}
