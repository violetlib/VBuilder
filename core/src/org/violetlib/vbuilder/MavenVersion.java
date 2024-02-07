/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vbuilder;

import org.jetbrains.annotations.*;

/**
  A representation of a Maven artifact version for the purposes of comparison and sorting.
*/

public final class MavenVersion
  implements Comparable<MavenVersion>
{
    public static @NotNull MavenVersion create(int major, int minor, boolean isSnapshot)
      throws IllegalArgumentException
    {
        if (major < 0) {
            throw new IllegalArgumentException("Major version must be non-negative");
        }
        if (minor < 0) {
            throw new IllegalArgumentException("Major version must be non-negative");
        }
        String text = major + "." + minor;
        if (isSnapshot) {
            text = text + "-SNAPSHOT";
        }
        return new MavenVersion(text, major, minor, isSnapshot);
    }

    public static @NotNull MavenVersion parse(@NotNull String original)
    {
        String majorText = "";
        String minorText = "";
        boolean isSnapshot = false;

        String s = original;
        String suffix = "-SNAPSHOT";
        if (s.endsWith(suffix)) {
            s = s.substring(0, s.length() - suffix.length());
            isSnapshot = true;
        }

        int pos = s.indexOf('.');
        if (pos >= 0) {
            majorText = s.substring(0, pos);
            minorText = s.substring(pos + 1);
            int pos2 = minorText.indexOf('.');
            if (pos2 >= 0) {
                minorText = minorText.substring(0, pos2);
            }
        }

        int major = 0;
        int minor = 0;

        try {
            major = Integer.parseInt(majorText);
        } catch (NumberFormatException ignore) {
        }

        try {
            minor = Integer.parseInt(minorText);
        } catch (NumberFormatException ignore) {
        }

        return new MavenVersion(original, major, minor, isSnapshot);
    }

    private final @NotNull String original;
    public final int major;
    public final int minor;
    public final boolean isSnapshot;

    private MavenVersion(@NotNull String original, int major, int minor, boolean isSnapshot)
    {
        this.original = original;
        this.major = major;
        this.minor = minor;
        this.isSnapshot = isSnapshot;
    }

    @Override
    public int compareTo(@NotNull MavenVersion o)
    {
        if (major != o.major) {
            return major - o.major;
        }
        if (minor != o.minor) {
            return minor - o.minor;
        }
        if (isSnapshot != o.isSnapshot) {
            return isSnapshot ? -1 : 1;
        }
        return 0;
    }

    @Override
    public @NotNull String toString()
    {
        return original;
    }
}
