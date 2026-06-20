package io.github.remote.konfig.sample

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import io.github.remote.konfig.OverrideStore
import io.github.remote.konfig.RemoteConfigProvider
import io.github.remote.konfig.RemoteConfigScreen
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

actual fun createRemoteConfigScreens(
    remoteConfigProvider: RemoteConfigProvider,
    overrideStore: OverrideStore
): List<RemoteConfigScreen> = emptyList() // Android uses Hilt and doesn't need this manual list

@HiltAndroidApp
class SampleHiltApp : Application()

@Module
@InstallIn(SingletonComponent::class)
abstract class SampleRemoteConfigModule {
    @Binds
    @Singleton
    abstract fun bindRemoteConfigProvider(impl: FakeRemoteConfigHiltProvider): RemoteConfigProvider
}

@Singleton
class FakeRemoteConfigHiltProvider @Inject constructor() : RemoteConfigProvider {
    private val delegate = io.github.remote.konfig.sample.FakeRemoteConfigProvider()
    override fun getRemoteConfig(key: String): String? = delegate.getRemoteConfig(key)
}

@AndroidEntryPoint
class ConfigListActivity : AppCompatActivity() {

    @Inject
    lateinit var screens: Set<@JvmSuppressWildcards RemoteConfigScreen>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
                    SampleAppHome(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                        entries = entries,
                    ) { entry ->
                        entry.screen.show(supportFragmentManager)
                    }
                }
            }
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
