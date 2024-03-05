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
import org.violetlib.collections.IMap;
import org.violetlib.vbuilder.MavenCoordinates;
import org.violetlib.vbuilder.MavenVersionManagement;
import org.violetlib.vbuilder.Reporter;
import org.violetlib.vbuilder.Scope;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.WeakHashMap;

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
        String prop = toVersionPropertyName(key);
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
        String prop = toVersionPropertyName(key);
        String existing = (String) PropertyHelper.getPropertyHelper(project).getProperty(prop);
        if (existing == null) {
            if (isValidKey(key)) {
                reporter.verbose("Setting preferred version for " + key + " to " + version);
                ph.setNewProperty(prop, version);
            } else {
                reporter.error("Invalid artifact key: " + key);
            }
        } else if (!existing.equals(version)) {
            reporter.info("Override ignored for " + key + " preferred version (" + existing + " supersedes " + version + ")");
        }
    }

    @Override
    public @Nullable Scope getScope(@NotNull String key)
    {
        String prop = toScopePropertyName(key);
        String encodedScope = (String) PropertyHelper.getPropertyHelper(project).getProperty(prop);
        return encodedScope != null ? decodeScope(encodedScope) : null;
    }

    @Override
    public void setScope(@NotNull String key, @NotNull Scope scope)
    {
        PropertyHelper ph = PropertyHelper.getPropertyHelper(project);
        String prop = toScopePropertyName(key);
        Scope existing = getScope(key);
        if (existing != null) {
            if (existing.equals(scope)) {
                return;
            }
            reporter.info("Override ignored for " + key + " scope (" + existing + " supersedes " + scope + ")");
        } else {
            reporter.verbose("Setting scope for " + key + " to " + scope + " " + this);
            String encodedScope = encodeScope(scope);
            if (encodedScope != null) {
                ph.setNewProperty(prop, encodedScope);
            }
        }
    }

    public boolean isValidKey(@NotNull String s)
    {
        MavenCoordinates mc = MavenCoordinates.parse(s);
        return mc != null && mc.version == null;
    }

    @Override
    public void logPreferredVersionsAndScopes(@NotNull Reporter reporter)
    {
        Hashtable<String,Object> props = PropertyHelper.getPropertyHelper(project).getProperties();
        List<String> names = new ArrayList<>(props.keySet());
        names.sort(String::compareToIgnoreCase);
        for (String name : names) {
            String key = fromVersionPropertyName(name);
            if (key != null) {
                String version = getPreferredVersion(key);
                Scope scope = getScope(key);
                if (version != null || scope != null) {
                    String message = "Use";
                    if (version != null) {
                        message = message + " " + version;
                    }
                    if (scope != null) {
                        message = message + " " + scope;
                    }
                    reporter.verbose(message);
                }
            }
        }
    }

    private static final @NotNull String PREFIX = "Artifact_";
    private static final @NotNull String VERSION_SUFFIX = "_Version";
    private static final @NotNull String SCOPE_SUFFIX = "_Scope";

    private @NotNull String toVersionPropertyName(@NotNull String key)
    {
        return PREFIX + key + VERSION_SUFFIX;
    }

    private @Nullable String fromVersionPropertyName(@NotNull String propertyName)
    {
        if (propertyName.startsWith(PREFIX) && propertyName.endsWith(VERSION_SUFFIX)) {
            String s = propertyName.substring(PREFIX.length());
            int len = s.length();
            return s.substring(0, len - VERSION_SUFFIX.length());
        }
        return null;
    }

    private @NotNull String toScopePropertyName(@NotNull String key)
    {
        return PREFIX + key + SCOPE_SUFFIX;
    }

    private @Nullable String fromScopePropertyName(@NotNull String propertyName)
    {
        if (propertyName.startsWith(PREFIX) && propertyName.endsWith(SCOPE_SUFFIX)) {
            String s = propertyName.substring(PREFIX.length());
            int len = s.length();
            return s.substring(0, len - SCOPE_SUFFIX.length());
        }
        return null;
    }

    private @Nullable String encodeScope(@NotNull Scope s)
    {
        if (s == Scope.COMPILE) {
            return "compile";
        }
        if (s == Scope.REQUIRED) {
            return "required";
        }
        if (s == Scope.RUNTIME) {
            return "runtime";
        }
        if (s == Scope.INCLUDED) {
            return "included";
        }
        return null;
    }

    private @Nullable Scope decodeScope(@NotNull String s)
    {
        if (s.equals("compile")) {
            return Scope.COMPILE;
        }
        if (s.equals("required")) {
            return Scope.REQUIRED;
        }
        if (s.equals("runtime")) {
            return Scope.RUNTIME;
        }
        if (s.equals("included")) {
            return Scope.INCLUDED;
        }
        return null;
    }
}
