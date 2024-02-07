/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;

/**
  An identification of a target architecture.
*/

public enum Architecture
{
    Intel ("x86"), ARM ("arm");

    private final @NotNull String name;

    Architecture(@NotNull String name)
    {
        this.name = name;
    }

    /**
      Return the name to use in messages and in file names.
    */

    public @NotNull String getName()
    {
        return name;
    }
}
