/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.collections.IList;
import org.violetlib.collections.IMap;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
  Collect preferred versions of Maven repo artifacts for a project.

  Maven artifacts are identified by a key with the form "<groupID>:<artifactID>".
*/

public class BasicMavenVersionManagement
  implements MavenVersionManagement
{
    public static @NotNull BasicMavenVersionManagement get(@NotNull Object context, @NotNull Reporter reporter)
    {
        return INSTANCES.computeIfAbsent(context, (k) -> create(reporter));
    }

    private static @NotNull BasicMavenVersionManagement create(@NotNull Reporter reporter)
    {
        return new BasicMavenVersionManagement(reporter);
    }

    private static final @NotNull WeakHashMap<Object,BasicMavenVersionManagement> INSTANCES = new WeakHashMap<>();

    private final @NotNull Reporter reporter;
    private final @NotNull Map<String,String> preferredVersions = new HashMap<>();
    private boolean isGlobalInstalled;

    private BasicMavenVersionManagement(@NotNull Reporter reporter)
    {
        this.reporter = reporter;
    }

    @Override
    public @Nullable String getPreferredVersion(@NotNull String key)
    {
        return preferredVersions.get(key);
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
        String existing = preferredVersions.get(key);
        if (existing == null) {
            if (isValidKey(key)) {
                reporter.verbose("Setting preferred version for " + key + " to " + version + " " + this);
                preferredVersions.put(key, version);
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
        IList<String> keys = IList.create(preferredVersions.keySet()).sort();
        for (String key : keys) {
            String version = preferredVersions.get(key);
            assert version != null;
            reporter.verbose("Using version " + version + " of " + key);
        }
    }
}
