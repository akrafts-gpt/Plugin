package io.github.remote.konfig.sample

import io.github.remote.konfig.OverrideStore
import io.github.remote.konfig.RemoteConfigProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeRemoteConfigProvider @Inject constructor(
    private val overrideStore: OverrideStore
) : RemoteConfigProvider {

    private val defaultConfigs: Map<String, String> = mapOf(
        "welcome" to "{\"text\":\"Hello\",\"enabled\":true}"
    )

    override fun getRemoteConfig(key: String): String? {
        return overrideStore.getOverride(key) ?: defaultConfigs[key]
    }
}
