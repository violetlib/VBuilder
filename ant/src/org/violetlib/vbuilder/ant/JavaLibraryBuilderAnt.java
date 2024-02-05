package org.violetlib.vbuilder.ant;

import org.violetlib.vbuilder.JavaLibraryBuilder;
import org.apache.tools.ant.Project;
import org.jetbrains.annotations.NotNull;

/**

*/

public class JavaLibraryBuilderAnt
  extends JARBuilderAnt
  implements JavaLibraryBuilder.Delegate
{
    public static @NotNull JavaLibraryBuilder.Delegate create(@NotNull Project project)
    {
        return new JavaLibraryBuilderAnt(project);
    }

    protected JavaLibraryBuilderAnt(@NotNull Project project)
    {
        super(project);
    }
}
