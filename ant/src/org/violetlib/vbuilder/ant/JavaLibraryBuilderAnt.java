/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

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
