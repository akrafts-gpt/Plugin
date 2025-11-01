package io.github.remote.konfig.sample

import io.github.remote.konfig.OverrideStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class FakeRemoteConfigProviderTest {

    private val overrideStore = OverrideStore()
    private val provider = FakeRemoteConfigProvider(overrideStore)
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun returnsAllSampleConfigs() {
        val keys = listOf(
            "welcome",
            "sample_profile_config",
            "sample_profile_with_option",
            "sample_deeply_nested",
            "sample_preview_config",
            "sample_message_envelope",
            "sample_choice_config",
        )

        keys.forEach { key ->
            assertNotNull(provider.getRemoteConfig(key), "Missing payload for $key")
        }
    }

    @Test
    fun remotePayloadUnaffectedByOverride() {
        val key = "welcome"
        val remotePayload = provider.getRemoteConfig(key)
        assertNotNull(remotePayload)

        val overridePayload = "{\"text\":\"Hi\",\"enabled\":false}"
        overrideStore.setOverride(key, overridePayload)

        val refreshedRemote = provider.getRemoteConfig(key)
        assertEquals(remotePayload, refreshedRemote)
        assertEquals(overridePayload, provider.getActiveConfig(key))
    }

    @Test
    fun samplePayloadsDecodeSuccessfully() {
        val profile = provider.getRemoteConfig("sample_profile_config")
        val profileConfig = json.decodeFromString(SampleProfileConfig.serializer(), profile!!)
        assertEquals("Sample Profile", profileConfig.title)

        val nested = provider.getRemoteConfig("sample_deeply_nested")
        val nestedConfig = json.decodeFromString(SampleDeeplyNestedConfig.serializer(), nested!!)
        assertEquals(2, nestedConfig.entries.size)

        val preview = provider.getRemoteConfig("sample_preview_config")
        val previewConfig = json.decodeFromString(SamplePreviewConfig.serializer(), preview!!)
        assertEquals(5, previewConfig.maxItems)

        val message = provider.getRemoteConfig("sample_message_envelope")
        val envelope = json.decodeFromString(SampleMessageEnvelope.serializer(), message!!)
        assertEquals(42, (envelope.response as SampleResponseC).amount)

        val choice = provider.getRemoteConfig("sample_choice_config")
        val choiceConfig = json.decodeFromString(SampleChoiceConfig.serializer(), choice!!)
        val testVariant = choiceConfig.value as SampleChoiceVariant.TestVariant
        assertEquals("tiered-pricing", testVariant.experimentName)
    }
}
