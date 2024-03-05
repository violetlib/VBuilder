/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vbuilder.ant;

import org.apache.maven.resolver.internal.ant.types.Dependencies;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.TaskContainer;
import org.apache.tools.ant.taskdefs.Javac;
import org.apache.tools.ant.taskdefs.Manifest;
import org.apache.tools.ant.types.Commandline;
import org.apache.tools.ant.types.Reference;
import org.apache.tools.ant.types.ResourceCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.collections.ICollection;
import org.violetlib.collections.IList;
import org.violetlib.collections.ISet;
import org.violetlib.collections.ListBuilder;
import org.violetlib.vbuilder.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
  Build a Java library.
  <p>
  A library is constructed from Java source files, compiled Java source files (class files), and JARs.
  <p>
  Source files intended for inclusion in the library are provided in the form of package structured directory
  trees. Multiple source trees can be specified.
  <h2>JDK releases</h2>
  <p>
  Ordinarily, all sources in a library are compiled against a single JDK release, which may be specified using the
  {@code release} attribute. The selected JDK release defines the
  Java language level that the compiler expects, the JDK API that the files are compiled
  against, and the class file version of the class files produced by the compiler.
  <p>
  In unusual cases, multiple source trees can be provided that specify different JDK releases.
  As each specified release corresponds to a different class file version, the ability to specify multiple
  releases is useful only if the library can ensure that the classes that are loaded are compatible with the
  Java runtime that the library is running on (see below for one possible technique).
  <p>
  Currently, the most common situation is a library that supports JDK 8 and later, but uses some JDK 8 specific APIs
  when running on JDK 8 and uses JDK 9+ APIs when running on JDK 9+.
  The recommended structure for such a library is to define three source trees:
  <ul>
  <li>One tree for code that uses JDK 8 specific APIs. This tree specifies JDK release 8.</li>
  <li>One tree for code that uses JDK 9+ specific APIs. This tree specifies JDK release 9.</li>
  <li>One tree for all other code. This tree does not specify a JDK release, but it is implicitly
  compiled against JDK 8 because the task itself specifies JDK 8.
  </li>
  </ul>
  <p>
  This structure is motivated by the fact that the JDK 8 specific APIs are considered internal APIs.
  For this reason,, a source tree that declares JDK release 8 is compiled with access to the internal
  APIs, but other source trees are compiled without access to the internal APIs (even though they are compiled
  against JDK 8).
  <p>
  <em>This task does not support the construction of multi-release JARs.</em> The recommended technique for ensuring
  that only classes that are supported on the running JDK are loaded is to use reflection to load JDK-specific classes.
  For client access, the JDK specific classes probably should subclass a common abstract base class that is not JDK
  specific or support a common interface that is not JDK specific.

  @ant.task name="javaLibrary"
*/

