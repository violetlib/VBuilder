package org.violetlib.vbuilder.ant;

import org.violetlib.vbuilder.JavaApplicationBuilder;
import org.apache.tools.ant.Project;
import org.jetbrains.annotations.NotNull;

/**

*/

public class JavaApplicationBuilderAnt
  extends JARBuilderAnt
  implements JavaApplicationBuilder.Delegate
{
    public static @NotNull JavaApplicationBuilder.Delegate create(@NotNull Project project)
    {
        return new JavaApplicationBuilderAnt(project);
    }

    private JavaApplicationBuilderAnt(@NotNull Project project)
    {
        super(project);
    }
}
