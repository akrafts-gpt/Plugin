package io.github.remote.konfig;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public final class RemoteKonfigPlugin implements Plugin<Project> {
    @Override
    public void apply(Project target) {
        target.getPlugins().withId("com.android.application", plugin -> {
            Project apiProject = target.getRootProject().findProject(":api");
            if (apiProject == null) {
                throw new IllegalStateException("remote-konfig plugin requires a project named ':api'");
            }
            target.getDependencies().add("implementation", apiProject);
            target.getLogger().info("remote-konfig applied: added :api dependency to " + target.getPath());
        });
    }
}
