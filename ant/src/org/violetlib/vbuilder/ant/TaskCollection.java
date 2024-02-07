/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vbuilder.ant;

import org.apache.tools.ant.Task;
import org.apache.tools.ant.TaskContainer;
import org.apache.tools.ant.types.DataType;
import org.jetbrains.annotations.NotNull;
import org.violetlib.collections.IList;

import java.util.ArrayList;
import java.util.List;

/**
  A collection of tasks.
  @ant.type name="tasks"
*/

public class TaskCollection
  extends DataType
  implements TaskContainer
{
    private final @NotNull List<Task> tasks = new ArrayList<>();

    @Override
    public void addTask(@NotNull Task t)
    {
        tasks.add(t);
    }

    public @NotNull IList<Task> getTasks()
    {
        return IList.create(tasks);
    }
}
