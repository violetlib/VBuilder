package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.collections.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.attribute.PosixFilePermission.*;

/**
  A builder of Java applications for macOS. This base class is intended to be independent of the build system.
  Ant-specific code is in a subclass.

  <p>
  This builder can build:
  <ul>
  <li>A universal (multi-architecture) bundled application.</li>
  <li>Any number of architecture-specific bundled applications.</li>
  <li>An executable JAR that includes native libraries.</li>
  <li>An application JAR that does not include native libraries.</li>
  <li>A basic JAR that includes only the directly specified classes and resources.</li>
  </ul>

  When building a universal bundled application, a JDK runtime is copied into the bundled application for
  each supported application. A universal bundled application requires a launcher that understands how to find
  the appropriate runtime for the execution environment.
  <p>
  Configuration parameters are bundled into a configuration object; see {@code createConfiguration()}.
*/

// TBD: support multi-release JARs
// Do tools like javac support exploded multi-release jars? no

// TBD: support JMOD files

public class JavaApplicationBuilder
{
    public static void createApplication(@NotNull Configuration g, @NotNull Delegate delegate)
      throws BuildException
    {
        new JavaApplicationBuilder(g, delegate).build();
    }

    public interface BuilderFactory
    {
        @NotNull Builder create(@NotNull JavaApplicationBuilder.Delegate delegate)
          throws BuildException;
    }

    public interface Builder
    {
        void createApplication(@NotNull Configuration g)
          throws BuildException;
    }

    public static @NotNull BuilderFactory createFactory(@NotNull JavaApplicationBuilder.Delegate delegate)
    {
        return new BuilderFactory()
        {
            @Override
            public @NotNull Builder create(@NotNull JavaApplicationBuilder.Delegate delegate)
              throws BuildException
            {
                return g -> createApplication(g, delegate);
            }
        };
    }

    /**
      Create a configuration.

      @param applicationName The application name. This value is stored in the bundled application and used when
      creating the name of the application bundle and other artifacts. The name must not be blank or contain a slash.

      @param mainClassName The fully-specified name of the main class of the application.

      @param classTrees A list of directories containing class files, arranged as appropriate for a classpath. These
      classes are copied into the application JAR.

      @param resources Resource files and directories to include in the application JAR. Directories are interpreted
      like class trees, with a structure that corresponds to a package hierarchy. {@code ant} FileSets may also
      be specified. (Resources can also be included in class trees.)

      @param jars A list of JAR files to be included in the application.

      @param infoPlistFile An {@code Info.plist} file to be included in a bundled application. If not specified, a
      default minimal {@code Info.plist} is created. (The default {@code Info.plist} file is rarely sufficient for a
      production application.)

      @param iconsFile A file defining application icons to be included in a bundled application.

      @param description A short description of the application.

      @param appResourcesDir A directory containing items to be copied into Contents/Resources of a bundled application.

      @param appContentsDir A directory containing items to be copied into Contents of a bundled application.

      @param buildRoot The directory where temporary build output files are to be stored.

      @param shouldBuildApplication If true, one or more bundled applications will be built, depending upon
      the value of {@code shouldCreateUniversalApplication}. The install locations of a single-architecture
      bundled application are specified in the corresponding architecture configuration.

      @param shouldCreateUniversalApplication If true and {@code shouldBuildApplication} is true, a single
      multi-architecture bundled application will be built.

      @param universalAppInstallDirs Directories where a universal bundled application should be installed.

      @param launcher A custom launcher. If null, the standard launcher supplied by {@code jpackage} is used. Note that
      a multi-architecture bundled application requires a custom launcher that knows how to find the appropriate
      Java runtime for the execution architecture.

      @param basicJarDir If not null, a basic JAR containing the class trees and resources will be created and
      installed in this directory.

      @param basicManifestFile A manifest file to include in the basic JAR. If null, no manifest is added to the basic
      JAR.

      @param executableJarDir If not null, an executable JAR is created and installed in this directory. The JAR
      contains the class trees, the resources, the specified JAR files and dynamic libraries, and a Manifest that
      identifies the main class.

      @param applicationJarDir If not null, a JAR is created and installed in this directory. The JAR contains
      the class trees, the resources, and the specified JAR files.

      @param applicationManifest A manifest to include in the application JAR and executable JAR. An {@code Ant}
      Manifest instance may also be specified.

      @param javaOptions JVM command line arguments.

      @param appArgs Application command line arguments.

      @param archConfigs A specification of the architectures for which bundled applications should be built or that
      should be included in a universal bundled application.

      @param nativeLibraries Native dynamic libraries to include in bundled applications. (and in the executable JAR?)
      The dynamic libraries should support all requested architectures.

      @param nativeFrameworks Native frameworks needed by the application.

      @param libOutputDirectory If not null, the required native libraries and frameworks are copied to this directory.

      @param codeSigningKey If not null, all code artifacts will be signed using this key to identify a signing
      certificate.
    */

    public static @NotNull Configuration createConfiguration(
      @NotNull String applicationName,
      @NotNull String mainClassName,
      @NotNull ISet<File> classTrees,
      @NotNull ISet<Object> resources,
      @NotNull ISet<File> jars,
      @Nullable File infoPlistFile,
      @Nullable String description,
      @Nullable File iconsFile,
      @Nullable File appResourcesDir,
      @Nullable File appContentsDir,
      @NotNull File buildRoot,
      boolean shouldBuildApplication,
      boolean shouldCreateUniversalApplication,
      @NotNull IList<File> universalAppInstallDirs,
      @Nullable File launcher,
      @Nullable File basicJarDir,
      @Nullable File basicManifestFile,
      @Nullable File executableJarDir,
      @Nullable File applicationJarDir,
      @Nullable Object applicationManifest,
      @NotNull IList<String> javaOptions,
      @NotNull IList<String> appArgs,
      @NotNull IMap<Architecture,ArchitectureConfiguration> archConfigs,
      @NotNull ISet<NativeLibrary> nativeLibraries,
      @NotNull ISet<NativeFramework> nativeFrameworks,
      @Nullable File libOutputDirectory,
      @Nullable String codeSigningKey
    )
    {
        return new Configuration(applicationName,
          classTrees,
          jars,
          infoPlistFile,
          description,
          iconsFile,
          appResourcesDir,
          appContentsDir,
          buildRoot,
          shouldBuildApplication,
          shouldCreateUniversalApplication,
          universalAppInstallDirs,
          launcher,
          basicJarDir,
          executableJarDir,
          applicationJarDir,
          basicManifestFile,
          applicationManifest,
          resources,
          mainClassName,
          javaOptions,
          appArgs,
          archConfigs,
          nativeLibraries,
          nativeFrameworks,
          libOutputDirectory,
          codeSigningKey);
    }

    public static class Configuration
    {
        public final @NotNull String applicationName;
        public final @NotNull ISet<File> classTrees;  // directories that contain class files (for classpath)
        public final @NotNull ISet<File> jars;        // JAR files to be included in the application
        public final @Nullable File infoPlistFile;    // custom Info.plist file
        public final @Nullable String description;
        public final @Nullable File iconsFile;        // custom .icns file

        public final @Nullable File appResourcesDir;  // directory containing items to install in the bundle Contents/Resources
        public final @Nullable File appContentsDir;    // directory containing items to install in the bundle Contents

        public final @NotNull File buildRoot;  // directory for build output

        public final boolean shouldBuildApplication;  // true => construct a bundled application
        public final boolean shouldCreateUniversalApplication;  // true => if multiple architecture are supported, create a universal application
        public final @NotNull IList<File> universalAppInstallDirs;    // where a universal application should be installed
        public final @Nullable File launcher;  // required for a universal application

        public final @Nullable File basicJarDir;  // optional => copy a basic JAR (specified classes and resources only) to this destination
        public final @Nullable File executableJarDir;  // optional => create an executable JAR in this location
        public final @Nullable File applicationJarDir;  // optional => install the full application JAR in this location

        public final @Nullable File basicManifestFile;
        public final @Nullable Object applicationManifest;  // File or implementation-dependent Manifest for application JAR and executable JAR
        public final @NotNull ISet<Object> resources;  // File, directory, or implementation-dependent description such as a FileSet.

        public final @NotNull String mainClassName;

        public final @NotNull IList<String> javaOptions;  // JVM command line arguments to a bundled app
        public final @NotNull IList<String> appArgs;      // application command line arguments to a bundled app

        public final @NotNull IMap<Architecture,ArchitectureConfiguration> archConfigs;

        /**
          Native libraries to copy into the application bundle.
        */

        public final @NotNull ISet<NativeLibrary> nativeLibraries;

        /**
          Native frameworks to copy into the application bundle.
          System frameworks are ignored.
        */

        public final @NotNull ISet<NativeFramework> nativeFrameworks;

        public final @Nullable File libOutputDirectory;

        public final @Nullable String codeSigningKey;

