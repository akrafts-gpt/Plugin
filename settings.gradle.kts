pluginManagement {
    includeBuild("plugin")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(org.gradle.api.initialization.resolve.RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "remote-konfig"

include(":api")
include(":processor")
include(":sample:app")

// Make the Android Studio bundled JDK discoverable on macOS machines so Gradle's toolchain lookup
// succeeds without requiring additional resolver plugins.
val isMacOs = System.getProperty("os.name")?.startsWith("Mac", ignoreCase = true) == true
if (isMacOs) {
    val androidStudioJbr = file("/Applications/Android Studio.app/Contents/jbr/Contents/Home")
    if (androidStudioJbr.isDirectory) {
        val existing = System.getProperty("org.gradle.java.installations.paths")
        val paths = listOfNotNull(existing, androidStudioJbr.absolutePath)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(separator = java.io.File.pathSeparator)

        if (paths.isNotBlank()) {
            System.setProperty("org.gradle.java.installations.paths", paths)
        }
    }
}
