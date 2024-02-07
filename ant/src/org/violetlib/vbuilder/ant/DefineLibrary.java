/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vbuilder.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.DataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.vbuilder.LibraryDefinition;
import org.violetlib.vbuilder.LibraryNameManagement;
import org.violetlib.vbuilder.MavenCoordinates;
import org.violetlib.vbuilder.Utils;

/**
  Define a relationship between a library name and the corresponding Maven repo artifact key.
  The name of a library is the name of the library JAR file without the {@code .jar} extension.
  An artifact key is a Maven repo coordinate without a version.
  <p>
  For example:
  {@snippet :
  <defineLibrary name="VAqua" key="org.violetlib:vaqua"/>
  }
  <p>
  Multiple library names may be associated with the same artifact key.
  If an attempt is made to associate multiple artifact keys with the same library name, only the first
  association is used.

  @ant.type name="defineLibrary"
*/

public class DefineLibrary
  extends DataType
{
    private @Nullable String libraryName;
    private @Nullable MavenCoordinates artifactKey;
    private boolean isRegistered;

    @Override
    public void setProject(Project project)
    {
        super.setProject(project);
        registerIfAppropriate();
    }

    /**
      Specify the library name.
      @ant.required
    */

    public void setName(@NotNull String name)
    {
        try {
            Utils.validateLibraryName(name);
        } catch (IllegalArgumentException e) {
            throw new BuildException(e.getMessage());
        }

        libraryName = name;
        registerIfAppropriate();
    }

    /**
      Specify the artifact key.
      @ant.required
    */

    public void setKey(@NotNull String key)
    {
        MavenCoordinates cs = MavenCoordinates.parse(key);
        if (cs == null) {
            throw new BuildException("Invalid artifact key: " + key);
        }
        if (cs.version != null) {
            throw new BuildException("Artifact version not allowed in key: " + key);
        }
        this.artifactKey = cs;
        registerIfAppropriate();
    }

    public @NotNull LibraryDefinition getLibraryDefinition()
    {
        return LibraryDefinition.create(getName(), getArtifactKey());
    }

    private @NotNull String getName()
    {
        if (libraryName == null) {
            throw new BuildException("A library name must be specified");
        }
        return libraryName;
    }

    private @NotNull MavenCoordinates getArtifactKey()
    {
        if (artifactKey == null) {
            throw new BuildException("An artifact key must be specified");
        }
        return artifactKey;
    }

    private void registerIfAppropriate()
    {
        if (!isRegistered) {
            Project p = getProject();
            if (p != null && artifactKey != null && libraryName != null) {
                isRegistered = true;
                LibraryNameManagement mm = LibraryNameManagement.get(p, ProjectReporter.create(p));
                mm.add(getLibraryDefinition());
            }
        }
    }
}
