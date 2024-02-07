/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;
import org.violetlib.collections.IMap;
import org.violetlib.collections.ISet;
import org.violetlib.collections.MapBuilder;
import org.violetlib.collections.SetBuilder;

import java.io.File;

/**

*/

public class SourceCollector
{
    public static @NotNull SourceCollector create(@NotNull Reporter reporter)
    {
        return new SourceCollector(reporter);
    }

    protected SourceCollector(@NotNull Reporter reporter)
    {
        this.reporter = reporter;
    }

    protected final @NotNull Reporter reporter;

    protected final @NotNull SetBuilder<File> classTrees = ISet.builder();
    protected final @NotNull SetBuilder<File> jarFiles = ISet.builder();
    protected final @NotNull SetBuilder<RelativeFile> resources = ISet.builder();
    protected final @NotNull SetBuilder<File> nativeLibraries = ISet.builder();
    protected final @NotNull SetBuilder<File> nativeLibrarySymbols = ISet.builder();

    public @NotNull ISet<File> getClassTrees()
    {
        return classTrees.values();
    }

    public @NotNull ISet<File> getJarFiles()
    {
        return jarFiles.values();
    }

    public @NotNull ISet<RelativeFile> getResources()
    {
        return resources.values();
    }

    public @NotNull ISet<NativeLibrary> getNativeLibraries()
    {
        ISet<File> nativeLibraryFiles = nativeLibraries.values();
        ISet<File> nativeLibrarySymbolDirectories = nativeLibrarySymbols.values();

        IMap<String,File> nativeLibrarySymbolsMap = createNativeLibrarySymbolsMap(nativeLibrarySymbolDirectories);
        SetBuilder<NativeLibrary> b = ISet.builder();
        for (File nl : nativeLibraryFiles) {
            File symbol = nativeLibrarySymbolsMap.get(nl.getPath());
            try {
                NativeLibrary lib = NativeLibrarySupport.createForFile(nl, reporter);
                if (symbol != null) {
                    lib = lib.withDebugSymbols(symbol);
                }
                b.add(lib);
            } catch (BuildException e) {
                reporter.error("Invalid native library: [" + e + "]:" + nl);
            }
        }
        return b.values();
    }

    private @NotNull IMap<String,File> createNativeLibrarySymbolsMap(@NotNull ISet<File> symbols)
    {
        MapBuilder<String,File> b = IMap.builder();
        for (File symbol : symbols) {
            symbol = symbol.getAbsoluteFile();
            File library = Utils.getLibraryFromSymbolsBundle(symbol);
            if (library != null) {
                b.put(library.getPath(), symbol);
            }
        }
        return b.value();
    }
}
