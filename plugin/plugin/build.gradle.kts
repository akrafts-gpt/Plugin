plugins {
    kotlin("jvm") version "2.0.21"
    `java-gradle-plugin`
    `maven-publish`
}

group = "io.github.remote.konfig"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
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
    publications {
        named<MavenPublication>("pluginMaven") {
            artifactId = "remote-konfig-gradle-plugin"
        }
    }
}
