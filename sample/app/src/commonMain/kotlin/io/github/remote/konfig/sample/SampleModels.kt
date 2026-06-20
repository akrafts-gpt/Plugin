package io.github.remote.konfig.sample

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.remote.konfig.HiltRemoteConfig
import io.github.remote.konfig.OverrideStore
import io.github.remote.konfig.RemoteConfigProvider
import io.github.remote.konfig.RemoteConfigScreen
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
@HiltRemoteConfig(DeeplyNestedShowcaseConfig.KEY)
data class DeeplyNestedShowcaseConfig(
    val title: String = "Remote Konfig Spotlight",
    val contactNumber: String = "+1-555-KONFIG",
    val provider: String = "Remote Konfig",
    val region: String = "Global",
    val lastUpdatedEpochMillis: Long = 1_708_565_200_000,
    val option: SampleOption = SampleOption.OPTION_ONE,
    val mode: SkipBehavior = SkipBehavior.SkippableStart,
    val enabled: Boolean = true,
    val detail: SampleDetails = SampleDetails(
        label = "New onboarding flow",
        highlighted = true,
        summary = SampleEntry(
            label = "Guided setup",
            highlighted = true,
        ),
    ),
    val entries: List<SampleEntry> = listOf(
        SampleEntry(label = "Step 1", highlighted = true),
        SampleEntry(label = "Step 2", highlighted = false),
        SampleEntry(label = "Step 3", highlighted = false),
    ),
    val tags: List<String> = listOf("beta", "onboarding", "remote-konfig"),
) {
    companion object {
        const val KEY: String = "deeply_nested_showcase"
    }
}

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
enum class SkipBehavior(val skipStart: Boolean, val skipMiddle: Boolean) {
    @SerialName("SkippableStart")
    SkippableStart(true, false),

    @SerialName("SkippableMiddle")
    SkippableMiddle(false, true),

    @SerialName("SkippableStartMiddle")
    SkippableStartMiddle(true, true),

    @SerialName("NotSkippable")
    NotSkippable(false, false),
}

@Serializable
enum class SampleOption {
    OPTION_ONE,
    OPTION_TWO,
    OPTION_THREE,
}

@Serializable
@HiltRemoteConfig(ProfileOptionsConfig.KEY)
data class ProfileOptionsConfig(
    val title: String = "Remote Konfig Premium",
    val contactNumber: String = "+1-555-KONFIG",
    val provider: String = "Remote Konfig",
    val region: String = "Global",
    val lastUpdatedEpochMillis: Long = 1_708_565_200_000,
    val option: SampleOption = SampleOption.OPTION_TWO,
) {
    companion object {
        const val KEY: String = "profile_options"
    }
}

@Serializable
@HiltRemoteConfig(WelcomeExperienceConfig.KEY)
data class WelcomeExperienceConfig(
    val text: String = "Welcome to Remote Konfig!",
    val enabled: Boolean = true,
) {
    companion object {
        const val KEY: String = "welcome"
    }
}

@Serializable
sealed class NotificationType {
    @Serializable
    @SerialName("Push")
    data class Push(
        val title: String,
        val body: String,
        val channelId: String = "default_channel"
    ) : NotificationType()

    @Serializable
    @SerialName("Email")
    data class Email(
        val subject: String,
        val recipient: String,
        val isHtml: Boolean = false
    ) : NotificationType()

    @Serializable
    @SerialName("InApp")
    data class InApp(
        val message: String,
        val durationMillis: Int = 3000
    ) : NotificationType()
}

@Serializable
@HiltRemoteConfig(NotificationShowcaseConfig.KEY)
data class NotificationShowcaseConfig(
    val mainNotification: NotificationType = NotificationType.Push("Welcome!", "Thanks for joining us.", "onboarding"),
    val fallbackNotifications: List<NotificationType> = listOf(
        NotificationType.InApp("Please complete your profile", 5000)
    )
) {
    companion object {
        const val KEY = "notification_showcase"
    }
}

class FakeRemoteConfigProvider : RemoteConfigProvider {

    private val json = Json { encodeDefaults = true }

    private val remoteConfigs: Map<String, String> = mapOf(
        WelcomeExperienceConfig.KEY to json.encodeToString(
            WelcomeExperienceConfig(
                text = "Welcome to Remote Konfig!",
                enabled = true,
            )
        ),
        ProfileOptionsConfig.KEY to json.encodeToString(
            ProfileOptionsConfig(
                title = "Remote Konfig Premium",
                contactNumber = "+1-555-KONFIG",
                provider = "Remote Konfig",
                region = "Global",
                lastUpdatedEpochMillis = 1_708_565_200_000,
                option = SampleOption.OPTION_TWO,
            )
        ),
        NotificationShowcaseConfig.KEY to json.encodeToString(
            NotificationShowcaseConfig()
        ),
        DeeplyNestedShowcaseConfig.KEY to json.encodeToString(
            DeeplyNestedShowcaseConfig()
        ),
    )

    override fun getRemoteConfig(key: String): String? = remoteConfigs[key]
}

expect fun createRemoteConfigScreens(
    remoteConfigProvider: RemoteConfigProvider,
    overrideStore: OverrideStore
): List<RemoteConfigScreen>

@Composable
fun SampleAppHome(
    modifier: Modifier = Modifier,
    entries: List<ScreenEntry>,
    onEntrySelected: (ScreenEntry) -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = "Welcome to the Remote Konfig sample app",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Preview the generated editors for a curated set of configs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    )
                }
            }
            items(entries) { entry ->
                RemoteConfigCard(entry = entry, onClick = { onEntrySelected(entry) })
            }
        }
    }
}

@Composable
private fun RemoteConfigCard(
    entry: ScreenEntry,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
            HighlightPill(
                label = "Type",
                value = entry.typeName,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            HighlightPill(
                label = "Key",
                value = entry.key,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun HighlightPill(
    label: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = CardDefaults.shape,
    ) {
        Text(
            text = "$label: $value",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Immutable
data class ScreenEntry(
    val id: String,
    val title: String,
    val typeName: String,
    val key: String,
    val screen: RemoteConfigScreen,
)

data class RemoteConfigMetadata(
    val typeName: String,
    val key: String,
)

val SampleScreenMetadata = mapOf(
    DeeplyNestedShowcaseConfig.KEY to RemoteConfigMetadata(
        typeName = DeeplyNestedShowcaseConfig::class.simpleName ?: "DeeplyNestedShowcaseConfig",
        key = DeeplyNestedShowcaseConfig.KEY,
    ),
    WelcomeExperienceConfig.KEY to RemoteConfigMetadata(
        typeName = WelcomeExperienceConfig::class.simpleName ?: "WelcomeExperienceConfig",
        key = WelcomeExperienceConfig.KEY,
    ),
    ProfileOptionsConfig.KEY to RemoteConfigMetadata(
        typeName = ProfileOptionsConfig::class.simpleName ?: "ProfileOptionsConfig",
        key = ProfileOptionsConfig.KEY,
    ),
    NotificationShowcaseConfig.KEY to RemoteConfigMetadata(
        typeName = NotificationShowcaseConfig::class.simpleName ?: "NotificationShowcaseConfig",
        key = NotificationShowcaseConfig.KEY,
    ),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
)

@Composable
fun SampleAppTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
