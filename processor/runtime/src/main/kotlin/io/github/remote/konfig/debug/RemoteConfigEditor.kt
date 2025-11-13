package io.github.remote.konfig.debug

import kotlin.reflect.KClass
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * Represents a generated editor for a specific remote configuration model.
 */
interface RemoteConfigEditor<T : Any> {
    /** The remote config key backing the editor. */
    val key: String

    /** Returns a default instance of the configuration type. */
    fun defaultInstance(): T

    /** Returns an empty instance used when creating a brand new config. */
    fun emptyInstance(): T = defaultInstance()

    /**
     * Describes the editable fields for the configuration.
     */
    fun fields(): List<FieldEditor>

    /**
     * Optional serializers module that should be merged into the dialog's JSON instance.
     * Implementations can override to register polymorphic subtypes used by the config model.
     */
    val serializersModule: SerializersModule
        get() = EmptySerializersModule()
}

/**
 * Metadata and callbacks required to edit a single field of a remote configuration.
 */
sealed interface FieldEditor {
    val label: String
    val getter: (Any) -> Any?
    val setter: (Any, Any?) -> Any
}

class StringFieldEditor(
    override val label: String,
    override val getter: (Any) -> Any?,
    override val setter: (Any, Any?) -> Any,
) : FieldEditor

class BooleanFieldEditor(
    override val label: String,
    override val getter: (Any) -> Any?,
    override val setter: (Any, Any?) -> Any,
) : FieldEditor

class IntFieldEditor(
    override val label: String,
    override val getter: (Any) -> Any?,
    override val setter: (Any, Any?) -> Any,
) : FieldEditor

class LongFieldEditor(
    override val label: String,
    override val getter: (Any) -> Any?,
    override val setter: (Any, Any?) -> Any,
) : FieldEditor

class FloatFieldEditor(
    override val label: String,
    override val getter: (Any) -> Any?,
    override val setter: (Any, Any?) -> Any,
) : FieldEditor

class DoubleFieldEditor(
    override val label: String,
    override val getter: (Any) -> Any?,
    override val setter: (Any, Any?) -> Any,
) : FieldEditor

class EnumFieldEditor(
    override val label: String,
    override val getter: (Any) -> Any?,
    override val setter: (Any, Any?) -> Any,
    val values: List<Enum<*>>,
) : FieldEditor

class ByteArrayFieldEditor(
    override val label: String,
    override val getter: (Any) -> Any?,
    override val setter: (Any, Any?) -> Any,
) : FieldEditor

class ClassFieldEditor(
    override val label: String,
    override val getter: (Any) -> Any?,
    override val setter: (Any, Any?) -> Any,
    val nestedFieldEditors: List<FieldEditor>,
) : FieldEditor

class ListFieldEditor(
    override val label: String,
    override val getter: (Any) -> Any?,
    override val setter: (Any, Any?) -> Any,
    val defaultItemProvider: () -> Any?,
    val itemEditor: FieldEditor,
) : FieldEditor

class PolymorphicFieldEditor(
    override val label: String,
    override val getter: (Any) -> Any?,
    override val setter: (Any, Any?) -> Any,
    val subclasses: List<KClass<*>>,
    val nestedFieldEditorsProvider: (KClass<*>) -> List<FieldEditor>,
    val defaultInstanceProvider: (KClass<*>) -> Any?,
) : FieldEditor
