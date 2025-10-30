package io.github.remote.konfig.sample

import io.github.remote.konfig.RemoteConfigProvider

private const val WELCOME_KEY = "welcome"
private const val WELCOME_JSON = """{"text":"Hello from Remote!"}"""

class FakeRemoteConfigProvider : RemoteConfigProvider {
    override fun getRemoteConfig(key: String): String? {
        return when (key) {
            WELCOME_KEY -> WELCOME_JSON
            else -> null
        }
    }
}
