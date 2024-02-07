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

*/

public interface BuildDelegate
  extends Reporter
{
    /**
      Terminate the build.
      This method must thrown an exception.
    */

    void announceBuildFailure(@NotNull String message, @Nullable Exception ex);

    @NotNull ExecutionService getExecutionService();
}
