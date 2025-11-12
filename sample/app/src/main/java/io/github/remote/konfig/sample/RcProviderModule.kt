package io.github.remote.konfig.sample

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.remote.konfig.RemoteConfigProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RcProviderModule {

    @Binds
    @Singleton
    abstract fun bindRemoteConfigProvider(impl: FakeRemoteConfigProvider): RemoteConfigProvider

}
