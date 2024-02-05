package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.collections.IMap;
import org.violetlib.collections.ISet;

import java.io.File;

/**
  An implementation of a description of a native library that supports multiple architectures using individual files.
*/

public class MultipleNativeLibrary
  extends NativeLibraryImplBase
{
    public static @NotNull NativeLibrary create(@NotNull String name,
                                                @NotNull IMap<Architecture,File> files,
                                                @Nullable File debugSymbols)
    {
        return new MultipleNativeLibrary(name, files, debugSymbols);
    }

    private final @NotNull IMap<Architecture,File> files;

    private MultipleNativeLibrary(@NotNull String name,
                                  @NotNull IMap<Architecture,File> files,
                                  @Nullable File debugSymbols)
      throws IllegalArgumentException
    {
        super(name, debugSymbols);

        if (files.isEmpty()) {
            throw new IllegalArgumentException("At least one file must be provided");
        }
        this.files = files;
    }

    @Override
    public boolean isSingle()
    {
        return files.size() == 1;
    }

    @Override
    public @NotNull ISet<Architecture> getArchitectures()
    {
        return files.keySet();
    }

    @Override
    public @Nullable File getFile(@NotNull Architecture a)
    {
        return files.get(a);
    }

    @Override
    public @Nullable File getFile()
    {
        return files.size() == 1 ? files.values().choose() : null;
    }

    @Override
    public @NotNull ISet<File> getAllFiles()
    {
        return files.values();
    }

    @Override
    public @NotNull NativeLibrary withDebugSymbols(@NotNull File debugSymbols)
    {
        return new MultipleNativeLibrary(getName(), files, debugSymbols);
    }

    @Override
    public @NotNull String toString()
    {
        StringBuilder sb = new StringBuilder();
        files.visit((a, f) -> sb.append("; " + a + ": " + f));
        String s = sb.toString();
        if (s.startsWith("; ")) {
            return s.substring(2);
        }
        return s;
    }
}
