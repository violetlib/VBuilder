package org.violetlib.vbuilder;

import org.jetbrains.annotations.*;

import java.util.Objects;

/**
  An object representing Maven coordinates (groupID, artifactID and optional version).
*/

public final class MavenCoordinates
{
    public static @NotNull MavenCoordinates create(@NotNull String group,
                                                   @NotNull String artifactID,
                                                   @Nullable String version)
    {
        return new MavenCoordinates(group, artifactID, version);
    }

    /**
      Create an object representing Maven coordinates. The version attribute is optional.
      @param s The coordinates as text.
      @return the coordinates object, or null if the text is not valid.
    */

    public static @Nullable MavenCoordinates parse(@NotNull String s)
    {
        // The components are group ID, artifact ID, version.
        // The version is optional.

        int pos = s.indexOf(':');
        if (pos > 0) {
            int pos2 = s.indexOf(':', pos+1);
            if (pos2 > pos+1) {
                String group = s.substring(0, pos);
                String artifactID = s.substring(pos+1, pos2);
                String vs = s.substring(pos2+1);
                String version = vs;
                int pos3 = vs.indexOf(':');
                if (pos3 >= 0) {
                    version = vs.substring(0, pos3);
                }
                return new MavenCoordinates(group, artifactID, version);
            } else if (pos2 < 0) {
                String group = s.substring(0, pos);
                String artifactID = s.substring(pos+1);
                return new MavenCoordinates(group, artifactID, null);
            }
        }
        return null;
    }

    public final @NotNull String group;
    public final @NotNull String artifactID;
    public final @NotNull String key;
    public final @Nullable String version;

    private MavenCoordinates(@NotNull String group, @NotNull String artifactID, @Nullable String version)
    {
        this.group = group;
        this.artifactID = artifactID;
        this.key = group + ':' + artifactID;
        this.version = version;
    }

    public boolean isSnapshot()
    {
        return version != null && version.contains("-SNAPSHOT");
    }

    public @NotNull MavenCoordinates withoutVersion()
    {
        if (version == null) {
            return this;
        }
        return new MavenCoordinates(group, artifactID, null);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof MavenCoordinates)) return false;
        MavenCoordinates that = (MavenCoordinates) o;
        return Objects.equals(group, that.group)
          && Objects.equals(artifactID, that.artifactID)
          && Objects.equals(key, that.key)
          && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(group, artifactID, key, version);
    }

    @Override
    public @NotNull String toString()
    {
        return version != null ? key + ":" + version : key;
    }

    /**
      Test a string to see if it is valid as a Maven artifact key (no version) or coordinates (with version).
    */

    public static boolean isValid(@NotNull String s)
    {
        return parse(s) != null;
    }

    /**
      Test a string to see if it is valid as a Maven artifact key (no version).
    */

    public static boolean isKeyOnly(@NotNull String s)
    {
        MavenCoordinates mc = parse(s);
        return mc != null && mc.version == null;
    }
}
