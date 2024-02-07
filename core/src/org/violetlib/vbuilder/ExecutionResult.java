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
  The results of executing a program.
*/

public class ExecutionResult
{
    public static @NotNull ExecutionResult createResult(int rc,
                                                        byte @NotNull [] binaryOutput,
                                                        @NotNull String output,
                                                        @NotNull String error)
    {
        return new ExecutionResult(rc, binaryOutput, output, error);
    }

    public final int rc;
    public final byte @NotNull [] binaryOutput;
    public final @NotNull String output;
    public final @NotNull String error;

    protected ExecutionResult(int rc, byte @NotNull [] binaryOutput, @NotNull String output, @NotNull String error)
    {
        this.rc = rc;
        this.binaryOutput = binaryOutput;
        this.output = output;
        this.error = error;
    }
}