public class JavaLibraryTask
  extends Task
  implements Reporter, TaskContainer
{
    /**
      Boolean property. If true, the library is installed in the local repo as a SNAPSHOT release.
      @ant.prop
    */

    public static final @NotNull String INSTALL_LOCAL_REPO = "install.local.repo";

    /**
      A property that specifies the default JDK release, if no valid release is specified for a source tree.
      @ant.prop
    */

    public static final @NotNull String JAVA_RELEASE = "java.release";

    /**
      A property that specifies the path to the java compiler. If undefined, standard locations are searched for the
      compiler.
      @ant.prop
    */

    public static final @NotNull String JAVAC = "javac";

    /**
      Boolean property. If true, javac is run in debug mode.
      @ant.prop
    */

    public static final @NotNull String COMPILE_DEBUG = "compile.debug";

    /**
      The boot class path to use when compiling for JDK 8. This path is required to allow access to internal APIs.
      @ant.prop
    */

    public static final @NotNull String BOOT_CLASSPATH_8 = "bootclasspath8";

    private @Nullable String libraryName;
    private @NotNull List<ClassPath> providedClassPaths = new ArrayList<>();
    private @Nullable Object manifest;
    private boolean manifestIsOptional;
    private @Nullable File basicOutputDirectory;
    private @Nullable File expandedOutputDirectory;
    private @Nullable String expandedName;
    private @Nullable String codeSigningKey;
    private @Nullable String mavenCoordinates;
    private @Nullable File buildRoot;
    private @Nullable String releaseText;
    private final @NotNull List<ResourceCollection> resources = new ArrayList<>();
    private final @NotNull List<JavaSourceTree> sourceTrees = new ArrayList<>();
    private final @NotNull List<Task> nestedTasks = new ArrayList<>();
    private final @NotNull List<Commandline.Argument> compilerArguments = new ArrayList<>();

    private boolean errorsFound;

    /**
      Specify the library name.
      @ant.prop name="name"
      @ant.required
    */

    public void setName(@NotNull String s)
    {
        this.libraryName = s;
    }

    /**
      Specify source trees and Java libraries required for compilation and/or inclusion in a library or application.
      The specified source trees and Java libraries augment the information obtained using the {@code classPathRef}
      attribute. If the {@code classPathRef} attribute is not defined an no nested {@link classpath} elements are
      provided, a {@link classpath} with identifier {@code Classpath.Use} will be used, if defined. {@link use}
      elements in the same project implicitly define that {@link classpath}.

      @ant.prop name="classpath"
    */

    public void addConfigured(@NotNull ClassPath cp)
    {
        providedClassPaths.add(cp);
    }

    /**
      Specify source trees and Java libraries required for compilation and/or inclusion in a library or application.
      @ant.prop name="classpathRef"
      @ant.optional This information may be provided using nested {@link classpath} elements.
    */

    public void setClasspathref(@NotNull Reference r)
    {
        ClassPath cp = new ClassPath(getProject());
        cp.setRefid(r);
        providedClassPaths.add(cp);
    }

    /**
      Specify an argument to pass to the Java compiler.
    */

    public void addConfiguredCompilerArg(@NotNull Commandline.Argument arg)
    {
        compilerArguments.add(arg);
    }

    /**
      Specify a file to be used as the JAR Manifest.
      @ant.prop name="manifest"
      @ant.optional
    */

    public void setManifest(@NotNull File f)
    {
        this.manifest = f;
        this.manifestIsOptional = false;
    }

    /**
      Specify a file to be used as the JAR Manifest.
      No error is issued if the file does not exist.

      @ant.prop name="optionalManifest"
      @ant.optional
    */

    public void setOptionalManifest(@NotNull File f)
    {
        this.manifest = f;
        this.manifestIsOptional = true;
    }

    /**
      Specify an Manifest to be included in the JAR file.
      @ant.prop name="manifest"
      @ant.optional
    */

    public void setManifest(@NotNull Manifest m)
    {
        this.manifest = m;
        this.manifestIsOptional = false;
    }

    /**
      Specify the directory where the basic JAR should be installed.
      @ant.prop name="dist"
      @ant.optional If not specified, a basic JAR will not be created.
    */

    public void setDist(@NotNull File f)
    {
        this.basicOutputDirectory = f;
    }

    /**
      Specify the directory where the expanded JAR should be installed.
      @ant.prop name="expandedDist"
      @ant.optional If not specified, an expanded JAR will not be created.
    */

    public void setExpandedDist(@NotNull File f)
    {
        this.expandedOutputDirectory = f;
    }

    /**
      Specify the basic name of the expanded JAR file to create. The name should not include the {@code ".jar"}
      suffix.
      @ant.prop name="expandedName"
      @ant.optional If not specified, a library name will be used.
    */

    public void setExpandedName(@NotNull String name)
    {
        this.expandedName = name;
    }

    /**
      Specify the key to use for code signing the JAR file.
      @ant.prop name="codeSigningKey"
      @ant.optional If not specified, the JAR file will not be code signed.
    */

    public void setCodeSigningKey(@NotNull String s)
    {
        this.codeSigningKey = s;
    }

    /**
      Specify the Maven repo coordinates to associate with this library.
      <em>This attribute is currently unused.</em>

      @ant.prop name="mavenCoordinates"
      @ant.optional
    */

    public void setMavenCoordinates(@NotNull String s)
    {
        this.mavenCoordinates = s;
    }

    /**
      Specify the build directory for this build.
      Subdirectories of the build directory may be used to store results of the build, unless specific
      directories have been configured.
      @ant.prop name=buildRoot"
      @ant.optional
    */

    public void setBuildRoot(@NotNull File f)
    {
        this.buildRoot = f;
    }

    /**
      Specify the default JDK release number. This attribute is used for sources for which no explicit
      release has been defined.
      <p>
      The JDK release number determines Java language level used to compile Java sources,
      the associated JDK API that the sources may use, and the class file version number
      of the generated class files.

      @ant.prop name="release"
      @ant.optional If not specified, the default release of the Java compiler is used.
    */

    public void setRelease(@NotNull String s)
    {
        this.releaseText = s;
    }

    /**
      Specify files to be included in the JAR. The files may include native libraries.
    */

    public void addConfigured(@NotNull ResourceCollection resources)
    {
        this.resources.add(resources);
    }

    /**
      Specify sources to be compiled.
    */

    public void addConfigured(@NotNull JavaSourceTree tree)
    {
        this.sourceTrees.add(tree);
    }

    /**
      Specify sources to be compiled.
    */

    public void addConfigured(@NotNull JavaSourceTreeList trees)
    {
        for (JavaSourceTree tree : trees.getTrees()) {
            sourceTrees.add(tree);
        }
    }

    /**
      Nested tasks are performed after compilation and before packaging the library contents in a JAR.
      Nested tasks can be used to create JNI based native libraries, which require header files
      produced by Java compiler for native Java methods.
      <p>
      Currently, only JNI tasks are supported.
    */

    @Override
    public void addTask(@NotNull Task task)
    {
        nestedTasks.add(task);
    }

    @Override
    public void execute()
      throws BuildException
    {
        AntUtils.init();

        Project p = getProject();

        String libraryName = getLibraryName();
        File buildRoot = validateBuildRoot(this.buildRoot);
        File basicJarFile = getBasicJarFile();
        File expandedJarFile = getExpandedJarFile();

        SourceCollectorAnt sc = SourceCollectorAnt.createAnt(this);
        MavenCoordinates coordinates = getMavenCoordinates();

        for (ResourceCollection r : this.resources) {
            sc.process(r);
        }

        IList<JavaSourceTree> sources = getSourceTrees();
        ISet<File> classTrees = sc.getClassTrees();
        ISet<File> jarFiles = sc.getJarFiles();
        ISet<RelativeFile> resources = sc.getResources();
        JavaLibraryBuilder.Delegate delegate = JavaLibraryBuilderAnt.create(getProject());
        JARBuilder.BuilderFactory jbf = JARBuilder.createFactory(delegate);

        int defaultRelease = getDefaultRelease(sources);

        if (errorsFound) {
            if (defaultRelease > 0) {
                info("  Default release: " + defaultRelease);
            }
            throw new BuildException("Unable to create Java library [errors found]");
        }

        if (sources.isEmpty()) {
            throw new BuildException("No Java sources have been defined");
        } else {
            for (JavaSourceTree tree : sources) {
                showSourceTree(tree, defaultRelease);
            }
        }

        MavenVersionManagement mm = AntMavenVersionManagement.get(p, new AntBuildDelegate(p));
        mm.logPreferredVersionsAndScopes(ProjectReporter.create(p));

        org.apache.tools.ant.types.Path compilePath = new org.apache.tools.ant.types.Path(p);
        org.apache.tools.ant.types.Path runtimePath = new org.apache.tools.ant.types.Path(p);

        Dependencies ds = new Dependencies();
        ds.setProject(p);

        if (providedClassPaths.isEmpty()) {
            ClassPath implicitClassPath = p.getReference(UseLibrary.IMPLICIT_CLASSPATH);
            if (implicitClassPath != null) {
                p.log("Using implicit class path", Project.MSG_VERBOSE);
                providedClassPaths.add(implicitClassPath);
            }
        }

        if (!providedClassPaths.isEmpty()) {
            IList<String> exceptions = IList.of(libraryName + ".jar");
            List<File> _compile = new ArrayList<>();
            List<File> _runtime = new ArrayList<>();

            for (ClassPath cp : providedClassPaths) {
                ClassPath.Output output = cp.execute(false, exceptions, mm);
                ds.addDependencies(output.dependencies);
                _compile.addAll(output.required.toJavaList());
                _runtime.addAll(output.runtime.toJavaList());
            }

            IList<File> compile = IList.create(_compile);
            IList<File> runtime = IList.create(_runtime);

            DependencySupport.Configuration dg
              = DependencySupport.createConfiguration(compile, runtime, ds, mm);
            DependencySupport.Result r = DependencySupport.compute(p, dg);
            compilePath = r.compilePath;
            jarFiles = jarFiles.extendingAll(r.runtimeFiles);

            if (false) {
                Reporter reporter = ProjectReporter.create(p);
                for (File f : r.compileFiles) {
                    if (!r.runtimeFiles.contains(f)) {
                        reporter.info("        " + f.getPath() + " [compile]");
                    }
                }
                for (File f : r.runtimeFiles) {
                    reporter.info("        " + f.getPath());
                }
                reporter.info("");
            }
        }

        File classesOutputDir = getClassOutputDir(buildRoot);
        File headersOutputDir = getHeaderOutputDir(buildRoot);
        IList<Commandline.Argument> args = getCompilerArguments();
        compileJava(sources, defaultRelease, compilePath, classesOutputDir, headersOutputDir, args);

        classTrees = classTrees.extending(classesOutputDir);

        // JNI based native libraries need the header files produced by Java compilation.
        // Run those tasks now.

        if (!nestedTasks.isEmpty()) {
            for (Task t : nestedTasks) {
                if (t instanceof NativeLibraryTask) {
                    t.perform();
                } else {
                    String name = t.getTaskName();
                    String message
                      = String.format("Nested task %s is not supported; only JNI Library tasks are supported", name);
                    throw new BuildException(message);
                }
            }
        }

        // If the native target is defined separately, need to run as a target.

        org.apache.tools.ant.Target nativeTarget = p.getTargets().get("native");
        if (nativeTarget != null) {
            p.getExecutor().executeTargets(p, new String[] {"native"});
        }

        // pick up native libraries that were generated by nested tasks in a non-default location

        for (ResourceCollection r : this.resources) {
            sc.process(r);
        }

        ISet<NativeLibrary> nativeLibraries = sc.getNativeLibraries();

        if (false) {
            System.err.println("Native libraries:");
            for (NativeLibrary nl : nativeLibraries) {
                System.err.println("  " + nl);
                System.err.println("  " + nl.getDebugSymbols());
            }
        }

        if (manifest instanceof File && manifestIsOptional) {
            File m = (File) manifest;
            if (!Files.isRegularFile(m.toPath())) {
                manifest = null;
            }
        }

        String signingKey = codeSigningKey != null && !AntUtils.isUndefined(codeSigningKey) ? codeSigningKey : null;

        // Installation in a local Maven repo is optional, must be enabled.
        String shouldInstallLocal = p.getProperty(INSTALL_LOCAL_REPO);
        if (!AntUtils.isTrue(shouldInstallLocal)) {
            coordinates = null;
        }

        JavaLibraryBuilder.Configuration g
          = JavaLibraryBuilder.createConfiguration(libraryName,
          classTrees, resources, jarFiles, nativeLibraries, manifest,
          basicJarFile, expandedJarFile, coordinates, signingKey, buildRoot, jbf);
        try {
            JavaLibraryBuilder.createLibrary(g, delegate);
        } catch (org.violetlib.vbuilder.BuildException e) {
            throw new BuildException(e);
        }
    }

    protected @NotNull String getLibraryName()
    {
        if (libraryName == null) {
            throw new BuildException("The library name must be specified");
        }
        return libraryName;
    }

    protected @Nullable File getBasicJarFile()
    {
        File dir = getBasicOutputDirectory();
        if (dir == null) {
            return null;
        }
        return new File(dir, getLibraryName() + ".jar");
    }

    protected @Nullable File getBasicOutputDirectory()
    {
        return validateOrCreateOptionalDirectory(basicOutputDirectory, "basic JAR output directory");
    }

    protected @Nullable File getExpandedJarFile()
    {
        File dir = getExpandedOutputDirectory();
        if (dir == null) {
            if (expandedName != null) {
                dir = getBasicOutputDirectory();
                if (dir != null) {
                    return new File(dir, expandedName + ".jar");
                }
            }

            return null;
        }
        return new File(dir, getExpandedName() + ".jar");
    }

    protected @Nullable File getExpandedOutputDirectory()
    {
        return validateOrCreateOptionalDirectory(expandedOutputDirectory, "expanded JAR output directory");
    }

    protected @NotNull String getExpandedName()
    {
        return expandedName != null ? expandedName : getLibraryName();
    }

    protected @NotNull IList<Commandline.Argument> getCompilerArguments()
    {
        return IList.create(compilerArguments);
    }

    protected @NotNull IList<JavaSourceTree> getSourceTrees()
    {
        ListBuilder<JavaSourceTree> b = IList.builder(IList.NO_DUPLICATES);

        for (JavaSourceTree tree : sourceTrees) {
            try {
                tree.validate();
            } catch (BuildException e) {
                error(e.getMessage());
                continue;
            }
            File base = validateSourceTreeBase(tree.getBase(), tree.isOptional());
            if (base != null) {
                b.add(tree);
            }
        }

        return b.values();
    }

    private @Nullable File validateSourceTreeBase(@Nullable File base, boolean isOptional)
    {
        assert base != null;
        Path p = base.toPath();
        if (Files.isDirectory(p)) {
            return Utils.resolve(base).getAbsoluteFile();
        }
        if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            error("Source tree not found [not a directory]: " + p);
            return null;
        }
        if (!isOptional) {
            error("Source tree not found: " + p);
        } else {
            verbose("Optional source tree not found: " + p);
        }
        return null;
    }

    private int getDefaultRelease(@NotNull ICollection<JavaSourceTree> trees)
    {
        int specifiedRelease = getSpecifiedRelease();
        return getMinimumRelease(specifiedRelease, trees);
    }

    private int getSpecifiedRelease()
    {
        if (releaseText != null) {
            int release = parseRelease(releaseText, "Invalid release");
            if (release > 0) {
                return release;
            }
        }

        String s = getProject().getProperty(JAVA_RELEASE);
        if (s != null && !s.isEmpty() && !AntUtils.isUndefined(s)) {
            int release = parseRelease(s, "Invalid java.release attribute");
            if (release > 0) {
                return release;
            }
        }

        return 0;
    }

    private int parseRelease(@NotNull String releaseText, @NotNull String message)
    {
        int release = 0;
        try {
            release = Integer.parseInt(releaseText);
        } catch (NumberFormatException ignore) {
        }
        if (release > 0) {
            return release;
        }
        error(message + ": " + releaseText);
        return 0;
    }

    private int getMinimumRelease(int specifiedRelease, @NotNull ICollection<JavaSourceTree> trees)
    {
        int result = Integer.MAX_VALUE;
        for (JavaSourceTree tree : trees) {
            int release = tree.getRelease();
            if (release > 0 && release < result) {
                result = release;
            }
        }
        if (specifiedRelease > 0 && specifiedRelease < result) {
            result = specifiedRelease;
        }
        return result == Integer.MAX_VALUE ? 0 : result;
    }

    private @NotNull File validateBuildRoot(@Nullable File f)
    {
        if (f == null) {
            throw new BuildException("A build output root must be defined");
        }
        if (Files.isDirectory(f.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return f;
        }
        if (Files.exists(f.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new BuildException("Build output directory already exists and cannot be created: " + f);
        }
        try {
            Files.createDirectories(f.toPath());
            return f;
        } catch (IOException ex) {
            throw new BuildException("Build output directory cannot be created: " + f + ", " + ex);
        }
    }

    private @Nullable File validateOrCreateOptionalDirectory(@Nullable File dir, @NotNull String description)
    {
        return dir != null ? validateOrCreateDirectory(dir, description) : null;
    }

    private @NotNull File validateOrCreateDirectory(@NotNull File dir, @NotNull String description)
    {
        if (Files.isDirectory(dir.toPath())) {
            return Utils.resolve(dir).getAbsoluteFile();
        } else if (Files.exists(dir.toPath())) {
            String message = "Specified " + description + " already exists and cannot be created: ";
            throw new BuildException(message + dir.getPath());
        } else {
            try {
                Files.createDirectories(dir.toPath());
                verbose("Created directory: " + dir);
                return dir;
            } catch (IOException ex) {
                String message = "Unable to create " + description + ": ";
                throw new BuildException(message + dir.getPath(), ex);
            }
        }
    }

    private @Nullable MavenCoordinates getMavenCoordinates()
    {
        if (mavenCoordinates != null) {
            MavenCoordinates coordinates = MavenCoordinates.parse(mavenCoordinates);
            if (coordinates == null && !isEmptyCoordinates(mavenCoordinates)) {
                error("Invalid Maven coordinates: " + mavenCoordinates);
            }
            return coordinates;
        }
        return null;
    }

    private @NotNull File getClassOutputDir(@NotNull File buildRoot)
    {
        File dir = new File(buildRoot, "classes");
        return prepareOutputDirectory(dir);
    }

    private @NotNull File getHeaderOutputDir(@NotNull File buildRoot)
    {
        File dir = new File(buildRoot, "headers");
        return prepareOutputDirectory(dir);
    }

    private void compileJava(@NotNull IList<JavaSourceTree> trees,
                             int defaultRelease,
                             @NotNull org.apache.tools.ant.types.Path classPath,
                             @NotNull File classesDir,
                             @NotNull File headersDir,
                             @NotNull IList<Commandline.Argument> args)
    {
        for (JavaSourceTree tree : trees) {
            compileJava(tree, defaultRelease, classPath, classesDir, headersDir, args);
        }
    }

    private void compileJava(@NotNull JavaSourceTree tree,
                             int defaultRelease,
                             @NotNull org.apache.tools.ant.types.Path classPath,
                             @NotNull File classesDir,
                             @NotNull File headersDir,
                             @NotNull IList<Commandline.Argument> args)
    {
        Project p = getProject();
        File base = tree.getBase();
        assert base != null;
        ISet<String> packages = tree.getPackages();

        info("Compiling " + base);

        //Reference cp = p.hasReference("cp") ? new Reference(p, "cp") : null;

        String bcps = p.getProperty(BOOT_CLASSPATH_8);
        org.apache.tools.ant.types.Path bcp = null;
        if (bcps != null) {
            bcp = new org.apache.tools.ant.types.Path(p);
            bcp.setPath(bcps);
        }

        String debug = p.getProperty("compile.debug");
        String javac = p.getProperty(JAVAC);

        if (javac == null) {
            File javaHome = Utils.getConfiguredJavaHome();
            File f = new File(javaHome, "bin/javac");
            if (Files.isRegularFile(f.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                javac = f.getAbsolutePath();
            }
            if (javac == null) {
                f = new File("/usr/bin/javac");
                if (Files.isRegularFile(f.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    javac = f.getAbsolutePath();
                }
            }
            if (javac == null) {
                throw new BuildException("Unable to find javac");
            }
        }

        verbose("Using: " + javac);

        boolean usingDefaultRelease = false;
        int release = tree.getRelease();
        if (release == 0) {
            usingDefaultRelease = true;
            release = defaultRelease;
        }

        Javac task = new Javac();
        task.setProject(p);
        task.setTaskName("javac");
        task.setSrcdir(new org.apache.tools.ant.types.Path(getProject(), base.getPath()));

        if (!packages.isEmpty()) {
            task.setIncludes(createIncludesForPackages(packages));
        }

        task.setExcludes("**/.*/**");
        task.setDestdir(classesDir);
        task.setIncludeantruntime(false);
        task.setClasspath(classPath);
        task.setDeprecation(true);
        task.setEncoding("UTF-8");
        if (debug != null && debug.equals("true")) {
            task.setDebug(true);
        }
        if (!args.isEmpty()) {
            for (Commandline.Argument arg : args) {
                String[] parts = arg.getParts();
                for (String part : parts) {
                    Javac.ImplementationSpecificArgument a = task.createCompilerArg();
                    a.setValue(part);
                }
            }
        }

        task.setFork(true);
        task.setExecutable(javac);

        if (release == 8) {
            if (usingDefaultRelease) {
                task.setRelease("8");
            } else {
                task.setSource("8");
                task.setTarget("8");
                if (bcp != null) {
                    task.setBootclasspath(bcp);
                }
            }
            // javac warns about source and target options
            Javac.ImplementationSpecificArgument arg = task.createCompilerArg();
            arg.setValue("-Xlint:-options");
        } else if (release > 8) {
            task.setRelease(Integer.toString(release));
        } else if (release > 0) {
            throw new BuildException("Unsupported release: " + release);
        }

        {
            Javac.ImplementationSpecificArgument arg = task.createCompilerArg();
            arg.setValue("-h");
            arg = task.createCompilerArg();
            arg.setValue(headersDir.getPath());
        }

        task.execute();
    }

    private @NotNull String createIncludesForPackages(@NotNull ISet<String> packages)
    {
        assert !packages.isEmpty();

        StringBuilder sb = new StringBuilder();
        for (String p : packages) {
            sb.append(" ");
            String pat = p.replace('.', '/');
            sb.append(pat);
            sb.append("/*.java");
        }
        return sb.toString().trim();
    }

    // Tolerate build scripts that construct coordinates from components.

    private boolean isEmptyCoordinates(@NotNull String s)
    {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != ':') {
                return false;
            }
        }
        return true;
    }

    private @NotNull File prepareOutputDirectory(@NotNull File dir)
    {
        File d = dir.getAbsoluteFile();
        Path p = dir.toPath();
        if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
            return d;
        }
        if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new BuildException("Specified output directory is not a directory: " + d);
        }
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            throw new BuildException("Unable to create output directory [" + e + "]: " + d);
        }
        return d;
    }

    private void showSourceTree(@NotNull JavaSourceTree tree, int defaultRelease)
    {
        String message = "  Java: " + tree.getBase();
        int release = tree.getRelease();
        if (release == 0) {
            if (defaultRelease > 0) {
                message = message + " [default=" + defaultRelease + "]";
            }
        } else if (release > 0) {
            message = message + " [" + release + "]";
        }
        info(message);
    }

    @Override
    public void info(@NotNull String message)
    {
        log(message, Project.MSG_WARN);
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
