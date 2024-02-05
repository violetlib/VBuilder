package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.collections.ISet;

import java.io.File;

/**
  An implementation of a description of a native library file that supports a single architecture.
*/

public class SingleArchitectureNativeLibrary
  extends NativeLibraryImplBase
{
    public static @NotNull NativeLibrary create(@NotNull String name,
                                                @NotNull Architecture a,
                                                @NotNull File f,
                                                @Nullable File debugSymbols)

    {
        return new SingleArchitectureNativeLibrary(name, a, f, debugSymbols);
    }

    private final @NotNull Architecture a;
    private final @NotNull File f;

    private SingleArchitectureNativeLibrary(@NotNull String name,
                                            @NotNull Architecture a,
                                            @NotNull File f,
                                            @Nullable File debugSymbols)
    {
        super(name, debugSymbols);

        this.a = a;
        this.f = f;
    }

    @Override
    public boolean isSingle()
    {
        return true;
    }

    @Override
    public @NotNull ISet<Architecture> getArchitectures()
    {
        return ISet.of(a);
    }

    @Override
    public @Nullable File getFile(@NotNull Architecture a)
    {
        return a == this.a ? f : null;
    }

    @Override
    public @NotNull File getFile()
    {
        return f;
    }

    @Override
    public @NotNull ISet<File> getAllFiles()
    {
        return ISet.of(f);
    }

    @Override
    public @NotNull NativeLibrary withDebugSymbols(@NotNull File debugSymbols)
    {
        return new SingleArchitectureNativeLibrary(getName(), a, f, debugSymbols);
    }

    @Override
    public @NotNull String toString()
    {
        return a + ": " + f;
    }
}
