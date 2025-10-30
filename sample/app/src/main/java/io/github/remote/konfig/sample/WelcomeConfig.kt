package io.github.remote.konfig.sample

import io.github.remote.konfig.HiltRemoteConfig
import kotlinx.serialization.Serializable

@Serializable
@HiltRemoteConfig("welcome")
data class WelcomeConfig(
    val text: String = "Hello",
    val enabled: Boolean = true
)
