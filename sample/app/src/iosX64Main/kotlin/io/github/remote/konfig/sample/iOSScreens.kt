package io.github.remote.konfig.sample

import io.github.remote.konfig.OverrideStore
import io.github.remote.konfig.RemoteConfigProvider
import io.github.remote.konfig.RemoteConfigScreen
import io.github.remote.konfig.generated.DeeplyNestedShowcaseConfigRemoteConfigScreen
import io.github.remote.konfig.generated.NotificationShowcaseConfigRemoteConfigScreen
import io.github.remote.konfig.generated.ProfileOptionsConfigRemoteConfigScreen
import io.github.remote.konfig.generated.WelcomeExperienceConfigRemoteConfigScreen

actual fun createRemoteConfigScreens(
    remoteConfigProvider: RemoteConfigProvider,
    overrideStore: OverrideStore
): List<RemoteConfigScreen> = listOf(
    DeeplyNestedShowcaseConfigRemoteConfigScreen(remoteConfigProvider, overrideStore),
    NotificationShowcaseConfigRemoteConfigScreen(remoteConfigProvider, overrideStore),
    ProfileOptionsConfigRemoteConfigScreen(remoteConfigProvider, overrideStore),
    WelcomeExperienceConfigRemoteConfigScreen(remoteConfigProvider, overrideStore)
)
