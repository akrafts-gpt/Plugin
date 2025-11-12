package io.github.remote.konfig.sample

import android.app.Application
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import io.github.remote.konfig.RemoteConfigProvider
import io.github.remote.konfig.RemoteConfigScreen
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@HiltAndroidApp
class SampleHiltApp : Application()

@Module
@InstallIn(SingletonComponent::class)
abstract class SampleRemoteConfigModule {
    @Binds
    @Singleton
    abstract fun bindRemoteConfigProvider(impl: FakeRemoteConfigProvider): RemoteConfigProvider
}

@Singleton
class FakeRemoteConfigProvider @Inject constructor() : RemoteConfigProvider {

    private val json = Json { encodeDefaults = true }

    private val remoteConfigs: Map<String, String> = mapOf(
        WelcomeConfig.KEY to json.encodeToString(
            WelcomeConfig(
                text = "Welcome to Remote Konfig!",
                enabled = true,
            )
        ),
        SampleProfileWithOptionConfig.KEY to json.encodeToString(
            SampleProfileWithOptionConfig(
                title = "Remote Konfig Premium",
                contactNumber = "+1-555-KONFIG",
                provider = "Remote Konfig",
                region = "Global",
                lastUpdatedEpochMillis = 1_708_565_200_000,
                option = SampleOption.OPTION_TWO,
            )
        ),
        SampleDeeplyNestedConfig.KEY to json.encodeToString(
            SampleDeeplyNestedConfig(
                title = "Remote Konfig Spotlight",
                contactNumber = "+1-555-KONFIG",
                provider = "Remote Konfig",
                region = "Global",
                lastUpdatedEpochMillis = 1_708_565_200_000,
                option = SampleOption.OPTION_ONE,
                mode = SkipBehavior.SkippableStart,
                enabled = true,
                detail = SampleDetails(
                    label = "New onboarding flow",
                    highlighted = true,
                    summary = SampleEntry(
                        label = "Guided setup",
                        highlighted = true,
                    ),
                ),
                entries = listOf(
                    SampleEntry(label = "Step 1", highlighted = true),
                    SampleEntry(label = "Step 2", highlighted = false),
                    SampleEntry(label = "Step 3", highlighted = false),
                ),
                tags = listOf("beta", "onboarding", "remote-konfig"),
            )
        ),
    )

    override fun getRemoteConfig(key: String): String? = remoteConfigs[key]
}

@AndroidEntryPoint
class ConfigListActivity : AppCompatActivity() {

    @Inject
    lateinit var screens: Set<@JvmSuppressWildcards RemoteConfigScreen>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SampleAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val entries = remember(screens) {
                        screens.mapNotNull { screen ->
                            val metadata = SampleScreenMetadata[screen.id] ?: return@mapNotNull null
                            ScreenEntry(
                                id = screen.id,
                                title = screen.title,
                                typeName = metadata.typeName,
                                key = metadata.key,
                                screen = screen,
                            )
                        }.sortedBy { it.title }
                    }
                    SampleAppHome(entries = entries) { entry ->
                        entry.screen.show(supportFragmentManager)
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleAppHome(
    entries: List<ScreenEntry>,
    onEntrySelected: (ScreenEntry) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
private data class ScreenEntry(
    val id: String,
    val title: String,
    val typeName: String,
    val key: String,
    val screen: RemoteConfigScreen,
)

private data class RemoteConfigMetadata(
    val typeName: String,
    val key: String,
)

private val SampleScreenMetadata = mapOf(
    SampleDeeplyNestedConfig.KEY to RemoteConfigMetadata(
        typeName = SampleDeeplyNestedConfig::class.simpleName ?: "SampleDeeplyNestedConfig",
        key = SampleDeeplyNestedConfig.KEY,
    ),
    WelcomeConfig.KEY to RemoteConfigMetadata(
        typeName = WelcomeConfig::class.simpleName ?: "WelcomeConfig",
        key = WelcomeConfig.KEY,
    ),
    SampleProfileWithOptionConfig.KEY to RemoteConfigMetadata(
        typeName = SampleProfileWithOptionConfig::class.simpleName ?: "SampleProfileWithOptionConfig",
        key = SampleProfileWithOptionConfig.KEY,
    ),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
)

private val SampleTypography = Typography()

@Composable
fun SampleAppTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SampleTypography,
        content = content,
    )
}

@Serializable
@HiltRemoteConfig(SampleDeeplyNestedConfig.KEY)
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
) {
    companion object {
        const val KEY: String = "sample_deeply_nested"
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
@HiltRemoteConfig(SampleProfileWithOptionConfig.KEY)
data class SampleProfileWithOptionConfig(
    val title: String,
    val contactNumber: String,
    val provider: String,
    val region: String,
    val lastUpdatedEpochMillis: Long,
    val option: SampleOption,
) {
    companion object {
        const val KEY: String = "sample_profile_with_option"
    }
}

@Serializable
@HiltRemoteConfig(WelcomeConfig.KEY)
data class WelcomeConfig(
    val text: String = "Hello",
    val enabled: Boolean = true,
) {
    companion object {
        const val KEY: String = "welcome"
    }
}
