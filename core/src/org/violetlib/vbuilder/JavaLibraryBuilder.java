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
import org.violetlib.collections.ISet;
import org.violetlib.collections.ListBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
  Build a Java library.

  The possible inputs are:
  <ul>
  <li>Java class files to include in the library.</li>
  <li>Resource files to include in the library.</li>
  <li>Jar files to expand into the library.</li>
  <li>Native libraries to include in the library.</li>
  <li>A Manifest to include in the library.</li>
  </ul>
  These inputs are combined to create one or two JAR files:
  <p>
  <ul>
  <li>A basic JAR file is created if an output directory is specified for the basic JAR file.
  The basic JAR file includes all sources except JAR files. The name of the created JAR file
  is {@code <libraryName>.jar}.
  </li>
  <li>An expanded JAR file is created if an output directory is specified for the expanded JAR file
  and at least one JAR file is specified as a source. The expanded JAR file includes all sources.
  The default name of the created JAR file is {@code <libraryName>Expanded.jar}.
  </li>
  </ul>
  If Maven artifact coordinates are provided, the basic JAR is installed in the local Maven repo as a snapshot release.
*/

public class JavaLibraryBuilder
{
    public static void createLibrary(@NotNull Configuration g, @NotNull Delegate delegate)
      throws BuildException
    {
        new JavaLibraryBuilder(g, delegate).build();
    }

    public interface BuilderFactory
    {
        @NotNull Builder create(@NotNull JavaLibraryBuilder.Delegate delegate);
    }

    public interface Builder
    {
        void createLibrary(@NotNull Configuration g)
          throws BuildException;
    }

    public static @NotNull BuilderFactory createFactory(@NotNull JavaLibraryBuilder.Delegate delegate)
    {
        return new BuilderFactory()
        {
            @Override
            public @NotNull Builder create(@NotNull JavaLibraryBuilder.Delegate delegate)
            {
                return g -> createLibrary(g, delegate);
            }
        };
    }

    public static @NotNull Configuration createConfiguration(@NotNull String libraryName,
                                                             @NotNull ISet<File> classTrees,
                                                             @NotNull ISet<RelativeFile> resources,
                                                             @NotNull ISet<File> jars,
                                                             @NotNull ISet<NativeLibrary> nativeLibraries,
                                                             @Nullable Object manifest,
                                                             @Nullable File basicJarFile,
                                                             @Nullable File expandedJarFile,
                                                             @Nullable MavenCoordinates coordinates,
                                                             @Nullable String codeSigningKey,
                                                             @NotNull File buildRoot,
                                                             @NotNull JARBuilder.BuilderFactory jarBuilderFactory)
    {
        return new Configuration(libraryName, classTrees, resources, jars,
          nativeLibraries, manifest, basicJarFile, expandedJarFile, coordinates, codeSigningKey, buildRoot,
          jarBuilderFactory);
    }

    public static class Configuration
    {
        public final @NotNull String libraryName;
        public final @NotNull ISet<File> classTrees;
        public final @NotNull ISet<RelativeFile> resources;
        public final @NotNull ISet<File> jars;
        public final @NotNull ISet<NativeLibrary> nativeLibraries;
        public final @Nullable Object manifest;
        public final @Nullable File basicJarFile;
        public final @Nullable File expandedJarFile;
        public final @Nullable MavenCoordinates coordinates;
        public final @Nullable String codeSigningKey;
        public final @NotNull File buildRoot;
        public final @NotNull JARBuilder.BuilderFactory jarBuilderFactory;

        private Configuration(@NotNull String libraryName,
                              @NotNull ISet<File> classTrees,
                              @NotNull ISet<RelativeFile> resources,
                              @NotNull ISet<File> jars,
                              @NotNull ISet<NativeLibrary> nativeLibraries,
                              @Nullable Object manifest,
                              @Nullable File basicJarFile,
                              @Nullable File expandedJarFile,
                              @Nullable MavenCoordinates coordinates,
                              @Nullable String codeSigningKey,
                              @NotNull File buildRoot,
                              @NotNull JARBuilder.BuilderFactory jarBuilderFactory)
        {
            this.libraryName = libraryName;
            this.classTrees = classTrees;
            this.resources = resources;
            this.jars = jars;
            this.nativeLibraries = nativeLibraries;
            this.manifest = manifest;
            this.basicJarFile = basicJarFile;
            this.expandedJarFile = expandedJarFile;
            this.coordinates = coordinates;
            this.codeSigningKey = codeSigningKey;
            this.buildRoot = buildRoot;
            this.jarBuilderFactory = jarBuilderFactory;
        }
    }

