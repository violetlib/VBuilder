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
import org.violetlib.collections.ISet;
import org.violetlib.collections.SetBuilder;

import javax.annotation.concurrent.Immutable;

/**
  A consistent and minimal set of dependency scopes.
*/

public final @Immutable class Scopes
{
    public static @NotNull Scopes empty()
    {
        return EMPTY;
    }

    public static @NotNull Scopes of(@NotNull Scope scope)
    {
        if (scope == Scope.INCLUDED) {
            return new Scopes(null, true);
        }
        return new Scopes(scope, false);
    }

    private static final @NotNull Scopes EMPTY = new Scopes(null, false);

    private final @Nullable Scope basicScope;
    private final boolean isIncluded;

    private Scopes(@Nullable Scope basicScope, boolean isIncluded)
    {
        this.basicScope = basicScope;
        this.isIncluded = isIncluded;
    }

    public @NotNull ISet<Scope> scopes()
    {
        if (basicScope == null && !isIncluded) {
            return ISet.empty();
        }
        SetBuilder<Scope> b = ISet.builder();
        b.addOptional(basicScope);
        if (isIncluded) {
            b.add(Scope.INCLUDED);
        }
        return b.values();
    }

    public boolean contains(@NotNull Scope scope)
    {
        if (scope == Scope.INCLUDED) {
            return isIncluded;
        } else {
            return basicScope == scope;
        }
    }

    public @NotNull Scopes extending(@NotNull Scope scope)
    {
        if (scope == Scope.INCLUDED) {
            return isIncluded ? this : new Scopes(basicScope, true);
        }

        if (basicScope == scope) {
            return this;
        }

        if (scope == Scope.COMPILE) {
            if (basicScope == Scope.REQUIRED) {
                return this;
            }
            if (basicScope == Scope.RUNTIME) {
                return new Scopes(Scope.REQUIRED, isIncluded);
            }
            return new Scopes(Scope.COMPILE, isIncluded);
        } else if (scope == Scope.RUNTIME) {
            if (basicScope == Scope.REQUIRED) {
                return this;
            }
            if (basicScope == Scope.COMPILE) {
                return new Scopes(Scope.REQUIRED, isIncluded);
            }
        } else if (scope == Scope.REQUIRED) {
            return new Scopes(Scope.REQUIRED, isIncluded);
        }

        throw new AssertionError("Unrecognized scope: " + scope);
    }
}
