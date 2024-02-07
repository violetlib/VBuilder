/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vbuilder.ant;

import org.apache.maven.resolver.internal.ant.types.Dependencies;
import org.apache.maven.resolver.internal.ant.types.Dependency;
import org.apache.maven.resolver.internal.ant.types.DependencyContainer;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.resources.FileResource;
import org.apache.tools.ant.types.resources.ZipResource;
import org.apache.tools.zip.ZipEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.collections.IList;
import org.violetlib.collections.ListBuilder;
import org.violetlib.vbuilder.MavenCoordinates;
import org.violetlib.vbuilder.MavenVersionManagement;
import org.violetlib.vbuilder.Reporter;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipException;

/**

*/

public class JarResourceProcessor
{
    /**
      Extend a list of required JAR files by adding indirect dependencies.
      Indirect dependencies are specified using the Class-Path or Dependencies attribute in the JAR manifest.

      @param sourceName A name used in error messages.
      @param searchPath The directories that are searched to find additional JAR files.
      @param direct The initially known required JAR files (specified as files, not as Maven artifacts).
      @param encoding An optional encoding.
      @param prefix An optional prefix.
      @param dependenciesSupplier A supplier of a Dependencies instance to which Maven dependencies are added.
      @param mavenVersionManagement A manager of Maven artifact dependencies.
      @param isStrict If true, unresolved dependencies are considered errors that will fail the build.
      @param exceptions These paths are ignored if they cannot be resolved.
      @param reporter A reporter of messages.
      @return the files.
    */

    public static @NotNull IList<File> process(@NotNull String sourceName,
                                               @NotNull IList<File> searchPath,
                                               @NotNull IList<File> direct,
                                               @Nullable String encoding,
                                               @Nullable String prefix,
                                               @NotNull Supplier<Dependencies> dependenciesSupplier,
                                               @Nullable MavenVersionManagement mavenVersionManagement,
                                               boolean isStrict,
                                               @NotNull IList<String> exceptions,
                                               @NotNull Reporter reporter)
      throws BuildException
    {
        return new JarResourceProcessor(sourceName, searchPath, direct, encoding, prefix, dependenciesSupplier,
          mavenVersionManagement, isStrict, exceptions, reporter, false).getFiles();
    }

    /**
      Create a list of resources needed to create a merged JAR file from a list of required JAR files and their
      indirect dependencies.

      @param searchPath The directories that are searched to find additional JAR files.
      @param direct The initially known required JAR files (specified as files, not as Maven artifacts).
      @param encoding An optional encoding.
      @param prefix An optional prefix.
      @param dependenciesSupplier A supplier of a Dependencies instance to which Maven dependencies are added.
      @param mavenVersionManagement A manager of Maven artifact dependencies.
      @param reporter A reporter of messages.
      @return the resources.
    */

    public static @NotNull IList<Resource> merge(@NotNull String sourceName,
                                                 @NotNull IList<File> searchPath,
                                                 @NotNull IList<File> direct,
                                                 @Nullable String encoding,
                                                 @Nullable String prefix,
                                                 @NotNull Supplier<Dependencies> dependenciesSupplier,
                                                 @Nullable MavenVersionManagement mavenVersionManagement,
                                                 @NotNull Reporter reporter)
      throws BuildException
    {
        return new JarResourceProcessor(sourceName, searchPath, direct, encoding, prefix, dependenciesSupplier,
          mavenVersionManagement, true, IList.empty(), reporter, true).getResources();
    }

    private final @Nullable MavenVersionManagement mavenVersionManagement;
    private final @NotNull String sourceName;
    private final boolean isMerge;
    private final @Nullable String encoding;
    private final @NotNull List<File> resultFiles = new ArrayList<>();
    private final @NotNull List<Resource> resultResources = new ArrayList<>();
    private final @NotNull Supplier<Dependencies> dependenciesSupplier;
    private final boolean isStrict;
    private final @NotNull IList<String> exceptions;
    private final @NotNull Reporter reporter;
    private boolean errorsFound;

