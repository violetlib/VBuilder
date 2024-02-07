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
import org.violetlib.collections.SetBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
  Build a JAR file.
*/

public class JARBuilder
{
    public static void createJAR(@NotNull Configuration g, @NotNull Delegate delegate)
      throws BuildException
    {
        new JARBuilder(g, delegate);
    }

    public interface BuilderFactory
    {
        @NotNull Builder create(@NotNull JARBuilder.Delegate delegate);
    }

    public interface Builder
    {
        void createLibrary(@NotNull Configuration g)
          throws BuildException;
    }

    public static @NotNull BuilderFactory createFactory(@NotNull JARBuilder.Delegate delegate)
    {
        return new BuilderFactory()
        {
            @Override
            public @NotNull Builder create(@NotNull JARBuilder.Delegate delegate)
            {
                return g -> createJAR(g, delegate);
            }
        };
    }

    /**
      Create a configuration for the creation of a JAR file.
      @param program The {@code jar} program to use.
      @param sources The sources of files to include in the JAR (see below).
      @param output The JAR file to create.
      @param manifest An optional manifest to include in the JAR (see below).
      @param mainClassName An optional main class name, for possible use by the delegate.
      @param createIndex If true, the JAR is indexed.
      <h2>Sources</h2>
      Sources specify files to include in the JAR and the corresponding relative paths that identify the files in the
      JAR.
      <p>
      Supported sources include:
      <ul>
      <li>A regular file. The file is included in the JAR. The associated path is the file name. In other words, the
      file appears at top level in the implied hierarchy. The file may be specified using a symbolic link.</li>

      <li>A file directory. The files in the tree rooted at the directory are included in the JAR, with the relative
      path from the root used as the path in the JAR. The directory may be specified using a symbolic link.
      Symbolic links in the tree are ignored.</li>

      <li>A {@link RelativeFile}. The file or directory specified by the {@code RelativeFile} is
      processed as described above, except that the path for the file or directory (as specified above) is prefixed by
      the relative path from the {@code RelativeFile}.</li>

      <li>Other types of sources may be supported by the delegate.</li>
      </ul>

      <h2>Manifest</h2>
      The manifest parameter may identify a manifest file or a {@link java.util.jar.Manifest} for inclusion in the JAR.
      In the latter case, the {@code Main-Class} attribute will be installed if available and not already present.
      Other options may be supported by the delegate.
    */

    public static @NotNull Configuration createConfiguration(@NotNull File program,
                                                             @NotNull IList<Object> sources,
                                                             @NotNull File output,
                                                             @Nullable Object manifest,
                                                             @Nullable String mainClassName,
                                                             boolean createIndex)
    {
        return new Configuration(program, sources, output, manifest, mainClassName, createIndex);
    }

    public static class Configuration
    {
        public final @NotNull File program;
        public final @NotNull IList<Object> sources;
        public final @NotNull File output;
        public final @Nullable Object manifest;
        public final @Nullable String mainClassName;
        public final boolean createIndex;

        private Configuration(@NotNull File program,
                              @NotNull IList<Object> sources,
                              @NotNull File output,
                              @Nullable Object manifest,
                              @Nullable String mainClassName,
                              boolean createIndex)
        {
            this.program = program;
            this.sources = sources;
            this.output = output;
            this.manifest = manifest;
            this.mainClassName = mainClassName;
            this.createIndex = createIndex;
        }
    }

    private final @NotNull Delegate delegate;
    protected boolean errorsFound;

    private JARBuilder(@NotNull Configuration g, @NotNull Delegate delegate)
      throws BuildException
    {
        this.delegate = delegate;

        File manifest = prepareManifest(g.manifest, g.mainClassName);
        String jarPath = getOutputPath(g.output);
        File jarFile = new File(jarPath);

        // Using JAR command
        IList<RelativeFile> files = collectSources(g.sources).sort();
        createJarFileUsingCommandWithFile(g.program, files, manifest, jarFile);

        if (g.createIndex) {
            createIndex(g.program, jarPath);
        }
    }

    private @NotNull ISet<RelativeFile> collectSources(@NotNull IList<Object> sources)
    {
        SetBuilder<RelativeFile> b = ISet.builder();
        for (Object source : sources) {
            if (source instanceof RelativeFile) {
                b.add((RelativeFile) source);
            } else {
                ISet<RelativeFile> rfs = resolveSource(source);
                b.addAll(rfs);
            }
        }
        return b.values();
    }

