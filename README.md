# remote-konfig

![CI](https://github.com/akrafts-gpt/Plugin/actions/workflows/build.yml/badge.svg)

remote-konfig is a type-safe remote configuration stack for Android built around Hilt, Kotlin Symbol Processing (KSP), and Compose-powered debug tools. Annotate your Kotlinx Serialization models once and remote-konfig will generate:

- Hilt modules that deserialize JSON payloads provided by your backend and expose the strongly-typed configs to the rest of the app.
- Override-aware editors and fragment screens so product and QA teams can inspect and tweak payloads directly on-device.
- An opinionated debug UI backed by an `OverrideStore` that persists overrides with DataStore for deterministic repro steps.

## Why remote-konfig?

Traditional remote config integrations trade correctness for speed: they ship stringly-typed dictionaries, hand-written parsing code, and ad-hoc editing screens. remote-konfig embraces the ideas outlined in the Medium draft—config schemas are declared once and the Gradle tooling keeps the Hilt graph, UI, and JSON parsing in sync. The result is a safer iteration loop and fewer papercuts when collaborating with design or experimentation teams.

## How it works

1. **Declare a schema** – Define a `@Serializable` Kotlin data class and annotate it with `@HiltRemoteConfig(key = "...")`. The annotation is provided by `api/src/main/kotlin/io/github/remote/konfig/HiltRemoteConfig.kt` and marks the class as the single source of truth for that remote key.
2. **Generate integrations** – `processor/src/main/kotlin/io/github/remote/konfig/processor/RemoteConfigProcessor.kt` runs under KSP and emits:
   - A Hilt module that pulls JSON from `RemoteConfigProvider` and deserializes it with Kotlinx Serialization.
   - A `RemoteConfigEditor` implementation describing every editable field, including nested, list, enum, and polymorphic structures.
   - A `RemoteConfigScreen` wrapper that renders a Compose dialog via `RemoteConfigDialogFragment` so you can surface editors anywhere in the app.
3. **Persist overrides** – The shared `OverrideStore` in `api/src/main/kotlin/io/github/remote/konfig/OverrideStore.kt` caches overrides in memory and mirrors them to a preferences DataStore. Edits made in the generated dialogs survive process deaths and are easy to share as JSON payloads.

## Module overview

- `plugin/` – a Gradle plugin that automatically adds the API dependency to Android application modules so configs can be injected without manual wiring.
- `api/` – Kotlinx Serialization-friendly contracts such as `RemoteConfigProvider`, `OverrideStore`, and `RemoteConfigScreen`.
- `processor/` – the KSP processor that creates Hilt modules, editors, and screens at build time.
- `processor/runtime/` – the Compose UI, dialog fragments, and editor runtime types consumed by the generated code.
- `sample/` – a showcase app demonstrating multi-screen editors, override flows, and Fake remote config providers.

## Getting started

### 1. Apply the Gradle plugin

In each Android application module that should consume remote configs:

```kotlin
plugins {
    id("com.android.application")
    kotlin("android")
    id("io.github.remote.konfig")
}
```

The plugin injects `implementation(project(":api"))` automatically so your app can `@Inject` generated configs without remembering the dependency manually.

### 2. Wire the dependencies

Add the runtime UI and processor modules alongside Kotlinx Serialization, Hilt, and KSP:

```kotlin
dependencies {
    implementation(project(":processor:runtime")) // Compose debug dialog + editors
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    ksp(project(":processor")) // Generates modules, editors, and screens
}
```

### 3. Provide remote JSON

Implement `RemoteConfigProvider` once in your Hilt graph. The interface (`api/src/main/kotlin/io/github/remote/konfig/RemoteConfigProvider.kt`) returns raw JSON for a given key; it can be backed by Firebase Remote Config, LaunchDarkly, or any in-house service.

### 4. Declare your configs

```kotlin
@Serializable
@HiltRemoteConfig(WelcomeExperienceConfig.KEY)
data class WelcomeExperienceConfig(
    val text: String = "Welcome to Remote Konfig!",
    val enabled: Boolean = true,
) {
    companion object { const val KEY = "welcome" }
}
```

After syncing the project, the generated artifacts include:

- `WelcomeExperienceConfigRemoteConfigModule` – contributes a `@Provides` method that decodes JSON into `WelcomeExperienceConfig` and makes it injectable.
- `WelcomeExperienceConfigRemoteConfigEditor` – exposes metadata for each field so the dialog can read and mutate values.
- `WelcomeExperienceConfigRemoteConfigScreen` – a `RemoteConfigScreen` implementation that launches the Compose editor dialog with the correct serializers and titles.

### 5. Surface the editors in debug builds

Inject the generated `RemoteConfigScreen` set (`Set<RemoteConfigScreen>`) and decide how to present them. The sample app lists each screen and calls `screen.show(fragmentManager)` to launch the dialog. QA, PM, or design can now edit payloads, persist overrides via `OverrideStore`, and share JSON dumps without rebuilding the app.

## Sample app

To explore the end-to-end flow, build and run the `sample/app` module:

```bash
./gradlew :sample:app:assembleDebug
```

Launch `sample-app` from Android Studio and tap any entry to open its generated editor. The dialog reads remote values via the fake provider in `sample/app/src/main/kotlin/io/github/remote/konfig/sample/SampleApp.kt` and writes overrides through the shared `OverrideStore`.

> **Note:** The project uses the Gradle 8.14.3 wrapper distribution. If you need to restore
> `gradle/wrapper/gradle-wrapper.jar`, download
> `https://services.gradle.org/distributions/gradle-8.14.3-bin.zip` and copy
> `gradle-8.14.3/lib/gradle-wrapper.jar` into `gradle/wrapper/`.

## License

Apache 2.0. See [LICENSE](LICENSE).
