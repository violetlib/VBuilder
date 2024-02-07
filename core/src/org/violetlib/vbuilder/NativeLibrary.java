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
import org.violetlib.collections.ISet;

import java.io.File;

/**
  A description of a native library, possibly with multiple versions for different architectures.
*/

public interface NativeLibrary
{
    /**
      The basic library name, e.g. {@code heif} for a file {@code libheif.dylib}.
     */

    @NotNull String getName();

    /**
      Return true if and only if the library is either specific to a single architecture or is a single file containing
      versions for multiple architectures.
    */

    boolean isSingle();

    /**
      Return the supported architectures.
    */

    @NotNull ISet<Architecture> getArchitectures();

    /**
      Return the actual library file for the specified architecture.

      @param a The architecture whose file is requested.

      @return the requested library file, or null if the library does not support the specified architecture.
    */

    @Nullable File getFile(@NotNull Architecture a);

    /**
      Return the unique library file, if there is only one associated file.

      @return the library file, or null if the library has multiple associated files.
    */

    @Nullable File getFile();

    /**
      Return all the associated library files.
    */

    @NotNull ISet<File> getAllFiles();

    /**
      Return the directory containing the associated debug symbols, if known.
      @return the directory, or null if not known.
    */

    @Nullable File getDebugSymbols();

    /**
      Return an equivalent NativeLibrary object bound to the specified debug symbols directory.
    */

    @NotNull NativeLibrary withDebugSymbols(@NotNull File debugSymbols);
}
