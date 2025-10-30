package io.github.remote.konfig.sample

import androidx.annotation.Keep
import io.github.remote.konfig.HiltRemoteConfig
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
@HiltRemoteConfig("contentTutorialConfig_52465")
data class DemoContent(
    val scamName: String,
    val phoneNumber: String,
    val mobileProvider: String,
    val location: String,
    val timestamp: Long,
)

@Serializable
enum class Option {
    Option1,
    Option2,
    Option3,
}

@Keep
@Serializable
@HiltRemoteConfig("contentTutorialConfig_52432")
data class TestDemoContent(
    val scamName: String,
    val phoneNumber: String,
    val mobileProvider: String,
    val location: String,
    val timestamp: Long,
    val option: Option,
)

@Keep
@Serializable
@HiltRemoteConfig("contentTutorialConfig_52432_test2")
data class Test2DemoContent(
    val scamName: String,
    val phoneNumber: String,
    val mobileProvider: String,
    val location: String,
    val timestamp: Long,
    val option: Option,
    val mode: SkipMode,
    val enabled: Boolean,
    val innerField: Inner,
    val innerList: List<Inner2>,
    val srings: List<String>,
)

@Serializable
data class Inner(
    val prop1: String,
    val allo: Boolean,
    val inner2: Inner2,
)

@Serializable
data class Inner2(
    val prop1: String,
    val allo: Boolean,
)

@Serializable
enum class SampleEnum {
    OPTION_A,
    OPTION_B,
    OPTION_C,
}

@Serializable
data class PreviewNestedConfig(
    val nestedString: String,
    val nestedBool: Boolean,
)

@Keep
@Serializable
@HiltRemoteConfig("contentTutorialConfig_524322")
data class PreviewConfig(
    val aString: String,
    val aBoolean: Boolean,
    val anInt: Int,
    val aLong: Long,
    val anEnum: SampleEnum,
    val nestedObject: PreviewNestedConfig,
    val aList: List<PreviewNestedConfig>,
)

@Serializable
sealed interface BaseRequest

@Serializable
data class RequestA(val id: Int) : BaseRequest

@Serializable
data class RequestB(val s: String) : BaseRequest

@Serializable
sealed interface BaseResponse

@Serializable
data class ResponseC(val payload: Long) : BaseResponse

@Serializable
data class ResponseD(val payload: ByteArray) : BaseResponse

@Keep
@Serializable
@HiltRemoteConfig("contentTutorialConfig_5243222")
data class Message(
    @Polymorphic val request: BaseRequest,
    @Polymorphic val response: BaseResponse,
)

@Serializable
@HiltRemoteConfig("onBoardingPremiumChoice_64811")
data class PremiumChoiceConfig(
    @Polymorphic val value: PremiumChoiceVariant,
)

@Serializable
sealed interface PremiumChoiceVariant {
    val variant: String
    val experimentName: String

    @Serializable
    @SerialName("Baseline")
    data class Baseline(
        override val variant: String,
        override val experimentName: String,
    ) : PremiumChoiceVariant

    @Serializable
    @SerialName("TestVariant")
    data class TestVariant(
        override val variant: String,
        override val experimentName: String,
        val tier1: Tier,
        val tier2: Tier,
    ) : PremiumChoiceVariant
}

@Serializable
data class Tier(
    val pretext: String,
    val title: String,
    val cta: String,
    val isFree: Boolean,
    val image: PremiumChoiceImage,
    val points: List<BulletPoint>,
    val description: String,
)

@Serializable
data class BulletPoint(
    val icon: PremiumChoiceBulletIcon,
    val title: String,
    val description: String,
)

@Serializable
enum class PremiumChoiceImage {
    @SerialName("CROWN")
    CROWN,
}

@Serializable
enum class PremiumChoiceBulletIcon {
    @SerialName("BELL")
    BELL,

    @SerialName("SHIELD")
    SHIELD,

    @SerialName("ROCKET")
    ROCKET,
}

/**
 * Config for skip button in onboarding education
 */
@Keep
@Serializable
enum class SkipMode(val skipStart: Boolean, val skipMiddle: Boolean) {
    /**
     * Users see an option to skip the tutorial only on the first page (button I know how it works). They cannot skip the tutorial once they start.
     */
    SkippableStart(true, false),

    /**
     * Users don't see skip option on the first page (button I know how it works is not shown), they see skip option only after they start the tutorial.
     */
    SkippableMiddle(false, true),

    /**
     * Users see an option to skip the tutorial on the first page (button I know how it works) and in the middle of the tutorial (button skip). This is also
     * the default behaviour when the value for this key is empty.
     */
    SkippableStartMiddle(true, true),

    /**
     * Skipping tutorial is not available anywhere, users are forced to complete the tutorial.
     */
    NotSkippable(false, false),
}