        protected Configuration(@NotNull String applicationName,
                                @NotNull ISet<File> classTrees,
                                @NotNull ISet<File> jars,
                                @Nullable File infoPlistFile,
                                @Nullable String description,
                                @Nullable File iconsFile,
                                @Nullable File appResourcesDir,
                                @Nullable File appContentsDir,
                                @NotNull File buildRoot,
                                boolean shouldBuildApplication,
                                boolean shouldCreateUniversalApplication,
                                @NotNull IList<File> universalAppInstallDirs,
                                @Nullable File launcher,
                                @Nullable File basicJarDir,
                                @Nullable File executableJarDir,
                                @Nullable File applicationJarDir,
                                @Nullable File basicManifestFile,
                                @Nullable Object applicationManifest,
                                @NotNull ISet<Object> resources,
                                @NotNull String mainClassName,
                                @NotNull IList<String> javaOptions,
                                @NotNull IList<String> appArgs,
                                @NotNull IMap<Architecture,ArchitectureConfiguration> archConfigs,
                                @NotNull ISet<NativeLibrary> nativeLibraries,
                                @NotNull ISet<NativeFramework> nativeFrameworks,
                                @Nullable File libOutputDirectory,
                                @Nullable String codeSigningKey)
        {
            this.applicationName = applicationName;
            this.classTrees = classTrees;
            this.jars = jars;
            this.infoPlistFile = infoPlistFile;
            this.description = description;
            this.iconsFile = iconsFile;
            this.appResourcesDir = appResourcesDir;
            this.appContentsDir = appContentsDir;
            this.buildRoot = buildRoot;
            this.shouldBuildApplication = shouldBuildApplication;
            this.shouldCreateUniversalApplication = shouldCreateUniversalApplication;
            this.universalAppInstallDirs = universalAppInstallDirs;
            this.launcher = launcher;
            this.basicJarDir = basicJarDir;
            this.executableJarDir = executableJarDir;
            this.applicationJarDir = applicationJarDir;
            this.basicManifestFile = basicManifestFile;
            this.applicationManifest = applicationManifest;
            this.resources = resources;
            this.mainClassName = mainClassName;
            this.javaOptions = javaOptions;
            this.appArgs = appArgs;
            this.archConfigs = archConfigs;
            this.nativeLibraries = nativeLibraries;
            this.nativeFrameworks = nativeFrameworks;
            this.libOutputDirectory = libOutputDirectory;
            this.codeSigningKey = codeSigningKey;
        }
    }

    /**
      Internal configuration that is created from the supplied configuration.
    */

    private static class InternalConfiguration
    {
        public final @NotNull BundledApplicationOption bundledApplicationOption;

        public final @NotNull IList<File> classTrees;
        public final @NotNull IList<File> classTreesAndJarFiles;
        public final @NotNull IList<Object> resources;  // regular File or FileSet

        public final @NotNull ISet<Architecture> targetArchitectures;
        public final @NotNull File buildRoot;
        public final @NotNull File libOutputDirectory;

        public final @NotNull ISet<NativeLibrary> nativeLibraries;
        public final @NotNull ISet<NativeFramework> nativeFrameworks;

        public InternalConfiguration(@NotNull BundledApplicationOption bundledApplicationOption,
                                     @NotNull IList<File> classTrees,
                                     @NotNull IList<File> classTreesAndJarFiles,
                                     @NotNull IList<Object> resources,
                                     @NotNull ISet<Architecture> targetArchitectures,
                                     @NotNull File buildRoot,
                                     @NotNull File libOutputDirectory,
                                     @NotNull ISet<NativeLibrary> nativeLibraries,
                                     @NotNull ISet<NativeFramework> nativeFrameworks)
        {
            this.bundledApplicationOption = bundledApplicationOption;
            this.classTrees = classTrees;
            this.classTreesAndJarFiles = classTreesAndJarFiles;
            this.resources = resources;
            this.targetArchitectures = targetArchitectures;
            this.buildRoot = buildRoot;
            this.libOutputDirectory = libOutputDirectory;
            this.nativeLibraries = nativeLibraries;
            this.nativeFrameworks = nativeFrameworks;
        }
    }

    private final @NotNull Configuration g;
    private final @NotNull InternalConfiguration gg;
    private final @NotNull Delegate delegate;

    enum BundledApplicationOption { SINGLE, MULTIPLE, UNIVERSAL }

    private final @NotNull Architecture executionArchitecture;
    private @Nullable File applicationJAR;

    private final @NotNull DuplicateChecker duplicateChecker = new DuplicateChecker();
    private final @NotNull Set<String> nativeLibraryNames = new HashSet<>();
    private final @NotNull Set<String> nativeFrameworkNames = new HashSet<>();
    private final @NotNull Set<NativeLibrary> nativeLibraries = new HashSet<>();
    private final @NotNull Set<NativeFramework> nativeFrameworks = new HashSet<>();

    private JavaApplicationBuilder(@NotNull Configuration g, @NotNull Delegate delegate)
    {
        this.g = g;
        this.delegate = delegate;

        String executionArchitectureProperty = System.getProperty("os.arch");
        executionArchitecture = executionArchitectureProperty.contains("x86") ? Architecture.Intel : Architecture.ARM;

        // debug
        // showConfiguration(g);

        gg = createInternalConfiguration(g);

        for (NativeLibrary nl : gg.nativeLibraries) {
            nativeLibraries.add(nl);
            copyNativeLibrary(nl, gg.libOutputDirectory);
        }

        for (NativeFramework nf : gg.nativeFrameworks) {
            nativeFrameworks.add(nf);
            copyFramework(nf, gg.libOutputDirectory);
        }
    }

    private @NotNull InternalConfiguration createInternalConfiguration(@NotNull Configuration g)
    {
        // Determine the target architecture(s) and associated bundle option

        ISet<Architecture> architectures = g.archConfigs.keySet();
        if (architectures.isEmpty()) {
            architectures = ISet.of(executionArchitecture);
        }

        // Validate the architecture(s)

        for (Architecture arch : architectures) {
            ArchitectureConfiguration a = g.archConfigs.get(arch);
            assert a != null;
            if (!Files.isDirectory(a.jdkRuntime.toPath())) {
                buildFailed("Specified JDK runtime for " + arch + " not found: " + a.jdkRuntime);
            }

            try {
                Utils.validateRuntime(a.jdkRuntime);
            } catch (Exception e) {
                buildFailed("JDK runtime for " + arch + " is invalid: " + e.getMessage());
            }
        }

        BundledApplicationOption bundledApplicationOption
          = determineBundledApplicationOption(architectures, g.shouldCreateUniversalApplication);

        {
            String s = g.applicationName;
            if (s.isEmpty() || s.contains("/") || s.contains(" ")) {
                buildFailed("Unsupported application name: " + s);
            }
        }

        if (g.shouldBuildApplication) {
            // Ensure that the required installation directories have been defined
            if (bundledApplicationOption == BundledApplicationOption.UNIVERSAL) {
                if (g.universalAppInstallDirs.isEmpty()) {
                    buildFailed("Cannot build universal application: no install locations have been defined");
                }
                if (g.launcher == null) {
                    buildFailed("Cannot build universal application: launcher has not been defined");
                }
                assert g.launcher != null;
                validateUniversalLauncher(g.launcher, architectures);
            } else {
                for (Architecture arch : architectures) {
                    ArchitectureConfiguration a = g.archConfigs.get(arch);
                    assert a != null;
                    if (a.appInstallDirs.isEmpty()) {
                        buildFailed("Cannot build application for " + arch + ": no install locations have been defined");
                    }
                }
            }
        }

        boolean willExpand = g.shouldBuildApplication || g.applicationJarDir != null;

        File libOutputDirectory = getDynamicLibraryOutputDirectory();
        IList<File> classTrees = collectClassTrees(g.classTrees);
        IList<File> classTreesAndJarFiles = willExpand ? collectClassTreesAndJarFiles(classTrees, g.jars) : IList.empty();
        IList<Object> resources = collectResources(g.resources);
        ISet<NativeLibrary> nativeLibraries = collectNativeLibraries(g.nativeLibraries);
        ISet<NativeFramework> nativeFrameworks = collectNativeFrameworks(g.nativeFrameworks, architectures);

        File buildRoot = validateBuildRoot(g.buildRoot);

        return new InternalConfiguration(bundledApplicationOption,
          classTrees,
          classTreesAndJarFiles,
          resources,
          architectures,
          buildRoot,
          libOutputDirectory,
          ISet.create(nativeLibraries),
          ISet.create(nativeFrameworks)
        );
    }

    private @NotNull BundledApplicationOption
    determineBundledApplicationOption(@NotNull ISet<Architecture> requested, boolean isUniversal)
    {
        if (requested.isEmpty()) {
            buildFailed("At least one architecture is required");
        }

        if (requested.size() == 1) {
            return BundledApplicationOption.SINGLE;
        }

        if (isUniversal) {
            return BundledApplicationOption.UNIVERSAL;
        }

        return BundledApplicationOption.MULTIPLE;
    }

    private @NotNull IList<File> collectClassTrees(@NotNull ISet<File> trees)
    {
        ListBuilder<File> b = IList.builder();
        for (File tree : trees) {
            if (!Files.isDirectory(tree.toPath())) {
                delegate.error("Specified class tree not found: " + tree);
            } else {
                b.add(tree.getAbsoluteFile());
            }
        }
        return b.values();
    }

    private @NotNull IList<File> collectClassTreesAndJarFiles(@NotNull IList<File> trees, @NotNull ISet<File> jars)
    {
        ListBuilder<File> b = IList.builder();
        b.addAll(trees);
        for (File jar : jars) {
            String name = jar.getName();
            if (!name.endsWith(".jar")) {
                delegate.error("Unexpected JAR file name: " + jar);
            } else {
                if (!Files.isRegularFile(jar.toPath())) {
                    delegate.error("Specified JAR file not found: " + jar);
                } else {
                    b.add(jar.getAbsoluteFile());
                }
            }
        }
        return b.values();
    }

    private @NotNull IList<Object> collectResources(@Nullable ISet<Object> resources)
    {
        ListBuilder<Object> b = IList.builder();
        if (resources != null) {
            for (Object o : resources) {
                if (o instanceof File) {
                    File f = Utils.resolve((File) o).getAbsoluteFile();
                    b.add(f);
                } else {
                    b.add(o);
                }
            }
        }
        return b.values();
    }

