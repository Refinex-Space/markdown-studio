package com.refinex.markdownstudio.welcome;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * The plugin loaded without a restart: shows the welcome tab. Written in Java because a Kotlin class
 * overrides every default method of the listener, its deprecated and experimental ones included.
 */
public final class WelcomeInstallListener implements DynamicPluginListener {
    @Override
    public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        WelcomeKt.welcomeAfterInstall(pluginDescriptor);
    }
}
