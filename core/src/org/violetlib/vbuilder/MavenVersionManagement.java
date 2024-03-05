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
import org.violetlib.collections.IMap;

/**
  Collect preferred versions and scopes of Maven repo artifacts.

  Maven artifacts are identified by a key with the form "<groupID>:<artifactID>".
*/

public interface MavenVersionManagement
{
    /**
      Return the preferred version for an artifact.
      @param key The artifact key.
      @return the preferred version, or null if none.
    */

    @Nullable String getPreferredVersion(@NotNull String key);

    /**
      Return the dependency scope for an artifact.
      @param key The artifact key.
      @return the associated scope, or null if none.
    */

    @Nullable Scope getScope(@NotNull String key);

    /**
      Install a global specification of preferred artifact versions.
      This operation has no effect after the first invocation.
      @param bindings A map from artifact key to version.
    */

    void installGlobalPreferredVersions(@NotNull IMap<String,String> bindings);

    /**
      Set the preferred version for an artifact. Once a version has been specified, subsequent attempts to specify a
      incompatible version may be ignored and logged.

      @param key The artifact key.
      @param version The version to set as the preferred version.
      @return true if the preferred version was not already
    */

    void setPreferredVersion(@NotNull String key, @NotNull String version);

    /**
      Set the dependency scope for an artifact. Once a scope has been specified, subsequent attempts to specify
      an incompatible scope may be ignored and logged.

      @param key The artifact key.
      @param scope The dependency scope.
    */

    void setScope(@NotNull String key, @NotNull Scope scope);

    void logPreferredVersionsAndScopes(@NotNull Reporter reporter);
}
