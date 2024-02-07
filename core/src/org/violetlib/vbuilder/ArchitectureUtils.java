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
