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
import org.apache.tools.ant.taskdefs.Manifest;
import org.apache.tools.ant.taskdefs.ManifestException;
import org.apache.tools.ant.types.*;
import org.apache.tools.ant.types.resources.FileProvider;
import org.apache.tools.ant.types.resources.FileResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.collections.*;
import org.violetlib.vbuilder.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.*;

/**
  Build a Java-based macOS bundled applications and/or executable JAR.
  Basic and application JAR files may also be created.
  <p>
  Three options for bundled applications are supported:
  <ul>
  <li>
  Building an application for a single target architecture. By default, the application is built for the current
  execution environment.
  </li>
  <li>
  Building multiple applications, one for each target architecture.
  </li>
  <li>
  Building a single application that supports multiple target architectures.
  </li>
  </ul>
  <p>
  Note that a multi-architecture application requires a
  <a href="https://github.com/violetlib/mac-universal-java-launcher">custom launcher</a>
  that knows how to find the appropriate Java
  runtime for the execution architecture.
  <p>
  By default, bundled applications created by this task use the screen menu bar and the system appearance.
  Use of the system appearance is required when using the
  <a href="https://violetlib.org/vaqua/overview.html">VAqua</a> look and feel, but is probably inappropriate
  otherwise. Both defaults can be overridden using explicit {@code jvmArg} elements.

  @ant.task name="javaApplication"
*/

