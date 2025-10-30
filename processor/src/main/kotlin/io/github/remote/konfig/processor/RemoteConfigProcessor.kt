package io.github.remote.konfig.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
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
        val symbols = resolver.getSymbolsWithAnnotation(HILT_REMOTE_CONFIG_FULL)
        val invalid = symbols.filterNot { it.validate() }.toList()

        symbols.filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .forEach { declaration ->
                val key = declaration.hiltRemoteConfigKey()
                if (key == null) {
                    logger.error("@HiltRemoteConfig requires a non-null key", declaration)
                    return@forEach
                }

                ProviderGenerator(
                    classDeclaration = declaration,
                    codeGenerator = codeGenerator,
                    configKey = key
                ).generate()

                EditorGenerator(
                    modelClass = declaration,
                    configKey = key,
                    codeGenerator = codeGenerator,
                    resolver = resolver,
                    logger = logger
                ).generate()
            }

        return invalid
    }

    override fun finish() = Unit

    override fun onError() = Unit

}

private class ProviderGenerator(
    private val classDeclaration: KSClassDeclaration,
    private val codeGenerator: CodeGenerator,
    private val configKey: String
) {
    fun generate() {
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
                    .addStatement("val key = %S", configKey)
                    .addStatement("val raw = overrideStore.get(key) ?: remoteConfigProvider.getRemoteConfig(key)")
                    .addStatement("requireNotNull(raw) { %P }", "Remote config for $configKey was null")
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
}

private class EditorGenerator(
    private val modelClass: KSClassDeclaration,
    private val configKey: String,
    private val codeGenerator: CodeGenerator,
    private val resolver: Resolver,
    private val logger: KSPLogger
) {
    fun generate() {
        val editorClassName = "${modelClass.simpleName.asString()}RemoteConfigEditor"
        val fileSpec = FileSpec.builder(GENERATED_PACKAGE, editorClassName)
            .addType(buildEditorType(editorClassName))
            .build()

        val source = modelClass.containingFile
        val dependencies = if (source != null) {
            Dependencies(aggregating = true, source)
        } else {
            Dependencies(aggregating = true)
        }

        fileSpec.writeTo(codeGenerator, dependencies)
    }

    private fun buildEditorType(editorClassName: String): TypeSpec {
        val modelTypeName = modelClass.toClassName()
        val editorInterface = REMOTE_CONFIG_EDITOR.parameterizedBy(modelTypeName)
        val builder = TypeSpec.classBuilder(editorClassName)
            .addSuperinterface(editorInterface)
            .addProperty(
                PropertySpec.builder("key", STRING, KModifier.OVERRIDE)
                    .initializer("%S", configKey)
                    .build()
            )
            .addFunction(defaultInstanceFun(modelTypeName))
            .addFunction(fieldsFun(modelTypeName))

        val subclasses = polymorphicSubclasses(modelClass)
        if (subclasses.isNotEmpty()) {
            builder.addProperty(
                PropertySpec.builder(
                    "polymorphicSubclasses",
                    LIST.parameterizedBy(STRING)
                )
                    .addModifiers(KModifier.PRIVATE)
                    .initializer(subclasses.joinToString(prefix = "listOf(", postfix = ")") { "\"$it\"" })
                    .addKdoc("Detected subclasses for polymorphic config.")
                    .build()
            )
        }

        return builder.build()
    }

    private fun defaultInstanceFun(modelTypeName: ClassName): FunSpec {
        val defaultValue = defaultValueForDeclaration(modelClass, mutableSetOf())
        return FunSpec.builder("defaultInstance")
            .addModifiers(KModifier.OVERRIDE)
            .returns(modelTypeName)
            .addCode("return %L\n", defaultValue)
            .build()
    }

    private fun fieldsFun(modelTypeName: ClassName): FunSpec {
        val editorFieldClass = EDITOR_FIELD.parameterizedBy(modelTypeName)
        val listType = LIST.parameterizedBy(editorFieldClass)
        val function = FunSpec.builder("fields")
            .addModifiers(KModifier.OVERRIDE)
            .returns(listType)

        if (!modelClass.isDataClass()) {
            logger.warn("${modelClass.qualifiedName?.asString() ?: modelClass.simpleName.asString()} is not a data class. No editor fields will be generated.")
            return function.addStatement("return emptyList()")
                .build()
        }

        val primaryParamNames = modelClass.primaryConstructor?.parameters
            ?.mapNotNull { it.name?.asString() }
            ?.toSet()
            ?: emptySet()

        val properties = modelClass.getAllProperties()
            .filter { it.extensionReceiver == null }
            .filter { it.simpleName.asString() in primaryParamNames }
            .filterNot { Modifier.PRIVATE in it.modifiers }
            .toList()

        if (properties.isEmpty()) {
            return function.addStatement("return emptyList()")
                .build()
        }

        val code = CodeBlock.builder()
        code.add("return listOf(\n")
        code.indent()

        properties.forEachIndexed { index, property ->
            val typeName = property.type.toTypeName()
            val todoComment = nestedEditorTodo(property)
            if (todoComment != null) {
                code.add("// %L\n", todoComment)
            }
            code.add("%T(\n", EDITOR_FIELD.parameterizedBy(modelTypeName))
            code.indent()
            code.add("name = %S,\n", property.simpleName.asString())
            code.add("type = %S,\n", typeName.toString())
            code.add("getter = %L,\n", getterBlock(property))
            code.add("setter = %L\n", setterBlock(property, typeName))
            code.unindent()
            code.add(")")
            if (index != properties.lastIndex) {
                code.add(",")
            }
            code.add("\n")
        }

        code.unindent()
        code.add(")")

        return function.addCode(code.build()).build()
    }

    private fun getterBlock(property: KSPropertyDeclaration): CodeBlock {
        val propertyName = property.simpleName.asString()
        return CodeBlock.of("{ it.%L }", propertyName)
    }

    private fun setterBlock(property: KSPropertyDeclaration, typeName: TypeName): CodeBlock {
        val propertyName = property.simpleName.asString()
        return CodeBlock.of("{ cfg, value -> cfg.copy(%L = value as %T) }", propertyName, typeName)
    }

    private fun nestedEditorTodo(property: KSPropertyDeclaration): String? {
        val typeDeclaration = property.type.resolve().declaration as? KSClassDeclaration ?: return null
        return if (typeDeclaration.classKind == ClassKind.CLASS && typeDeclaration.modifiers.contains(Modifier.DATA)) {
            "TODO: Support nested editors for ${typeDeclaration.qualifiedName?.asString() ?: typeDeclaration.simpleName.asString()}"
        } else {
            null
        }
    }

    private fun defaultValueForDeclaration(
        declaration: KSClassDeclaration,
        visited: MutableSet<String>
    ): CodeBlock {
        val qualifiedName = declaration.qualifiedName?.asString()
        if (qualifiedName != null && !visited.add(qualifiedName)) {
            return CodeBlock.of("error(%S)", "Cyclic default value for $qualifiedName")
        }

        when (qualifiedName) {
            "kotlin.String" -> return CodeBlock.of("%S", "")
            "kotlin.Boolean" -> return CodeBlock.of("false")
            "kotlin.Int" -> return CodeBlock.of("0")
            "kotlin.Long" -> return CodeBlock.of("0L")
            "kotlin.Float" -> return CodeBlock.of("0f")
            "kotlin.Double" -> return CodeBlock.of("0.0")
            "kotlin.Short" -> return CodeBlock.of("0")
            "kotlin.Byte" -> return CodeBlock.of("0")
            "kotlin.ByteArray" -> return CodeBlock.of("byteArrayOf()")
        }

        if (declaration.classKind == ClassKind.ENUM_CLASS) {
            return CodeBlock.of("%T.entries.first()", declaration.toClassName())
        }

        if (declaration.modifiers.contains(Modifier.SEALED) || declaration.classKind == ClassKind.INTERFACE) {
            val subclasses = declaration.findPolymorphicSubclasses().toList()
            if (subclasses.isNotEmpty()) {
                return defaultValueForDeclaration(subclasses.first(), visited)
            }
            logger.warn("No subclasses found for polymorphic type ${declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()}.")
            return CodeBlock.of("error(%S)", "Cannot create default instance for ${declaration.simpleName.asString()}")
        }

        val constructor = declaration.primaryConstructor
        if (constructor == null) {
            return CodeBlock.of("%T()", declaration.toClassName())
        }

        val parameters = constructor.parameters.filterNot(KSValueParameter::hasDefault)
        if (parameters.isEmpty()) {
            return CodeBlock.of("%T()", declaration.toClassName())
        }

        val block = CodeBlock.builder()
        block.add("%T(\n", declaration.toClassName())
        block.indent()
        parameters.forEach { parameter ->
            val name = parameter.name?.asString() ?: return@forEach
            val type = parameter.type.resolve()
            val defaultValue = defaultValueForType(type, visited)
            block.add("%L = %L,\n", name, defaultValue)
        }
        block.unindent()
        block.add(")")

        if (qualifiedName != null) {
            visited.remove(qualifiedName)
        }

        return block.build()
    }

    private fun defaultValueForType(type: KSType, visited: MutableSet<String>): CodeBlock {
        if (type.nullability == Nullability.NULLABLE) {
            return CodeBlock.of("null")
        }

        val declaration = type.declaration as? KSClassDeclaration
        val qualifiedName = declaration?.qualifiedName?.asString()

        when (qualifiedName) {
            "kotlin.String" -> return CodeBlock.of("%S", "")
            "kotlin.Boolean" -> return CodeBlock.of("false")
            "kotlin.Int" -> return CodeBlock.of("0")
            "kotlin.Long" -> return CodeBlock.of("0L")
            "kotlin.Float" -> return CodeBlock.of("0f")
            "kotlin.Double" -> return CodeBlock.of("0.0")
            "kotlin.Short" -> return CodeBlock.of("0")
            "kotlin.Byte" -> return CodeBlock.of("0")
            "kotlin.ByteArray" -> return CodeBlock.of("byteArrayOf()")
            "kotlin.collections.List" -> {
                val elementType = type.arguments.firstOrNull()?.type?.resolve()?.toTypeName()
                return if (elementType != null) {
                    CodeBlock.of("emptyList<%T>()", elementType)
                } else {
                    CodeBlock.of("emptyList()")
                }
            }
            "kotlin.collections.MutableList" -> {
                val elementType = type.arguments.firstOrNull()?.type?.resolve()?.toTypeName()
                return if (elementType != null) {
                    CodeBlock.of("emptyList<%T>().toMutableList()", elementType)
                } else {
                    CodeBlock.of("mutableListOf()")
                }
            }
            "kotlin.collections.Set" -> {
                val elementType = type.arguments.firstOrNull()?.type?.resolve()?.toTypeName()
                return if (elementType != null) {
                    CodeBlock.of("emptySet<%T>()", elementType)
                } else {
                    CodeBlock.of("emptySet()")
                }
            }
            "kotlin.collections.MutableSet" -> {
                val elementType = type.arguments.firstOrNull()?.type?.resolve()?.toTypeName()
                return if (elementType != null) {
                    CodeBlock.of("emptySet<%T>().toMutableSet()", elementType)
                } else {
                    CodeBlock.of("mutableSetOf()")
                }
            }
            "kotlin.collections.Map" -> {
                val keyType = type.arguments.getOrNull(0)?.type?.resolve()?.toTypeName()
                val valueType = type.arguments.getOrNull(1)?.type?.resolve()?.toTypeName()
                return if (keyType != null && valueType != null) {
                    CodeBlock.of("emptyMap<%T, %T>()", keyType, valueType)
                } else {
                    CodeBlock.of("emptyMap()")
                }
            }
            "kotlin.collections.MutableMap" -> {
                val keyType = type.arguments.getOrNull(0)?.type?.resolve()?.toTypeName()
                val valueType = type.arguments.getOrNull(1)?.type?.resolve()?.toTypeName()
                return if (keyType != null && valueType != null) {
                    CodeBlock.of("emptyMap<%T, %T>().toMutableMap()", keyType, valueType)
                } else {
                    CodeBlock.of("mutableMapOf()")
                }
            }
        }

        if (declaration == null) {
            return CodeBlock.of("error(%S)", "Cannot create default value for ${type.displayName()}")
        }

        return when (declaration.classKind) {
            ClassKind.ENUM_CLASS -> CodeBlock.of("%T.entries.first()", declaration.toClassName())
            ClassKind.CLASS -> defaultValueForDeclaration(declaration, visited)
            ClassKind.INTERFACE, ClassKind.OBJECT, ClassKind.ANNOTATION_CLASS -> {
                val subclasses = declaration.findPolymorphicSubclasses().toList()
                if (subclasses.isNotEmpty()) {
                    defaultValueForDeclaration(subclasses.first(), visited)
                } else {
                    CodeBlock.of("error(%S)", "Cannot create default value for ${declaration.simpleName.asString()}")
                }
            }
            else -> CodeBlock.of("error(%S)", "Cannot create default value for ${declaration.simpleName.asString()}")
        }
    }

    private fun polymorphicSubclasses(declaration: KSClassDeclaration): List<String> {
        if (!declaration.isPolymorphicRoot()) return emptyList()
        return declaration.findPolymorphicSubclasses()
            .mapNotNull { it.qualifiedName?.asString() }
            .distinct()
            .sorted()
            .toList()
    }

    private fun KSClassDeclaration.findPolymorphicSubclasses(): Sequence<KSClassDeclaration> {
        val qualifiedName = this.qualifiedName?.asString() ?: return emptySequence()
        return if (Modifier.SEALED in modifiers) {
            getSealedSubclasses()
        } else {
            resolver.getAllFiles()
                .flatMap { it.declarations }
                .filterIsInstance<KSClassDeclaration>()
                .filter { candidate ->
                    candidate.classKind == ClassKind.CLASS && Modifier.ABSTRACT !in candidate.modifiers &&
                        candidate.superTypes.any { superType ->
                            superType.resolve().declaration.qualifiedName?.asString() == qualifiedName
                        }
                }
        }
    }

    private fun KSClassDeclaration.isPolymorphicRoot(): Boolean {
        if (Modifier.SEALED in modifiers) return true
        if (classKind == ClassKind.INTERFACE) {
            return annotations.any { it.matchesQualifiedName(SERIALIZABLE_ANNOTATION) }
        }
        return false
    }
}

private fun KSClassDeclaration.hiltRemoteConfigKey(): String? {
    val annotation = annotations.firstOrNull { annotation ->
        annotation.shortName.asString() == HILT_REMOTE_CONFIG_SIMPLE ||
            annotation.annotationType.resolve().declaration.qualifiedName?.asString() == HILT_REMOTE_CONFIG_FULL
    } ?: return null

    val keyArgument = annotation.arguments.firstOrNull { it.name?.asString() == "key" || it.name == null }
    return keyArgument?.value as? String
}

private fun KSAnnotation.matchesQualifiedName(expected: String): Boolean {
    val resolved = annotationType.resolve()
    val declaration = resolved.declaration
    if (declaration is KSClassDeclaration) {
        return declaration.qualifiedName?.asString() == expected
    }
    return false
}

private fun KSClassDeclaration.isDataClass(): Boolean = Modifier.DATA in modifiers

private fun KSType.displayName(): String {
    val declarationName = declaration.qualifiedName?.asString() ?: toString()
    if (arguments.isEmpty()) return declarationName
    return buildString {
        append(declarationName)
        append('<')
        append(arguments.joinToString(", ") { argument ->
            argument.type?.resolve()?.displayName() ?: "*"
        })
        append('>')
        if (nullability == Nullability.NULLABLE) {
            append('?')
        }
    }
}

private const val GENERATED_PACKAGE = "io.github.remote.konfig.generated"
private val STRING = String::class.asTypeName()
private val REMOTE_CONFIG_EDITOR = ClassName("io.github.remote.konfig.debug", "RemoteConfigEditor")
private val EDITOR_FIELD = ClassName("io.github.remote.konfig.debug", "EditorField")
private const val SERIALIZABLE_ANNOTATION = "kotlinx.serialization.Serializable"
private const val HILT_REMOTE_CONFIG_FULL = "io.github.remote.konfig.HiltRemoteConfig"
private const val HILT_REMOTE_CONFIG_SIMPLE = "HiltRemoteConfig"

