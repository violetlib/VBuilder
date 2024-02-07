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
  Implementation support for ExecutionService. Supports a dynamically installable service implementation.
*/

public class ExecutionServiceImpl
{
    private static @Nullable ExecutionService theService;

    public static synchronized @NotNull ExecutionService getExecutionService()
    {
        if (theService == null) {
            throw new UnsupportedOperationException("No execution service is available");
        }
        return theService;
    }

    public static synchronized void installExecutionService(@NotNull ExecutionService es)
    {
        theService = es;
    }
}
