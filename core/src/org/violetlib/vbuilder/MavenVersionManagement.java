/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vbuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import org.violetlib.collections.IList;

import org.jetbrains.annotations.*;
import org.violetlib.collections.IMap;

/**
  Collect preferred versions of Maven repo artifacts.

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
      Install a global specification of preferred artifact versions.
      This operation has no effect after the first invocation.
      @param bindings A map from artifact key to version.
    */

    void installGlobalPreferredVersions(@NotNull IMap<String,String> bindings);

    /**
      Set the preferred version for an artifact. Once a version has been specified, subsequent attempts to specify a
      different version are ignored and logged.

      @param key The artifact key.
      @param version The version to set as the preferred version.
    */

    void setPreferredVersion(@NotNull String key, @NotNull String version);

    void logPreferredVersions(@NotNull Reporter reporter);
}
