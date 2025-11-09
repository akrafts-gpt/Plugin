package io.github.remote.konfig.sample

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.remote.konfig.OverrideStore
import io.github.remote.konfig.RemoteConfigProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RcProviderModule {

    @Binds
    @Singleton
    abstract fun bindRemoteConfigProvider(impl: FakeRemoteConfigProvider): RemoteConfigProvider

    companion object {
        @Provides
        @Singleton
        fun provideOverrideStore(
            @ApplicationContext context: Context,
        ): OverrideStore {
            return OverrideStore(context)
        }
    }
}
