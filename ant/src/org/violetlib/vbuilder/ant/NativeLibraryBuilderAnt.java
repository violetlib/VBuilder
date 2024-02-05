package org.violetlib.vbuilder.ant;

import org.violetlib.vbuilder.NativeLibraryBuilder;
import org.apache.tools.ant.Project;
import org.jetbrains.annotations.NotNull;

/**

*/

public class NativeLibraryBuilderAnt
  extends AntBuildDelegate
  implements NativeLibraryBuilder.Delegate
{
    public static @NotNull NativeLibraryBuilder.Delegate create(@NotNull Project project)
    {
        return new NativeLibraryBuilderAnt(project);
    }

    public NativeLibraryBuilderAnt(@NotNull Project project)
    {
        super(project);
    }
}
