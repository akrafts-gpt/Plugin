import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.21"
    `java-gradle-plugin`
    `maven-publish`
}

group = "io.github.remote.konfig"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(gradleApi())
}

gradlePlugin {
    plugins {
        create("remoteKonfig") {
            id = "io.github.remote.konfig"
            implementationClass = "io.github.remote.konfig.RemoteKonfigPlugin"
            displayName = "Remote Konfig Gradle Plugin"
            description = "Adds the remote-konfig API dependency to Android application modules."
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        if (name == "pluginMaven") {
            artifactId = "remote-konfig-gradle-plugin"
        }
    }
}
