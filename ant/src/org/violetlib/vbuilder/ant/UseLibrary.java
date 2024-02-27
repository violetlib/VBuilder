/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vbuilder.ant;

import org.apache.tools.ant.types.DataType;
import org.violetlib.vbuilder.MavenCoordinates;
import org.jetbrains.annotations.*;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Reference;
import org.violetlib.vbuilder.MavenVersionManagement;

/**
 *  Specify the preferred version for an artifact. If multiple definitions are found for the same artifact, the first one
 *  is used.
 *  <p>
 *  The following definitions are equivalent:
 *  {@snippet :
 *  <use key="org.violetlib:vaqua" version="10"/>
 *  <use coords="org.violetlib:vaqua:10"/>
 *  }
 *  @ant.type name="use"
 */

public class UseLibrary
  extends DataType
{
    private @Nullable String artifactKey; // group ID and artifact ID
    private @Nullable String version;
    private boolean isRegistered;

    public UseLibrary(@NotNull Project project)
    {
        setProject(project);
    }

    @Override
    public void setRefid(@NotNull Reference r)
    {
        if (artifactKey != null || version != null) {
            throw tooManyAttributes();
        }
        super.setRefid(r);
    }

    @Override
    public void setProject(Project project)
    {
        super.setProject(project);
        registerIfAppropriate();
    }

    /**
      An identification of the required library.
      The key is a prefix of a Maven repo coordinate containing the organization and the artifact identifier.
      @ant.prop name="key"
      @ant.optional Either a key and version must be defined, or coordinates must be defined.
    */

    public void setKey(@NotNull String key)
    {
        checkAttributesAllowed();
        validateKey(key);
        this.artifactKey = key;
        registerIfAppropriate();
    }

    /**
      The desired version the required library.
      @ant.prop name="version"
      @ant.optional Either a key and version must be defined, or coordinates must be defined.
    */

    public void setVersion(@NotNull String version)
    {
        checkAttributesAllowed();
        validateVersion(version);
        this.version = version;
        registerIfAppropriate();
    }

    /**
      Specify the required library and the desired version of the library.
      @ant.prop name="coords"
      @ant.optional Either a key and version must be defined, or coordinates must be defined.
    */

    public void setCoords(@NotNull String s)
    {
        checkAttributesAllowed();
        MavenCoordinates mc = MavenCoordinates.parse(s);
        if (mc == null) {
            throw new BuildException("Invalid Maven artifact coordinates: " + s);
        }
        validateKey(mc.key);
        if (mc.version != null) {
            validateVersion(mc.version);
            this.artifactKey = mc.key;
            this.version = mc.version;
            registerIfAppropriate();
        } else {
            throw new BuildException("Maven artifact coordinates must include a version: " + s);
        }
    }

    private void validateKey(@NotNull String s)
    {
        MavenCoordinates mc = MavenCoordinates.parse(s);
        if (mc == null || mc.version != null) {
            throw new BuildException("Invalid Maven artifact key: " + s);
        }
    }

    private void validateVersion(@NotNull String s)
    {
        if (s.contains(":")) {
            throw new BuildException("Invalid Maven artifact version: " + s);
        }
    }

    public @NotNull String getArtifactKey()
    {
        if (isReference()) {
            return getRef().getArtifactKey();
        }
        if (artifactKey == null) {
            throw new BuildException("An artifact key is required");
        }
        return artifactKey;
    }

    public @NotNull String getVersion()
    {
        if (isReference()) {
            return getRef().getVersion();
        }
        if (version == null) {
            throw new BuildException("A version is required");
        }
        return version;
    }

    private UseLibrary getRef()
    {
        Project p = getProject();
        return getCheckedRef(UseLibrary.class, getDataTypeName(), p);
    }

    public void registerIfAppropriate()
    {
        if (!isRegistered) {
            Project p = getProject();
            if (p != null && artifactKey != null && version != null) {
                isRegistered = true;
                MavenVersionManagement mm = AntMavenVersionManagement.get(p, ProjectReporter.create(p));
                mm.setPreferredVersion(artifactKey, version);
            }
        }
    }
}
