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