    protected @NotNull ISet<RelativeFile> resolveSource(@NotNull Object source)
    {
        if (source instanceof File) {
            ISet<RelativeFile> fs = resolveSourceFile((File) source);
            return fs != null ? fs : ISet.empty();
        }
        return delegate.resolveSource(source);
    }

    protected @Nullable ISet<RelativeFile> resolveSourceFile(@NotNull File f)
    {
        Path p = f.toPath();
        if (Files.isDirectory(p)) {
            try {
                String basePath = f.getAbsolutePath() + "/";
                IList<File> files = Utils.getFilesInTree(Utils.resolve(f), true);
                SetBuilder<RelativeFile> b = ISet.builder();
                for (File ff : files) {
                    String name = ff.getName();
                    if (name.startsWith(".")) {
                        continue;
                    }
                    if (name.equals("Manifest")) {
                        // Special case for my current usage
                        continue;
                    }
                    String fpath = ff.getAbsolutePath();
                    if (fpath.startsWith(basePath)) {
                        String rpath = fpath.substring(basePath.length());
                        RelativeFile rf = RelativeFile.create(rpath, ff);
                        b.add(rf);
                    } else {
                        errorsFound = true;
                        delegate.error("File " + fpath + " not in " + basePath);
                    }
                }

                ISet<RelativeFile> rfs = b.values();
                return rfs;
            } catch (IOException e) {
                errorsFound = true;
                delegate.error("Unable to process source directory: " + f);
                return null;
            }
        } else if (Files.isRegularFile(p)) {
            String path = f.getName();
            File ff = Utils.resolve(f);
            return ISet.of(RelativeFile.create(path, ff));
        } else if (Files.exists(p)) {
            errorsFound = true;
            delegate.error("Unsupported source: " + f);
            return null;
        } else {
            errorsFound = true;
            delegate.error("Source not found: " + f);
            return null;
        }
    }

    private @Nullable File prepareManifest(@Nullable Object manifest, @Nullable String mainClassName)
    {
        if (manifest instanceof File) {
            File f = (File) manifest;
            Path p = f.toPath();
            if (Files.isRegularFile(p)) {
                return Utils.resolve(f).getAbsoluteFile();
            }
            if (!Files.exists(p)) {
                buildFailed("Manifest not found: " + f.getPath());
            } else {
                buildFailed("Manifest is not a file: " + f.getPath());
            }
            throw new AssertionError();
        }

        if (manifest instanceof java.util.jar.Manifest) {
            java.util.jar.Manifest m = (java.util.jar.Manifest) manifest;
            File f = writeManifestToTemp(m, mainClassName);
            if (f != null) {
                return f;
            }
            buildFailed("Unable to write Manifest to a temporary file");
            throw new AssertionError();
        }

        if (manifest != null) {
            File m = delegate.resolveManifest(manifest, mainClassName);
            if (m != null) {
                return m;
            }
        }

        return null;
    }

    private @Nullable File writeManifestToTemp(@NotNull java.util.jar.Manifest m, @Nullable String mainClassName)
    {
        if (mainClassName != null) {
            java.util.jar.Attributes as = m.getMainAttributes();
            if (!as.containsKey("Main-Class")) {
                as.put("Main-Class", mainClassName);
            }
        }
        try {
            File temp = Files.createTempFile("Manifest", "mf").toFile();
            writeManifestFile(m, temp);
            return temp;
        } catch (IOException e) {
            delegate.error("Unable to create temporary manifest file [" + e + "]");
            return null;
        }
    }

    private void writeManifestFile(@NotNull java.util.jar.Manifest m, @NotNull File f)
      throws IOException
    {
        try (FileOutputStream s = new FileOutputStream(f)) {
            m.write(s);
        }
    }

