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
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.collections.ICollection;
import org.violetlib.collections.IList;
import org.violetlib.collections.ListBuilder;
import org.violetlib.vbuilder.*;

import java.io.File;
import java.util.*;
import java.util.function.Function;

/**
  Specify Java libraries required for compilation and/or inclusion in a library or application.
  <p>
  Java libraries (JAR files) may be identified as files (using FileList-based nested elements), by library name, by
  Maven coordinates, or using nested Maven Resolver dependencies.
  <p>
  A library name is the name of the JAR file without the {@code .jar} extension.
  Named libraries are located using a search path.
  <p>
  Maven coordinates do not need to specify versions. Preferred versions of Maven artifacts may be specified
  using the {@link use} element.

  @ant.type name="classpath"
*/

public class ClassPath
  extends DataType
  implements Reporter
{
    /*
      The categories of dependencies (names may change):

      Compile elements are needed at compile time only.

      Required elements are needed at compile time. Their closure is needed in an application or combined JAR.

      Included elements are not needed at compile time, but are needed to construct an expanded JAR (or application).

      Runtime elements are not needed at compile time, but are needed in an application.

      Source trees do not work because the Ant javac task supplies a -sourcepath to javac.
    */

    /**
      A Path with this ID defines a default search path for libraries, if no search path is defined.
      @ant.ref name="lib.path" type="Path"
    */

    public static final @NotNull String LIB_PATH = "lib.path";

    /**
      An internal Dependencies created and managed by this class.
    */

    private static final @NotNull String LIB_DEPENDENCIES = "lib.dependencies";

    private @Nullable Path searchPath;
    private @Nullable IList<File> searchFiles;

    private boolean isApplication;

    /**
      Dependencies that need to be traced by the Maven resolver.
    */

    private @Nullable Dependencies dependencies;

    private @Nullable MavenVersionManagement mm;
    private @NotNull IList<String> exclusions = IList.empty();
    private boolean isTop;

    private enum Scope { REQUIRED, COMPILE, RUNTIME, INCLUDED }

    private @Nullable String compileLibraryNames;
    private @Nullable String requiredLibraryNames;
    private @Nullable String includedLibraryNames;
    private @Nullable String runtimeLibraryNames;

    private final @NotNull List<ClassPathElement> compile = new ArrayList<>();
    private final @NotNull List<ClassPathElement> required = new ArrayList<>();
    private final @NotNull List<ClassPathElement> included = new ArrayList<>();
    private final @NotNull List<ClassPathElement> runtime = new ArrayList<>();
    private final @NotNull List<ClassPath> nested = new ArrayList<>();
    private final @NotNull List<Dependency> nestedDependencies = new ArrayList<>();
    private @Nullable Map<String,String> usedMavenVersions;
    private @Nullable FoundLibraryReporter foundLibraryReporter;

    private @Nullable ClassPath.Output cachedOutput;

    private boolean errorsFound;
    private boolean librarySearchPathWarningIssued;
    private final @NotNull Set<String> replacementGuidanceIssued = new HashSet<>();
    private final @NotNull Set<String> usingLatestWarningIssued = new HashSet<>();

    public static class Output
    {
        public final @NotNull IList<File> compile;
        public final @NotNull IList<File> required;
        public final @NotNull IList<File> included;
        public final @NotNull IList<File> runtime;
        public final @NotNull Dependencies dependencies;

        private Output(@NotNull IList<File> compile,
                       @NotNull IList<File> required,
                       @NotNull IList<File> included,
                       @NotNull IList<File> runtime,
                       @NotNull Dependencies dependencies)
        {
            this.compile = compile;
            this.required = required;
            this.included = included;
            this.runtime = runtime;
            this.dependencies = dependencies;
        }

        public @NotNull IList<File> getCompile()
        {
            return compile;
        }

        public @NotNull IList<File> getRequired()
        {
            return required;
        }

        public @NotNull IList<File> getIncluded()
        {
            return included;
        }

        public @NotNull IList<File> getRuntime()
        {
            return runtime;
        }

        public @NotNull Dependencies getDependencies()
        {
            return dependencies;
        }
    }

    public ClassPath(@NotNull Project project)
    {
        setProject(project);
    }

    private @NotNull ClassPath.Output getOutput()
    {
        if (cachedOutput != null) {
            return cachedOutput;
        }
        processNestedDependencies();
        IList<File> compile = getCompileFiles();
        IList<File> required = getRequiredFiles();
        IList<File> included = getIncludedFiles();
        IList<File> runtime = getRuntimeFiles();
        Dependencies d = ensureDependencies();
        cachedOutput = new Output(compile, required, included, runtime, d);
        if (isTop) {
            assert foundLibraryReporter != null;
            showPreferredVersions();
            foundLibraryReporter.show(this);
        }
        return cachedOutput;
    }

    private void showPreferredVersions()
    {
        assert usedMavenVersions != null;
        if (!usedMavenVersions.isEmpty()) {
            info("");
            info("Selected artifact versions:");
            IList<String> keys = IList.create(usedMavenVersions.keySet()).sort();
            for (String key : keys) {
                info(String.format("  %40s:%s", key, usedMavenVersions.get(key)));
            }
            info("");
        }
    }

    private void showFiles(@NotNull ICollection<File> files, @NotNull String category)
    {
        info(String.format("%s files", category));
        for (File f : files) {
            info("  " + f.getPath());
        }
    }

    private @NotNull IList<File> getFiles(@NotNull Iterable<ClassPathElement> es)
    {
        ListBuilder<File> b = IList.builder(IList.NO_DUPLICATES);
        for (ClassPathElement e : es) {
            IList<File> fs = e.getFiles();
            b.addAll(fs);
        }
        return b.values();
    }

    private @NotNull IList<String> getLibraryNames(@NotNull Iterable<ClassPathElement> es)
    {
        ListBuilder<String> b = IList.builder(IList.NO_DUPLICATES);
        for (ClassPathElement e : es) {
            IList<String> ns = e.getLibraryNames();
            b.addAll(ns);
        }
        return b.values();
    }

    @Override
    protected void checkChildrenAllowed()
    {
        throw noChildrenAllowed();
    }

    @Override
    public void setRefid(Reference r)
      throws BuildException
    {
        if (!compile.isEmpty() || !required.isEmpty() || !included.isEmpty() || !runtime.isEmpty()) {
            throw tooManyAttributes();
        }
        super.setRefid(r);
    }

    /**
      Specify the search path to be used when looking for dependent JARs identified by library name.
      @ant.prop name="searchPath"
      @ant.optional
      If no search path is specified, the path with ID {@code lib.path} is used (if defined).
    */

    public void setSearchPath(@NotNull Path path)
    {
        this.searchPath = path;
    }

    /**
      Specify the ID of a search path to be used when looking for dependent JARs identified by library name.
      @ant.prop name="searchPathRef"
      @ant.optional
      If no search path is specified, the path with ID {@code lib.path} is used (if defined).
    */

    public void setSearchPathRef(@NotNull Reference ref)
    {
        this.searchPath = ref.getReferencedObject();
    }

    /**
      Specify Java libraries that are required only for compilation.
      Libraries may be specified using names or artifact coordinates (versions not required).
      @ant.optional
    */

    public void setCompile(@NotNull String s)
    {
        compileLibraryNames = s;
    }

    /**
      Specify Java libraries that are required for compilation and execution.
      Libraries may be specified using names or artifact coordinates (versions not required).
      @ant.optional
    */

    public void setRequired(@NotNull String s)
    {
        requiredLibraryNames = s;
    }

//    /**
//      Specify the names of the Java libraries to be included in an expanded Jar.
//    */
//
//    public void setIncluded(@NotNull String s)
//    {
//        includedLibraryNames = s;
//    }

    /**
      Specify Java libraries that are required for execution, but not for compilation.
      Libraries may be specified using names or artifact coordinates (versions not required).
      @ant.optional
    */

    public void setRuntime(@NotNull String s)
    {
        runtimeLibraryNames = s;
    }

    public @NotNull Output execute(boolean isApplication,
                                   @NotNull IList<String> exclusions,
                                   @Nullable MavenVersionManagement mm)
    {
        return execute(isApplication, exclusions, mm, null, null);
    }

    private @NotNull Output execute(boolean isApplication,
                                    @NotNull IList<String> exclusions,
                                    @Nullable MavenVersionManagement mm,
                                    @Nullable Map<String,String> usedMavenVersions,
                                    @Nullable FoundLibraryReporter foundLibraryReporter)
    {
        if (isReference()) {
            ClassPath cp = getRef();
            return cp.execute(isApplication, exclusions, mm, usedMavenVersions, foundLibraryReporter);
        } else {
            this.isApplication = isApplication;
            this.exclusions = exclusions;
            this.mm = mm;
            this.isTop = foundLibraryReporter == null;
            if (usedMavenVersions != null) {
                this.usedMavenVersions = usedMavenVersions;
            } else {
                this.usedMavenVersions = new HashMap<>();
            }
            if (foundLibraryReporter != null) {
                this.foundLibraryReporter = foundLibraryReporter;
            } else {
                this.foundLibraryReporter = FoundLibraryReporter.create(this);
            }
            return getOutput();
        }
    }

    private @NotNull IList<File> getCompileFiles()
    {
        return getFiles(compile, Output::getCompile, compileLibraryNames, Scope.COMPILE);
    }

    private @NotNull IList<File> getRequiredFiles()
    {
        IList<File> files = getFiles(required, Output::getRequired, requiredLibraryNames, Scope.REQUIRED);
        return isTop ? computeClosure(files) : files;
    }

    private @NotNull IList<File> getIncludedFiles()
    {
        return getFiles(included, Output::getIncluded, includedLibraryNames, Scope.INCLUDED);
    }

    private @NotNull IList<File> getRuntimeFiles()
    {
        return getFiles(runtime, Output::getRuntime, requiredLibraryNames, Scope.RUNTIME);
    }

    private @NotNull Output getNestedOutput(@NotNull ClassPath n)
    {
        return n.execute(isApplication, exclusions, mm, usedMavenVersions, foundLibraryReporter);
    }

    private @NotNull IList<File> getFiles(@NotNull List<ClassPathElement> es,
                                          @NotNull Function<Output,IList<File>> outfun,
                                          @Nullable String names,
                                          @NotNull Scope scope)
    {
        if (isReference()) {
            throw new AssertionError("This method should not be called on a reference");
        }
        ListBuilder<File> b = IList.builder();
        b.addAll(getFiles(es));
        for (ClassPath n : nested) {
            Output o = getNestedOutput(n);
            b.addAll(outfun.apply(o));
        }
        if (names != null) {
            b.addAll(getLibraries(names, scope));
        }
        IList<String> libraryNames = getLibraryNames(es);
        b.addAll(getLibraries(libraryNames, scope));
        return b.values();
    }

    private void processNestedDependencies()
    {
        if (!nestedDependencies.isEmpty()) {
            IList<Dependency> ds = IList.create(nestedDependencies);
            nestedDependencies.clear();
            for (Dependency d : ds) {
                String version = d.getVersion();
                if (version == null) {
                    String key = d.getGroupId() + ":" + d.getArtifactId();
                    version = getPreferredVersion(key);
                    d.setVersion(version);
                }
                addDependency(d);
            }
        }
    }

    private @NotNull IList<File> computeClosure(@NotNull IList<File> es)
    {
        String sourceName = getProject().getName();
        ListBuilder<File> fb = IList.builder();
        IList<File> inputs = getJarFiles(es);
        fb.addAll(inputs);
        IList<File> jarSearchPath = createSearchPath(ensureSearchFileList(), inputs, getJarFiles(getRuntimeFiles()));
        Reporter r = ProjectReporter.create(getProject());
        IList<File> allJars = JarResourceProcessor.process(sourceName, jarSearchPath, inputs, null, null,
          this::ensureDependencies, mm, isApplication, exclusions, r);
        ListBuilder<File> rb = IList.builder();
        rb.addAll(es);
        for (File f : allJars) {
            rb.add(f);
        }
        return rb.values();
    }

    private @NotNull IList<File> createSearchPath(@NotNull IList<File> configuredSearchPath,
                                                  @NotNull IList<File> requireElements,
                                                  @NotNull IList<File> directRuntimeElements)
    {
        ListBuilder<File> b = IList.builder(IList.NO_DUPLICATES);
        b.addAll(configuredSearchPath);
        for (File e : requireElements) {
            IList<File> jars = getJars(e);
            for (File jar : jars) {
                addContainingDirectory(b, jar);
            }
        }
        for (File e : directRuntimeElements) {
            IList<File> jars = getJars(e);
            for (File jar : jars) {
                addContainingDirectory(b, jar);
            }
        }
        return b.values();
    }

    private void addContainingDirectory(@NotNull ListBuilder<File> b, @NotNull File f)
    {
        File parent = f.getParentFile();
        if (parent != null && parent.isDirectory()) {
            b.add(parent);
        }
    }

    private @NotNull IList<File> getJarFiles(@NotNull IList<File> es)
    {
        ListBuilder<File> b = IList.builder();
        for (File e : es) {
            b.addAll(getJarFiles(e));
        }
        return b.values();
    }

    private @NotNull IList<File> getJarFiles(@NotNull File e)
    {
        ListBuilder<File> b = IList.builder();
        IList<File> jars = getJars(e);
        b.addAll(jars);
        return b.values();
    }

    /**
      Return the JAR files identified by a class path element.
    */

    private @NotNull IList<File> getJars(@NotNull File f)
    {
        ListBuilder<File> b = IList.builder();
        if (f.getName().endsWith(".jar")) {
            b.add(f);
        }
        return b.values();
    }

    private ClassPath getRef()
    {
        Project p = getProject();
        return getCheckedRef(ClassPath.class, getDataTypeName(), p);
    }

    /**
      Specify additional source trees and required libraries using a nested or referenced element.
    */

    public void add(@NotNull ClassPath c)
    {
        if (c == this) {
            throw circularReference();
        }
        if (c.getProject() == null) {
            c.setProject(getProject());
        }
        if (isReference()) {
            throw noChildrenAllowed();
        }
        nested.add(c);
        setChecked(false);
    }

    /**
      Specify libraries that are required at compile time but not at run time.

      @ant.type
      @ant.optional
    */

    public void addConfiguredCompile(@NotNull ClassPathElement c)
    {
        if (isReference()) {
            throw noChildrenAllowed();
        }
        if (c == null) {
            return;
        }
        compile.add(c);
        setChecked(false);
    }

    /**
      Specify libraries that are required at compile time and at run time.

      @ant.type
      @ant.optional
    */

    public void addConfiguredRequired(@NotNull ClassPathElement c)
    {
        if (isReference()) {
            throw noChildrenAllowed();
        }
        if (c == null) {
            return;
        }
        required.add(c);
        setChecked(false);
    }

//    public void addConfiguredIncluded(@NotNull ClassPathElement c)
//    {
//        if (isReference()) {
//            throw noChildrenAllowed();
//        }
//        if (c == null) {
//            return;
//        }
//        included.add(c);
//        setChecked(false);
//    }

    /**
      Specify libraries that are required at run time.

      @ant.type
      @ant.optional
    */

    public void addConfiguredRuntime(@NotNull ClassPathElement c)
    {
        if (isReference()) {
            throw noChildrenAllowed();
        }
        if (c == null) {
            return;
        }
        runtime.add(c);
        setChecked(false);
    }

    /**
      Specify libraries that are required at compile time but not at run time.

      @ant.type
      @ant.optional
    */

    public void addConfiguredCompileFiles(@NotNull FileList files)
    {
        if (isReference()) {
            throw noChildrenAllowed();
        }
        compile.add(ClassPathElement.create(files));
        setChecked(false);
    }

    /**
      Specify libraries that are required at compile time and at run time.

      @ant.type
      @ant.optional
    */

    public void addConfiguredRequiredFiles(@NotNull FileList files)
    {
        if (isReference()) {
            throw noChildrenAllowed();
        }
        required.add(ClassPathElement.create(files));
        setChecked(false);
    }

    /**
      Specify libraries that are required at run time.

      @ant.type
      @ant.optional
    */

    public void addConfiguredRuntimeFiles(@NotNull FileList files)
    {
        if (isReference()) {
            throw noChildrenAllowed();
        }
        runtime.add(ClassPathElement.create(files));
        setChecked(false);
    }

    /**
      Specify libraries that are required at compile time but not at run time.

      @ant.type
      @ant.optional
    */

    public void addConfiguredCompilePath(@NotNull Path path)
    {
        if (isReference()) {
            throw noChildrenAllowed();
        }
        compile.add(ClassPathElement.create(path));
        setChecked(false);
    }

    /**
      Specify libraries that are required at compile time and at run time.

      @ant.type
      @ant.optional
    */

    public void addConfiguredRequiredPath(@NotNull Path path)
    {
        if (isReference()) {
            throw noChildrenAllowed();
        }
        required.add(ClassPathElement.create(path));
        setChecked(false);
    }

    /**
      Specify libraries that are required at run time.

      @ant.type
      @ant.optional
    */

    public void addConfiguredRuntimePath(@NotNull Path path)
    {
        if (isReference()) {
            throw noChildrenAllowed();
        }
        runtime.add(ClassPathElement.create(path));
        setChecked(false);
    }

    /**
      Specify a dependence on an artifact using a Maven Resolver
      <a href="https://maven.apache.org/resolver-ant-tasks/apidocs/org/apache/maven/resolver/internal/ant/types/Dependency.html">Dependency</a>.

      @ant.type
      @ant.optional
    */

    public void add(@NotNull Dependency d)
    {
        if (isReference()) {
            throw noChildrenAllowed();
        }
        nestedDependencies.add(d);
    }

    private @NotNull IList<File> getLibraries(@NotNull String s, @NotNull Scope scope)
    {
        ListBuilder<File> b = IList.builder();
        StringTokenizer st = new StringTokenizer(s);
        while (st.hasMoreTokens()) {
            String name = st.nextToken();
            File e = findLibrary(name, scope);
            b.addOptional(e);
        }
        return b.values();
    }

    private @NotNull IList<File> getLibraries(@NotNull IList<String> names, @NotNull Scope scope)
    {
        ListBuilder<File> b = IList.builder();
        for (String name : names) {
            File e = findLibrary(name, scope);
            b.addOptional(e);
        }
        return b.values();
    }

    private @NotNull IList<File> ensureSearchFileList()
    {
        if (searchFiles != null) {
            return searchFiles;
        }
        if (searchPath == null) {
            Project p = getProject();
            searchPath = p.getReference(LIB_PATH);
        }
        searchFiles = searchPath != null ? createSearchFileList(searchPath) : IList.empty();
        return searchFiles;
    }

    private @NotNull IList<File> createSearchFileList(@NotNull Path p)
    {
        ListBuilder<File> b = IList.builder();
        for (Resource r : p) {
            b.addOptional(AntUtils.getResourceFile(r));
        }
        return b.values();
    }

    private @Nullable File findLibrary(@NotNull String name, @NotNull Scope scope)
    {
        MavenCoordinates mc = MavenCoordinates.parse(name);
        if (mc == null) {
            LibraryNameManagement n = LibraryNameManagement.get(getProject(), this);
            MavenCoordinates c = n.getKey(name);
            if (c != null && !replacementGuidanceIssued.contains(name)) {
                replacementGuidanceIssued.add(name);
                info("Could replace library " + name + " with artifact " + c);
                // Not doing this automatically, because a library that is only in the local repo
                // may not have dependency information.
            }
        }
        if (mc != null) {
            processMavenDependency(mc, scope);
            return null;
        }

        IList<File> jarSearchPath = ensureSearchFileList();
        if (!librarySearchPathWarningIssued && jarSearchPath.isEmpty()) {
            librarySearchPathWarningIssued = true;
            info("A search path must be specified to find libraries by name. The default path ID is \"lib.path\".");
        }

        for (File f : jarSearchPath) {
            String requested = name;
            if (!name.contains(".")) {
                name = name + ".jar";
            }
            File p = new File(f, name);
            if (p.isFile()) {
                //info("Found " + requested + " at " + p.getPath() + " for " + scope);
                foundLibraryReporter.add(requested, p.getPath(), scope.name());
                return p;
            }
        }

        String message = "Library not found: " + name;
        if (scope == Scope.INCLUDED || scope == Scope.RUNTIME) {
            message = message + " [" + scope + "]";
            info(message);
        } else {
            error(message);
        }
        return null;
    }

    private @NotNull ClassPathElement createClassPathElement(@NotNull File f)
    {
        FileList.FileName n = new FileList.FileName();
        n.setName(f.getName());
        FileList l = new FileList();
        l.setDir(f.getParentFile());
        l.addConfiguredFile(n);
        ClassPathElement e = new ClassPathElement();
        e.addConfiguredfiles(l);
        return e;
    }

    private void processMavenDependency(@NotNull MavenCoordinates mc, @NotNull Scope scope)
    {
        if (scope == Scope.INCLUDED) {
            // Dependencies of included Jars are not traced
            return;
        }

        Project project = getProject();
        String version = mc.version;
        if (version == null) {
            version = getPreferredVersion(mc.key);
        }
        Dependency d = new Dependency();
        d.setProject(project);
        d.setCoords(mc.key + ":" + version);
        if (scope == Scope.COMPILE) {
            d.setScope("provided");
        } else if (scope == Scope.RUNTIME) {
            d.setScope("runtime");
        }
        addDependency(d);
    }

    private void addDependency(@NotNull Dependency d)
    {
        String key = d.getVersionlessKey();
        String version = d.getVersion();
        assert version != null;

        Dependencies dependencies = ensureDependencies();
        Dependency existing = find(dependencies, key);
        if (existing != null) {
            String existingVersion = existing.getVersion();
            assert existingVersion != null;
            if (version.equals(existingVersion)) {
                // redundant
                return;
            }
            error(String.format("Conflicting versions for %s — %s, %s", key, existingVersion, version));
            return;
        }

        dependencies.addDependency(d);
    }

    private @Nullable Dependency find(@NotNull Dependencies d, @NotNull String key)
    {
        List<DependencyContainer> dcs = d.getDependencyContainers();
        for (DependencyContainer dc : dcs) {
            if (dc instanceof Dependencies) {
                Dependency found = find((Dependencies)dc, key);
                if (found != null) {
                    return found;
                }
            } else if (dc instanceof Dependency) {
                Dependency candidate = (Dependency) dc;
                if (candidate.getVersionlessKey().equals(key)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private @NotNull String getPreferredVersion(@NotNull String key)
    {
        assert usedMavenVersions != null;
        if (mm != null) {
            String preferredVersion = mm.getPreferredVersion(key);
            if (preferredVersion == null) {
                if (!usingLatestWarningIssued.contains(key)) {
                    usingLatestWarningIssued.add(key);
                    info("No preferred version found for " + key + ", will use latest version");
                }
                return "LATEST";
            } else {
                usedMavenVersions.put(key, preferredVersion);
                return preferredVersion;
            }
        } else {
            if (!usingLatestWarningIssued.contains(key)) {
                usingLatestWarningIssued.add(key);
                info("Maven version management not supported: will use latest version for " + key);
            }
            return "LATEST";
        }
    }

    private @NotNull Dependencies ensureDependencies()
    {
        if (dependencies == null) {
            Project project = getProject();
            dependencies = project.getReference(LIB_DEPENDENCIES);
            if (dependencies == null) {
                dependencies = new Dependencies();
                dependencies.setProject(project);
                project.addReference(LIB_DEPENDENCIES, dependencies);
            }
        }
        return dependencies;
    }

    @Override
    public void info(@NotNull String message)
    {
        log(message, Project.MSG_INFO);
    }

    @Override
    public void verbose(@NotNull String message)
    {
        log(message, Project.MSG_VERBOSE);
    }

    @Override
    public void error(@NotNull String message)
    {
        errorsFound = true;
        log(message, Project.MSG_ERR);
    }
}
