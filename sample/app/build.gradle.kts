plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
    id("io.github.remote.konfig")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    
    jvm("desktop")
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        configureEach {
            languageSettings.optIn("kotlin.expectactual.classes")
        }

        commonMain {
            dependencies {
                implementation(project(":api"))
                implementation(project(":processor:runtime"))
                implementation(libs.kotlinx.serialization.json)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
            }
        }
        
        androidMain {
            dependencies {
                implementation(libs.compose.activity)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.activity.ktx)
                implementation(libs.androidx.constraintlayout)
                implementation(libs.hilt.android)
                implementation(libs.hilt.navigation.compose)
                implementation(libs.material)
            }
        }
        
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "io.github.remote.konfig.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.remote.konfig.sample"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    "kspAndroid"(project(":processor"))
    "kspDesktop"(project(":processor"))
    "kspIosX64"(project(":processor"))
    "kspIosArm64"(project(":processor"))
    "kspIosSimulatorArm64"(project(":processor"))
    
    // Hilt only for Android
    "kapt"(libs.hilt.compiler)
    
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.hilt.android.testing)
    "kaptAndroidTest"(libs.hilt.compiler)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}

// Disable Kapt for desktop to avoid build errors
tasks.matching { it.name.contains("kapt") && it.name.contains("Desktop") }.configureEach {
    enabled = false
}

kapt {
    correctErrorTypes = true
}

compose.desktop {
    application {
        mainClass = "io.github.remote.konfig.sample.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "io.github.remote.konfig.sample"
            packageVersion = "1.0.0"
        }
    }
}