    private static class InternalConfiguration
    {
        public final @NotNull String libraryName;
        public final @NotNull IList<File> classTrees;
        public final @NotNull IList<File> jarFiles;
        public final @NotNull IList<RelativeFile> resources;
        public final @NotNull IList<NativeLibrary> nativeLibraries;
        public final @Nullable Object manifest;
        public final @Nullable File basicJarFile;
        public final @Nullable File expandedJarFile;
        public final @Nullable MavenCoordinates coordinates;
        public final @NotNull File buildRoot;
        public final @Nullable String codeSigningKey;
        public final @NotNull JARBuilder.BuilderFactory jarBuilderFactory;

        public InternalConfiguration(@NotNull String libraryName,
                                     @NotNull IList<File> classTrees,
                                     @NotNull IList<File> jarFiles,
                                     @NotNull IList<RelativeFile> resources,
                                     @NotNull IList<NativeLibrary> nativeLibraries,
                                     @Nullable Object manifest,
                                     @Nullable File basicJarFile,
                                     @Nullable File expandedJarFile,
                                     @Nullable MavenCoordinates coordinates,
                                     @NotNull File buildRoot,
                                     @Nullable String codeSigningKey,
                                     @NotNull JARBuilder.BuilderFactory jarBuilderFactory)
        {
            this.libraryName = libraryName;
            this.classTrees = classTrees;
            this.jarFiles = jarFiles;
            this.resources = resources;
            this.nativeLibraries = nativeLibraries;
            this.manifest = manifest;
            this.basicJarFile = basicJarFile;
            this.expandedJarFile = expandedJarFile;
            this.coordinates = coordinates;
            this.buildRoot = buildRoot;
            this.codeSigningKey = codeSigningKey;
            this.jarBuilderFactory = jarBuilderFactory;
        }
    }

    private final @NotNull InternalConfiguration g;
    private final @NotNull Delegate delegate;

    private boolean errorsFound;

    private JavaLibraryBuilder(@NotNull Configuration g, @NotNull Delegate delegate)
    {
        this.delegate = delegate;
        this.g = validate(g);
    }

    public void build()
      throws BuildException
    {
        File program = new File("/usr/bin/jar");
        IList<Object> basicSources = collectSources();

        if (g.basicJarFile == null && g.expandedJarFile == null) {
            buildFailed("At least one output must be specified");
            throw new AssertionError();
        }

        if (g.basicJarFile != null) {
            File jarDest = validateOutputFile(g.basicJarFile);
            JARBuilder.Configuration bb = JARBuilder.createConfiguration(
              program, basicSources, jarDest, g.manifest, null, false);
            JARBuilder.createJAR(bb, delegate);
            delegate.info("Created: " + jarDest);

            if (g.codeSigningKey != null) {
                codeSignFile(jarDest, g.codeSigningKey);
            }

            if (g.coordinates != null) {
                installInLocalRepo(jarDest, g.coordinates);
            }
        }

        if (g.expandedJarFile != null) {
            File jarSources = collectExpandedJarSources();
            if (jarSources != null) {
                File jarDest = validateOutputFile(g.expandedJarFile);
                JARBuilder.Configuration bb = JARBuilder.createConfiguration(
                  program, basicSources.appending(jarSources), jarDest, g.manifest, null, false);
                JARBuilder.createJAR(bb, delegate);
                delegate.info("Created: " + jarDest);

                if (g.codeSigningKey != null) {
                    codeSignFile(jarDest, g.codeSigningKey);
                }
            }
        }

        if (errorsFound) {
            buildFailed("Errors detected building a Java Library");
        }
    }

