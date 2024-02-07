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
import org.apache.tools.ant.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.collections.IList;
import org.violetlib.collections.ListBuilder;
import org.violetlib.vbuilder.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
  Install a snapshot release in the local repo.

  @ant.task name="installLocal"
*/

public class MavenInstallLocalTask
  extends Task
{
    private @Nullable File jarFile;
    private @Nullable String coordinates;

    /**
      The JAR file to be installed in the local repo.
      @ant.prop name="jar"
      @ant.required
    */

    public void setJar(@NotNull File f)
    {
        jarFile = f;
    }

    /**
      The coordinates to use for the repo artifact. The version will be replaced by the corresponding SNAPSHOT
      release version.
      @ant.prop name="coordinates"
      @ant.required
    */

    public void setCoordinates(@NotNull String s)
    {
        coordinates = s;
    }

    @Override
    public void execute()
      throws BuildException
    {
        AntUtils.init();

        File program = Utils.findExecutable("mvn");
        if (program == null) {
            buildFailed("Unable to find mvn executable");
            return;
        }

        if (jarFile == null) {
            buildFailed("A JAR file must be specified");
            throw new AssertionError();
        }

        if (!Files.isRegularFile(jarFile.toPath())) {
            buildFailed("File not found: " + jarFile);
            throw new AssertionError();
        }

        if (this.coordinates == null) {
            buildFailed("Maven repo coordinates must be specified");
            throw new AssertionError();
        }

        MavenCoordinates coordinates = MavenCoordinates.parse(this.coordinates);
        if (coordinates == null) {
            buildFailed("Invalid coordinates: " + this.coordinates);
            throw new AssertionError();
        }

        String version = coordinates.version;
        if (version != null && !version.contains("-SNAPSHOT")) {
            version = version + "-SNAPSHOT";
        }
        coordinates = MavenCoordinates.create(coordinates.group, coordinates.artifactID, version);

        ListBuilder<String> b = IList.builder();
        b.add("install:install-file");
        b.add("-Dfile=" + jarFile);
        b.add("-DgroupId=" + coordinates.group);
        b.add("-DartifactId=" + coordinates.artifactID);
        if (coordinates.version != null) {
            b.add("-Dversion=" + coordinates.version);
        }
        b.add("-Dpackaging=jar");
        b.add("-quiet");
        IList<String> args = b.values();
        ExecutionConfiguration g = ExecutionConfiguration.create(program, "install_local", args);
        ExecutionService es = ExecutionService.get();
        try {
            ExecutionResult r = es.execute(g);
            if (r.rc != 0) {
                error("Maven install failed: " + r.rc);
                if (!r.error.isEmpty()) {
                    error(r.error);
                }
                if (!r.output.isEmpty()) {
                    error(r.output);
                }
                buildFailed("Maven install failed");
                throw new AssertionError();
            }
            info("Installed locally as: " + coordinates);
        } catch (IOException e) {
            buildFailed("Unable to execute mvn program", e);
            throw new AssertionError();
        }
    }

    public void info(@NotNull String message)
    {
        log(message, Project.MSG_WARN);
    }

    public void verbose(@NotNull String message)
    {
        log(message, Project.MSG_VERBOSE);
    }

    public void error(@NotNull String message)
    {
        log(message, Project.MSG_ERR);
    }

    /**
      This method is expected to throw a RuntimeException.
    */

    protected final void buildFailed(@NotNull String message)
    {
        announceBuildFailure(message, null);
        throw new AssertionError("announceBuildFailure failed to throw an exception");
    }

    /**
      This method is expected to throw a RuntimeException.
    */

    protected final void buildFailed(@NotNull String message, @NotNull Exception ex)
    {
        announceBuildFailure(message, ex);
        throw new AssertionError("announceBuildFailure failed to throw an exception");
    }

    protected void announceBuildFailure(@NotNull String message, @Nullable Exception ex)
    {
        if (ex != null) {
            ex.printStackTrace();
            throw new BuildException(message, ex);
        } else {
            throw new BuildException(message);
        }
    }
}
