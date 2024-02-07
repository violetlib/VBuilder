/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
  A generic service for running Unix-like programs.
*/

public interface ExecutionService
{
    static @NotNull ExecutionService get()
    {
        return ExecutionServiceImpl.getExecutionService();
    }

    @NotNull ExecutionResult execute(@NotNull ExecutionConfiguration g)
      throws IOException;
}
