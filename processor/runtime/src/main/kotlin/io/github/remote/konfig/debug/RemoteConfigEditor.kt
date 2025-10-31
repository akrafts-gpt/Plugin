package io.github.remote.konfig.debug

import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * Represents a generated editor for a specific remote configuration model.
 */
interface RemoteConfigEditor<T> {
    /** The remote config key backing the editor. */
    val key: String

    /** Returns a default instance of the configuration type. */
    fun defaultInstance(): T

    /**
     * Describes the editable fields for the configuration.
     */
    fun fields(): List<EditorField<T>>

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
data class EditorField<T>(
    val name: String,
    val type: String,
    val getter: (T) -> Any?,
    val setter: (T, Any?) -> T
)
