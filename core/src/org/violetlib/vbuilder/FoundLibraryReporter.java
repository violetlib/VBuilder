/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;
import org.violetlib.collections.IList;

import java.util.HashMap;
import java.util.Map;

/**

*/

public class FoundLibraryReporter
{
    public static @NotNull FoundLibraryReporter create(@NotNull Reporter reporter)
    {
        return new FoundLibraryReporter(reporter);
    }

    private final @NotNull Reporter reporter;
    private final @NotNull Map<String,Info> libraryInfoMap = new HashMap<>();

    private static class Info
    {
        public final @NotNull String location;
        public @NotNull Scopes scopes;

        public Info(@NotNull String location, @NotNull Scope scope)
        {
            this.location = location;
            this.scopes = Scopes.of(scope);
        }
    }

    private FoundLibraryReporter(@NotNull Reporter reporter)
    {
        this.reporter = reporter;
    }

    public void add(@NotNull String libraryName, @NotNull String location, @NotNull Scope scope)
    {
        Info existing = libraryInfoMap.get(libraryName);
        if (existing != null) {
            if (!location.equals(existing.location)) {
                reporter.error("Inconsistent locations for library " + libraryName);
                reporter.error("  " + existing.location);
                reporter.error("  " + location + " [ignored]");
            }
            existing.scopes = existing.scopes.extending(scope);
        } else {
            Info info = new Info(location, scope);
            libraryInfoMap.put(libraryName, info);
        }
    }

    public void show(@NotNull Reporter r)
    {
        if (!libraryInfoMap.isEmpty()) {
            IList<String> names = IList.create(libraryInfoMap.keySet()).sort();
            r.info("Using libraries:");
            for (String name : names) {
                Info info = libraryInfoMap.get(name);
                assert info != null;
                showLibrary(r, name, info);
            }
            r.info("");
        }
    }

    private void showLibrary(@NotNull Reporter r, @NotNull String name, @NotNull Info info)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("  ");
        sb.append(String.format("%25s", name));
        sb.append(": ");
        sb.append(info.location);
        IList<Scope> scopes = IList.create(info.scopes.scopes()).sort();
        for (Scope scope : scopes) {
            sb.append("  ");
            sb.append(scope);
        }
        r.info(sb.toString());
    }
}
