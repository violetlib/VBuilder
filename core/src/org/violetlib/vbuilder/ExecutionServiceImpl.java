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
