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

public interface Reporter
{
    void info(@NotNull String msg);

    void verbose(@NotNull String msg);

    void error(@NotNull String msg);
}