    private @NotNull ISet<NativeLibrary> collectNativeLibraries(@NotNull ISet<NativeLibrary> libs)
    {
        SetBuilder<NativeLibrary> b = ISet.builder();
        for (NativeLibrary lib : libs) {
            // Ensure that the required architectures are present
            for (Architecture arch : gg.targetArchitectures) {
                File f = lib.getFile(arch);
                if (f == null) {
                    delegate.error("Required library " + lib.getName() + " does not support " + arch);
                } else {
                    if (!Files.isRegularFile(f.toPath())) {
                        delegate.error("Required library file not found: " + f);
                    }
                }
            }
            b.add(lib);
        }
        return b.values();
    }

    private @NotNull ISet<NativeFramework> collectNativeFrameworks(@NotNull ISet<NativeFramework> frs,
                                                                   @NotNull ISet<Architecture> architectures)
    {
        SetBuilder<NativeFramework> b = ISet.builder();
        for (NativeFramework fr : frs) {
            NativeLibrary lib = fr.getLibrary();
            if (lib == null) {
                // ignore system frameworks
            } else {
                // Ensure that the required architectures are present
                for (Architecture arch : architectures) {
                    File f = lib.getFile(arch);
                    if (f == null) {
                        delegate.error("Required framework " + fr.getName() + " does not support " + arch);
                    } else {
                        if (!Files.isRegularFile(f.toPath())) {
                            delegate.error("Required framework file not found: " + f);
                        }
                    }
                }
                b.add(fr);
            }
        }
        return b.values();
    }

    private boolean isValidRuntime(@NotNull File f)
    {
        File java = new File(f, "bin/java");
        return Files.isRegularFile(java.toPath());
    }