public class JavaApplicationTask
  extends Task
  implements Reporter
{
    /**
      This property specifies the application name to use if no name is specified in the task definition.
      @ant.prop
    */

    public static final @NotNull String APPNAME = "appname";

    /**
      This property specifies the default JDK runtime to install in an arm64 application if no arm64 JDK is specified in
      the task definition.
      @ant.prop type="File"
    */

    public static final @NotNull String JDK_ARM = "jdk_arm";

    /**
      This property specifies the default JDK runtime to install in an x86_64 application if no x86 JDK is specified in
      the task definition.
      @ant.prop type="File"
    */

    public static final @NotNull String JDK_X86 = "jdk_x86";

    /**
      This property specifies the JDK runtime to install in a multi-architecture application if no generic JDK is
      specified in the task definition.
      @ant.prop type="File"
    */

    public static final @NotNull String JDK = "jdk";

    /**
      This property specifies the application installation directory to use for arm64-specific bundled applications if
      no arm64 application installation directory is specified in the task definition.
      @ant.prop type="File"
    */

    public static final @NotNull String DIST_ARM = "dist_arm";

    /**
      This property specifies the application installation directory to use for x86-specific bundled applications if no
      x86 application installation directory is specified in the task definition.
      @ant.prop type="File"
    */

    public static final @NotNull String DIST_X86 = "dist_x86";

    /**
      This property specifies the application installation directory for bundled applications that support multiple
      architectures or single architecture bundled applications when no architecture-specific output directory is
      specified in the task definition or via a property.
      @ant.prop type="File"
    */

    public static final @NotNull String DIST = "dist";

    /**
      This property specifies the JAR installation directory to use if no valid JAR installation directory is specified
      in the task definition.
      @ant.prop type="File"
    */

    public static final @NotNull String JAR_DIST = "jardist";

    /**
      This property specifies a directory where intermediate work products should be written. If not defined, work
      products are stored in a directory named {@code out} in the build directory.
      @ant.prop type="File"
    */

    public static final @NotNull String WORK_DIR = "workdir";

    /**
      This property specifies a directory containing class files to include in the application, unless
      {@code includeStandardContents} is false.
      @ant.prop type="File"
    */

    public static final @NotNull String CLASSES_DIR = "classesdir";

    /**
      This property specifies the Java launcher to use in the bundled application if no launcher is specified in the
      task definition. If neither option is specified, the default {@code jpackage} launcher is used.
      @ant.prop type="File"
    */

    public static final @NotNull String JAVA_LAUNCHER = "javaLauncher";

    /**
      This property specifies a code signing key to use if no code signing key is specified in the task definition. If
      neither option is specified, code signing is not performed.
      @ant.prop
    */

    public static final @NotNull String CODE_SIGN_KEY = "codeSignKey";

    /**
      This property provides a default value for the similarly named attribute.
      @ant.prop type="boolean"
    */

    public static final @NotNull String UNIVERSAL_APPLICATION = "universalApplication";

    /**
      The ID of a resource collection whose contents are to be included in the application, unless
      {@code includeStandardContents} is false.
      @ant.ref type="ResourceCollection"
    */

    public static final @NotNull String JAR_RESOURCES = "jar.resources";

    /**
      The ID of a task collection to be run at the start of this task.
      @ant.ref type="TaskCollection"
    */

    public static final @NotNull String APPLICATION_DEFAULT_TASKS = "application.default.tasks";

    // This task uses JavaApplicationBuilder to do the actual building.
    // The job of this task is to gather data and construct a Configuration to be passed to
    // JavaApplicationBuilder.
    // This task need not validate inputs; JavaApplicationBuilder does that.

    private final @NotNull Architecture executionArchitecture;

    // Options that select which products to create and install
    protected boolean installBasicJar = true;  // defaults to true because the basic JAR is useful for dependency analysis
    protected boolean installExecutableJar = false;
    protected boolean installApplicationJar = true;
    protected boolean installApplication = true;

    protected @Nullable File appDist;
    protected @Nullable File appDist_x86;
    protected @Nullable File appDist_arm;

    protected final @NotNull List<File> appInstallLocations = new ArrayList<>();
    protected final @NotNull List<File> appInstallLocations_x86 = new ArrayList<>();
    protected final @NotNull List<File> appInstallLocations_arm = new ArrayList<>();

    protected @Nullable File outputRoot_x86;
    protected @Nullable File outputRoot_arm;

    protected boolean attachable = false;
    protected @Nullable String codeSigningKey;
    protected @Nullable String mainClassName;
    protected boolean includeStandardContents = true;
    protected @Nullable Boolean universalAppOption;
    protected @NotNull String applicationDescription = "";

    // A JDK is a bundle. It contains Contents/Home, which is the path to the JDK runtime image.
    // The JDK runtime image (also called JAVA_HOME) contains subdirectories bin and lib.

    // The jpackage program wants a runtime image, but as a convenience, it allows a JDK to be provided. It maps the
    // JDK path to the corresponding JDK runtime image path.

    // The runtime in a bundled app created by jpackage is a JDK (unfortunate naming). It is created from a JDK runtime
    // image, with the extra Contents created or installed by jpackage.

    protected @Nullable File jdk;
    protected @Nullable File jdk_x86;
    protected @Nullable File jdk_arm;

    protected @Nullable ISet<Architecture> requestedTargetArchitectures;
    protected @Nullable File jarDist;
    protected @Nullable File basicManifestFile;
    protected @Nullable File appManifestFile;
    protected @Nullable File launcher;
    protected @Nullable String applicationName;
    protected @NotNull String applicationSignature = "????";
    protected @Nullable String splashFile;
    protected @Nullable File infoPlist;
    protected @Nullable File toolDist;
    protected @NotNull String vmSize = "512m";

    protected @Nullable MavenVersionManagement mm;

    protected final @NotNull List<FileSet> resources = new ArrayList<>();
    protected final @NotNull List<ResourceCollection> jars = new ArrayList<>();
    protected final @NotNull List<String> jvmArgs = new ArrayList<>();
    protected final @NotNull List<String> appArgs = new ArrayList<>();
    protected final @NotNull List<ClassPath> classPaths = new ArrayList<>();
    protected final @NotNull List<ResourceCollection> nativeLibraries = new ArrayList<>();
    protected final @NotNull List<ResourceCollection> nativeFrameworks = new ArrayList<>();

    public JavaApplicationTask()
    {
        String executionArchitectureProperty = System.getProperty("os.arch");
        executionArchitecture = executionArchitectureProperty.contains("x86") ? Architecture.Intel : Architecture.ARM;
    }

    /**
      Specify an additional install location for the bundled application. This location is used for applications
      that support multiple target architectures and for applications created using the default target architecture.
      @ant.type name="appInstall"
    */

    public void addConfiguredAppInstall(@NotNull FileResource file)
    {
        File f = file.getFile();
        if (f != null) {
            appInstallLocations.add(f);
        }
    }

    /**
      Specify an additional install location for an arm64 bundled application.
      @ant.type name="appInstallArm"
    */

    public void addConfiguredAppInstallArm(@NotNull FileResource file)
    {
        File f = file.getFile();
        if (f != null) {
            appInstallLocations_arm.add(f);
        }
    }

    /**
      Specify an additional install location for an x86 bundled application.
      @ant.type name="appInstallIntel"
    */

    public void addConfiguredAppInstallIntel(@NotNull FileResource file)
    {
        File f = file.getFile();
        if (f != null) {
            appInstallLocations_x86.add(f);
        }
    }

    /**
      Specify a command line argument to configure the Java virtual machine.
      @ant.type name="jvmArg"
    */

    public void addConfiguredJvmArg(@NotNull Commandline.Argument arg)
    {
        String[] parts = arg.getParts();
        if (parts.length > 0) {
            Collections.addAll(jvmArgs, parts);
        } else {
            error("Missing JVM argument");
        }
    }

    /**
      Specify a command line argument to pass to the application.
      @ant.type name="arg"
    */

    public void addConfiguredArg(@NotNull Commandline.Argument arg)
    {
        String[] parts = arg.getParts();
        if (parts.length > 0) {
            Collections.addAll(appArgs, parts);
        } else {
            error("Missing argument");
        }
    }

    /**
      Specify files to be included in the application. The files may include native libraries.
    */

    public void addResource(@NotNull FileSet fs)
    {
        resources.add(fs);
    }

    /**
      Specify class trees and JARs required by the library for compilation and/or execution.
    */

    public void addConfigured(@NotNull ClassPath cp)
    {
        classPaths.add(cp);
    }

    /**
      Specify JARs required by the library for execution.
    */

    public void addConfiguredJar(@NotNull Path path)
    {
        jars.add(path);
    }

    /**
      Specify native libraries required by the library for execution.
    */

    public void addConfiguredNativeLibrary(@NotNull Path path)
    {
        nativeLibraries.add(path);
    }

    /**
      Specify Frameworks required by the library for execution.
    */

    public void addConfiguredFramework(@NotNull Path path)
    {
        nativeFrameworks.add(path);
    }

    /**
      Specify the fully qualified name of the application main class.
      @ant.required
    */

    public void setMainClass(@NotNull String className)
    {
        this.mainClassName = className;
    }

    /**
      Specify the name of the generated application JAR, without a suffix.
      <em>This attribute is unsupported.</em>
      @ant.optional
    */

    public void setApplicationJarFileName(@NotNull String fn)
    {
        throw new BuildException("Application JAR file name is unsupported");
    }

    /**
      Specify whether the application permits being attached to a debugger.
      @ant.optional Defaults to false.
    */

    public void setAttachable(boolean b)
    {
        this.attachable = b;
    }

    /**
      Specify the key to use for signing code resources.
      @ant.optional
    */

    public void setCodeSigningKey(@NotNull String s)
    {
        this.codeSigningKey = s;
    }

    /**
      Specify the directory to install the application. See the description of the {@code dist} property for details.
      @ant.optional
    */

    public void setDist(@NotNull File dir)
    {
        this.appDist = dir;
    }

    /**
      Specify the directory to install an x86 application. See the description of the {@code dist_x86} property for
      details.
      @ant.optional
    */

    public void setDist_x86(@NotNull File dir)
    {
        this.appDist_x86 = dir;
    }

    /**
      Specify the directory to install an arm64 application. See the description of the {@code dist_arm} property for
      details.
      @ant.optional
    */

    public void setDist_arm(@NotNull File dir)
    {
        this.appDist_arm = dir;
    }

    /**
      Specify the location of a JDK to install in the application. See the description of the {@code jdk} property for
      details.
      @ant.optional
    */

    public void setJdk(@NotNull File f)
    {
        jdk = f;
    }

    /**
      Specify the location of a JDK to install in an x86 application. See the description of the {@code jdk_x86}
      property for details.
      @ant.optional
    */

    public void setJdk_x86(@NotNull File f)
    {
        jdk_x86 = f;
    }

    /**
      Specify the location of a JDK to install in an arm64 application. See the description of the {@code jdk_arm}
      property for details.
      @ant.optional
    */

    public void setJdk_arm(@NotNull File f)
    {
        jdk_arm = f;
    }

    /**
      Specify whether standard contents should be installed in the application.
      The contents are defined by a {@code ResourceCollection} whose ID is {@code jar.resources}.

      @ant.optional Defaults to true.
    */

    public void setIncludeStandardContents(boolean b)
    {
        this.includeStandardContents = b;
    }

    /**
      Specify whether to build a universal (x86 and arm64) application.
      Note that a multi-architecture application requires a custom launcher that knows how to find the appropriate Java
      runtime for the execution architecture.
      @ant.optional Defaults to the value of the {@code universalApplication} property,
      or to false if the property is not defined.
    */

    public void setUniversalApplication(boolean b) {
        this.universalAppOption = b;
    }

    /**
      Specify the target architecture(s) for the application.
      Note that a multi-architecture application requires a custom launcher that knows how to find the appropriate Java
      runtime for the execution architecture.
      @ant.optional Defaults to the execution architecture, unless a universal application is requested.
    */

    public void setArch(@NotNull String s)
    {
        Set<Architecture> as = new HashSet<>();
        StringTokenizer st = new StringTokenizer(s, " ,");
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            Architecture a = ArchitectureUtils.parseArchitecture(token);
            if (a == null) {
                log("Unrecognized target architecture: " + token, Project.MSG_WARN);
            } else if (as.contains(a)) {
                log("Duplicate target architecture: " + token, Project.MSG_WARN);
            } else {
                as.add(a);
            }
        }
        requestedTargetArchitectures = ISet.create(as);
    }

    /**
      Specify a short description of the application to include in the bundled application property list.
      @ant.optional
    */

    public void setDescription(@NotNull String description)
    {
        this.applicationDescription = description;
    }

    /**
      Specify whether to create a bundled application.
      @ant.optional Defaults to true.
    */

    public void setInstallApplication(boolean b)
    {
        this.installApplication = b;
    }

    /**
      Specify whether to create a basic JAR. The basic JAR contains the class trees and resources.
      @ant.optional Defaults to true.
    */

    public void setInstallBasicJar(boolean b)
    {
        this.installBasicJar = b;
    }

    /**
      Specify whether to create an executable JAR. The executable JAR contains the class trees, the resources, the
      required JAR files and dynamic libraries, and a Manifest that identifies the main class.
      @ant.optional Defaults to false.
    */

    public void setInstallExecutableJar(boolean b)
    {
        this.installExecutableJar = b;
    }

    /**
      Specify whether to create an application JAR.
      The application JAR contains the class trees, the resources, and the required JAR files.
      The application JAR is not executable, but it is appropriate for inclusion in a bundled application.
      @ant.optional Defaults to true.
    */

    public void setInstallApplicationJar(boolean b)
    {
        this.installApplicationJar = b;
    }

    /**
      Specify the directory where the basic JAR file should be installed.
      @ant.optional If not specified, the value of the {@code jardist} property will be used.
      If no directory is specified or found, the basic JAR file will be installed in the {@code jars} subdirectory
      of the build directory.
    */

    public void setJarDist(@NotNull File dir)
    {
        this.jarDist = dir;
    }

    /**
      Specify a manifest file to include in the basic JAR.
      @ant.optional If not specified, the basic JAR will not include a manifest.
    */

    public void setBasicJarManifest(@NotNull File manifestFile)
    {
        this.basicManifestFile = manifestFile;
    }

    /**
      Specify a manifest file to include in the application JAR or executable JAR.
      @ant.optional If not specified, a simple manifest will be created.
    */

    public void setManifest(@NotNull File manifestFile)
    {
        this.appManifestFile = manifestFile;
    }

    /**
      Specify the launcher program to include in the bundled application. Note that a multi-architecture
      application requires a custom launcher that knows how to find the appropriate
      Java runtime for the execution architecture.
      @ant.optional If not specified, the default {@code jpackage} launcher is used.
    */

    public void setLauncher(@NotNull File f)
    {
        this.launcher = f;
    }

    /**
      Specify the application name. This name is stored in the bundled application and used when creating the name of
      the application bundle and other artifacts. It is displayed in the screen menu bar when the application is
      active. The name should be short. It must not be blank or contain a slash.
      @ant.optional If not specified, the value of the {@code appname} property is used.
      If neither is default, the ant project name is used.
    */

    public void setApplicationName(@NotNull String applicationName)
    {
        this.applicationName = applicationName;
    }

    /**
      Specify the application signature to include in a generated property list for the application.
      <em>This attribute is currently unsupported.</em>
      @ant.optional
    */

    public void setApplicationSignature(@NotNull String applicationSignature)
    {
        this.applicationSignature = applicationSignature;
    }

    /**
      Specify the (name) of an image to display while the bundled application is starting up.
      @ant.optional If not specified, no splash screen will be automatically displayed for the application.
    */

    public void setSplashFile(@NotNull String splashFile)
    {
        this.splashFile = splashFile;
    }

    /**
      Specify an info.plist file to include in the bundled application.
      @ant.optional If not specified, a file named {@code package/macos/Info.plist} in the build
      directory will be used. If no file is specified or found, a default info.plist file is created.
    */

    public void setInfoPlist(@NotNull File f)
    {
        this.infoPlist = f;
    }


    /**
      Specify the directory where an executable JAR should be installed, if one is requested.
      @ant.optional If not specified and an executable JAR is requested, it will be installed in the build
      directory in the {@code jars} subdirectory.
    */

    public void setToolDist(@NotNull File dir)
    {
        this.toolDist = dir;
    }

    /**
      Specify the virtual memory size for the bundled application.
      @ant.optional If not specified, a default size is used.
    */

    public void setVMSize(@NotNull String vmSize)
    {
        this.vmSize = vmSize;
    }

    @Override
    public void execute()
    {
        AntUtils.init();

        Project p = getProject();
        String applicationName = getApplicationName();
        String mainClassName = getMainClassName();

        mm = AntMavenVersionManagement.get(p, new AntBuildDelegate(p));

        installDefaults();

        mm.logPreferredVersions(ProjectReporter.create(p));

        TaskCollection th = p.getReference(APPLICATION_DEFAULT_TASKS);
        if (th != null) {
            IList<Task> tasks = th.getTasks();
            for (Task t : tasks) {
                t.perform();
            }
        }

        JavaApplicationBuilder.Configuration g = createConfiguration(applicationName, mainClassName);
        showParameters(g);
        JavaApplicationBuilder.Delegate delegate = JavaApplicationBuilderAnt.create(p);
        try {
            JavaApplicationBuilder.createApplication(g, delegate);
        } catch (org.violetlib.vbuilder.BuildException e) {
            throw new BuildException(e);
        }
    }

    private void installDefaults()
    {
        if (classPaths.isEmpty()) {
            Project p = getProject();
            ClassPath cp = p.getReference("cp");
            if (cp != null) {
                info("Using default classpath to find class trees");
                classPaths.add(cp);
            }
        }
    }

    private @NotNull JavaApplicationBuilder.Configuration createConfiguration(@NotNull String applicationName,
                                                                              @NotNull String mainClassName)
    {
        File baseDir = getBaseDir();

        // First figure out which architectures are desired and supported.

        IMap<Architecture,ArchitectureConfiguration> allArchitectureConfigs = getArchConfigs(baseDir);
        ISet<Architecture> requestedArchitectures = getBuildArchitectures(allArchitectureConfigs.keySet());
        IMap<Architecture,ArchitectureConfiguration> architectures = allArchitectureConfigs.subset(requestedArchitectures);

        ISet<File> classTrees = getClassTrees();
        ISet<File> jars = getJarFiles();
        File infoPlistFile = getInfoPlistFile(baseDir);
        File iconsFile = getApplicationIconsFile(baseDir);
        File appResourcesDir = getApplicationResourcesDir(baseDir);
        File appContentsDir = getApplicationContentsDir(baseDir);
        File buildRoot = getBuildRoot(baseDir);
        boolean shouldBuildApplication = getShouldBuildApplication();
        boolean shouldCreateUniversalApplication = getShouldCreateUniversalApplication();
        IList<File> universalAppInstallLocations = getAppInstallDirs(null);
        File launcher = getSpecifiedLauncher();
        File basicJarDir = getBasicJarDir(buildRoot);
        File executableJarDir = getExecutableJarDir(buildRoot);
        File applicationJarDir = getApplicationJarDir(buildRoot);
        File basicManifestFile = getBasicManifestFile();
        Object applicationManifest = getApplicationManifest();
        ISet<Object> resources = getResources(baseDir);
        IList<String> javaOptions = getJavaOptions();
        IList<String> appArgs = getAppArgs();
        ISet<NativeLibrary> nativeLibraries = getNativeLibraries();
        ISet<NativeFramework> nativeFrameworks = getNativeFrameworks();
        File libOutputDirectory = getLibraryOutputDir(buildRoot);
        String codeSigningKey = getCodeSigningKey();
        return JavaApplicationBuilder.createConfiguration(
          applicationName,
          mainClassName,
          classTrees,
          resources,
          jars,
          infoPlistFile,
          applicationDescription,
          iconsFile,
          appResourcesDir,
          appContentsDir,
          buildRoot,
          shouldBuildApplication,
          shouldCreateUniversalApplication,
          universalAppInstallLocations,
          launcher,
          basicJarDir,
          basicManifestFile,
          executableJarDir,
          applicationJarDir,
          applicationManifest,
          javaOptions,
          appArgs,
          architectures,
          nativeLibraries,
          nativeFrameworks,
          libOutputDirectory,
          codeSigningKey
        );
    }

    private @NotNull String getApplicationName()
    {
        if (applicationName == null) {
            applicationName = getProperty(APPNAME);
            if (applicationName == null) {
                applicationName = getProperty("ant.project.name");
            }
        }

        if (applicationName == null) {
            throw new BuildException("An application name must be specified.");
        }

        return applicationName;
    }

    private void showParameters(@NotNull JavaApplicationBuilder.Configuration g)
    {
        if (g.shouldBuildApplication) {
            if (g.shouldCreateUniversalApplication) {
                info("Building universal Java application: " + g.applicationName);
            } else {
                info("Building Java application: " + g.applicationName);
            }
        }

        for (Architecture arch : g.archConfigs.keySet()) {
            ArchitectureConfiguration a = g.archConfigs.get(arch);
            assert a != null;
            info("  JDK [" + arch.getName() + "]: " + a.jdkRuntime);
        }

        if (g.basicJarDir != null) {
            info("  Basic jar installation directory: " + g.basicJarDir);
        }
        if (g.executableJarDir != null) {
            info("  Executable jar installation directory: " + g.executableJarDir);
        }
        if (g.applicationJarDir != null) {
            info("  Application jar installation directory: " + g.applicationJarDir);
        }

        info("  Main class: " + g.mainClassName);
        info("  VM size: " + vmSize);
        info("  Application signature: " + applicationSignature);

        if (splashFile != null) {
            info("  Splash file: " + splashFile);
        }

        if (g.codeSigningKey != null) {
            info("  Code signing key: " + g.codeSigningKey);
        }
    }

    private @NotNull IMap<Architecture,ArchitectureConfiguration> getArchConfigs(@NotNull File base)
    {
        MapBuilder<Architecture,ArchitectureConfiguration> b = IMap.builder();
        ArchitectureConfiguration x86 = getConfiguration(base, Architecture.Intel);
        ArchitectureConfiguration arm = getConfiguration(base, Architecture.ARM);
        b.putOptional(Architecture.Intel, x86);
        b.putOptional(Architecture.ARM, arm);
        return b.value();
    }

    private @Nullable ArchitectureConfiguration getConfiguration(@NotNull File base, @NotNull Architecture arch)
    {
        File jdk = getJavaRuntimeForArch(arch);
        if (jdk != null) {
            File outputRoot = getOutputRootForArch(arch, base);
            IList<File> appInstallDirs = getAppInstallDirs(arch);
            return ArchitectureConfiguration.create(outputRoot, appInstallDirs, jdk);
        } else {
            error("Did not find Java runtime for " + arch.getName());
            return null;
        }
    }

    private @Nullable File getJavaRuntimeForArch(@NotNull Architecture arch)
    {
        File jdk = getDefaultJdk(arch);
        if (jdk == null) {
            File defaultJDK = this.jdk;
            if (defaultJDK != null) {
                try {
                    ISet<Architecture> archs = Utils.getJavaRuntimeArchitectures(defaultJDK);
                    if (archs.contains(arch)) {
                        return defaultJDK;
                    }
                } catch (IOException ignore) {
                }
            }
        }
        return jdk;
    }

    private @NotNull File getOutputRootForArch(@NotNull Architecture arch, @NotNull File base)
    {
        if (arch == Architecture.Intel) {
            return getOutputRoot_x86(base);
        }
        if (arch == Architecture.ARM) {
            return getOutputRoot_arm(base);
        }
        throw new UnsupportedOperationException("Unknown architecture: " + arch.getName());
    }

    private @NotNull IList<File> getAppInstallDirs(@Nullable Architecture arch)
    {
        ListBuilder<File> b = IList.builder();
        b.addOptional(getPrimaryAppInstallDir(arch));
        for (File f : getExtraApplicationInstallDirs(arch)) {
            b.addOptional(validateOrCreateDirectory(f, "application installation directory"));
        }
        return b.values();
    }

    private @Nullable File getPrimaryAppInstallDir(@Nullable Architecture arch)
    {
        File sf = getSpecifiedAppInstallDir(arch);
        if (sf != null) {
            return sf;
        }

        String prop = getPrimaryAppInstallDirProperty(arch);
        if (prop == null) {
            return null;
        }
        String s = getProperty(prop);
        if (s != null) {
            File f = new File(s);
            return validateOrCreateDirectory(f, "application installation directory");
        }

        return null;
    }

    private @Nullable String getPrimaryAppInstallDirProperty(@Nullable Architecture arch)
    {
        if (arch == Architecture.Intel) {
            return DIST_X86;
        }
        if (arch == Architecture.ARM) {
            return DIST_ARM;
        }
        if (arch == null) {
            return DIST;
        }
        return null;
    }

    private @Nullable File getSpecifiedAppInstallDir(@Nullable Architecture arch)
    {
        File f = null;
        if (arch == Architecture.Intel) {
            f = appDist_x86;
        } else if (arch == Architecture.ARM) {
            f = appDist_arm;
        }
        if (f == null) {
            f = appDist;
        }
        if (f != null && !Files.isDirectory(f.toPath())) {
            error("Invalid specified install directory: " + f);
            f = null;
        }
        return f;
    }

    private @NotNull IList<File> getExtraApplicationInstallDirs(@Nullable Architecture arch)
    {
        IList<File> files;

        if (arch == Architecture.Intel) {
            files = IList.create(appInstallLocations_x86)
              .appendingAll(getFileProperty(DIST_X86, "application installation directory"));
        } else if (arch == Architecture.ARM) {
            files = IList.create(appInstallLocations_arm)
              .appendingAll(getFileProperty(DIST_ARM, "application installation directory"));
        } else if (arch == null) {
            files = IList.create(appInstallLocations);
        } else {
            files = IList.empty();
        }

        ListBuilder<File> b = IList.builder();
        for (File f : files) {
            b.addOptional(validateOrCreateDirectory(f, "application installation directory"));
        }
        return b.values();
    }

    private @NotNull IList<File> getFileProperty(@NotNull String prop, @NotNull String description)
    {
        String s = getProperty(prop);
        if (s != null && !s.isEmpty()) {
            File dir = new File(s);
            File f = validateOrCreateDirectory(dir, description);
            return IList.of(f);
        }
        return IList.empty();
    }

    private @NotNull ISet<Object> getResources(@NotNull File base)
    {
        SetBuilder<Object> b = ISet.builder();
        b.addAll(resources);
        addDefaultResources(b);
        return b.values();
    }

    private void addDefaultResources(@NotNull SetBuilder<Object> b)
    {
        if (includeStandardContents) {
            Project p = getProject();
            if (p != null) {
                Object fs = p.getReference(JAR_RESOURCES);
                if (fs == null) {
                    info("Resources " + JAR_RESOURCES + " is undefined");
                } else {
                    info("Resources " + JAR_RESOURCES + " type is " + fs.getClass().getSimpleName());
                }
                b.addOptional(fs);
            }
        }
    }

    private @NotNull String getMainClassName()
    {
        if (mainClassName == null) {
            throw new BuildException("A main class name must be specified.");
        }
        return mainClassName;
    }

    private @NotNull File getBuildRoot(@NotNull File base)
    {
        {
            String s = getProperty(WORK_DIR);
            if (s != null) {
                File f = new File(s);
                return validateBuildRoot(f);
            }
        }

        File f = new File(base, "out");
        return validateBuildRoot(f);
    }

    private @NotNull File validateBuildRoot(@NotNull File f)
    {
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

    private @NotNull File getJarDist(@NotNull File buildRoot)
    {
        String description = "jar installation directory";

        if (jarDist != null) {
            File dir = validateOrCreateDirectory(jarDist, description);
            if (dir != null) {
                return dir;
            }
        }

        String s = getProperty(JAR_DIST);
        if (s != null && !s.isEmpty()) {
            File dir = new File(s);
            return validateOrCreateDirectory(dir, description);
        }

        return validateOrCreateDirectory(new File(buildRoot, "jars"), "basic JAR output directory");
    }

    private @Nullable File getDefaultJdk(@Nullable Architecture arch)
    {
        String prop = arch == Architecture.ARM ? JDK_ARM : (arch == Architecture.Intel ? JDK_X86 : JDK);
        String s = getProperty(prop);
        if (s != null && !s.isEmpty()) {
            return new File(s);
        }
        return null;
    }

    private boolean getShouldBuildApplication()
    {
        return installApplication;
    }

    private boolean getShouldCreateUniversalApplication()
    {
        return getCalculatedUniversalApplicationOption(true);
    }

    private boolean getCalculatedUniversalApplicationOption(boolean defaultValue)
    {
        if (universalAppOption != null) {
            return universalAppOption;
        }
        Boolean b = getBooleanProperty(UNIVERSAL_APPLICATION);
        if (b != null) {
            return b;
        }
        return defaultValue;
    }

    private @Nullable Boolean getBooleanProperty(@NotNull String name)
    {
        String s = getProperty(name);
        if (s != null && !s.isEmpty()) {
            s = s.toLowerCase();
            if (s.equals("true")) {
                return true;
            }
            if (s.equals("false")) {
                return false;
            }
            throw new BuildException("Invalid value for boolean property: " + name);
        }
        return null;
    }

    private @NotNull File validateOrCreateDirectory(@NotNull File dir, @NotNull String description)
    {
        if (Files.isDirectory(dir.toPath())) {
            return dir;
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

    private @NotNull File getOutputRoot_arm(@NotNull File base)
    {
        if (outputRoot_arm == null) {
            String s = getProperty(WORK_DIR);
            if (s != null) {
                outputRoot_arm = new File(s, "arm");
            } else {
                outputRoot_arm = new File(base, "out_arm");
            }
        }

        if (outputRoot_arm.isDirectory()) {
            return outputRoot_arm;
        }
        if (Files.exists(outputRoot_arm.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new BuildException("Output directory already exists and cannot be created: " + outputRoot_arm.getPath());
        }
        try {
            Files.createDirectories(outputRoot_arm.toPath());
        } catch (IOException ex) {
            throw new BuildException("Unable to create output directory: " + outputRoot_arm.getPath() + ", " + ex);
        }
        return outputRoot_arm;
    }

    private @NotNull File getOutputRoot_x86(@NotNull File base)
    {
        if (outputRoot_x86 == null) {
            String s = getProperty(WORK_DIR);
            if (s != null) {
                outputRoot_x86 = new File(s, "x86");
            } else {
                outputRoot_x86 = new File(base, "out_x86");
            }
        }

        if (outputRoot_x86.isDirectory()) {
            return outputRoot_x86;
        }
        if (Files.exists(outputRoot_x86.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new BuildException("Output directory already exists and cannot be created: " + outputRoot_x86.getPath());
        }
        try {
            Files.createDirectories(outputRoot_x86.toPath());
        } catch (IOException ex) {
            throw new BuildException("Unable to create output directory: " + outputRoot_x86.getPath() + ", " + ex);
        }
        return outputRoot_x86;
    }

    private @NotNull File getLibraryOutputDir(@NotNull File root)
    {
        File libDirectory = new File(root, "lib");
        if (Files.exists(libDirectory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isDirectory(libDirectory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                return libDirectory;
            }
            throw new BuildException("Library output directory already exists and cannot be created: " + libDirectory.getPath());
        }
        try {
            Files.createDirectories(libDirectory.toPath());
        } catch (IOException ex) {
            throw new BuildException("Unable to create library output directory: " + libDirectory.getPath() + ", " + ex);
        }
        return libDirectory;
    }

    private @NotNull File getBaseDir()
    {
        Project p = getProject();
        if (p != null) {
            File dir = p.getBaseDir();
            if (dir != null) {
                if (dir.isDirectory()) {
                    return dir;
                }
                throw new BuildException("Unable to find project base directory: " + dir.getPath());
            }
        }
        throw new BuildException("The project base directory is undefined");
    }

    private @Nullable File getBasicJarDir(@NotNull File buildRoot)
    {
        if (installBasicJar) {
            return getJarDist(buildRoot);
        }
        return null;
    }

    private @Nullable File getExecutableJarDir(@NotNull File buildRoot)
    {
        if (installExecutableJar) {
            if (toolDist != null) {
                return validateOrCreateDirectory(toolDist, "executable JAR output directory");
            }
            return validateOrCreateDirectory(new File(buildRoot, "jars"), "executable JAR output directory");
        }
        return null;
    }

    private @Nullable File getApplicationJarDir(@NotNull File buildRoot)
    {
        if (installApplicationJar) {
            return validateOrCreateDirectory(new File(buildRoot, "jars"), "application JAR output directory");
        }
        return null;
    }

    private @Nullable File getBasicManifestFile()
    {
        return basicManifestFile;
    }

    private @Nullable Object getApplicationManifest()
    {
        if (appManifestFile != null) {
            if (Files.isRegularFile(appManifestFile.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                return appManifestFile;
            } else {
                throw new BuildException("Application manifest file not found: " + appManifestFile.getPath());
            }
        } else {
            // Create a default manifest for an executable JAR
            Manifest manifest = new Manifest();
            try {
                manifest.addConfiguredAttribute(new Manifest.Attribute("Main-Class", mainClassName));
                return manifest;
            } catch (ManifestException ex) {
                throw new AssertionError("Unexpected exception", ex);
            }
        }
    }

    private @Nullable File getSpecifiedLauncher()
    {
        if (launcher != null) {
            if (Files.isRegularFile(launcher.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                return launcher;
            }
            error("Specified Java launcher not found: " + launcher);
        }

        String s = getProperty(JAVA_LAUNCHER);
        if (s != null && !s.isEmpty()) {
            File f = new File(s);
            if (Files.isRegularFile(f.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                return f;
            }
            error("Specified Java launcher not found: " + s);
        }

        return null;
    }

    /**
      Return the architectures to be supported by the built application.
      @param possible The architectures that are available.
    */

    private @NotNull ISet<Architecture> getBuildArchitectures(ISet<Architecture> possible)
    {
        if (requestedTargetArchitectures == null || requestedTargetArchitectures.isEmpty()) {
            // If no architecture is specified, build for the execution architecture unless a universal
            // application is requested.
            if (getShouldCreateUniversalApplication()) {
                return possible;
            }
            return ISet.of(executionArchitecture);
        }
        for (Architecture a : requestedTargetArchitectures) {
            if (!possible.contains(a)) {
                throw new BuildException("Requested target architecture " + a + " is unsupported");
            }
        }
        return requestedTargetArchitectures;
    }

    private @NotNull String getVMSize()
    {
        return vmSize;
    }

    private @NotNull IList<String> getJavaOptions()
    {
        ListBuilder<String> b = IList.builder();

        String vmSize = getVMSize();
        b.add("-Xmx" + vmSize);

        // TBD
        b.add("-Xdock:icon=Contents/Resources/" + applicationName + ".icns");

        if (splashFile != null) {
            b.add("-splash:" + splashFile);
        }

        // TBD: splashFile should be an Application Resource

        b.add("-Dapple.awt.application.name=" + applicationName);
        b.add("-Dapple.laf.useScreenMenuBar=true");
        b.add("-Dapple.awt.application.appearance=system");
        if (attachable) {
            b.add("-agentlib:jdwp=transport=dt_socket,suspend=n,server=y,address=5009");
        }
//        if (libDir != null && !nativeLibraryNames.isEmpty()) {
//            String libraryPath = createLibraryPath(nativeLibraryNames);
//            b.add("-Djava.library.path=" + libraryPath);
//        }

        for (String arg : jvmArgs) {
            b.add(arg);
        }

        // TBD: have a general way to supply global JVM properties
        String umt = getProperty("sun.awt.macos.useMainThread");
        if (umt != null) {
            b.add("-Dsun.awt.macos.useMainThread=" + umt);
        }
        return b.values();
    }

    private @NotNull IList<String> getAppArgs()
    {
        return IList.create(appArgs);
    }

    private @NotNull ISet<NativeLibrary> getNativeLibraries()
    {
        SetBuilder<NativeLibrary> b = ISet.builder();
        for (ResourceCollection rc : nativeLibraries) {
            ISet<NativeLibrary> nls
              = AntUtils.getResourceCollectionFilesMapped(rc, this::toNativeLibrary);
            if (nls != null) {
                b.addAll(nls);
            } else {
                throw new BuildException("Native library element must specify files");
            }
        }
        return b.values();
    }

    private @Nullable NativeLibrary toNativeLibrary(@NotNull File f)
    {
        if (Files.isRegularFile(f.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            try {
                return NativeLibrarySupport.createForFile(f, this);
            } catch (org.violetlib.vbuilder.BuildException e) {
                error(e.getMessage());
                return null;
            }
        } else {
            throw new BuildException("Native library file not found: " + f);
        }
    }

    private @NotNull ISet<NativeFramework> getNativeFrameworks()
    {
        SetBuilder<NativeFramework> b = ISet.builder();
        for (ResourceCollection rc : nativeFrameworks) {
            ISet<NativeFramework> nfs
              = AntUtils.getResourceCollectionFilesMapped(rc, this::toNativeFramework);
            if (nfs != null) {
                b.addAll(nfs);
            } else {
                throw new BuildException("Native framework element must specify files");
            }
        }
        return b.values();
    }

    private @NotNull NativeFramework toNativeFramework(@NotNull File f)
    {
        String name = f.getName();
        if (Files.isDirectory(f.toPath()) && name.endsWith(".framework")) {
            return NativeFrameworkImpl.createFramework(f);
        } else {
            throw new BuildException("Native framework must be a directory with .framework suffix: " + f);
        }
    }

    private @NotNull ISet<File> getClassTrees()
    {
        SetBuilder<File> b = ISet.builder();
        addDefaultClasspath(b);
        return b.values();
    }

    private @NotNull ISet<File> getJarFiles()
    {
        SetBuilder<File> b = ISet.builder();

        Dependencies deps = new Dependencies();

        for (ClassPath rc : classPaths) {
            ClassPath.Output o = rc.execute(true, IList.empty(), mm);
            for (File f : o.included) {
                if (isJarFile(f)) {
                    b.add(f);
                }
            }
            for (File f : o.runtime) {
                if (isJarFile(f)) {
                    b.add(f);
                }
            }
            for (File f : o.required) {
                if (isJarFile(f)) {
                    b.add(f);
                }
            }
            deps.addDependencies(o.dependencies);
        }

        for (ResourceCollection rc : jars) {
            ISet<File> files = AntUtils.getResourceCollectionFiles(rc, this::isJarFile);
            if (files != null && !files.isEmpty()) {
                b.addAll(files);
            } else {
                throw new BuildException("Jar element must specify files");
            }
        }

        IList<File> jars = IList.create(b.values());
        DependencySupport.Configuration dg
          = DependencySupport.createConfiguration(IList.empty(), jars, deps, mm);
        DependencySupport.Result r = DependencySupport.compute(getProject(), dg);
        Path p = r.runtimePath;
        IList<File> files = AntUtils.getPathFiles(p, this::isJarFile);
        if (files != null) {
            b.addAll(files);
        }
        return b.values();
    }

    private boolean isJarFile(@NotNull File f)
    {
        return Files.isRegularFile(f.toPath()) && f.getName().endsWith(".jar");
    }

    private void addDefaultClasspath(@NotNull SetBuilder<File> b)
    {
        if (includeStandardContents) {
            String classesDirOption = getProperty(CLASSES_DIR);
            if (classesDirOption != null) {
                Project p = getProject();
                if (p != null) {
                    Path cp = new Path(p, classesDirOption);
                    addPath(cp, b);
                }
            }
        }
    }

    private void addPath(@NotNull Path p, @NotNull SetBuilder<File> b)
    {
        if (p.isFilesystemOnly()) {
            for (Resource r : p) {
                FileProvider fp = r.as(FileProvider.class);
                assert fp != null;
                File f = fp.getFile();
                if (Files.isDirectory(f.toPath())) {
                    b.add(f);
                }
            }
        } else {
            throw new BuildException("Classpath or jar element must specify files or directories");
        }
    }

    private void addJar(@NotNull Path p, @NotNull SetBuilder<File> b)
    {
        if (p.isFilesystemOnly()) {
            for (Resource r : p) {
                FileProvider fp = r.as(FileProvider.class);
                assert fp != null;
                File f = fp.getFile();
                if (Files.isRegularFile(f.toPath())) {
                    b.add(f);
                }
            }
        } else {
            throw new BuildException("Classpath or jar element must specify files or directories");
        }
    }

    protected @Nullable File getInfoPlistFile(@NotNull File base)
    {
        if (infoPlist != null && Files.isRegularFile(infoPlist.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return infoPlist;
        }

        File plist = new File(base, "package/macos/Info.plist");
        if (Files.isRegularFile(plist.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return plist;
        }
        plist = new File(base, "package/macosx/Info.plist");
        if (Files.isRegularFile(plist.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return plist;
        }
        return null;
    }

    protected @Nullable File getApplicationResourcesDir(@NotNull File base)
    {
        File f = new File(base, "resources_macosx");
        if (Files.isDirectory(f.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return f;
        }
        f = new File(base, "resources_macos");
        if (Files.isDirectory(f.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return f;
        }
        return null;
    }

    protected @Nullable File getApplicationContentsDir(@NotNull File base)
    {
        File f = new File(base, "bundle_macosx");
        if (Files.isDirectory(f.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return f;
        }
        f = new File(base, "bundle_macos");
        if (Files.isDirectory(f.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return f;
        }
        return null;
    }

    protected @Nullable File getApplicationIconsFile(@NotNull File base)
    {
        String name = applicationName + ".icns";
        File f = new File(base, "package/macosx/" + name);
        if (Files.isRegularFile(f.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return f;
        }
        f = new File(base, "package/macos/" + name);
        if (Files.isRegularFile(f.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return f;
        }
        return null;
    }

    protected @Nullable String getCodeSigningKey()
    {
        if (codeSigningKey != null && !codeSigningKey.isEmpty()) {
            return codeSigningKey;
        }

        String s = getProperty(CODE_SIGN_KEY);
        if (s != null && !s.isEmpty()) {
            return s;
        }

        return s;
    }

    private @Nullable String getProperty(@NotNull String propertyName)
    {
        Project p = getProject();
        return p != null ? p.getProperty(propertyName) : null;
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
}
