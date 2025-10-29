plugins {
    kotlin("jvm") version "2.0.21" apply false
}

allprojects {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
