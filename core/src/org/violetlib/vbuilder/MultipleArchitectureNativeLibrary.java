package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.collections.ISet;

import java.io.File;

/**
  An implementation of a description of a native library file that supports multiple architectures.
*/

public class MultipleArchitectureNativeLibrary
  extends NativeLibraryImplBase
{
    public static @NotNull NativeLibrary create(@NotNull String name,
                                                @NotNull ISet<Architecture> as,
                                                @NotNull File f,
                                                @Nullable File debugSymbols)
    {
        return new MultipleArchitectureNativeLibrary(name, as, f, debugSymbols);
    }

    private final @NotNull ISet<Architecture> as;
    private final @NotNull File f;

    private MultipleArchitectureNativeLibrary(@NotNull String name,
                                              @NotNull ISet<Architecture> as,
                                              @NotNull File f,
                                              @Nullable File debugSymbols)
      throws IllegalArgumentException
    {
        super(name, debugSymbols);

        if (as.isEmpty()) {
            throw new IllegalArgumentException("At least one architecture must be specified");
        }
        this.as = as;
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
        return as;
    }

    @Override
    public @Nullable File getFile(@NotNull Architecture a)
    {
        return as.contains(a) ? f : null;
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
        return new MultipleArchitectureNativeLibrary(getName(), as, f, debugSymbols);
    }

    @Override
    public @NotNull String toString()
    {
        StringBuilder sb = new StringBuilder();
        for (Architecture a : as) {
            sb.append(" ");
            sb.append(a);
        }
        sb.append(": ");
        sb.append(f);
        String s = sb.toString();
        return s.trim();
    }
}
