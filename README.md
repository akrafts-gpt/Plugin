# remote-konfig

![CI](https://github.com/akrafts-gpt/Plugin/actions/workflows/build.yml/badge.svg)

remote-konfig is a Gradle plugin, API, and code generator that wires a remote configuration provider into Android apps. Annotate a Kotlin data class with `@HiltRemoteConfig` and the tooling will:

- Bind the class to a remote config entry (for example, a Firebase parameter) so it can be injected through Hilt.
- Generate a strongly typed editor screen (using KSP and KotlinPoet) that developers can surface in an internal QA or debug menu.
- Provide a development-only override store so values can be changed locally without touching the upstream provider.

The repository contains:

- `plugin/` – a Gradle plugin that automatically adds the API dependency to Android application modules.
- `api/` – an Android library containing the `RemoteConfigProvider` contract, a development-aware `OverrideStore`, and UI contracts for generated editors.
- `sample/app/` – a Hilt-enabled sample app demonstrating usage and override flows.

## Getting started

### Prerequisites
- Java 17+
- Android Studio Ladybug or newer

### Build the sample

```bash
./gradlew :sample:app:assembleDebug
```

> **Note:** The project uses the Gradle 8.14.3 wrapper distribution. If you need to restore
> `gradle/wrapper/gradle-wrapper.jar`, download
> `https://services.gradle.org/distributions/gradle-8.14.3-bin.zip` and copy
> `gradle-8.14.3/lib/gradle-wrapper.jar` into `gradle/wrapper/`.

### Run the sample

1. Open the project in Android Studio.
2. Run the `sample-app` configuration on a device or emulator.
3. The main screen shows the `welcome` message returned by the fake remote config provider.
4. Use the override controls to persist a custom value. Re-open the activity to observe the stored override.

### Apply the plugin

In an Android application module:

```kotlin
plugins {
    id("com.android.application")
    kotlin("android")
    id("io.github.remote.konfig")
}
```

The plugin adds `implementation(project(":api"))` automatically so the app can inject `RemoteConfigProvider` and the generated config bindings.

### Annotate a config model

1. Declare a `@Serializable` data class that mirrors the payload returned by your remote config service.
2. Annotate the class with `@HiltRemoteConfig(key = "welcome_message")`.
3. When KSP runs, remote-konfig will generate:
   - A Hilt module that deserialises the remote payload (or a saved override) into your type.
   - A Compose-powered editor fragment that can be injected into any debug-only entry point.
   - A `RemoteConfigScreen` implementation that surfaces the editor in a list of tools.

### Development overrides

The generated editors talk to `OverrideStore`, a persistence layer that only activates while the app is in development mode (for example `BuildConfig.DEBUG`). Use the convenience factory to obtain the default implementation:

```kotlin
val overrides = OverrideStore.default(
    context = applicationContext,
    developmentMode = BuildConfig.DEBUG,
)
```

When development mode is enabled, overrides are saved to a `DataStore` on the device. Subsequent requests for the same config return the overridden payload, allowing rapid iteration without touching the upstream backend. In production builds (when `developmentMode` is `false`), the store becomes a no-op and the app always consumes the remote provider's values.

## Roadmap

- [ ] KSP integration for configuration schemas
- [ ] Remote backends (Firebase, LaunchDarkly, etc.)
- [ ] Compose samples

## License

Apache 2.0. See [LICENSE](LICENSE).
