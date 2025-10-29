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

toolchainManagement {
    jvm {
        jdkDownload {
            repositories {
                jdkRepository("temurin")
            }
        }
    }
}

rootProject.name = "remote-konfig"

include(":api")
include(":sample:app")
