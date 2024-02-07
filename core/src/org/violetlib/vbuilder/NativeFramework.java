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

import java.io.File;

/**
  A description of a native framework. Third party frameworks are assumed to provide a dynamic library that includes
  all required architectures.
*/

public interface NativeFramework
{
    /**
      Return the basic framework name, e.g. JavaRuntimeSupport.
    */

    @NotNull String getName();

    /**
      Return the dynamic library of the framework.
      @return the dynamic library, or null if the framework is a system framework.
    */

    @Nullable NativeLibrary getLibrary();

    /**
      Return the root directory of the framework.
      @return the root directory, or null if the framework is a system framework.
    */

    @Nullable File getRoot();

    /**
      Return the directory containing the associated debug symbols, if known.
      @return the directory, or null if not known.
    */

    @Nullable File getDebugSymbols();

    /**
      Return an equivalent NativeFramework object bound to the specified debug symbols directory.
    */

    @NotNull NativeFramework withDebugSymbols(@NotNull File f);
}
