/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vbuilder.ant;

import org.apache.maven.resolver.internal.ant.types.Dependency;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.DataType;
import org.apache.tools.ant.types.Reference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.vbuilder.MavenCoordinates;
import org.violetlib.vbuilder.MavenVersionManagement;
import org.violetlib.vbuilder.Scope;
import org.violetlib.vbuilder.Utils;

/**
  *  Specify the preferred version for a library specified as a Maven Repo artifact. If multiple definitions are found
  *  for the same artifact, the first one is used.
  *  <p>
  *  The following definitions are equivalent:
  *  {@snippet :
 *  <use key="org.violetlib:vaqua" version="10"/>
 *  <use coords="org.violetlib:vaqua:10"/>
 *  }
  *  <p>
  *  {@code Use} elements are implemented using Ant properties, which means that the specified versions are inherited by
  *  default when the {@code Ant} task is used to execute a build script. (Scopes are not inherited.)
  *  <p>
  *  {@code Use} elements within a project define an implicit {@link classpath} with the identifier {@code
 *  Classpath.Use} that records the associated artifact keys (but not versions) and a dependency scope that may
  *  optionally be specified. The default scope is {@code required}. This {@link classpath} is updated even if
  *  the specified version has been pre-empted by a prior {@code use} element.
  *  @ant.type name="use"
*/

public class UseLibrary
  extends DataType
{
    public static final @NotNull String IMPLICIT_CLASSPATH = "Classpath.Use";

    private @Nullable String artifactKey; // group ID and artifact ID
    private @Nullable String version;
    private @Nullable Scope scope;
    private boolean isRegistered;
    private @Nullable Dependency dependency;
    private @Nullable MavenVersionManagement versionManagement;

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
      The key is a prefix of a Maven repo coordinate containing the group identifier and the artifact identifier.
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

    /**
      Specify the scope to be associated with this library in the implicit {@code Classpath}.
      Supported values are: {@code compile} (required only at compile time), {@code required} (required
      at compile time and during execution), and {@code runtime} (required only during execution).

      @ant.prop name="scope"
      @ant.optional The default scope is {@code required}.
    */

    public void setScope(@NotNull String s)
    {
        checkAttributesAllowed();
        this.scope = validateScope(s);
        registerIfAppropriate();
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

    private @NotNull Scope validateScope(@NotNull String s)
    {
        if (s.equals("compile")) {
            return Scope.COMPILE;
        }
        if (s.equals("required")) {
            return Scope.REQUIRED;
        }
        if (s.equals("runtime")) {
            return Scope.RUNTIME;
        }
        throw new BuildException("Invalid scope: " + s);
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
        MavenVersionManagement mm = getVersionManagement();
        if (mm != null && artifactKey != null) {
            if (!isRegistered) {
                if (version != null) {
                    isRegistered = true;
                    mm.setPreferredVersion(artifactKey, version);
                    if (scope != null) {
                        mm.setScope(artifactKey, scope);
                    }
                    updateClasspath();
                }
            } else if (scope != null) {
                mm.setScope(artifactKey, scope);
                updateClasspath();
            }
        }
    }

    /**
      Update the associated classpath. This method may be called twice if the scope attribute is set after the
      identification attributes are set. (Because this element is a data type, there is no method that is called after
      all attributes have been set.) The version is not used in the dependency added to the classpath, because
      the version may have been superseded with a pattern that can only be resolved by the Maven resolver.
    */

    private void updateClasspath()
    {
        assert isRegistered;
        assert artifactKey != null;

        Dependency d = new Dependency();
        int pos = artifactKey.indexOf(':');
        assert pos > 0;
        String groupId = artifactKey.substring(0, pos);
        String artifactId = artifactKey.substring(pos+1);
        d.setGroupId(groupId);
        d.setArtifactId(artifactId);
        MavenVersionManagement mm = getVersionManagement();
        assert mm != null;
        String actualVersion = mm.getPreferredVersion(artifactKey);
        Scope scope = mm.getScope(artifactKey);
        if (actualVersion != null) {
            d.setVersion(actualVersion);
        }
        d.setScope(toMavenScope(scope));

        ClassPath classPath = getImplicitClassPath();

        if (false) {
            // debug
            Scope acutalScope = mm.getScope(artifactKey);
            if (dependency != null) {
                System.out.println("Updating implicit classpath " + artifactKey + " " + actualVersion + " " + acutalScope);
            } else {
                System.out.println("Extending implicit classpath " + artifactKey + " " + actualVersion + " " +  acutalScope);
            }
        }

        if (actualVersion != null) {
            classPath.replaceDependency(dependency, d);
        }
        dependency = d;
    }

    private @NotNull ClassPath getImplicitClassPath()
    {
        Project p = getProject();
        ClassPath classPath = p.getReference(IMPLICIT_CLASSPATH);
        if (classPath != null) {
            return classPath;
        }
        classPath = new ClassPath(p);
        p.addReference(IMPLICIT_CLASSPATH, classPath);
        return classPath;
    }

    private @NotNull String toMavenScope(@Nullable Scope s)
    {
        String ms = Utils.toMavenScope(s);
        return ms != null ? ms : "compile";
    }

    private @Nullable MavenVersionManagement getVersionManagement()
    {
        if (versionManagement != null) {
            return versionManagement;
        }
        Project p = getProject();
        if (p != null) {
            versionManagement = AntMavenVersionManagement.get(p, ProjectReporter.create(p));
        }
        return versionManagement;
    }
}
