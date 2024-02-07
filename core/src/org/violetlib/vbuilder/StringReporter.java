/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;

/**

*/

public final class StringReporter
  implements Reporter
{
    public static @NotNull StringReporter create()
    {
        return new StringReporter();
    }

    private final @NotNull StringBuilder sb = new StringBuilder();

    private StringReporter()
    {
    }

    @Override
    public void info(@NotNull String msg)
    {
        sb.append(msg);
        sb.append("\n");
    }

    @Override
    public void verbose(@NotNull String msg)
    {
        info(msg);
    }

    @Override
    public void error(@NotNull String msg)
    {
        info(msg);
    }

    public @NotNull String getMessages()
    {
        return sb.toString();
    }
}
