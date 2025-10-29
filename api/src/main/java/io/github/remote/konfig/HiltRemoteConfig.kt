package io.github.remote.konfig

import dagger.hilt.GeneratesRootInput

/**
 * Marks a [kotlinx.serialization.Serializable] class whose instance should be provided via Hilt by
 * decoding a remote configuration payload.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@GeneratesRootInput
public annotation class HiltRemoteConfig(val key: String)
