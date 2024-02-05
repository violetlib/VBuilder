package org.violetlib.vbuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import org.violetlib.collections.IList;

import org.jetbrains.annotations.*;
import org.violetlib.collections.IMap;

/**
  Collect preferred versions of Maven repo artifacts for a project.

  Maven artifacts are identified by a key with the form "<groupID>:<artifactID>".
*/

public class MavenVersionManagement
{
    public static @NotNull MavenVersionManagement get(@NotNull Object context, @NotNull Reporter reporter)
    {
        return INSTANCES.computeIfAbsent(context, (k) -> create(reporter));
    }

    /**
      Create a manager of Maven repo artifacts.
      Each project should create its own manager.
    */

    private static @NotNull MavenVersionManagement create(@NotNull Reporter reporter)
    {
        return new MavenVersionManagement(reporter);
    }

    private static final @NotNull WeakHashMap<Object,MavenVersionManagement> INSTANCES = new WeakHashMap<>();

    private final @NotNull Reporter reporter;
    private final @NotNull Map<String,String> preferredVersions = new HashMap<>();
    private boolean isGlobalInstalled;

    private MavenVersionManagement(@NotNull Reporter reporter)
    {
        this.reporter = reporter;
    }

    /**
      Return the preferred version for an artifact.
      @param key The artifact key.
      @return the preferred version, or null if none.
    */

    public @Nullable String getPreferredVersion(@NotNull String key)
    {
        return preferredVersions.get(key);
    }

    public void installGlobalPreferredVersions(@NotNull IMap<String,String> bindings)
    {
        if (!isGlobalInstalled) {
            isGlobalInstalled = true;
            bindings.visit(this::setPreferredVersion);
        }
    }

    /**
      Set the preferred version for an artifact. Once a version has been specified, subsequent attempts to specify a
      different version are ignored and logged.

      @param key The artifact key.
      @param version The version to set as the preferred version.
    */

    public void setPreferredVersion(@NotNull String key, @NotNull String version)
    {
        String existing = preferredVersions.get(key);
        if (existing == null) {
            if (isValidKey(key)) {
                //reporter.info("Setting preferred version for " + key + ": " + version + " " + this);
                preferredVersions.put(key, version);
            } else {
                reporter.error("Invalid artifact key: " + key);
            }
        } else if (!existing.equals(version)) {
            reporter.info("Ignoring attempt to select version " + version + " for " + key);
        }
    }

    public boolean isValidKey(@NotNull String s)
    {
        MavenCoordinates mc = MavenCoordinates.parse(s);
        return mc != null && mc.version == null;
    }

    public void logPreferredVersions()
    {
        IList<String> keys = IList.create(preferredVersions.keySet()).sort();
        for (String key : keys) {
            String version = preferredVersions.get(key);
            assert version != null;
            reporter.verbose("Using version " + version + " of " + key);
        }
    }
}