    private JarResourceProcessor(@NotNull String sourceName,
                                 @NotNull IList<File> searchPath,
                                 @NotNull IList<File> direct,
                                 @Nullable String encoding,
                                 @Nullable String prefix,
                                 @NotNull Supplier<Dependencies> dependenciesSupplier,
                                 @Nullable MavenVersionManagement mavenVersionManagement,
                                 boolean isStrict,
                                 @NotNull IList<String> exceptions,
                                 @NotNull Reporter reporter,
                                 boolean isMerge)
      throws BuildException
    {
        this.sourceName = sourceName;
        this.encoding = encoding;
        this.isMerge = isMerge;
        this.dependenciesSupplier = dependenciesSupplier;
        this.reporter = reporter;
        this.mavenVersionManagement = mavenVersionManagement;
        this.isStrict = isStrict;
        this.exceptions = exceptions;

        ClosedSet closure = new ClosedSet(searchPath);
        for (File f : direct) {
            closure.addLibraryName(f.getAbsolutePath(), sourceName);
        }
        List<File> closed = closure.getFiles();

        for (File f : closed) {
            if (isMerge && isJar(f)) {
                addJarResources(f);
            } else {
                if (!resultFiles.contains(f)) {
                    resultFiles.add(f);
                }
            }
        }

        if (errorsFound) {
            throw new BuildException("Errors found");
        }
    }

    private @NotNull IList<File> getFiles()
    {
        return IList.create(resultFiles);
    }

    private @NotNull IList<Resource> getResources()
    {
        if (resultFiles.isEmpty()) {
            return IList.create(resultResources);
        }
        ListBuilder<Resource> b = IList.builder();
        for (File f : resultFiles) {
            b.add(new FileResource(f));
        }
        b.addAll(resultResources);
        return b.values();
    }

    private void addJarResources(@NotNull File f)
    {
        try {
            JarFile jf = new JarFile(f);
            addJarResources(jf);
        } catch (IOException | SecurityException ex) {
            throw new BuildException("problem reading JAR file" + f, ex);
        }
    }

    private void addJarResources(@NotNull JarFile jf)
    {
        File f = new File(jf.getName());
        Enumeration<JarEntry> m = jf.entries();
        while (m.hasMoreElements()) {
            JarEntry e = m.nextElement();
            String name = e.getName();
            if (name.equals("Manifest")) {
                continue;
            }
            if (name.startsWith("META-INF")) {
                continue;
            }
            try {
                ZipResource r = new ZipResource(f, encoding, new ZipEntry(e));
                resultResources.add(r);
            } catch (ZipException ex) {
                throw new BuildException("problem creating JAR file entry " + f, ex);
            }
        }
    }

    private boolean isJar(@NotNull File f)
    {
        return f.getName().endsWith(".jar");
    }

    /**
      This class computes the transitive closure of a set of JAR files based on a dependency
      relationship. A source JAR file is mapped to a set of dependent JAR files by resolving the source file
      to a JAR file and reading the Class-Path or Dependencies (preferred) manifest attribute of that JAR file.
      Each element of a Class-Path attribute
      that is a JAR file name ("something.jar") will be added to the set of JAR file names (after
      stripping any directory path).
      Each element of a Dependency attribute that identifies a Maven coordinate will be added to a Dependencies
      instance. Other elements are assumed to be library names ("something") which are mapped to Jar file names
      ("something.jar").
    */

    private class ClosedSet
    {
        private final @NotNull IList<File> directorySearchPath;
        private final @NotNull LinkedHashSet<File> set = new LinkedHashSet<>();

        public ClosedSet(@NotNull IList<File> directorySearchPath)
        {
            this.directorySearchPath = directorySearchPath;
        }

        public @NotNull List<File> getFiles()
        {
            return new ArrayList<>(set);
        }

        /**
          Add a JAR file to the set.
          @param path The file path.
          @param source A description of the source of this information, used in an exception message.
        */

        public void addLibraryName(@NotNull String path, @NotNull String source)
        {
            if (!path.endsWith(".jar")) {
                return;
            }

            File f = resolveJarFilename(path);
            if (f == null) {
                 if (!exceptions.contains(path)) {
                    String message = "Unable to resolve " + path + " in " + source;
                    if (isStrict) {
                        error(message);
                    } else {
                        info(message);
                    }
                }
                return;
            }

            if (set.contains(f)) {
                return;
            }

            set.add(f);

            verbose("Adding " + f.getPath() + " from " + source);

            addRequired(f);
        }

        private void addRequired(@NotNull File f)
        {
            if (!f.isFile()) {
                throw new BuildException("Required JAR file does not exist: " + f.getPath());
            }

            try {
                JarFile jf = new JarFile(f, false, JarFile.OPEN_READ);

                try {
                    Manifest m = jf.getManifest();
                    Attributes as = m.getMainAttributes();
                    String dp = as.getValue("Dependencies");
                    if (dp != null) {
                        processDependencies(dp, f);
                    } else {
                        String cp = as.getValue("Class-Path");
                        if (cp != null) {
                            processClassPath(cp, f);
                        } else {
                            info("JAR file " + f.getPath() + " has no Class-Path or Dependencies attribute");
                        }
                    }
                } finally {
                    jf.close();
                }
            } catch (BuildException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new BuildException("Unable to read JAR file " + f.getPath() + ": " + ex);
            }
        }

