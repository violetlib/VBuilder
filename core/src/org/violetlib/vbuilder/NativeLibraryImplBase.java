package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.vbuilder.NativeLibrary;

import java.io.File;

/**

*/

public abstract class NativeLibraryImplBase
  implements NativeLibrary
{
    private final @NotNull String name;
    private final @Nullable File debugSymbols;

    protected NativeLibraryImplBase(@NotNull String name, @Nullable File debugSymbols)
    {
        this.name = name;
        this.debugSymbols = debugSymbols;
    }

    @Override
    public final @NotNull String getName()
    {
        return name;
    }

    @Override
    public final @Nullable File getDebugSymbols()
    {
        return debugSymbols;
    }
}
