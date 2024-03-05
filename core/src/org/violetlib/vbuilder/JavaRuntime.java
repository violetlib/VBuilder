/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;
import org.violetlib.types.InvalidDataException;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
  A description of a Java runtime. A Java runtime can exist in a bare form (containing top level subdirectories such as
  bin and lib) or as a macOS bundle.
*/

public final class JavaRuntime
{
    /**
      Create a description of a Java runtime or runtime bundle.
    */

    public static @NotNull JavaRuntime create(@NotNull File dir)
      throws FileNotFoundException, InvalidDataException
    {
        File home = new File(dir, "Contents/Home");
        return home.isDirectory() ? ofBundle(dir) : of(dir);
    }

    /**
      Create a description of a Java runtime.
    */

    public static @NotNull JavaRuntime of(@NotNull File dir)
      throws FileNotFoundException, InvalidDataException
    {
        File startup = validateRuntime(dir);

        return new JavaRuntime(false, dir, dir, startup);
    }

    /**
      Create a description of a Java runtime bundle.
    */

    public static @NotNull JavaRuntime ofBundle(@NotNull File dir)
      throws FileNotFoundException, InvalidDataException
    {
        File root = new File(dir, "Contents/Home");
        if (!root.isDirectory()) {
            throw InvalidDataException.create("Missing Contents/Home directory");
        }
        File startup = validateRuntime(root);

        return new JavaRuntime(true, dir, root, startup);
    }

    private static @NotNull File validateRuntime(@NotNull File root)
      throws FileNotFoundException, InvalidDataException
    {
        if (!root.isDirectory()) {
            throw new FileNotFoundException(root.getPath());
        }

        File lib = new File(root, "lib");
        if (!lib.isDirectory()) {
            throw InvalidDataException.create("Missing lib directory");
        }

        File bin = new File(root, "bin");
        if (!bin.isDirectory()) {
            throw InvalidDataException.create("Missing lib directory");
        }

        File jli1 = new File(lib, "libjli.dylib");
        File jli2 = new File(lib, "jli/libjli.dylib");
        File jli3 = new File(root, "jre/lib/libjli.dylib");
        List<File> jlis = List.of(jli1, jli2, jli3);
        for (File f : jlis) {
            if (f.isFile()) {
                return f;
            }
        }
        throw InvalidDataException.create("Missing file libjli.dylib");
    }

    private final boolean isBundle;
    private final @NotNull File top;
    private final @NotNull File runtime;
    private final @NotNull File startupLibrary;

    private JavaRuntime(boolean isBundle, @NotNull File top, @NotNull File runtime, @NotNull File startupLibrary)
    {
        this.isBundle = isBundle;
        this.top = top;
        this.runtime = runtime;
        this.startupLibrary = startupLibrary;
    }

    /**
      Indicate whether the runtime is packaged as a macOS bundle.
      @return true if and only if the runtime is packaged as a runtime bundle.
    */

    public boolean isBundle()
    {
        return isBundle;
    }

    /**
      Return the top directory of the containing bundle (if the runtime is packaged in a bundle) or runtime
      (otherwise). If specified as a symbolic link, the link is resolved.
    */

    public @NotNull File top()
    {
        return Utils.resolve(top);
    }

    /**
      Return the directory containing the runtime.
      If specified as a symbolic link, the link is resolved.
    */

    public @NotNull File runtime()
    {
        return Utils.resolve(runtime);
    }

    /**
      Return the startup library.
    */

    public @NotNull File startupLibrary()
    {
        return startupLibrary;
    }

    @Override
    public @NotNull String toString()
    {
        return top.getPath();
    }
}
