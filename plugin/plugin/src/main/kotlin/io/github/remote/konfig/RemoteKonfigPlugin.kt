package io.github.remote.konfig

import org.gradle.api.Plugin
import org.gradle.api.Project

class RemoteKonfigPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.withId("com.android.application") {
            val apiProject = target.rootProject.findProject(":api")
                ?: error("remote-konfig plugin requires a project named ':api'")
            target.dependencies.add("implementation", apiProject)
            target.logger.info("remote-konfig applied: added :api dependency to ${target.path}")
        }
    }
}
