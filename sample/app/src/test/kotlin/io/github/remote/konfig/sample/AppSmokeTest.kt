package io.github.remote.konfig.sample

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.remote.konfig.RemoteConfigScreen
import javax.inject.Inject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(
    application = HiltTestApplication::class,
    manifest = Config.NONE,
    sdk = [34]
)
class AppSmokeTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var screens: Set<@JvmSuppressWildcards RemoteConfigScreen>

    @Test
    fun hiltProvidesRemoteConfigScreens() {
        hiltRule.inject()
        assertTrue(screens.isNotEmpty(), "Expected generated RemoteConfigScreen implementations")
    }
}
