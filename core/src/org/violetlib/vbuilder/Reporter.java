package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;

/**

*/

public interface Reporter
{
    void info(@NotNull String msg);

    void verbose(@NotNull String msg);

    void error(@NotNull String msg);
}
