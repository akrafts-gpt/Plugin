package io.github.remote.konfig.debug

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
