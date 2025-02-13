package io.jenkins.plugins.dfcli.actions;

import hudson.model.BuildBadgeAction;

/**
 * Represents the build-info URL Action with the Distroface icon.
 */
public class BuildInfoBuildBadgeAction implements BuildBadgeAction {
    private final String url;

    public BuildInfoBuildBadgeAction(String url) {
        this.url = url;
    }

    public String getIconFileName() {
        return "/plugin/dfcli/icons/distroface-icon.png";
    }

    public String getDisplayName() {
        return "Distroface Build Info";
    }

    public String getUrlName() {
        return this.url;
    }
}
