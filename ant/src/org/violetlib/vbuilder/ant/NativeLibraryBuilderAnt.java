/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

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
