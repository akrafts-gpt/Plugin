package io.github.remote.konfig.sample

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.remote.konfig.RemoteConfigProvider

@Module
@InstallIn(SingletonComponent::class)
object RemoteConfigModule {
    @Provides
    fun provideRemoteConfigProvider(): RemoteConfigProvider = FakeRemoteConfigProvider()
}

private class FakeRemoteConfigProvider : RemoteConfigProvider {
    override fun getRemoteConfig(key: String): String? {
        return when (key) {
            "welcome" -> """{"text":"Hello from Remote!"}"""
            else -> null
        }
    }
}
