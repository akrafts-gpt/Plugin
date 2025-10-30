# remote-konfig

![CI](https://github.com/akrafts-gpt/Plugin/actions/workflows/ci.yml/badge.svg)

remote-konfig is a Gradle plugin and Android API bundle that wires a remote configuration provider into Android apps. The repository contains:

- `plugin/` – a Gradle plugin that automatically adds the API dependency to Android application modules.
- `api/` – an Android library containing the `RemoteConfigProvider` contract and an `OverrideStore` with SharedPreferences helpers.
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

The plugin adds `implementation(project(":api"))` automatically so the app can inject `RemoteConfigProvider`.

## Roadmap

- [ ] KSP integration for configuration schemas
- [ ] Remote backends (Firebase, LaunchDarkly, etc.)
- [ ] Compose samples

## License

Apache 2.0. See [LICENSE](LICENSE).
