/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;
import org.violetlib.collections.IList;

import java.io.File;

/**
  A configuration for one target architecture.
*/

public class ArchitectureConfiguration
{
    public static @NotNull ArchitectureConfiguration
    create(@NotNull File outputRoot,
           @NotNull IList<File> appInstallDirs,
           @NotNull JavaRuntime javaRuntime)
    {
        return new ArchitectureConfiguration(outputRoot, appInstallDirs, javaRuntime);
    }

    public final @NotNull File outputRoot;             // where architecture-specific generated files are placed
    public final @NotNull IList<File> appInstallDirs;  // directories where the (single architecture) bundled application should be installed
    public final @NotNull JavaRuntime javaRuntime;     // the Java runtime to use

    private ArchitectureConfiguration(@NotNull File outputRoot,
                                      @NotNull IList<File> appInstallDirs,
                                      @NotNull JavaRuntime javaRuntime)
    {
        this.outputRoot = outputRoot;
        this.appInstallDirs = appInstallDirs;
        this.javaRuntime = javaRuntime;
    }
}
