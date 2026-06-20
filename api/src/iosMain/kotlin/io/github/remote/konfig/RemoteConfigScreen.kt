package io.github.remote.konfig

actual interface RemoteConfigScreen {
    actual val id: String
    actual val title: String

    fun show()
}
