package io.github.remote.konfig.sample

import io.github.remote.konfig.HiltRemoteConfig
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@HiltRemoteConfig("sample_profile_config")
data class SampleProfileConfig(
    val title: String,
    val contactNumber: String,
    val provider: String,
    val region: String,
    val lastUpdatedEpochMillis: Long,
)

@Serializable
enum class SampleOption {
    OPTION_ONE,
    OPTION_TWO,
    OPTION_THREE,
}

@Serializable
@HiltRemoteConfig("sample_profile_with_option")
data class SampleProfileWithOptionConfig(
    val title: String,
    val contactNumber: String,
    val provider: String,
    val region: String,
    val lastUpdatedEpochMillis: Long,
    val option: SampleOption,
)

@Serializable
@HiltRemoteConfig("sample_deeply_nested")
data class SampleDeeplyNestedConfig(
    val title: String,
    val contactNumber: String,
    val provider: String,
    val region: String,
    val lastUpdatedEpochMillis: Long,
    val option: SampleOption,
    val mode: SkipBehavior,
    val enabled: Boolean,
    val detail: SampleDetails,
    val entries: List<SampleEntry>,
    val tags: List<String>,
)

@Serializable
data class SampleDetails(
    val label: String,
    val highlighted: Boolean,
    val summary: SampleEntry,
)

@Serializable
data class SampleEntry(
    val label: String,
    val highlighted: Boolean,
)

@Serializable
enum class SampleEnum {
    OPTION_A,
    OPTION_B,
    OPTION_C,
}

@Serializable
data class PreviewNestedItem(
    val nestedString: String,
    val nestedBool: Boolean,
)

@Serializable
@HiltRemoteConfig("sample_preview_config")
data class SamplePreviewConfig(
    val title: String,
    val enabled: Boolean,
    val maxItems: Int,
    val expirationMillis: Long,
    val selection: SampleEnum,
    val nested: PreviewNestedItem,
    val nestedList: List<PreviewNestedItem>,
)

@Serializable
sealed interface SampleRequest

@Serializable
data class SampleRequestA(val id: Int) : SampleRequest

@Serializable
data class SampleRequestB(val payload: String) : SampleRequest

@Serializable
sealed interface SampleResponse

@Serializable
data class SampleResponseC(val amount: Long) : SampleResponse

@Serializable
data class SampleResponseD(val data: ByteArray) : SampleResponse {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SampleResponseD) return false
        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int = data.contentHashCode()
}

@Serializable
@HiltRemoteConfig("sample_message_envelope")
data class SampleMessageEnvelope(
    @Polymorphic val request: SampleRequest,
    @Polymorphic val response: SampleResponse,
)

@Serializable
@HiltRemoteConfig("sample_choice_config")
data class SampleChoiceConfig(
    @Polymorphic val value: SampleChoiceVariant,
)

@Serializable
sealed interface SampleChoiceVariant {
    val variant: String
    val experimentName: String

    @Serializable
    @SerialName("Baseline")
    data class Baseline(
        override val variant: String,
        override val experimentName: String,
    ) : SampleChoiceVariant

    @Serializable
    @SerialName("TestVariant")
    data class TestVariant(
        override val variant: String,
        override val experimentName: String,
        val tier1: SampleTier,
        val tier2: SampleTier,
    ) : SampleChoiceVariant
}

@Serializable
data class SampleTier(
    val pretext: String,
    val title: String,
    val cta: String,
    val isFree: Boolean,
    val image: SampleChoiceImage,
    val points: List<SampleBulletPoint>,
    val description: String,
)

@Serializable
data class SampleBulletPoint(
    val icon: SampleChoiceBulletIcon,
    val title: String,
    val description: String,
)

@Serializable
enum class SampleChoiceImage {
    @SerialName("CROWN")
    CROWN,
}

@Serializable
enum class SampleChoiceBulletIcon {
    @SerialName("BELL")
    BELL,

    @SerialName("SHIELD")
    SHIELD,

    @SerialName("ROCKET")
    ROCKET,
}

@Serializable
enum class SkipBehavior(val skipStart: Boolean, val skipMiddle: Boolean) {
    SkippableStart(true, false),
    SkippableMiddle(false, true),
    SkippableStartMiddle(true, true),
    NotSkippable(false, false)
}
