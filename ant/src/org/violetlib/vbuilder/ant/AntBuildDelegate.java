package org.violetlib.vbuilder.ant;

import org.violetlib.vbuilder.BuildDelegate;
import org.violetlib.vbuilder.ExecutionService;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**

*/

public class AntBuildDelegate
  implements BuildDelegate
{
    protected final @NotNull Project project;
    protected final @NotNull ExecutionService executionService;

    protected AntBuildDelegate(@NotNull Project project)
    {
        AntUtils.init();
        this.project = project;
        this.executionService = ExecutionService.get();
    }

    @Override
    public @NotNull ExecutionService getExecutionService()
    {
        return executionService;
    }

    protected void log(@NotNull String message, int level)
    {
        project.log(message, level);
    }

    @Override
    public void info(@NotNull String message)
    {
        log(message, Project.MSG_WARN);
    }

    @Override
    public void error(@NotNull String message)
    {
        log(message, Project.MSG_ERR);
    }

    @Override
    public void verbose(@NotNull String message)
    {
        log(message, Project.MSG_VERBOSE);
    }

    @Override
    public void announceBuildFailure(@NotNull String message, @Nullable Exception ex)
    {
        if (ex != null) {
            ex.printStackTrace();
            throw new BuildException(message, ex);
        } else {
            throw new BuildException(message);
        }
    }
}
