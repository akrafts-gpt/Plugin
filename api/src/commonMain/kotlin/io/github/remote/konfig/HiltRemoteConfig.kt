package io.github.remote.konfig

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class HiltRemoteConfig(val key: String)
