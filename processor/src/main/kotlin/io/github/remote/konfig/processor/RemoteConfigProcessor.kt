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
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ParameterSpec
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

                ScreenGenerator(
                    modelClass = declaration,
                    configKey = key,
                    codeGenerator = codeGenerator,
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

        val polymorphicBindings = polymorphicBindings()
        if (polymorphicBindings.isNotEmpty()) {
            builder.addProperty(
                PropertySpec.builder("serializersModule", SERIALIZERS_MODULE_CLASS, KModifier.OVERRIDE)
                    .initializer(buildSerializersModuleInitializer(polymorphicBindings))
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
        val listType = LIST.parameterizedBy(FIELD_EDITOR)
        val function = FunSpec.builder("fields")
            .addModifiers(KModifier.OVERRIDE)
            .returns(listType)

        if (!modelClass.isDataClass()) {
            logger.warn("${modelClass.qualifiedName?.asString() ?: modelClass.simpleName.asString()} is not a data class. No editor fields will be generated.")
            return function.addStatement("return emptyList()")
                .build()
        }

        val editorsCode = generateFieldEditorsListCode(modelClass, modelTypeName)
        return function.addCode("return %L\n", editorsCode).build()
    }

    private fun generateFieldEditorsListCode(
        classDeclaration: KSClassDeclaration,
        parentType: ClassName = classDeclaration.toClassName()
    ): CodeBlock {
        val primaryParamNames = classDeclaration.primaryConstructor?.parameters
            ?.mapNotNull { it.name?.asString() }
            ?.toSet()
            ?: emptySet()

        val properties = classDeclaration.getAllProperties()
            .filter { it.extensionReceiver == null }
            .filter { primaryParamNames.isEmpty() || it.simpleName.asString() in primaryParamNames }
            .filterNot { Modifier.PRIVATE in it.modifiers }
            .toList()

        if (properties.isEmpty()) {
            return CodeBlock.of("emptyList()")
        }

        val entries = properties.mapNotNull { property ->
            generateFieldEditorExpression(classDeclaration, parentType, property)
        }

        if (entries.isEmpty()) {
            return CodeBlock.of("emptyList()")
        }

        return CodeBlock.builder()
            .add("listOf(\n")
            .indent()
            .apply {
                entries.forEachIndexed { index, entry ->
                    add("%L", entry)
                    if (index != entries.lastIndex) {
                        add(",")
                    }
                    add("\n")
                }
            }
            .unindent()
            .add(")")
            .build()
    }

    private fun generateFieldEditorExpression(
        owner: KSClassDeclaration,
        parentType: ClassName,
        property: KSPropertyDeclaration
    ): CodeBlock? {
        val label = property.simpleName.asString().replaceFirstChar(Char::titlecase)
        val type = property.type.resolve()
        val typeName = type.toTypeName()
        val propertyName = property.simpleName.asString()
        val getter = CodeBlock.of("{ (it as %T).%L }", parentType, propertyName)
        val setter = CodeBlock.of("{ data, value -> (data as %T).copy(%L = value as %T) }", parentType, propertyName, typeName)

        val propertyDeclaration = type.declaration as? KSClassDeclaration
        val isEnum = propertyDeclaration?.classKind == ClassKind.ENUM_CLASS
        val isDataClass = propertyDeclaration?.isDataClass() == true
        val isList = typeName.toString().startsWith("kotlin.collections.List")
        val isPolymorphic = property.annotations.any { it.matchesQualifiedName(POLYMORPHIC_ANNOTATION) }

        return when {
            isPolymorphic && propertyDeclaration != null -> {
                val subclasses = propertyDeclaration.findPolymorphicSubclasses().toList()
                if (subclasses.isEmpty()) {
                    logger.warn(
                        "No subclasses found for polymorphic property ${propertyName} of ${owner.simpleName.asString()}"
                    )
                    buildSimpleFieldEditor(STRING_FIELD_EDITOR, label, getter, setter)
                } else {
                    generatePolymorphicFieldEditor(
                        propertyLabel = label,
                        getter = getter,
                        setter = createPolymorphicSetter(parentType, propertyName, typeName, type.nullability),
                        subclasses = subclasses
                    )
                }
            }
            isEnum && propertyDeclaration != null -> {
                CodeBlock.builder()
                    .add("%T(\n", ENUM_FIELD_EDITOR).indent()
                    .add("label = %S,\n", label)
                    .add("getter = %L,\n", getter)
                    .add("setter = %L,\n", setter)
                    .add("values = listOf(*%T.values())\n", propertyDeclaration.toClassName())
                    .unindent().add(")")
                    .build()
            }
            typeName.toString() == "kotlin.String" -> buildSimpleFieldEditor(STRING_FIELD_EDITOR, label, getter, setter)
            typeName.toString() == "kotlin.Boolean" -> buildSimpleFieldEditor(BOOLEAN_FIELD_EDITOR, label, getter, setter)
            typeName.toString() == "kotlin.Int" -> buildSimpleFieldEditor(INT_FIELD_EDITOR, label, getter, setter)
            typeName.toString() == "kotlin.Long" -> buildSimpleFieldEditor(LONG_FIELD_EDITOR, label, getter, setter)
            typeName.toString() == "kotlin.Float" -> buildSimpleFieldEditor(FLOAT_FIELD_EDITOR, label, getter, setter)
            typeName.toString() == "kotlin.Double" -> buildSimpleFieldEditor(DOUBLE_FIELD_EDITOR, label, getter, setter)
            typeName.toString() == "kotlin.ByteArray" -> buildSimpleFieldEditor(BYTE_ARRAY_FIELD_EDITOR, label, getter, setter)
            isList -> generateListFieldEditor(owner, propertyName, label, getter, type, setter)
            isDataClass && propertyDeclaration != null -> {
                val nestedEditors = generateFieldEditorsListCode(propertyDeclaration, propertyDeclaration.toClassName())
                CodeBlock.builder()
                    .add("%T(\n", CLASS_FIELD_EDITOR).indent()
                    .add("label = %S,\n", label)
                    .add("getter = %L,\n", getter)
                    .add("setter = %L,\n", setter)
                    .add("nestedFieldEditors = %L\n", nestedEditors)
                    .unindent().add(")")
                    .build()
            }
            else -> {
                logger.warn(
                    "Unsupported property type ${typeName} on ${owner.simpleName.asString()}.$propertyName"
                )
                val fallbackGetter = if (type.nullability == Nullability.NULLABLE) {
                    CodeBlock.of("{ ((it as %T).%L)?.toString() ?: \"\" }", parentType, propertyName)
                } else {
                    CodeBlock.of("{ (it as %T).%L.toString() }", parentType, propertyName)
                }
                buildSimpleFieldEditor(
                    editorClass = STRING_FIELD_EDITOR,
                    label = label,
                    getter = fallbackGetter,
                    setter = CodeBlock.of("{ data, _ -> data }")
                )
            }
        }
    }

    private fun buildSimpleFieldEditor(
        editorClass: ClassName,
        label: String,
        getter: CodeBlock,
        setter: CodeBlock
    ): CodeBlock {
        return CodeBlock.builder()
            .add("%T(\n", editorClass).indent()
            .add("label = %S,\n", label)
            .add("getter = %L,\n", getter)
            .add("setter = %L\n", setter)
            .unindent()
            .add(")")
            .build()
    }

    private fun createPolymorphicSetter(
        parentType: ClassName,
        propertyName: String,
        propertyType: TypeName,
        nullability: Nullability
    ): CodeBlock {
        return if (nullability == Nullability.NOT_NULL) {
            CodeBlock.of(
                "{ data, value -> if (value != null) (data as %T).copy(%L = value as %T) else data }",
                parentType,
                propertyName,
                propertyType
            )
        } else {
            CodeBlock.of(
                "{ data, value -> (data as %T).copy(%L = value as %T) }",
                parentType,
                propertyName,
                propertyType
            )
        }
    }

    private fun generateListFieldEditor(
        owner: KSClassDeclaration,
        propertyName: String,
        label: String,
        getter: CodeBlock,
        type: KSType,
        setter: CodeBlock
    ): CodeBlock {
        val itemType = type.arguments.firstOrNull()?.type?.resolve()
        if (itemType == null) {
            logger.warn("Could not resolve list item type for property ${owner.simpleName.asString()}.$propertyName")
            return buildSimpleFieldEditor(STRING_FIELD_EDITOR, label, getter, setter)
        }

        val itemDeclaration = itemType.declaration as? KSClassDeclaration
        if (itemDeclaration == null) {
            logger.warn("Unsupported list item type for property ${owner.simpleName.asString()}.$propertyName")
            return buildSimpleFieldEditor(STRING_FIELD_EDITOR, label, getter, setter)
        }

        val defaultItem = defaultValueForType(itemType, mutableSetOf())
        val itemEditor = generateItemEditor(itemType, itemDeclaration)

        return CodeBlock.builder()
            .add("%T(\n", LIST_FIELD_EDITOR).indent()
            .add("label = %S,\n", label)
            .add("getter = %L,\n", getter)
            .add("setter = %L,\n", setter)
            .add("defaultItemProvider = { %L },\n", defaultItem)
            .add("itemEditor = %L\n", itemEditor)
            .unindent().add(")")
            .build()
    }

    private fun generatePolymorphicFieldEditor(
        propertyLabel: String,
        getter: CodeBlock,
        setter: CodeBlock,
        subclasses: List<KSClassDeclaration>
    ): CodeBlock {
        val block = CodeBlock.builder()
        block.add("%T(\n", POLYMORPHIC_FIELD_EDITOR).indent()
        block.add("label = %S,\n", propertyLabel)
        block.add("getter = %L,\n", getter)
        block.add("setter = %L,\n", setter)
        block.add("subclasses = listOf(\n").indent()
        subclasses.forEach { subclass ->
            block.add("%T::class,\n", subclass.toClassName())
        }
        block.unindent().add("),\n")
        block.add("nestedFieldEditorsProvider = { clazz ->\n").indent()
        block.add("when(clazz) {\n").indent()
        subclasses.forEach { subclass ->
            val nestedEditors = generateFieldEditorsListCode(subclass, subclass.toClassName())
            block.add("%T::class -> %L\n", subclass.toClassName(), nestedEditors)
        }
        block.add("else -> emptyList()\n").unindent()
        block.add("}\n").unindent()
        block.add("},\n")
        block.add("defaultInstanceProvider = { clazz ->\n").indent()
        block.add("when(clazz) {\n").indent()
        subclasses.forEach { subclass ->
            val defaultInstance = defaultValueForDeclaration(subclass, mutableSetOf())
            block.add("%T::class -> %L\n", subclass.toClassName(), defaultInstance)
        }
        block.add("else -> null\n").unindent()
        block.add("}\n").unindent()
        block.add("}\n")
        block.unindent().add(")")
        return block.build()
    }

    private fun generateItemEditor(itemType: KSType, itemTypeDecl: KSClassDeclaration): CodeBlock {
        val itemTypeName = itemType.toTypeName()
        val label = "Item"
        return when {
            itemTypeName.toString() == "kotlin.String" -> buildSimpleFieldEditor(
                STRING_FIELD_EDITOR,
                label,
                CodeBlock.of("{ it as %T }", itemTypeName),
                CodeBlock.of("{ _, value -> value as %T }", itemTypeName)
            )
            itemTypeName.toString() == "kotlin.Boolean" -> buildSimpleFieldEditor(
                BOOLEAN_FIELD_EDITOR,
                label,
                CodeBlock.of("{ it as %T }", itemTypeName),
                CodeBlock.of("{ _, value -> value as %T }", itemTypeName)
            )
            itemTypeName.toString() == "kotlin.Int" -> buildSimpleFieldEditor(
                INT_FIELD_EDITOR,
                label,
                CodeBlock.of("{ it as %T }", itemTypeName),
                CodeBlock.of("{ _, value -> value as %T }", itemTypeName)
            )
            itemTypeName.toString() == "kotlin.Long" -> buildSimpleFieldEditor(
                LONG_FIELD_EDITOR,
                label,
                CodeBlock.of("{ it as %T }", itemTypeName),
                CodeBlock.of("{ _, value -> value as %T }", itemTypeName)
            )
            itemTypeName.toString() == "kotlin.Float" -> buildSimpleFieldEditor(
                FLOAT_FIELD_EDITOR,
                label,
                CodeBlock.of("{ it as %T }", itemTypeName),
                CodeBlock.of("{ _, value -> value as %T }", itemTypeName)
            )
            itemTypeName.toString() == "kotlin.Double" -> buildSimpleFieldEditor(
                DOUBLE_FIELD_EDITOR,
                label,
                CodeBlock.of("{ it as %T }", itemTypeName),
                CodeBlock.of("{ _, value -> value as %T }", itemTypeName)
            )
            itemTypeName.toString() == "kotlin.ByteArray" -> buildSimpleFieldEditor(
                BYTE_ARRAY_FIELD_EDITOR,
                label,
                CodeBlock.of("{ it as %T }", itemTypeName),
                CodeBlock.of("{ _, value -> value as %T }", itemTypeName)
            )
            itemTypeDecl.classKind == ClassKind.ENUM_CLASS -> CodeBlock.builder()
                .add("%T(\n", ENUM_FIELD_EDITOR).indent()
                .add("label = %S,\n", label)
                .add("getter = { it as %T },\n", itemTypeName)
                .add("setter = { _, value -> value as %T },\n", itemTypeName)
                .add("values = listOf(*%T.values())\n", itemTypeDecl.toClassName())
                .unindent().add(")")
                .build()
            itemTypeDecl.isDataClass() -> {
                val nestedEditors = generateFieldEditorsListCode(itemTypeDecl, itemTypeDecl.toClassName())
                CodeBlock.builder()
                    .add("%T(\n", CLASS_FIELD_EDITOR).indent()
                    .add("label = %S,\n", label)
                    .add("getter = { it },\n")
                    .add("setter = { _, value -> value },\n")
                    .add("nestedFieldEditors = %L\n", nestedEditors)
                    .unindent().add(")")
                    .build()
            }
            else -> {
                logger.warn("Unsupported list item type ${itemTypeName}")
                CodeBlock.builder()
                    .add("%T(\n", STRING_FIELD_EDITOR).indent()
                    .add("label = %S,\n", label)
                    .add("getter = { it?.toString() ?: \"\" },\n")
                    .add("setter = { data, _ -> data }\n")
                    .unindent().add(")")
                    .build()
            }
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

    private fun polymorphicBindings(): LinkedHashMap<KSClassDeclaration, List<KSClassDeclaration>> {
        val bindings = linkedMapOf<KSClassDeclaration, List<KSClassDeclaration>>()
        modelClass.getAllProperties()
            .filter { property ->
                property.annotations.any { it.matchesQualifiedName(POLYMORPHIC_ANNOTATION) }
            }
            .forEach { property ->
                val declaration = property.type.resolve().declaration as? KSClassDeclaration ?: return@forEach
                if (!declaration.isPolymorphicRoot()) {
                    logger.warn(
                        "${property.simpleName.asString()} is marked @Polymorphic but ${declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()} is not a polymorphic root.",
                        property
                    )
                    return@forEach
                }

                val subclasses = declaration.findPolymorphicSubclasses()
                    .distinctBy { it.qualifiedName?.asString() }
                    .sortedBy { it.qualifiedName?.asString() ?: it.simpleName.asString() }
                    .toList()

                if (subclasses.isEmpty()) {
                    logger.warn(
                        "No subclasses found for polymorphic type ${declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()} referenced from ${modelClass.qualifiedName?.asString() ?: modelClass.simpleName.asString()}.${property.simpleName.asString()}"
                    )
                    return@forEach
                }

                bindings.putIfAbsent(declaration, subclasses)
            }

        return bindings
    }

    private fun buildSerializersModuleInitializer(
        bindings: Map<KSClassDeclaration, List<KSClassDeclaration>>
    ): CodeBlock {
        val block = CodeBlock.builder()
        block.add("%M {\n", SERIALIZERS_MODULE_FUNCTION)
        block.indent()
        bindings.forEach { (root, subclasses) ->
            block.add("%M(%T::class) {\n", POLYMORPHIC_FUNCTION, root.toClassName())
            block.indent()
            subclasses.forEach { subclass ->
                block.add("%M(%T::class)\n", SUBCLASS_FUNCTION, subclass.toClassName())
            }
            block.unindent()
            block.add("}\n")
        }
        block.unindent()
        block.add("}")
        return block.build()
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

private class ScreenGenerator(
    private val modelClass: KSClassDeclaration,
    private val configKey: String,
    private val codeGenerator: CodeGenerator,
) {
    fun generate() {
        val screenSimpleName = "${modelClass.simpleName.asString()}RemoteConfigScreen"
        val dialogSimpleName = "${screenSimpleName}DialogFragment"
        val moduleSimpleName = "${screenSimpleName}Module"
        val editorSimpleName = "${modelClass.simpleName.asString()}RemoteConfigEditor"

        val screenType = ClassName(GENERATED_PACKAGE, screenSimpleName)
        val dialogType = ClassName(GENERATED_PACKAGE, dialogSimpleName)
        val moduleType = ClassName(GENERATED_PACKAGE, moduleSimpleName)
        val editorType = ClassName(GENERATED_PACKAGE, editorSimpleName)
        val modelTypeName = modelClass.toClassName()

        val fileSpec = FileSpec.builder(GENERATED_PACKAGE, screenSimpleName)
            .addType(buildScreenType(screenType, dialogType))
            .addType(buildDialogType(dialogType, modelTypeName, editorType))
            .addType(buildBindingModule(screenType, moduleType))
            .build()

        val source = modelClass.containingFile
        val dependencies = if (source != null) {
            Dependencies(aggregating = true, source)
        } else {
            Dependencies(aggregating = true)
        }

        fileSpec.writeTo(codeGenerator, dependencies)
    }

    private fun buildScreenType(screenType: ClassName, dialogType: ClassName): TypeSpec {
        val constructor = FunSpec.constructorBuilder()
            .addAnnotation(INJECT)
            .build()

        val dialogTag = "${configKey}_remote_config"

        return TypeSpec.classBuilder(screenType)
            .addSuperinterface(REMOTE_CONFIG_SCREEN)
            .primaryConstructor(constructor)
            .addProperty(
                PropertySpec.builder("id", STRING, KModifier.OVERRIDE)
                    .initializer("%S", configKey)
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("title", STRING, KModifier.OVERRIDE)
                    .initializer("%S", "${modelClass.simpleName.asString()} for $configKey")
                    .build(),
            )
            .addFunction(
                FunSpec.builder("show")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("fragmentManager", FRAGMENT_MANAGER)
                    .addStatement("%T().show(fragmentManager, %S)", dialogType, dialogTag)
                    .build(),
            )
            .build()
    }

    private fun buildDialogType(
        dialogType: ClassName,
        modelTypeName: ClassName,
        editorType: ClassName,
    ): TypeSpec {
        val serializerType = K_SERIALIZER.parameterizedBy(modelTypeName)
        val editorInterface = REMOTE_CONFIG_EDITOR.parameterizedBy(modelTypeName)

        return TypeSpec.classBuilder(dialogType)
            .addAnnotation(ANDROID_ENTRY_POINT)
            .superclass(REMOTE_CONFIG_DIALOG_FRAGMENT.parameterizedBy(modelTypeName))
            .addProperty(
                PropertySpec.builder("configKey", STRING, KModifier.OVERRIDE)
                    .initializer("%S", configKey)
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("screenTitle", STRING, KModifier.OVERRIDE)
                    .initializer("%S", "${modelClass.simpleName.asString()} for $configKey")
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("serializer", serializerType, KModifier.OVERRIDE)
                    .initializer("%T.serializer()", modelTypeName)
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("editor", editorInterface, KModifier.OVERRIDE)
                    .initializer("%T()", editorType)
                    .build(),
            )
            .build()
    }

    private fun buildBindingModule(screenType: ClassName, moduleType: ClassName): TypeSpec {
        val bindFunName = "bind${screenType.simpleName}"
        return TypeSpec.interfaceBuilder(moduleType)
            .addAnnotation(MODULE)
            .addAnnotation(
                AnnotationSpec.builder(INSTALL_IN)
                    .addMember("%T::class", SINGLETON_COMPONENT)
                    .build(),
            )
            .addFunction(
                FunSpec.builder(bindFunName)
                    .addAnnotation(BINDS)
                    .addAnnotation(INTO_SET)
                    .addModifiers(KModifier.ABSTRACT)
                    .addParameter("impl", screenType)
                    .returns(REMOTE_CONFIG_SCREEN)
                    .build(),
            )
            .build()
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
private val FIELD_EDITOR = ClassName("io.github.remote.konfig.debug", "FieldEditor")
private val STRING_FIELD_EDITOR = ClassName("io.github.remote.konfig.debug", "StringFieldEditor")
private val BOOLEAN_FIELD_EDITOR = ClassName("io.github.remote.konfig.debug", "BooleanFieldEditor")
private val INT_FIELD_EDITOR = ClassName("io.github.remote.konfig.debug", "IntFieldEditor")
private val LONG_FIELD_EDITOR = ClassName("io.github.remote.konfig.debug", "LongFieldEditor")
private val FLOAT_FIELD_EDITOR = ClassName("io.github.remote.konfig.debug", "FloatFieldEditor")
private val DOUBLE_FIELD_EDITOR = ClassName("io.github.remote.konfig.debug", "DoubleFieldEditor")
private val ENUM_FIELD_EDITOR = ClassName("io.github.remote.konfig.debug", "EnumFieldEditor")
private val BYTE_ARRAY_FIELD_EDITOR = ClassName("io.github.remote.konfig.debug", "ByteArrayFieldEditor")
private val CLASS_FIELD_EDITOR = ClassName("io.github.remote.konfig.debug", "ClassFieldEditor")
private val LIST_FIELD_EDITOR = ClassName("io.github.remote.konfig.debug", "ListFieldEditor")
private val POLYMORPHIC_FIELD_EDITOR = ClassName("io.github.remote.konfig.debug", "PolymorphicFieldEditor")
private val REMOTE_CONFIG_SCREEN = ClassName("io.github.remote.konfig", "RemoteConfigScreen")
private val FRAGMENT_MANAGER = ClassName("androidx.fragment.app", "FragmentManager")
private val REMOTE_CONFIG_DIALOG_FRAGMENT = ClassName("io.github.remote.konfig.debug", "RemoteConfigDialogFragment")
private val K_SERIALIZER = ClassName("kotlinx.serialization", "KSerializer")
private val SERIALIZERS_MODULE_CLASS = ClassName("kotlinx.serialization.modules", "SerializersModule")
private val SERIALIZERS_MODULE_FUNCTION = MemberName("kotlinx.serialization.modules", "SerializersModule")
private val POLYMORPHIC_FUNCTION = MemberName("kotlinx.serialization.modules", "polymorphic")
private val SUBCLASS_FUNCTION = MemberName("kotlinx.serialization.modules", "subclass")
private val ANDROID_ENTRY_POINT = ClassName("dagger.hilt.android", "AndroidEntryPoint")
private val MODULE = ClassName("dagger", "Module")
private val INSTALL_IN = ClassName("dagger.hilt", "InstallIn")
private val SINGLETON_COMPONENT = ClassName("dagger.hilt.components", "SingletonComponent")
private val BINDS = ClassName("dagger", "Binds")
private val INTO_SET = ClassName("dagger.multibindings", "IntoSet")
private val INJECT = ClassName("javax.inject", "Inject")
private const val POLYMORPHIC_ANNOTATION = "kotlinx.serialization.Polymorphic"
private const val SERIALIZABLE_ANNOTATION = "kotlinx.serialization.Serializable"
private const val HILT_REMOTE_CONFIG_FULL = "io.github.remote.konfig.HiltRemoteConfig"
private const val HILT_REMOTE_CONFIG_SIMPLE = "HiltRemoteConfig"

