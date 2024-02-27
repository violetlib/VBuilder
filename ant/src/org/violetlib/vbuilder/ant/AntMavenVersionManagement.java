/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vbuilder.ant;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.PropertyHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.collections.IList;
import org.violetlib.collections.IMap;
import org.violetlib.vbuilder.MavenCoordinates;
import org.violetlib.vbuilder.MavenVersionManagement;
import org.violetlib.vbuilder.Reporter;

import java.util.*;

/**
  Collect preferred versions of Maven repo artifacts for a project.

  Maven artifacts are identified by a key with the form "<groupID>:<artifactID>".
  <p>
  This implementation uses Ant properties to store the mapping from artifact key to version, so that version
  selections are inherited when using the Ant task.
*/

public class AntMavenVersionManagement
  implements MavenVersionManagement
{
    public static @NotNull AntMavenVersionManagement get(@NotNull Project project, @NotNull Reporter reporter)
    {
        return INSTANCES.computeIfAbsent(project, (k) -> create(project, reporter));
    }

    private static @NotNull AntMavenVersionManagement create(@NotNull Project project, @NotNull Reporter reporter)
    {
        return new AntMavenVersionManagement(project, reporter);
    }

    private static final @NotNull WeakHashMap<Project,AntMavenVersionManagement> INSTANCES = new WeakHashMap<>();

    private final @NotNull Project project;
    private final @NotNull Reporter reporter;
    private boolean isGlobalInstalled;

    private AntMavenVersionManagement(@NotNull Project project, @NotNull Reporter reporter)
    {
        this.project = project;
        this.reporter = reporter;
    }

    @Override
    public @Nullable String getPreferredVersion(@NotNull String key)
    {
        String prop = toPropertyName(key);
        return (String) PropertyHelper.getPropertyHelper(project).getProperty(prop);
    }

    @Override
    public void installGlobalPreferredVersions(@NotNull IMap<String,String> bindings)
    {
        if (!isGlobalInstalled) {
            isGlobalInstalled = true;
            bindings.visit(this::setPreferredVersion);
        }
    }

    @Override
    public void setPreferredVersion(@NotNull String key, @NotNull String version)
    {
        PropertyHelper ph = PropertyHelper.getPropertyHelper(project);
        String prop = toPropertyName(key);
        String existing = (String) PropertyHelper.getPropertyHelper(project).getProperty(prop);
        if (existing == null) {
            if (isValidKey(key)) {
                reporter.verbose("Setting preferred version for " + key + " to " + version);
                ph.setNewProperty(prop, version);
            } else {
                reporter.error("Invalid artifact key: " + key);
            }
        } else if (!existing.equals(version)) {
            reporter.info("Override ignored for " + key + " preferred version (" + version + ")");
        }
    }

    public boolean isValidKey(@NotNull String s)
    {
        MavenCoordinates mc = MavenCoordinates.parse(s);
        return mc != null && mc.version == null;
    }

    @Override
    public void logPreferredVersions(@NotNull Reporter reporter)
    {
        Hashtable<String,Object> props = PropertyHelper.getPropertyHelper(project).getProperties();
        List<String> names = new ArrayList<>(props.keySet());
        names.sort(String::compareToIgnoreCase);
        for (String name : names) {
            String key = fromPropertyName(name);
            if (key != null) {
                String version = getPreferredVersion(key);
                if (version != null) {
                    reporter.verbose("Use " + key + ": " + version);
                }
            }
        }
    }

    private static final @NotNull String PREFIX = "Artifact_";
    private static final @NotNull String SUFFIX = "_Version";

    private @NotNull String toPropertyName(@NotNull String key)
    {
        return PREFIX + key + SUFFIX;
    }

    private @Nullable String fromPropertyName(@NotNull String propertyName)
    {
        if (propertyName.startsWith(PREFIX) && propertyName.endsWith(SUFFIX)) {
            String s = propertyName.substring(PREFIX.length());
            int len = s.length();
            return s.substring(0, len - SUFFIX.length());
        }
        return null;
    }
}
