package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
  A description of the intended execution context for native code.
  This implementation supports macOS minimum releases.
*/

public final class NativeTarget
{
    public static @NotNull NativeTarget parse(@NotNull String s)
    {
        return new NativeTarget(s);
    }

    public static @NotNull NativeTarget create(@NotNull String arch,
                                               @Nullable String vendor,
                                               @NotNull String os,
                                               @Nullable String minOSVersion)
    {
        return new NativeTarget(arch, vendor, os, minOSVersion);
    }

    private final @NotNull String value;
    private final @NotNull String arch;
    private final @Nullable String vendor;
    private final @NotNull String os;
    private final @Nullable String minOSVersion;

    private NativeTarget(@NotNull String s)
    {
        value = s;

        int pos = s.indexOf('-');
        if (pos >= 0) {
            arch = s.substring(0, pos);
            s = s.substring(pos+1);
        } else {
            arch = "";
        }

        pos = s.indexOf('-');
        if (pos >= 0) {
            if (pos > 0) {
                vendor = s.substring(0, pos);
            } else {
                vendor = null;
            }
            s = s.substring(pos+1);
        } else {
            vendor = null;
        }

        if (s.startsWith("macos")) {
            os = "macos";
            minOSVersion = s.substring(5);
        } else {
            os = s;
            minOSVersion = null;
        }
    }

    private NativeTarget(@NotNull String arch,
                         @Nullable String vendor,
                         @NotNull String os,
                         @Nullable String minOSVersion)
    {
        this.arch = arch;
        this.vendor = vendor;
        this.os = os;
        this.minOSVersion = minOSVersion;

        StringBuilder sb = new StringBuilder();
        sb.append(arch);
        if (vendor != null) {
            sb.append("-");
            sb.append(vendor);
        }
        sb.append("-");
        sb.append(os);
        if (minOSVersion != null) {
            sb.append(minOSVersion);
        }

        this.value = sb.toString();
    }

    public @NotNull String getArchName()
    {
        return arch;
    }

    public @Nullable Architecture getArch()
    {
        return ArchitectureUtils.parseArchitecture(arch);
    }

    public @Nullable String getVendor()
    {
        return vendor;
    }

    public @NotNull String getOS()
    {
        return os;
    }

    public @Nullable String getMinOSVersion()
    {
        return minOSVersion;
    }

    public @NotNull String getValue()
    {
        return value;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof NativeTarget)) {
            return false;
        }
        return Objects.equals(value, ((NativeTarget) o).value);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(value);
    }

    public @NotNull String toString()
    {
        return value;
    }
}
