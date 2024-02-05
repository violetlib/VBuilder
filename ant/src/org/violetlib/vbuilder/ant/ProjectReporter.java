package org.violetlib.vbuilder.ant;

import org.apache.tools.ant.Project;
import org.jetbrains.annotations.NotNull;
import org.violetlib.vbuilder.Reporter;

/**

*/

public class ProjectReporter
  implements Reporter
{
    public static @NotNull Reporter create(@NotNull Project project)
    {
        return new ProjectReporter(project);
    }

    private ProjectReporter(@NotNull Project project)
    {
        this.project = project;
    }

    private final @NotNull Project project;

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
}