        /**
          Process the Class-Path attribute of a JAR manifest.
          @param s The attribute value.
          @param f The JAR file.
        */

        private void processClassPath(@NotNull String s, @NotNull File f)
        {
            // We assume that the class path attribute of the JAR file indicates the JAR files upon which this JAR file
            // depends. Only class path elements that name JAR files are considered. Any directory name is ignored. The
            // JAR file name will be resolved using the class path attribute of this element.

            List<String> jarNames = extractJarNamesFromClassPath(s);
            for (String jarName : jarNames) {
                addLibraryName(jarName, f.getPath());
            }
        }

        /**
          Process the Dependencies attribute of a JAR manifest.
          @param s The attribute value.
          @param f The JAR file.
        */

        private void processDependencies(@NotNull String s, @NotNull File f)
        {
            List<String> libraryNames = extractLibraryNamesFromDependencies(s, f);
            for (String libraryName : libraryNames) {
                addLibraryName(libraryName, f.getPath());
            }
        }

        /**
          Use the directory search path to locate a JAR file given a file name.

          @param fn The name or path of a JAR file. If <code>fn</code> is an absolute path, then
          <code>directorySearchPath</code> is ignored and the designated file is returned (if it exists). Otherwise,
          it is resolved in turn using each directory in <code>directorySearchPath</code> until an existing file is
          found.

          @return the existing JAR file located as described above, or null if no existing file is found.
        */

        private @Nullable File resolveJarFilename(@NotNull String fn)
        {
            if (!fn.endsWith(".jar")) {
                fn = fn + ".jar";
            }

            if (fn.startsWith("/")) {
                File f = new File(fn);
                if (f.isFile()) {
                    return f;
                } else {
                    return null;
                }
            }

            for (File dir : directorySearchPath) {
                File f = new File(dir, fn);
                if (f.isFile()) {
                    return f;
                }
            }

            return null;
        }

        private @NotNull List<String> extractJarNamesFromClassPath(@NotNull String path)
        {
            List<String> a = new ArrayList<>();
            StringTokenizer tok = new StringTokenizer(path);
            while (tok.hasMoreTokens()) {
                String pathElement = tok.nextToken();
                if (pathElement.endsWith(".jar")) {
                    // Remove any directory part
                    File f = new File(pathElement);
                    String name = f.getName();
                    a.add(name);
                }
            }

            return a;
        }
    }

    private @NotNull List<String> extractLibraryNamesFromDependencies(@NotNull String path, @NotNull File source)
    {
        List<String> a = new ArrayList<>();
        StringTokenizer tok = new StringTokenizer(path);
        while (tok.hasMoreTokens()) {
            String s = tok.nextToken();
            if (MavenCoordinates.isValid(s)) {
                processMavenCoordinate(s, source);
            } else if (isBasicLibraryName(s)) {
                verbose("Adding library " + s + " to contents from " + source.getName());
                a.add(s + ".jar");
            } else {
                error("Unrecognized dependency in " + sourceName + ": " + s);
            }
        }

        return a;
    }

    private void processMavenCoordinate(@NotNull String s, @NotNull File source)
    {
        if (mavenVersionManagement != null) {
            if (!s.endsWith(":provided")) {
                // The Maven resolver requires a version number.
                if (MavenCoordinates.isKeyOnly(s)) {
                    String version = mavenVersionManagement.getPreferredVersion(s);
                    if (version == null) {
                        info("No preferred version for " + s);
                        s = s + ":[1)";
                    } else {
                        s = s + ":" + version;
                    }
                }

                Dependencies dependencies = dependenciesSupplier.get();
                Dependency d = new Dependency();
                d.setCoords(s);

                if (isDuplicate(d, dependencies.getDependencyContainers())) {
                    verbose("Ignoring duplicate dependency: " + s);
                } else {
                    dependencies.addDependency(d);
                    verbose("Adding " + s + " to contents from " + source.getName());
                }
            }
        } else {
            error("Maven coordinates are not supported");
        }
    }

    private boolean isDuplicate(@NotNull Dependency d, @NotNull List<DependencyContainer> dcs)
    {
        String key = d.getVersionlessKey();
        for (DependencyContainer dc : dcs) {
            if (dc instanceof Dependency) {
                if (key.equals(((Dependency) dc).getVersionlessKey())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isBasicLibraryName(@NotNull String s)
    {
        return !s.isEmpty() && !s.contains(".");
    }

    public void info(@NotNull String message)
    {
        reporter.info(message);
    }

    public void verbose(@NotNull String message)
    {
        reporter.verbose(message);
    }

    public void error(@NotNull String message)
    {
        errorsFound = true;
        reporter.error(message);
    }
}
