package io.github.remote.konfig.sample

import io.github.remote.konfig.RemoteConfigProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeRemoteConfigProvider @Inject constructor() : RemoteConfigProvider {

    private val remoteConfigs: Map<String, String> = mapOf(
        "welcome" to "{\"text\":\"Hello\",\"enabled\":true}"
    )

    override fun getRemoteConfig(key: String): String? = remoteConfigs[key]
}
