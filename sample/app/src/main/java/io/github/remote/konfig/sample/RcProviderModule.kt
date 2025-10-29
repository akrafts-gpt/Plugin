package io.github.remote.konfig.sample

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.remote.konfig.RemoteConfigProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RcProviderModule {

    @Provides
    @Singleton
    fun provideRemoteConfigProvider(): RemoteConfigProvider = FakeRemoteConfigProvider()
}
