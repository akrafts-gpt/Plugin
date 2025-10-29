package io.github.remote.konfig.sample

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.remote.konfig.OverrideStore

@HiltAndroidApp
class RemoteKonfigSampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        OverrideStore.loadFromPreferences(getSharedPreferences(PREFS_NAME, MODE_PRIVATE))
    }

    companion object {
        const val PREFS_NAME: String = "remote_konfig_overrides"
    }
}
