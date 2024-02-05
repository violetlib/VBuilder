package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
  Utilities related to target architectures.
*/

public class ArchitectureUtils
{
    public static @Nullable Architecture parseArchitecture(@NotNull String s)
    {
        if (s.equals("x86") || s.equals("x86_64")) {
            return Architecture.Intel;
        }
        if (s.equals("arm") || s.equals("arm64")) {
            return Architecture.ARM;
        }
        return null;
    }
}