    private @NotNull String getOutputPath(@NotNull File f)
    {
        Path p = f.toPath();
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            return f.getAbsolutePath();
        }
        if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
            return f.getAbsolutePath();
        }
        buildFailed("Output file is not a file: " + f);
        throw new AssertionError();
    }

    private void createJarFileUsingCommand(@NotNull File program,
                                           @NotNull IList<RelativeFile> sources,
                                           @Nullable File manifestFile,
                                           @NotNull File jarFile)
    {
        // TBD: probably safer to write a file?

        // Note: the JAR program accepts only files whose path ends with the associated relative path.

        ListBuilder<String> b = IList.builder();
        b.add("--create");
        b.add("--file=" + jarFile.getAbsolutePath());
        if (manifestFile != null) {
            b.add("--manifest=" + manifestFile);
        }

        // Apparently, -C is not sticky, so no advantage to partitioning by directory.

        for (RelativeFile rf : sources) {
            File baseDir = rf.getBaseDirectory();
            if (baseDir != null) {
                b.add("-C");
                b.add(baseDir.getAbsolutePath());
                delegate.verbose("    " + rf.getPath());
                b.add(rf.getPath());
            } else {
                errorsFound = true;
                delegate.error("Cannot include file [inconsistent path]:" + rf.getFile());
            }
        }

        IList<String> args = b.values();

        if (errorsFound) {
            buildFailed("Unable to create JAR " + jarFile);
        }

        ExecutionConfiguration e = ExecutionConfiguration.create(program, "jar", args);
        ExecutionService es = ExecutionService.get();
        try {
            ExecutionResult r = es.execute(e);
            if (r.rc != 0) {
                delegate.error("create JAR failed " + jarFile);
                if (!r.error.isEmpty()) {
                    delegate.error(r.error);
                }
                buildFailed("create JAR failed " + jarFile);
                throw new AssertionError();
            }
        } catch (IOException ex) {
            buildFailed("Unable to run " + e.program + ": " + ex);
            throw new AssertionError();
        }
    }

    private void createJarFileUsingCommandWithFile(@NotNull File program,
                                                   @NotNull IList<RelativeFile> sources,
                                                   @Nullable File manifestFile,
                                                   @NotNull File jarFile)
      throws BuildException
    {
        // Note: the JAR program accepts only files whose path ends with the associated relative path.

        ListBuilder<String> b = IList.builder();
        b.add("--create");
        b.add("--file=" + jarFile.getAbsolutePath());
        if (manifestFile != null) {
            b.add("--manifest=" + manifestFile);
        }

        // Apparently, -C is not sticky, so no advantage to partitioning by directory.

        try {
            Path tmp = Files.createTempFile("jararg", ".txt");
            try (FileWriter s = new FileWriter(tmp.toFile())) {
                for (RelativeFile rf : sources) {
                    File baseDir = rf.getBaseDirectory();
                    if (baseDir != null) {
                        s.write(" -C ");
                        s.write(baseDir.getAbsolutePath());
                        delegate.verbose("    " + rf.getPath());
                        s.write(" ");
                        s.write(rf.getPath());
                    } else {
                        errorsFound = true;
                        delegate.error("Cannot include file [inconsistent path]:" + rf.getFile());
                    }
                }
            }
            b.add("@" + tmp.toAbsolutePath());
        } catch (IOException e) {
            throw new BuildException("Unable to write jar command file: " + e);
        }

        IList<String> args = b.values();

        if (errorsFound) {
            buildFailed("Unable to create JAR " + jarFile);
        }

        ExecutionConfiguration e = ExecutionConfiguration.create(program, "jar", args);
        ExecutionService es = ExecutionService.get();
        try {
            ExecutionResult r = es.execute(e);
            if (r.rc != 0) {
                delegate.error("create JAR failed " + jarFile);
                if (!r.error.isEmpty()) {
                    delegate.error(r.error);
                }
                buildFailed("create JAR failed " + jarFile);
                throw new AssertionError();
            }
        } catch (IOException ex) {
            buildFailed("Unable to run " + e.program + ": " + ex);
            throw new AssertionError();
        }
    }

    private void createIndex(@NotNull File program, @NotNull String jarFile)
    {
        IList<String> args = IList.of("--generate-index=" + jarFile);
        ExecutionConfiguration e = ExecutionConfiguration.create(program, "jar", args);
        ExecutionService es = ExecutionService.get();
        try {
            ExecutionResult r = es.execute(e);
            if (r.rc != 0) {
                delegate.error("create JAR index failed");
                if (!r.error.isEmpty()) {
                    delegate.error(r.error);
                }
                buildFailed("create JAR index failed");
                throw new AssertionError();
            }
        } catch (IOException ex) {
            buildFailed("Unable to run " + e.program, ex);
            throw new AssertionError();
        }
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
      extends BuildDelegate
    {
        @Nullable File resolveManifest(@NotNull Object manifest, @Nullable String mainClassName);

        @NotNull ISet<RelativeFile> resolveSource(@NotNull Object source);
    }
}
