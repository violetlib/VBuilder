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
  A configuration for executing a Unix-like program.
*/

public class ExecutionConfiguration
{
    public static @NotNull ExecutionConfiguration create(@NotNull File program,
                                                         @NotNull String taskName,
                                                         @NotNull IList<String> arguments)
    {
        return new ExecutionConfiguration(program, taskName, arguments);
    }

    public final @NotNull File program;
    public final @NotNull String taskName;
    public final @NotNull IList<String> arguments;

    private ExecutionConfiguration(@NotNull File program,
                                   @NotNull String taskName,
                                   @NotNull IList<String> arguments)
    {
        this.program = program;
        this.taskName = taskName;
        this.arguments = arguments;
    }
}
