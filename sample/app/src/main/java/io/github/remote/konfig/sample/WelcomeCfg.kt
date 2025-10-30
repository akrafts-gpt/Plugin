package io.github.remote.konfig.sample

import io.github.remote.konfig.HiltRemoteConfig
import kotlinx.serialization.Serializable

@Serializable
@HiltRemoteConfig(key = "welcome")
data class WelcomeCfg(val text: String)
