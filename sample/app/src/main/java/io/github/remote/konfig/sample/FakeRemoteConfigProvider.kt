package io.github.remote.konfig.sample

import io.github.remote.konfig.OverrideStore
import io.github.remote.konfig.RemoteConfigProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class FakeRemoteConfigProvider @Inject constructor(
    private val overrideStore: OverrideStore
) : RemoteConfigProvider {

    private val json = Json {
        encodeDefaults = true
        prettyPrint = true
    }

    private val defaultConfigs: Map<String, String> = mapOf(
        "welcome" to json.encodeToString(WelcomeConfig()),
        "sample_profile_config" to json.encodeToString(
            SampleProfileConfig(
                title = "Sample Profile",
                contactNumber = "+1-800-555-0101",
                provider = "RemoteKonfig",
                region = "US",
                lastUpdatedEpochMillis = 1_700_000_000_000,
            )
        ),
        "sample_profile_with_option" to json.encodeToString(
            SampleProfileWithOptionConfig(
                title = "Profile With Option",
                contactNumber = "+1-800-555-0102",
                provider = "RemoteKonfig",
                region = "CA",
                lastUpdatedEpochMillis = 1_700_050_000_000,
                option = SampleOption.OPTION_TWO,
            )
        ),
        "sample_deeply_nested" to json.encodeToString(
            SampleDeeplyNestedConfig(
                title = "Deeply Nested",
                contactNumber = "+1-800-555-0103",
                provider = "RemoteKonfig",
                region = "EU",
                lastUpdatedEpochMillis = 1_700_100_000_000,
                option = SampleOption.OPTION_THREE,
                mode = SkipBehavior.SkippableStart,
                enabled = true,
                detail = SampleDetails(
                    label = "Primary",
                    highlighted = true,
                    summary = SampleEntry(label = "Summary", highlighted = false)
                ),
                entries = listOf(
                    SampleEntry(label = "First", highlighted = true),
                    SampleEntry(label = "Second", highlighted = false)
                ),
                tags = listOf("featured", "beta"),
            )
        ),
        "sample_preview_config" to json.encodeToString(
            SamplePreviewConfig(
                title = "Preview",
                enabled = true,
                maxItems = 5,
                expirationMillis = 86_400_000,
                selection = SampleEnum.OPTION_B,
                nested = PreviewNestedItem(nestedString = "Nested", nestedBool = false),
                nestedList = listOf(
                    PreviewNestedItem(nestedString = "One", nestedBool = true),
                    PreviewNestedItem(nestedString = "Two", nestedBool = false)
                ),
            )
        ),
        "sample_message_envelope" to json.encodeToString(
            SampleMessageEnvelope(
                request = SampleRequestA(id = 7),
                response = SampleResponseC(amount = 42),
            )
        ),
        "sample_choice_config" to json.encodeToString(
            SampleChoiceConfig(
                value = SampleChoiceVariant.TestVariant(
                    variant = "test",
                    experimentName = "tiered-pricing",
                    tier1 = SampleTier(
                        pretext = "Starter",
                        title = "Essential",
                        cta = "Choose Plan",
                        isFree = true,
                        image = SampleChoiceImage.CROWN,
                        points = listOf(
                            SampleBulletPoint(
                                icon = SampleChoiceBulletIcon.BELL,
                                title = "Alerts",
                                description = "Get notified instantly",
                            ),
                            SampleBulletPoint(
                                icon = SampleChoiceBulletIcon.ROCKET,
                                title = "Launch",
                                description = "Fast-track onboarding",
                            )
                        ),
                        description = "Ideal for getting started",
                    ),
                    tier2 = SampleTier(
                        pretext = "Premium",
                        title = "Professional",
                        cta = "Upgrade",
                        isFree = false,
                        image = SampleChoiceImage.CROWN,
                        points = listOf(
                            SampleBulletPoint(
                                icon = SampleChoiceBulletIcon.SHIELD,
                                title = "Security",
                                description = "Advanced threat protection",
                            ),
                        ),
                        description = "Unlock every feature",
                    )
                )
            )
        )
    )

    override fun getRemoteConfig(key: String): String? {
        return defaultConfigs[key]
    }

    fun getActiveConfig(key: String): String? {
        return overrideStore.getOverride(key) ?: defaultConfigs[key]
    }
}
