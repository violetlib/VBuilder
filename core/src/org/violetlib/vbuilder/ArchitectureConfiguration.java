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
           @NotNull File jdkRuntime)
    {
        return new ArchitectureConfiguration(outputRoot, appInstallDirs, jdkRuntime);
    }

    public final @NotNull File outputRoot;             // where architecture-specific generated files are placed
    public final @NotNull IList<File> appInstallDirs;  // directories where the (single architecture) bundled application should be installed
    public final @NotNull File jdkRuntime;             // the JDK runtime to use

    private ArchitectureConfiguration(@NotNull File outputRoot,
                                      @NotNull IList<File> appInstallDirs,
                                      @NotNull File jdkRuntime)
    {
        this.outputRoot = outputRoot;
        this.appInstallDirs = appInstallDirs;
        this.jdkRuntime = Utils.resolve(jdkRuntime);
    }
}