    private @NotNull IList<Object> collectSources()
    {
        ListBuilder<Object> b = IList.builder();
        b.addAll(g.classTrees);
        b.addAll(g.resources);

        for (NativeLibrary nl : g.nativeLibraries) {
            File f = nl.getFile();
            if (f != null) {
                b.add(f);
                File sf = nl.getDebugSymbols();
                if (sf != null) {
                    File parent = sf.getParentFile();
                    RelativeFile rf = RelativeFile.create(parent, sf);
                    b.add(rf);
                }
            } else {
                error("Multiple file native library not supported: " + nl.getName());
            }
        }

        return b.values();
    }

    private @Nullable File collectExpandedJarSources()
    {
        if (!g.jarFiles.isEmpty()) {
            for (File f : g.jarFiles) {
                try {
                    if (Utils.isModularJarFile(f)) {
                        error("Modular JAR files may not be expanded: " + f);
                        return null;
                    }
                } catch (IOException ignore) {
                    // error will be reported later
                }
            }
            return expandJars(g.jarFiles);
        }
        return null;
    }

    private @NotNull File expandJars(@NotNull IList<File> jarFiles)
    {
        File tempDir;
        try {
            tempDir = Files.createTempDirectory("expanded").toFile();
        } catch (IOException e) {
            buildFailed("Unable to create temporary directory", e);
            throw new AssertionError();
        }

        JarExpander.Configuration eg = JarExpander.createConfiguration(
          ISet.create(jarFiles), tempDir, tempDir, null);
        try {
            JarExpander.Result r = JarExpander.expand(eg, delegate);
            if (r.errorsFound) {
                buildFailed("Encountered errors expanding JAR file(s)");
                throw new AssertionError();
            }
            return tempDir;
        } catch (IOException e) {
            buildFailed("Unable to expand JAR file(s)", e);
            throw new AssertionError();
        }
    }

    private @NotNull InternalConfiguration validate(@NotNull Configuration g)
    {
        String libraryName = validateLibraryName(g.libraryName);
        IList<File> classTrees = collectClassTrees(g.classTrees);
        IList<File> jarFiles = collectJarFiles(g.jars);
        IList<RelativeFile> resources = collectResources(g.resources);
        IList<NativeLibrary> nativeLibraries = collectNativeLibraries(g.nativeLibraries);
        Object manifest = processManifest(g.manifest);
        File basicJarFile = validateOptionalOutputFile(g.basicJarFile);
        File expandedJarFile = validateOptionalOutputFile(g.expandedJarFile);
        File buildRoot = validateBuildRoot(g.buildRoot);

        if (errorsFound) {
            buildFailed("Unable to build Java library");
            throw new AssertionError();
        }

        return new InternalConfiguration(libraryName, classTrees, jarFiles, resources,
          nativeLibraries, manifest, basicJarFile, expandedJarFile, g.coordinates, buildRoot, g.codeSigningKey,
          g.jarBuilderFactory);
    }

    private @NotNull String validateLibraryName(@NotNull String name)
    {
        if (name.isBlank()) {
            error("Library name must not be blank");
        } else if (name.contains("/") || name.contains(".")) {
            error("Invalid library name");
        }
        return name;
    }

    private @NotNull IList<File> collectClassTrees(@NotNull ISet<File> trees)
    {
        ListBuilder<File> b = IList.builder();
        for (File tree : trees) {
            if (!Files.isDirectory(tree.toPath())) {
                error("Specified class tree not found: " + tree);
            } else {
                b.add(tree.getAbsoluteFile());
            }
        }
        return b.values();
    }

    private @NotNull IList<File> collectJarFiles(@NotNull ISet<File> jars)
    {
        ListBuilder<File> b = IList.builder();
        for (File jar : jars) {
            String name = jar.getName();
            if (!name.endsWith(".jar")) {
                error("Unexpected JAR file name: " + jar);
            } else {
                if (!Files.isRegularFile(jar.toPath())) {
                    error("Specified JAR file not found: " + jar);
                } else {
                    b.add(jar.getAbsoluteFile());
                }
            }
        }
        return b.values();
    }

    private @NotNull IList<RelativeFile> collectResources(@Nullable ISet<RelativeFile> resources)
    {
        ListBuilder<RelativeFile> b = IList.builder();
        if (resources != null) {
            for (RelativeFile rf : resources) {
                File f = rf.getFile();
                if (Files.isRegularFile(f.toPath())) {
                    b.add(RelativeFile.create(rf.getPath(), Utils.resolve(f).getAbsoluteFile()));
                } else {
                    error("Specified resource not found: " + f);
                }
            }
        }
        return b.values();
    }