    private @NotNull File validateBuildRoot(@NotNull File f)
    {
        Path p = f.toPath();
        if (Files.isDirectory(p)) {
            return f;
        }
        if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            buildFailed("Specified build directory is not a directory: " + p);
        }
        buildFailed("Specified build directory not found: " + p);
        throw new AssertionError();
    }

    private @Nullable File validateOutputDirectory(@Nullable File dir)
    {
        if (dir == null) {
            return null;
        }
        Path p = dir.toPath();
        if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
            return dir;
        }
        if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            buildFailed("Specified output directory is not a directory: " + p);
        }
        buildFailed("Specified output directory not found: " + p);
        throw new AssertionError();
    }

    private void showConfiguration(@NotNull Configuration g)
    {
        delegate.info("Main class: " + g.mainClassName);

        if (g.classTrees.isEmpty()) {
            delegate.info("No class trees specified");
        } else {
            delegate.info("Class trees:");
            for (File f : g.classTrees) {
                delegate.info("  " + f);
            }
        }

        if (g.jars.isEmpty()) {
            delegate.info("No JARs specified");
        } else {
            delegate.info("JARs:");
            for (File f : g.jars) {
                delegate.info("  " + f);
            }
        }

        if (g.resources.isEmpty()) {
            delegate.info("No resources specified");
        } else {
            delegate.info("Resources:");
            for (Object f : g.resources) {
                delegate.info("  " + f);
            }
        }

        if (g.shouldBuildApplication) {
            delegate.info("Bundled application requested");
        }

        if (g.shouldCreateUniversalApplication) {
            delegate.info("Universal application requested");
            for (File dir : g.universalAppInstallDirs) {
                delegate.info("  Install location: " + dir);
            }
        }

        if (g.basicJarDir != null) {
            delegate.info("Basic JAR requested: " + g.basicJarDir);
        }

        if (g.executableJarDir != null) {
            delegate.info("Executable JAR requested: " + g.executableJarDir);
        }

        if (g.archConfigs.isEmpty()) {
            delegate.info("No architectures are specified");
        } else {
            StringBuilder sb = new StringBuilder("Specified architectures:");
            for (Architecture arch : g.archConfigs.keySet()) {
                sb.append(" ");
                sb.append(arch);
            }
            delegate.info(sb.toString());
        }

        if (g.codeSigningKey != null) {
            delegate.info("Code signing key: " + g.codeSigningKey);
        }
    }

    public void build()
      throws BuildException
    {
        if (g.basicJarDir != null) {
            File dir = validateOutputDirectory(g.basicJarDir);
            File dest = new File(dir, g.applicationName + ".jar");
            try {
                Files.deleteIfExists(dest.toPath());
            } catch (IOException ex) {
                buildFailed("Unable to delete previous basic jar file: "
                  + dest.getPath() + ", " + ex);
            }
            createBasicJar(dest, gg.classTrees, gg.resources, g.basicManifestFile);
            delegate.info("Installed basic JAR: " + g.basicJarDir);
        }

        if (g.shouldBuildApplication || g.executableJarDir != null) {

            ISet<Architecture> allArchitectures = ISet.create(gg.targetArchitectures);

            File expandedClassesDir = getExpandedClassesDir();
            JarExpander.Result r = copyElements(gg.classTreesAndJarFiles, expandedClassesDir, gg.libOutputDirectory);
            nativeLibraries.addAll(r.nativeLibraries.toJavaList());
            nativeFrameworks.addAll(r.nativeFrameworks.toJavaList());

            // At this point, some native libraries and/or frameworks may have been identified.
            // They may refer to other native libraries that should also be copied.

            // For linking purposes, only the native library component of a framework is needed

            if (!nativeLibraries.isEmpty() || !nativeFrameworks.isEmpty()) {

                for (NativeLibrary lib : nativeLibraries) {
                    nativeLibraryNames.add(lib.getName());
                }

                IList<NativeLibrary> deps
                  = getDependentNativeLibraries(ISet.create(nativeLibraries), allArchitectures)
                  .appendingAll(getDependentNativeLibrariesOfFrameworks(ISet.create(nativeFrameworks), allArchitectures));

                IList<NativeFramework> frameworks = getDependentNativeFrameworks(ISet.create(nativeFrameworks));
                showFrameworks("Dependent native frameworks:", frameworks);
                showLibraries("Dependent native libraries:", deps);

                for (NativeLibrary lib : deps) {
                    nativeLibraryNames.add(lib.getName());
                    copyNativeLibrary(lib, gg.libOutputDirectory);
                }

                for (NativeFramework framework : frameworks) {
                    nativeFrameworkNames.add(framework.getName());
                    copyFramework(framework, gg.libOutputDirectory);
                }
            }

            duplicateChecker.reportCollisions();

            File ajar;
            File ajdir = validateOutputDirectory(g.applicationJarDir);
            if (ajdir != null) {
                ajar = new File(ajdir, g.applicationName + "App.jar");
            } else {
                ajar = getOutputFile(g.applicationName + ".jar");
            }

            createApplicationJar(ajar, g.applicationManifest, g.mainClassName, expandedClassesDir, gg.resources);

            checkDependencies(ajar);

            applicationJAR = ajar;

            if (g.shouldBuildApplication) {
                reportLibrariesAndFrameworks();
                if (gg.bundledApplicationOption == BundledApplicationOption.UNIVERSAL) {
                    createAndInstallUniversalApplication();
                } else {
                    for (Architecture a : allArchitectures) {
                        createAndInstallApplication(a);
                    }
                }
                // TBD: tickle Launch Services?
            }

            if (g.executableJarDir != null) {
                File dir = validateOutputDirectory(g.executableJarDir);
                File dest = new File(dir, g.applicationName + "Program.jar");
                // The executable JAR is the same as the application JAR except that the executable JAR includes native
                // libraries.
                if (nativeLibraries.isEmpty()) {
                    installExecutableJar(ajar, dest);
                } else {
                    IList<NativeLibrary> nls = IList.create(nativeLibraries);
                    createExecutableJar(dest, g.applicationManifest, g.mainClassName, expandedClassesDir, gg.resources, nls);
                }
            }
        }
    }

    private void checkDependencies(@NotNull File applicationJar)
    {
        File home = new File(System.getProperty("java.home"));
        File program = new File(home, "bin/jdeps");

        IList<String> args = IList.of("-s", applicationJar.getAbsolutePath());
        ExecutionConfiguration g = ExecutionConfiguration.create(program, "check_dependencies", args);
        ExecutionService es = ExecutionService.get();
        try {
            ExecutionResult r = es.execute(g);
            if (r.rc != 0) {
                buildFailed("jdeps failed");
                throw new AssertionError();
            }
            if (!r.output.isEmpty()) {
                delegate.info("JDK Dependencies:");
                // Avoid excess verbiage
                String[] lines = r.output.split("\n");
                for (String line : lines) {
                    if (!line.contains("JDK removed internal API")) {
                        int pos = line.indexOf(" -> ");
                        if (pos >= 0) {
                            line = line.substring(pos + 4);
                        }
                        if (!line.equals("not found")) {
                            // a dependence on a non-JDK module is not interesting.
                            delegate.info("  " + line);
                        }
                    }
                }
                if (r.output.contains("JDK removed internal API")) {
                    showInternalAPIDependencies(applicationJar);
                }
            }
        } catch (IOException e) {
            buildFailed("Unable to run jdeps", e);
            throw new AssertionError();
        }
    }

    private void showInternalAPIDependencies(@NotNull File applicationJar)
    {
        File home = new File(System.getProperty("java.home"));
        File program = new File(home, "bin/jdeps");

        IList<String> args = IList.of("--jdk-internals", applicationJar.getAbsolutePath());
        ExecutionConfiguration g = ExecutionConfiguration.create(program, "check_dependencies", args);
        ExecutionService es = ExecutionService.get();
        try {
            ExecutionResult r = es.execute(g);
            if (r.rc != 0) {
                buildFailed("jdeps failed");
                throw new AssertionError();
            }
            if (!r.output.isEmpty()) {
                delegate.info("JDK Internal API Dependencies:");
                // Avoid excess verbiage
                boolean eawtFound = false;
                String[] lines = r.output.split("\n");
                for (String line : lines) {
                    if (line.contains("Use ")) {
                        int pos = line.indexOf("Use ");
                        line = line.substring(0, pos).trim();
                        if (line.contains("com.apple.eawt.")) {
                            if (!eawtFound) {
                                eawtFound = true;
                                delegate.info("  com.apple.eawt");
                            }
                        } else {
                            delegate.info("  " + line);
                        }
                    }
                }
            }
        } catch (IOException e) {
            buildFailed("Unable to run jdeps", e);
            throw new AssertionError();
        }
    }

    private void checkForAndMoveDebugSymbols(@NotNull NativeLibrary lib,
                                             @NotNull File sourceDir,
                                             @NotNull File targetDir)
    {
        File f = lib.getFile();
        if (f != null) {
            String fn = f.getName();
            File sourceFile = new File(sourceDir, fn);
            File dsym = getAssociatedDebugSymbolsDirectory(sourceFile);
            if (Files.isDirectory(dsym.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                try {
                    File dsymTarget = new File(targetDir, dsym.getName());
                    Utils.moveDirectoryToOutput(dsym, dsymTarget);
                } catch (MessageException e) {
                    String message = e.createMessage("Unable to install library debug symbols [@@@]");
                    buildFailed(message);
                    throw new AssertionError();
                }
            }
        }
    }

    private void checkForAndMoveDebugSymbols(@NotNull NativeFramework lib,
                                             @NotNull File sourceDir,
                                             @NotNull File targetDir)
    {
        File f = lib.getRoot();
        if (f != null) {
            String fn = f.getName();
            File sourceFile = new File(sourceDir, fn);
            File dsym = getAssociatedDebugSymbolsDirectory(sourceFile);
            if (Files.isDirectory(dsym.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                try {
                    File dsymTarget = new File(targetDir, dsym.getName());
                    Utils.moveDirectoryToOutput(dsym, dsymTarget);
                } catch (MessageException e) {
                    String message = e.createMessage("Unable to install framework debug symbols [@@@]");
                    buildFailed(message);
                    throw new AssertionError();
                }
            }
        }
    }

    private @NotNull File getExpandedClassesDir()
    {
        return getCleanOutputDirectory("expanded");
    }

    private @NotNull File getJarOutputDir()
    {
        return getOutputDirectory("jars");
    }

    private @NotNull File getDynamicLibraryOutputDirectory()
    {
        if (g.libOutputDirectory != null) {
            if (!Files.isDirectory(g.libOutputDirectory.toPath())) {
                buildFailed("Native library output directory not found: " + g.libOutputDirectory);
            }
            return g.libOutputDirectory;
        }

        return getOutputDirectory("lib");
    }

    private @NotNull File getOutputFile(@NotNull String name)
    {
        Path p = new File(gg.buildRoot, name).toPath();
        if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.delete(p);
            } catch (IOException e) {
                buildFailed("Unable to delete existing output file: " + p);
            }
        } else if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            buildFailed("Output file already exists in another form: " + p);
        }
        return p.toFile();
    }

    private @NotNull File getOutputDirectory(@NotNull String name)
    {
        Path dp = new File(gg.buildRoot, name).toPath();
        try {
            if (Files.isDirectory(dp, LinkOption.NOFOLLOW_LINKS)) {
                return dp.toFile();
            }
            if (Files.exists(dp, LinkOption.NOFOLLOW_LINKS)) {
                buildFailed("Build directory is not a directory: " + dp);
            }
            Files.createDirectories(dp);
        } catch (IOException e) {
            buildFailed("Unable to create build directory: " + dp);
        }
        return dp.toFile();
    }

    private @NotNull File getCleanOutputDirectory(@NotNull String name)
    {
        Path dp = new File(gg.buildRoot, name).toPath();
        try {
            if (Files.isDirectory(dp, LinkOption.NOFOLLOW_LINKS)) {
                Utils.deleteDirectoryContents(dp);
                return dp.toFile();
            }
            if (Files.exists(dp, LinkOption.NOFOLLOW_LINKS)) {
                buildFailed("Build directory is not a directory: " + dp);
            }
            Files.createDirectories(dp);
        } catch (IOException e) {
            buildFailed("Unable to create build directory: " + dp);
        }
        return dp.toFile();
    }

    private @NotNull File getOutputLocationForTree(@NotNull String name)
    {
        Path dp = new File(gg.buildRoot, name).toPath();
        try {
            if (Files.isDirectory(dp, LinkOption.NOFOLLOW_LINKS)) {
                Utils.deleteDirectoryContents(dp);
            } else {
                Files.createDirectories(dp.getParent());
            }
        } catch (IOException e) {
            buildFailed("Unable to setup build directory: " + dp);
        }
        return dp.toFile();
    }

    /**
      Determine the native frameworks that need to be added to the application.
    */

    private IList<NativeFramework> getDependentNativeFrameworks(@NotNull ISet<NativeFramework> frameworks)
    {
        // TBD: check for dependencies
        return IList.create(frameworks);
    }

    /**
      Determine the dependent native libraries that need to be added to the application based on the
      specified required native frameworks.
      The libraries of interest are libraries installed by HomeBrew, which cannot be assumed to be present
      in the execution environment of the application.
    */

    private IList<NativeLibrary> getDependentNativeLibrariesOfFrameworks(@NotNull ISet<NativeFramework> frameworks,
                                                                         @NotNull ISet<Architecture> architectures)
    {
        // TBD
        return IList.empty();
    }

    /**
      Determine the dependent native libraries that need to be added to the application based on the
      specified required native libraries.
      The libraries of interest are libraries installed by HomeBrew, which cannot be assumed to be present
      in the execution environment of the application.
    */

    private IList<NativeLibrary> getDependentNativeLibraries(@NotNull ISet<NativeLibrary> libs,
                                                             @NotNull ISet<Architecture> architectures)
    {
        Set<String> knownLibraryNames = getLibraryNames(libs).toJavaSet();

        ListBuilder<NativeLibrary> b = IList.builder();
        List<NativeLibrary> remaining = libs.toJavaList();
        while (!remaining.isEmpty()) {
            NativeLibrary lib = remaining.remove(0);
            delegate.verbose("Processing native library: " + lib.getName());
            try {
                IMap<String,NativeLibrary> deps
                  = filterDeps(architectures,
                  LibraryDependencies.getDependencies(lib, JavaApplicationBuilder::pathToLibraryName, delegate));
                if (!deps.isEmpty()) {
                    // Assuming that the dependent libraries are present for all architectures
                    for (NativeLibrary dlib : deps.values()) {
                        if (!knownLibraryNames.contains(dlib.getName())) {
                            knownLibraryNames.add(dlib.getName());
                            b.add(dlib);
                            remaining.add(dlib);
                            delegate.verbose("  Adding dependent native library: " + dlib.getName());
                        }
                    }
                }
            } catch (BuildException e) {
                buildFailed("Unable to get native library dependencies: " + e);
            }
        }
        return b.values();
    }

    private @NotNull ISet<String> getLibraryNames(@NotNull ISet<NativeLibrary> libs)
    {
        SetBuilder<String> b = ISet.builder();
        for (NativeLibrary lib : libs) {
            b.add(lib.getName());
        }
        return b.values();
    }

    private static @Nullable String pathToLibraryName(@NotNull String path)
    {
        // only interested in HomeBrew installed libraries, which by definition are not normally available

        int pos = path.lastIndexOf('/');
        if (pos >= 0) {
            String name = NativeLibrarySupport.toLibraryName(path.substring(pos + 1));
            if (name != null && (path.startsWith("/usr/local/opt") || path.startsWith("/opt/homebrew/opt"))) {
                return name;
            }
        }
        return null;
    }

    private @NotNull IMap<String,NativeLibrary> filterDeps(@NotNull ISet<Architecture> architectures,
                                                           @NotNull IMap<String,NativeLibrary> deps)
    {
        // Remove uninteresting architectures
        MapBuilder<String,NativeLibrary> b = IMap.builder();
        for (String basicName : deps.keySet()) {
            NativeLibrary dep = deps.get(basicName);
            assert dep != null;
            ISet<Architecture> supportedAndNeeded = dep.getArchitectures().intersecting(architectures);
            int count = supportedAndNeeded.size();
            if (count == 1) {
                Architecture a = supportedAndNeeded.choose();
                b.put(basicName, toSingleArchitecture(dep, a));
            } else {
                b.put(basicName, toMultipleArchitecture(dep, supportedAndNeeded));
            }
        }
        return b.value();
    }

    private @NotNull NativeLibrary toSingleArchitecture(@NotNull NativeLibrary lib, @NotNull Architecture a)
    {
        ISet<Architecture> supported = lib.getArchitectures();
        assert supported.contains(a);
        if (supported.size() == 1) {
            return lib;
        }
        File f = lib.getFile(a);
        assert f != null;
        return SingleArchitectureNativeLibrary.create(lib.getName(), a, f, null);
    }

    private @NotNull NativeLibrary toMultipleArchitecture(@NotNull NativeLibrary lib, @NotNull ISet<Architecture> as)
    {
        ISet<Architecture> supported = lib.getArchitectures();
        if (supported.equals(as)) {
            return lib;
        }
        if (lib.isSingle()) {
            File f = lib.getFile();
            assert f != null;
            return MultipleArchitectureNativeLibrary.create(lib.getName(), as, f, null);
        } else {
            MapBuilder<Architecture,File> b = IMap.builder();
            for (Architecture a : as) {
                File f = lib.getFile(a);
                assert f != null;
                b.put(a, f);
            }
            return MultipleNativeLibrary.create(lib.getName(), b.value(), null);
        }
    }

    private void createAndInstallUniversalApplication()
    {
        IList<Architecture> architectures = IList.create(gg.targetArchitectures);

        File launcher = g.launcher;
        assert launcher != null;

        File appBuildLocation = getOutputLocationForTree(g.applicationName + ".app");
        File firstRuntime = null;

        boolean isFirst = true;
        for (Architecture arch : architectures) {
            ArchitectureConfiguration a = g.archConfigs.get(arch);
            assert a != null;
            if (isFirst) {
                isFirst = false;
                // Create an application image using the first runtime. Then move the runtime to the appropriate location
                // in the application image.
                createApplicationImage(arch, appBuildLocation);
                File standardRuntimeLocation = new File(appBuildLocation, "Contents/runtime");
                if (!Files.isDirectory(standardRuntimeLocation.toPath())) {
                    buildFailed("Failed to find installed runtime at " + standardRuntimeLocation.getPath());
                }
                fixJLI(a.jdkRuntime, standardRuntimeLocation);
                try {
                    File specificLocation = getRuntimeLocation(appBuildLocation, arch);
                    Files.move(standardRuntimeLocation.toPath(), specificLocation.toPath());
                    firstRuntime = specificLocation;
                } catch (IOException e) {
                    buildFailed("Unable to install Intel runtime: " + e.getMessage());
                }
            } else {
                // Copy the runtime for this architecture into the application image.
                File specificLocation = getRuntimeLocation(appBuildLocation, arch);
                try {
                    assert firstRuntime != null;
                    installSecondaryRuntime(firstRuntime, a, specificLocation);
                } catch (IOException e) {
                    buildFailed("Unable to install runtime for " + arch.getName() + ", " + e, e);
                }
            }
        }

        installLauncher(appBuildLocation, g.launcher);

        customizeApplication(appBuildLocation);
        if (g.codeSigningKey != null) {
            codeSignApplication(appBuildLocation, g.codeSigningKey);
        }
        for (File d : g.universalAppInstallDirs) {
            File dir = validateOutputDirectory(d);
            File dest = new File(dir, g.applicationName + ".app");
            installApplication(appBuildLocation, dest);
        }
    }

    private void installSecondaryRuntime(@NotNull File firstRuntime,
                                         @NotNull ArchitectureConfiguration a,
                                         @NotNull File targetRuntime)
      throws IOException
    {
        File dest = new File(targetRuntime, "Contents/Home");
        File macos = new File(targetRuntime, "Contents/MacOS");
        Files.createDirectories(dest.toPath());
        Files.createDirectories(macos.toPath());

        ISet<String> excludes = ISet.of("jmods", "src.zip", "demo", "man");
        Utils.copyDirectory(a.jdkRuntime, targetRuntime, excludes);

        // Create the extra Contents. This duplicates what jpackage has already done for the first architecture.
        File existingPropertyListFile = new File(firstRuntime, "Contents/Info.plist");
        File targetPropertyListFile = new File(targetRuntime, "Contents/Info.plist");
        Files.copy(existingPropertyListFile.toPath(), targetPropertyListFile.toPath(), COPY_ATTRIBUTES, REPLACE_EXISTING);
        File targetLibraryFile = new File(targetRuntime, "Contents/MacOS/libjli.dylib");
        copyJLILibrary(a.jdkRuntime, targetLibraryFile);
    }

    private @NotNull File getRuntimeLocation(@NotNull File app, @NotNull Architecture arch)
    {
        return new File(app, "Contents/runtime-" + arch.getName());
    }

    private void validateUniversalLauncher(@NotNull File launcher, @NotNull ICollection<Architecture> architectures)
    {
        if (!Files.isRegularFile(launcher.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            buildFailed("Launcher not found: " + launcher);
        }
        try {
            ISet<Architecture> launcherArchitectures = NativeLibrarySupport.getArchitectures(launcher);
            for (Architecture arch : architectures) {
                if (!launcherArchitectures.contains(arch)) {
                    buildFailed("Launcher does not support " + arch);
                }
            }
        } catch (IOException ex) {
            buildFailed("Unsupported launcher: " + ex.getMessage());
        }
        if (!Files.isExecutable(launcher.toPath())) {
            buildFailed("Specified launcher is not executable: " + launcher);
        }
    }

    private void copyJLILibrary(@NotNull File runtime, @NotNull File target)
      throws IOException
    {
        Path jliName = new File("libjli.dylib").toPath();
        try (Stream<Path> walk = Files.walk(runtime.toPath())) {
            Path jli = walk
              .filter(file -> file.getFileName().equals(jliName))
              .findFirst()
              .orElseThrow(NoSuchElementException::new);
            Files.copy(jli, target.toPath(), COPY_ATTRIBUTES, REPLACE_EXISTING);
        } catch (NoSuchElementException ex) {
            throw new IOException("libjli.dylib not found in " + runtime);
        }
    }

    /**
      Create and install a single-architecture bundled application.
      @param targetArchitecture The target architecture, which must be defined.
    */

    private void createAndInstallApplication(@NotNull Architecture targetArchitecture)
    {
        ArchitectureConfiguration a = g.archConfigs.get(targetArchitecture);
        assert a != null;

        IList<File> installDirs = a.appInstallDirs;
        assert !installDirs.isEmpty();

        // Define the location where the application build product should be located
        String name = g.applicationName + "-" + targetArchitecture.getName() + ".app";
        File appBuildLocation = getOutputFile(name);

        createApplication(targetArchitecture, appBuildLocation);
        customizeApplication(appBuildLocation);
        if (g.codeSigningKey != null) {
            codeSignApplication(appBuildLocation, g.codeSigningKey);
        }

        for (File d : installDirs) {
            File dir = validateOutputDirectory(d);
            File dest = new File(dir, g.applicationName + ".app");
            installApplication(appBuildLocation, dest);
        }
    }

    /**
      Create a bundled application for a single target architecture.
    */

    protected void createApplication(@NotNull Architecture targetArchitecture, @NotNull File dest)
    {
        createApplicationImage(targetArchitecture, dest);

        File launcher = g.launcher;
        if (launcher != null) {
            replaceLauncher(dest, launcher);
        } else {

            // The default launcher installed by jpackage is specific to the execution environment.
            // If the target is different, the corresponding default launcher can be obtained from the
            // target JDK base image.

            ArchitectureConfiguration a = g.archConfigs.get(targetArchitecture);
            assert a != null;
            if (targetArchitecture != executionArchitecture) {
                fixLauncher(targetArchitecture, dest, a.jdkRuntime);
            } else {
                File lf = new File(dest, "Contents/MacOS/" + g.applicationName);
                updateLauncherSearchPath(lf);
            }
        }
    }

    protected void createApplicationImage(@NotNull Architecture arch, @NotNull File appDest)
    {
        ArchitectureConfiguration a = g.archConfigs.get(arch);
        assert a != null;
        File javaRuntime = a.jdkRuntime;
        IList<String> javaOptions = g.javaOptions;

        if (Files.isDirectory(appDest.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            try {
                Utils.deleteDirectory(appDest.toPath());
            } catch (IOException e) {
                buildFailed("Unable to delete existing application: " + appDest);
            }
        } else if (Files.exists(appDest.toPath())) {
            buildFailed("Existing application found that is not a directory: " + appDest);
        }

        if (!nativeLibraryNames.isEmpty()) {
            String libraryPath = createLibraryPath(nativeLibraryNames);
            javaOptions = javaOptions.appending("-Djava.library.path=" + libraryPath);
        }

        assert applicationJAR != null;
        createApplicationImage(g.applicationName, g.mainClassName, applicationJAR, javaRuntime,
          javaOptions, g.appArgs, appDest);

        if (!Files.isDirectory(appDest.toPath())) {
            buildFailed("Failed to create application image: " + appDest.getPath());
        }

        delegate.info("Created application image: " + appDest);
    }

    /**
      Create an application image, which is basically a bundled application that has not been signed.
    */

    private void createApplicationImage(@NotNull String applicationName,
                                        @NotNull String mainClassName,
                                        @NotNull File applicationJAR,
                                        @NotNull File javaRuntime,
                                        @NotNull IList<String> javaOptions,
                                        @NotNull IList<String> appArgs,
                                        @NotNull File appDest
    )
    {
        // jpackage uses the application name to create the name of the bundle. To create an application image with a
        // different name requires creating the image in a temporary directory and moving it into place.

        File tempDir = null;
        File destDir = appDest.getParentFile();

        if (!appDest.getName().equals(applicationName + ".app")) {
            tempDir = new File(destDir, "temp");
            try {
                Files.createDirectory(tempDir.toPath());
            } catch (IOException e) {
                buildFailed("Unable to create temporary directory: " + tempDir);
                throw new AssertionError();
            }
            destDir = tempDir;
        }

        String home = System.getProperty("user.home");
        File program = new File(home, "bin/jpackage");

        ListBuilder<String> args = IList.builder();

        String javaOptionsString = null;
        if (!javaOptions.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String option : javaOptions) {
                sb.append(" ");
                sb.append(option);
            }
            javaOptionsString = sb.substring(1);
        }

        //args.add("create-image");
        args.add("--type");
        args.add("app-image");

        // --input is required and it must contain the application JAR (but not the basic JAR)
        try {
            Path t = Files.createTempDirectory("appbuild");
            Path f = t.resolve(applicationJAR.getName());
            Files.copy(applicationJAR.toPath(), f, COPY_ATTRIBUTES);
            args.add("--input");
            args.add(t.toString());
        } catch (IOException ignore) {
        }

        args.add("--main-jar");
        args.add(applicationJAR.getName());

        args.add("--dest");
        args.add(destDir.getAbsolutePath());

        args.add("-n");
        args.add(applicationName);

        if (g.description != null && !g.description.isBlank()) {
            args.add("--description");
            args.add(g.description);
        }

//        if (codeSigningKey != null) {
//            // suppressed because current jpackage cannot find my certificate
//            // args.add("--mac-sign");  -- obsolete
//            // args.add("--mac-signing-key-user-name");
//            // args.add(codeSigningKey);
//        }

        //args.add("--verbose");

        args.add("--main-class");
        args.add(mainClassName);

        args.add("--runtime-image");
        args.add(javaRuntime.getAbsolutePath());

        if (javaOptionsString != null) {
            args.add("--java-options");
            args.add(javaOptionsString);
        }

        if (!appArgs.isEmpty()) {
            args.add("--arguments");
            args.addAll(appArgs);
        }

        ExecutionConfiguration g = ExecutionConfiguration.create(program, "create_image", args.values());
        ExecutionService es = ExecutionService.get();
        try {
            ExecutionResult r = es.execute(g);
            if (r.rc != 0) {
                delegate.error("jpackage failed");
                if (!r.error.isEmpty()) {
                    delegate.error(r.error);
                }
                buildFailed("jpackage failed");
                throw new AssertionError();
            }

            if (tempDir != null) {
                File actualApplicationImage = new File(tempDir, applicationName + ".app");
                if (!Files.isDirectory(actualApplicationImage.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    buildFailed("Expected application image not found: " + actualApplicationImage);
                    throw new AssertionError();
                }
                try {
                    Files.move(actualApplicationImage.toPath(), appDest.toPath());
                } catch (IOException e) {
                    buildFailed("Unable to relocate application image: " + e);
                    throw new AssertionError();
                }
            }

        } catch (IOException e) {
            buildFailed("Unable to run jpackage", e);
            throw new AssertionError();
        } finally {
            if (tempDir != null) {
                try {
                    Utils.deleteDirectory(tempDir.toPath());
                } catch (IOException e) {
                    delegate.error("Unable to delete temporary application image directory: " + e);
                }
            }
        }
    }

    private void updateLauncherSearchPath(@NotNull File launcher)
    {
        File program = new File("/usr/bin/install_name_tool");
        IList<String> args = IList.of("-add_rpath", "@executable_path/../Frameworks", launcher.getAbsolutePath());
        ExecutionConfiguration g = ExecutionConfiguration.create(program, "update_launcher_search_path", args);
        ExecutionService es = ExecutionService.get();
        try {
            ExecutionResult r = es.execute(g);
            if (r.rc != 0) {
                delegate.error("Install name tool failed on: " + launcher);
                if (!r.error.isEmpty()) {
                    delegate.error(r.error);
                }
                buildFailed("Install name tool failed on: " + launcher);
                throw new AssertionError();
            }
        } catch (IOException e) {
            buildFailed("Unable to run install name tool, e");
            throw new AssertionError();
        }
    }

    private void installLauncher(@NotNull File application, @NotNull File launcher)
    {
        try {
            File target = new File(application, "Contents/MacOS/" + g.applicationName);
            Files.copy(launcher.toPath(), target.toPath(), COPY_ATTRIBUTES, REPLACE_EXISTING);
            Set<PosixFilePermission> permissions
              = new HashSet<>(Arrays.asList(OWNER_EXECUTE, OWNER_READ, GROUP_EXECUTE, GROUP_READ, OTHERS_EXECUTE, OTHERS_READ));
            Files.setPosixFilePermissions(target.toPath(), permissions);
            delegate.info("Installed launcher from " + launcher);
        } catch (IOException e) {
            String msg = "Unable to install launcher: " + e.getMessage();
            buildFailed(msg);
        }
    }

    private void fixLauncher(@NotNull Architecture targetArchitecture,
                             @NotNull File application,
                             @NotNull File jdkBaseImage)
    {
        String prefix = "Unable to build " + targetArchitecture + " application: ";

        // Find the jpackage module file
        File jmodFile = new File(jdkBaseImage, "jmods/jdk.jpackage.jmod");
        if (!jmodFile.isFile()) {
            String msg = prefix + jmodFile + " not found";
            buildFailed(msg);
        }

        try (ZipFile zf = new ZipFile(jmodFile, ZipFile.OPEN_READ)) {
            String name = "classes/jdk/jpackage/internal/resources/jpackageapplauncher";
            ZipEntry e = zf.getEntry(name);
            if (e == null) {
                String msg = prefix + "launcher not found in " + jmodFile + " using name " + name;
                buildFailed(msg);
            }
            assert e != null;
            InputStream s = zf.getInputStream(e);
            File launcher = new File(application, "Contents/MacOS/" + g.applicationName);
            if (!launcher.isFile()) {
                String msg = prefix + "existing launcher not found at " + launcher;
                buildFailed(msg);
            }
            Files.copy(s, launcher.toPath(), COPY_ATTRIBUTES, REPLACE_EXISTING);
            Set<PosixFilePermission> permissions
              = new HashSet<>(Arrays.asList(OWNER_EXECUTE, OWNER_READ, GROUP_EXECUTE, GROUP_READ, OTHERS_EXECUTE, OTHERS_READ));
            Files.setPosixFilePermissions(launcher.toPath(), permissions);

            delegate.info("Installed launcher from " + jmodFile);

            updateLauncherSearchPath(launcher);

        } catch (IOException ex) {
            String msg = prefix + ex.getMessage();
            buildFailed(msg);
        }
    }

    /**
      Older JDKs have a symlink at Contents/MacOS/libjli.dylib.
      jpackage always copies the actual library file to that location.
      This method restores the symlink.
    */

    private void fixJLI(@NotNull File jdkBaseImage, @NotNull File targetRuntime)
    {
        File original = new File(jdkBaseImage, "Contents/MacOS/libjli.dylib");
        File target = new File(targetRuntime, "Contents/MacOS/libjli.dylib");
        if (!original.exists()) {
            buildFailed("Unable to find: " + original);
        }
        if (!target.exists()) {
            buildFailed("Unable to find: " + target);
        }
        Path op = original.toPath();
        Path tp = target.toPath();
        if (Files.isSymbolicLink(op) && !Files.isSymbolicLink(tp)) {
            try {
                Files.delete(tp);
                Files.createSymbolicLink(tp, new File("../Home/lib/libjli.dylib").toPath());
            } catch (IOException ex) {
                buildFailed("Unable to create symlink at " + tp + " " + ex);
            }
        }
    }

    private void replaceLauncher(@NotNull File application, @NotNull File replacement)
    {
        String prefix = "Unable to replace launcher: ";
        File launcher = new File(application, "Contents/MacOS/" + g.applicationName);
        if (!launcher.isFile()) {
            String msg = prefix + "existing launcher not found at " + launcher;
            buildFailed(msg);
        }
        try {
            Files.copy(replacement.toPath(), launcher.toPath(), COPY_ATTRIBUTES, REPLACE_EXISTING);
            delegate.info("Using launcher: " + replacement);
        } catch (IOException ex) {
            String msg = prefix + ex.getMessage();
            buildFailed(msg);
        }
    }

    private void createBasicJar(@NotNull File jarFile,
                                @NotNull IList<File> classTrees,
                                @NotNull IList<Object> resources,
                                @Nullable File manifest)
      throws BuildException
    {
        File program = new File("/usr/bin/jar");
        IList<Object> sources = IList.cast(classTrees).appendingAll(resources);

        JARBuilder.Configuration e
          = JARBuilder.createConfiguration(program, sources, jarFile, manifest, null, false);
        JARBuilder.createJAR(e, delegate);
        delegate.info("  Created: " + jarFile.getPath());
    }

    private void createApplicationJar(@NotNull File jarFile,
                                      @Nullable Object manifest,  // File or subclass-dependent
                                      @NotNull String mainClassName,
                                      @NotNull File classesDir,
                                      @NotNull IList<Object> resources)
      throws BuildException
    {
        File program = new File("/usr/bin/jar");
        IList<Object> sources = IList.cast(IList.of(classesDir)).appendingAll(resources);
        JARBuilder.Configuration e
          = JARBuilder.createConfiguration(program, sources, jarFile, manifest, mainClassName, true);
        JARBuilder.createJAR(e, delegate);
        delegate.info("  Created: " + jarFile.getPath());
    }

    private void createExecutableJar(@NotNull File jarFile,
                                     @Nullable Object manifest,  // File or subclass-dependent
                                     @NotNull String mainClassName,
                                     @NotNull File classesDir,
                                     @NotNull IList<Object> resources,
                                     @NotNull IList<NativeLibrary> nativeLibraries)
      throws BuildException
    {
        // The executable JAR is the same as the application JAR except that it also contains native libraries.
        // Native frameworks are not supported because they contain symlinks.

        File program = new File("/usr/bin/jar");
        IList<Object> sources = IList.cast(IList.of(classesDir)).appendingAll(resources);
        for (NativeLibrary nl : nativeLibraries) {
            File lf = nl.getFile();
            if (lf == null) {
                delegate.error("Native library " + nl.getName() + " cannot be installed in the executable JAR [not a single file]");
            } else {
                sources = sources.appending(lf);
                File f = nl.getDebugSymbols();
                if (f != null) {
                    RelativeFile rf = RelativeFile.create(f.getParentFile(), f);
                    sources = sources.appending(rf);
                }
            }
        }

        JARBuilder.Configuration e
          = JARBuilder.createConfiguration(program, sources, jarFile, manifest, mainClassName, true);
        JARBuilder.createJAR(e, delegate);
        delegate.info("  Created: " + jarFile.getPath());
    }

    protected void codeSignApplication(@NotNull File app, @NotNull String signingKey)
    {
        File contents = new File(app, "Contents");
        File frameworks = new File(contents, "Frameworks");
        File[] libs = frameworks.listFiles();
        if (libs != null) {
            for (File f : libs) {
                if (f.getName().endsWith(".dylib")) {
                    codeSignFile(f, signingKey);
                } else if (f.getName().endsWith(".framework")) {
                    codeSignFramework(f, signingKey);
                }
            }
        }

        // Sign (or update) the launcher and other executables.

        File macos = new File(contents, "MacOS");
        File[] bins = macos.listFiles();
        if (bins != null) {
            for (File f : bins) {
                codeSignFile(f, signingKey);
            }
        }

        // Sign the runtime(s)
        File[] fs = contents.listFiles();
        if (fs != null) {
            for (File f : fs) {
                String name = f.getName();
                if (name.startsWith("runtime")) {
                    codeSignRuntime(f, signingKey);
                }
            }
        }

        codeSignFile(app, signingKey);
    }

    protected void codeSignRuntime(@NotNull File f, @NotNull String signingKey)
    {
        try {
            try (Stream<Path> stream = Files.walk(f.toPath())) {
                stream.peek(this::makeWritable)
                  .filter(this::isSignableFile)
                  .forEach(p -> replaceCodeSign(p.toFile(), signingKey));
            }
        } catch (IOException ex) {
            delegate.error("Unable to scan runtime: " + f + " " + ex);
        }
    }

    private void makeWritable(@NotNull Path p)
    {
        if (!Files.isSymbolicLink(p)) {
            try {
                Set<PosixFilePermission> pfp = Files.getPosixFilePermissions(p);
                if (!pfp.contains(PosixFilePermission.OWNER_WRITE)) {
                    pfp = EnumSet.copyOf(pfp);
                    pfp.add(PosixFilePermission.OWNER_WRITE);
                    Files.setPosixFilePermissions(p, pfp);
                }
            } catch (IOException ex) {
                delegate.verbose("Unable to make writable: " + p + " " + ex);
            }
        }
    }

    private boolean isSignableFile(@NotNull Path p)
    {
        if (Files.isSymbolicLink(p)) {
            return false;
        }
        if (!Files.isRegularFile(p)) {
            return false;
        }
        if (Files.isExecutable(p) || p.toString().endsWith(".dylib")) {
            return !p.toString().contains("dylib.dSYM/Contents") && !p.toString().contains("/legal/");
        }
        return false;
    }

    protected void codeSignFramework(@NotNull File f, @NotNull String signingKey)
    {
        codeSignFile(f, signingKey);
    }

    protected void replaceCodeSign(@NotNull File f, @NotNull String signingKey)
    {
        removeSignature(f);
        codeSignFile(f, signingKey);
    }

    private void removeSignature(@NotNull File f)
    {
        File program = new File("/usr/bin/codesign");
        IList<String> args = IList.of("--remove-signature", f.getAbsolutePath());
        ExecutionConfiguration g = ExecutionConfiguration.create(program, "remove_signature", args);
        ExecutionService es = ExecutionService.get();
        try {
            ExecutionResult r = es.execute(g);
            if (r.rc != 0) {
                // TBD: is this a problem
                delegate.error("Unable to remove signature from " + f + ": code " + r.rc);
            }
        } catch (IOException e) {
            buildFailed("Unable to execute code sign program", e);
            throw new AssertionError();
        }
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

    protected void customizeApplication(@NotNull File app)
    {
        if (g.infoPlistFile != null) {
            File plist = g.infoPlistFile;
            if (Files.isRegularFile(plist.toPath())) {
                File target = new File(app, "Contents/Info.plist");
                try {
                    Utils.copyFile(plist, target);
                    delegate.info("  Installed: " + plist);
                } catch (IOException ex) {
                    buildFailed("Unable to install Info.plist", ex);
                }
            } else {
                File f = new File(app, "Contents/Info.plist");
                if (Files.isRegularFile(f.toPath())) {
                    customizeInfoPlist(f);
                } else {
                    buildFailed("Info.plist not found");
                }
            }
        }

        if (g.iconsFile != null) {
            File icons = g.iconsFile;
            if (Files.isRegularFile(icons.toPath())) {
                String name = icons.getName();
                File target = new File(app, "Contents/Resources/" + name);
                try {
                    Utils.copyFile(icons, target);
                    delegate.info("  Installed: " + icons);
                } catch (IOException ex) {
                    buildFailed("Unable to install " + icons, ex);
                }
            } else {
                buildFailed("Icons file not found: " + icons);
            }
        }

        // TBD: If an icons file is installed in any way, the -Xdock:icon java option should be added.

//        b.add("-Xdock:icon=Contents/Resources/" + shortName + ".icns");

        if (!nativeLibraryNames.isEmpty() || !nativeFrameworks.isEmpty()) {
            File target = new File(app, "Contents/Frameworks");
            createDirectory(target);
            installNativeLibrariesAndFrameworks(gg.libOutputDirectory, target, 0);
        }

        if (g.appResourcesDir != null) {
            File resourcesDir = g.appResourcesDir;
            if (Files.isDirectory(resourcesDir.toPath())) {
                File target = new File(app, "Contents/Resources");
                createDirectory(target);
                installResources(app, resourcesDir, target, 0);
                delegate.info("  Installed resources: " + resourcesDir);
            } else {
                buildFailed("Resources directory not found: " + resourcesDir);
            }
        }

        if (g.appContentsDir != null) {
            File bundleContentsDir = g.appContentsDir;
            if (Files.isDirectory(bundleContentsDir.toPath())) {
                File target = new File(app, "Contents");
                installBundleContents(bundleContentsDir, target, 0);
                delegate.info("  Installed bundle contents: " + bundleContentsDir);
            } else {
                buildFailed("Bundle contents directory not found: " + bundleContentsDir);
            }
        }
    }

    private void customizeInfoPlist(@NotNull File f)
    {
        try {
            List<String> lines = Files.readAllLines(f.toPath());
            int count = lines.size();
            List<String> insertion = new ArrayList<>();
            insertion.add("  <key>LSEnvironment</key>");
            insertion.add("  <dict>");
            insertion.add("    <key>DYLD_FRAMEWORK_PATH</key>");
            insertion.add("    <string>@executable_path/../Frameworks:/System/Library/Frameworks</string>");
            insertion.add("  </dict>");
            lines.addAll(count-2, insertion);
            Files.write(f.toPath(), lines);
        } catch (IOException ex) {
            buildFailed("Unable to customize Info.plist", ex);
        }
    }

    private void installResources(@NotNull File app, @NotNull File sourceDir, @NotNull File targetDir, int depth)
    {
        File[] files = sourceDir.listFiles();
        if (files != null) {
            for (File f : files) {
                File childTarget = new File(targetDir, f.getName());
                if (Files.isDirectory(f.toPath())) {
                    // special case
                    if (depth == 0 && f.getName().equals("bin")) {
                        childTarget = new File(app, "Contents/MacOS");
                    }
                    createDirectory(childTarget);
                    installResources(app, f, childTarget, depth+1);
                } else if (Files.isRegularFile(f.toPath())) {
                    try {
                        // Attributes must be preserved because executable files may be copied
                        Files.copy(f.toPath(), childTarget.toPath(), REPLACE_EXISTING, COPY_ATTRIBUTES);
                    } catch (IOException ex) {
                        buildFailed("Unable to install file: " + childTarget.getPath(), ex);
                    }
                }
            }
        }
    }

    private void installBundleContents(@NotNull File sourceDir, @NotNull File targetDir, int depth)
    {
        File[] files = sourceDir.listFiles();
        if (files != null) {
            for (File f : files) {
                File childTarget = new File(targetDir, f.getName());
                if (Files.isDirectory(f.toPath())) {
                    createDirectory(childTarget);
                    installBundleContents(f, childTarget, depth+1);
                } else if (Files.isRegularFile(f.toPath())) {
                    try {
                        Utils.copyFile(f, childTarget);
                    } catch (IOException ex) {
                        buildFailed("Unable to install file: " + childTarget.getPath(), ex);
                    }
                }
            }
        }
    }

    private void reportLibrariesAndFrameworks()
    {
        if (!nativeLibraryNames.isEmpty()) {
            StringBuilder sb = new StringBuilder("Installing native libraries:");
            List<String> names = new ArrayList<>(nativeLibraryNames);
            Collections.sort(names);
            for (String name : names) {
                sb.append(" ");
                sb.append(name);
            }
            delegate.info(sb.toString());
        }
        if (!nativeFrameworkNames.isEmpty()) {
            StringBuilder sb = new StringBuilder("Using frameworks:");
            List<String> names = new ArrayList<>(nativeFrameworkNames);
            Collections.sort(names);
            for (String name : names) {
                sb.append(" ");
                sb.append(name);
            }
            delegate.info(sb.toString());
        }
    }

    private @NotNull String createLibraryPath(@NotNull Set<String> libraryNames)
    {
        return "$APPDIR/../Frameworks";
    }

    private void installNativeLibrariesAndFrameworks(@NotNull File sourceDir, @NotNull File targetDir, int depth)
    {
        File[] files = sourceDir.listFiles();
        if (files != null) {
            for (File f : files) {
                File target = new File(targetDir, f.getName());
                try {
                    if (Utils.isNativeFramework(f) || Utils.isNativeFrameworkSymbols(f) || Utils.isNativeLibrarySymbols(f)) {
                        Utils.copyDirectoryReplacing(f, target);
                    } else if (Utils.isNativeLibrary(f)) {
                        Utils.copyFile(f, target);
                    }
                } catch (IOException ex) {
                    buildFailed("Unable to install file: " + target.getPath(), ex);
                }
            }
        }
    }

    public void installJarFile(@NotNull File source, @NotNull File target)
    {
        try {
            Files.copy(source.toPath(), target.toPath(), REPLACE_EXISTING, COPY_ATTRIBUTES);
            delegate.info("  Installed: " + target.getPath());
        } catch (IOException ex) {
            buildFailed("Unable to install " + target.getPath(), ex);
        }
    }

    public void installApplication(@NotNull File source, @NotNull File target)
    {
        if (!source.equals(target)) {
            if (Files.isDirectory(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                try {
                    Utils.deleteDirectory(target.toPath());
                } catch (IOException e) {
                    buildFailed("Unable to delete old installed application", e);
                }
            }
            try {
                Utils.copyDirectory(source, target);
            } catch (IOException e) {
                buildFailed("Unable to install application", e);
            }
            delegate.info("  Installed application: " + target.getPath());
        } else {
            delegate.info("  Created application: " + source.getPath());
        }
    }

    public void installExecutableJar(@NotNull File source, @NotNull File target)
    {
        try {
            Files.copy(source.toPath(), target.toPath(), REPLACE_EXISTING, COPY_ATTRIBUTES);
            delegate.info("  Installed executable JAR: " + target.getPath());
        } catch (IOException ex) {
            buildFailed("Unable to install executable JAR: " + target.getPath(), ex);
        }
    }

    /**
      Copy source elements from the specified JAR files and directory trees.
      @param sources The JAR files and directory tree roots.
      @param classTarget The destination directory for classes and resources.
      @param libTarget The destination directory for native libraries and frameworks.
    */

    protected @NotNull JarExpander.Result copyElements(@NotNull IList<File> sources,
                                                       @NotNull File classTarget,
                                                       @NotNull File libTarget)
    {
        ISet<File> sourceFiles = ISet.create(sources);

        JarExpander.Configuration g =
          JarExpander.createConfiguration(sourceFiles, classTarget, libTarget, libTarget);
        try {
            JarExpander.Result r = JarExpander.expand(g, delegate);
            if (r.errorsFound) {
                buildFailed("Errors detected");
                throw new AssertionError();
            }
            return r;
        } catch (IOException e) {
            buildFailed("Unable to copy: " + e);
            throw new AssertionError();
        }
    }

    private void validateForExpansion(@NotNull ISet<File> fs)
    {
        for (File f : fs) {
            try {
                if (Utils.isModularJarFile(f)) {
                    buildFailed("Modular JAR files may not be expanded: " + f);
                    throw new AssertionError();
                }
            } catch (IOException ignore) {
                // error will be reported later
            }
        }
    }

    protected void showFrameworks(@Nullable String header, @NotNull ICollection<NativeFramework> frameworks)
    {
        if (!frameworks.isEmpty()) {
            if (header != null) {
                delegate.info(header);
            }
            for (NativeFramework framework : frameworks) {
                showFramework(framework);
            }
        }
    }

    protected void showLibraries(@Nullable String header, @NotNull ICollection<NativeLibrary> libs)
    {
        if (!libs.isEmpty()) {
            if (header != null) {
                delegate.info(header);
            }
            for (NativeLibrary lib : libs) {
                showLibrary(lib);
            }
        }
    }

    protected void showLibrary(@NotNull NativeLibrary lib)
    {
        StringBuilder sb = new StringBuilder(lib.getName());
        ISet<Architecture> as = lib.getArchitectures();
        File f = lib.getFile();
        if (f != null) {
            for (Architecture a : as) {
                sb.append(" ");
                sb.append(a);
            }
            sb.append("=");
            sb.append(f.getPath());
        } else {
            for (Architecture a : as) {
                f = lib.getFile(a);
                assert f != null;
                sb.append(" ");
                sb.append(a);
                sb.append("=");
                sb.append(f.getPath());
            }
        }
        delegate.info("  " + sb);
    }

    protected void showFramework(@NotNull NativeFramework framework)
    {
        delegate.info("  " + framework);

        // TBD: obtain native library to determine supported architectures

//            StringBuilder sb = new StringBuilder(lib.getName());
//            ISet<Architecture> as = lib.getArchitectures();
//            File f = lib.getFile();
//            if (f != null) {
//                for (Architecture a : as) {
//                    sb.append(" ");
//                    sb.append(a);
//                }
//                sb.append("=");
//                sb.append(f.getPath());
//            } else {
//                for (Architecture a : as) {
//                    f = lib.getFile(a);
//                    assert f != null;
//                    sb.append(" ");
//                    sb.append(a);
//                    sb.append("=");
//                    sb.append(f.getPath());
//                }
//            }
//            log("  " + sb.toString());
    }

    /**
      Copy a framework. If the framework exists in the target directory, it will be deleted.
    */

    private void copyFramework(@NotNull NativeFramework framework, @NotNull File dir)
    {
        File fm = framework.getRoot();
        if (fm != null) {
            File target = new File(dir, framework.getName() + ".framework");
            try {
                Utils.copyDirectoryReplacing(fm, target);
            } catch (IOException e) {
                buildFailed("Unable to copy framework: " + framework + " [" + e + "]");
            }
        }
    }

    private void copyNativeLibrary(@NotNull NativeLibrary lib, @NotNull File targetDir)
    {
        File f = getNativeLibraryFile(lib);
        File targetFile = new File(targetDir, NativeLibrarySupport.createLibraryFileName(lib.getName()));

        try {
            if (!targetDir.isDirectory()) {
                Files.createDirectories(targetDir.toPath());
            }
            delegate.verbose("Copying native library " + lib.getName() + " to " + targetFile);
            Utils.copyFile(f, targetFile);
            copyNativeLibraryDebugSymbols(lib, targetDir);
        } catch (IOException ex) {
            buildFailed("Unable to copy native library" + lib.getName()
              + " to " + targetFile.getPath(), ex);
        }
    }

    private void copyNativeLibraryDebugSymbols(@NotNull NativeLibrary lib, @NotNull File targetDir)
    {
        File dsym = lib.getDebugSymbols();

        delegate.info("debug symbols: " + dsym);


        if (dsym == null) {
            File f = getNativeLibraryFile(lib);
            dsym = getAssociatedDebugSymbolsDirectory(f);

            delegate.info("replacement debug symbols: " + dsym);


        }
        String dsymName = dsym.getName();
        File dsymTarget = new File(targetDir, dsymName);
        try {
            if (Files.isDirectory(dsym.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isDirectory(dsymTarget.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    Utils.deleteDirectory(dsymTarget.toPath());
                } else if (Files.exists(dsymTarget.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    buildFailed("Unable to copy native library debug symbols [destination is not a directory]: "
                      + dsymTarget);
                    throw new AssertionError();
                }
                Utils.copyDirectory(dsym, dsymTarget);
            }
        } catch (IOException e) {
            buildFailed("Unable to copy native library debug symbols" + lib.getName()
              + " to " + dsymTarget.getPath(), e);
        }
    }

    private @NotNull File getAssociatedDebugSymbolsDirectory(@NotNull File f)
    {
        String dsymName = f.getName() + ".dSYM";
        return new File(f.getParentFile(), dsymName);
    }

    private @NotNull File getNativeLibraryFile(@NotNull NativeLibrary lib)
    {
        File f = lib.getFile();
        if (f != null) {
            return f;
        }
        String name = lib.getName();
        try {
            f = NativeLibrarySupport.createUniversalLibrary(name, lib.getAllFiles());
        } catch (IOException ex) {
            buildFailed("Unable to create universal library " + name + ": " + ex);
        }
        assert f != null;
        return f;
    }

    private class DuplicateChecker
    {
        private final @NotNull Map<String,List<String>> sourceMap = new HashMap<>();
        private final @NotNull Set<String> collisions = new HashSet<>();

        public void add(@NotNull String source, @NotNull String entryName)
        {
            List<String> sources = sourceMap.computeIfAbsent(entryName, k -> new ArrayList<>());
            sources.add(source);
        }

        public void registerCollision(@NotNull String entryName)
        {
            collisions.add(entryName);
        }

        public void reportCollisions()
        {
            if (!collisions.isEmpty()) {
                List<String> names = new ArrayList<>(collisions);
                Collections.sort(names);
                for (String entryName : names) {
                    String message = "File " + entryName + " appears in multiple sources:";
                    List<String> sources = sourceMap.get(entryName);
                    assert sources != null;
                    for (String source : sources) {
                        message = message + " " + source;
                    }
                    delegate.error(message);
                }
            }
        }
    }

    private void createDirectory(@NotNull File f)
    {
        try {
            Files.createDirectory(f.toPath());
        } catch (FileAlreadyExistsException ex) {
            // ignore
        } catch (IOException ex) {
            buildFailed("Unable to create directory: " + f.getPath(), ex);
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
      extends JARBuilder.Delegate
    {
    }
}
