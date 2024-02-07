/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.collections.IList;
import org.violetlib.collections.ListBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
  Collect library names and Maven repo artifacts for a project.

  Maven artifacts are identified by a key with the form "<groupID>:<artifactID>".
*/

public class LibraryNameManagement
{
    public static @NotNull LibraryNameManagement get(@NotNull Object context, @NotNull Reporter reporter)
    {
        return INSTANCES.computeIfAbsent(context, (k) -> create(reporter));
    }

    /**
      Create a manager of library names.
      Each project should create its own manager.
    */

    private static @NotNull LibraryNameManagement create(@NotNull Reporter reporter)
    {
        return new LibraryNameManagement(reporter);
    }

    private static final @NotNull WeakHashMap<Object,LibraryNameManagement> INSTANCES = new WeakHashMap<>();

    private final @NotNull Reporter reporter;
    private final @NotNull List<LibraryDefinition> definitions = new ArrayList<>();

    private LibraryNameManagement(@NotNull Reporter reporter)
    {
        this.reporter = reporter;
    }

    /**
      Return the artifact key corresponding to a library name.
      @param name The library name.
      @return the artifact key, or null if none.
    */

    public @Nullable MavenCoordinates getKey(@NotNull String name)
    {
        for (LibraryDefinition d : definitions) {
            if (d.getLibraryName().equals(name)) {
                return d.getArtifactKey();
            }
        }
        return null;
    }

    /**
      Return the library name corresponding to an artifact coordinate.
      @param key The artifact coordinate.
      @return the library name, or null if none.
    */

    public @Nullable String getLibraryName(@NotNull MavenCoordinates key)
    {
        if (key.version != null) {
            key = key.withoutVersion();
        }

        for (LibraryDefinition d : definitions) {
            if (d.getArtifactKey().equals(key)) {
                return d.getLibraryName();
            }
        }
        return null;
    }

    /**
      Add a definition. Once an artifact key has been specified for a library name, subsequent attempts to specify a
      different artifact key for that library name are ignored and logged.

      @param definition The library definition.
    */

    public void add(@NotNull LibraryDefinition definition)
    {
        for (LibraryDefinition d : definitions) {
            boolean sameName = d.getLibraryName().equals(definition.getLibraryName());
            boolean sameKey = d.getArtifactKey().equals(definition.getArtifactKey());
            if (sameName && sameKey) {
                return;
            }
            if (sameName) {
                reporter.info("Ignoring attempt to specify a different artifact for library name "
                  + definition.getLibraryName());
                return;
            }
        }
        definitions.add(definition);
    }

    public void logDefinitions()
    {
        for (String name : getLibraryNames()) {
            MavenCoordinates key = getKey(name);
            assert key != null;
            reporter.verbose(String.format("%s = %s", name, key));
        }
    }

    public @NotNull IList<String> getLibraryNames()
    {
        ListBuilder<String> b = IList.builder(IList.NO_DUPLICATES);
        for (LibraryDefinition d : definitions) {
            b.add(d.getLibraryName());
        }
        return b.values().sort();
    }
}