    private @NotNull IList<NativeLibrary> collectNativeLibraries(@NotNull ISet<NativeLibrary> libs)
    {
        ListBuilder<NativeLibrary> b = IList.builder();
        for (NativeLibrary lib : libs) {
            // Ensure that the expected files are present
            for (File f : lib.getAllFiles()) {
                if (!Files.isRegularFile(f.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    error("Missing native library file: " + f);
                }
            }
            b.add(lib);
        }
        return b.values();
    }

    private @Nullable Object processManifest(@Nullable Object manifest)
    {
        if (manifest == null) {
            return null;
        }
        if (manifest instanceof File) {
            Path p = ((File) manifest).toPath();
            if (Files.isRegularFile(p)) {
                return Utils.resolve(p.toFile()).getAbsoluteFile();
            }
            error("Manifest not found: " + p);
            return null;
        }
        return manifest;
    }

    private void codeSignFile(@NotNull File f, @NotNull String signingKey)
    {
        if (!Files.isSymbolicLink(f.toPath())) {
            File program = new File("/usr/bin/codesign");
            IList<String> args = IList.of("--sign", signingKey, "--timestamp", "--force", f.getAbsolutePath());
            ExecutionConfiguration g = ExecutionConfiguration.create(program, "sign_file", args);
            ExecutionService es = ExecutionService.get();
            try {
                ExecutionResult r = es.execute(g);
                if (r.rc != 0) {
                    delegate.error("Code signing failed: " + f.getPath());
                    if (!r.error.isEmpty()) {
                        delegate.error(r.error);
                    }
                    buildFailed("Code signing failed");
                    throw new AssertionError();
                }
            } catch (IOException e) {
                buildFailed("Unable to execute code sign program", e);
                throw new AssertionError();
            }
        }
    }

    private void installInLocalRepo(@NotNull File jarFile, @NotNull MavenCoordinates coordinates)
    {
        File program = Utils.findExecutable("mvn");
        if (program == null) {
            buildFailed("Unable to find mvn executable");
            return;
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
                delegate.error("Maven install failed: " + r.rc);
                if (!r.error.isEmpty()) {
                    delegate.error(r.error);
                }
                if (!r.output.isEmpty()) {
                    delegate.error(r.output);
                }
                buildFailed("Maven install failed");
                throw new AssertionError();
            }
            delegate.info("Installed locally as: " + coordinates);
        } catch (IOException e) {
            buildFailed("Unable to execute mvn program", e);
            throw new AssertionError();
        }
    }

    private @NotNull File validateBuildRoot(@NotNull File f)
    {
        Path p = f.toPath();
        if (Files.isDirectory(p)) {
            return f;
        }
        if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            buildFailed("Specified build directory is not a directory: " + p);
            throw new AssertionError();
        }
        buildFailed("Specified build directory not found: " + p);
        throw new AssertionError();
    }

    private @Nullable File validateOptionalOutputFile(@Nullable File f)
    {
        return f != null ? validateOutputFile(f) : null;
    }

    private @NotNull File validateOutputFile(@NotNull File f)
    {
        Path p = f.toPath();
        if (Files.exists(p, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
            buildFailed("Output file is not a file: " + f);
            throw new AssertionError();
        }
        Path parent = p.getParent();
        if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            buildFailed("Output file directory not found: " + parent);
            throw new AssertionError();
        }
        return f.getAbsoluteFile();
    }

    private void error(@NotNull String message)
    {
        errorsFound = true;
        delegate.error(message);
    }

    /**
      This method is expected to throw a RuntimeException.
    */

    protected final void buildFailed(@NotNull String message)
    {
        delegate.announceBuildFailure(message, null);
        throw new RuntimeException("announceBuildFailure failed to throw an exception");
    }

    /**
      This method is expected to throw a RuntimeException.
    */

    protected final void buildFailed(@NotNull String message, @NotNull Exception ex)
    {
        delegate.announceBuildFailure(message, ex);
        throw new RuntimeException("announceBuildFailure failed to throw an exception");
    }

    public interface Delegate
      extends JARBuilder.Delegate
    {
    }
}
