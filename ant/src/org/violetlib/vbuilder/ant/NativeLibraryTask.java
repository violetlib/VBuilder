package org.violetlib.vbuilder.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.*;
import org.apache.tools.ant.types.resources.FileResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.collections.*;
import org.violetlib.vbuilder.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
  TBD:

  Could use a better way of handling default options, as currently there is no way to suppress them.
*/

/**
  Build a JNI-based native library.
  The native library is created by compiling a designated set of C[-compatible] source files and linking them together.

  <p>
  This task is currently specific to macOS. It generates a universal library, if required.

  @ant.task name="jni"

  <p>
  It would be nice to invoke the compiler just once to do all the compiling and linking, and it is almost possible to
  do so. The hangup is (1) not all architectures are supported in all SDK versions, and (2) the technique for
  specifying an architecture-specific compiler option does not work for one of the necessary options (because the
  option requires two command line arguments). Therefore, we do all the compiles and links separately, and create the
  universal library at the end.
*/

public class NativeLibraryTask
  extends Task
{
    /**
      Specifies a default value for the visibility parameter.
      @ant.prop
    */

    public static final @NotNull String JNI_VISIBILITY = "jniVisibility";

    /**
      Specifies a default value for the {@code includePath} parameter.
      @ant.prop type="Path"
    */

    public static final @NotNull String JNI_INCLUDE_PATH = "jniIncludePath";

    /**
      Refers to a set of targets that create JNI libraries. These targets are invoked by the JavaLibrary task
      after the native header files are generated and before the generated native libraries are packaged into the
      library JAR.

      @ant.ref type="TargetContainer"
    */

    public static final @NotNull String JNI_DEFAULT_TARGETS = "jni.default.targets";

    private final @NotNull List<ResourceCollection> sourceFiles = new ArrayList<>();
    private @Nullable Path includePath;
    private @Nullable Path libraryPath;

    private final @NotNull List<Option> requiredLibraries = new ArrayList<>();
    private final @NotNull List<Option> requiredFrameworks = new ArrayList<>();

    private final @NotNull List<Option> compilerOptions = new ArrayList<>();       // basic compiler options
    private final @NotNull List<Option> warningOptions = new ArrayList<>();        // warning message options
    private final @NotNull List<Option> linkerOptions = new ArrayList<>();         // basic linker options
    private @Nullable String debugOption;
    private @Nullable String visibilityOption;

    private final @NotNull Set<NativeTarget> targets = new HashSet<>();
    private @Nullable String outputFile;
    private @Nullable String installDir;                  // an optional extra install directory

    // The extra install directory is used because I do not know how to get IDEA to build a dynamic library. It could be
    // one with a separate ant target, but this way is safer because ant cannot delete a directory safely (it follows
    // symlinks).

    // Options for dynamic libraries
    private @Nullable String installName;
    private @Nullable String libraryVersion;
    private @Nullable String libraryCompatibilityVersion;

    private final @NotNull File sdkRoot;

    /**
      Support for some architectures was added in OS 10.5 and removed in OS 10.7.
      We must not try to compile or link against an earlier SDK.
    */

    private @NotNull IList<String> availableSDKVersions;           // list of available SDK versions
    private @NotNull IMap<String,String> architectureSDKVersions;  // map architecture name to preferred SDK version for that architecture
    private @NotNull String latestSDKVersion;
    private @NotNull File latestSDK;

    private @Nullable String compiler;
    private @Nullable String dsymutil;

    public NativeLibraryTask()
    {
        sdkRoot = findSDKRoot();

        compilerOptions.add(new Option("-fmessage-length=0"));
        compilerOptions.add(new Option("-pipe"));

//        if (availableSDKVersions.contains("10.6")) {
//            compilerOptions.add(new Option("-Xarch_ppc"));
//            compilerOptions.add(new Option("-mtune=G5"));
//        }

        warningOptions.add(new Option("-Wmost"));
        warningOptions.add(new Option("-Wno-trigraphs"));

        linkerOptions.add(new Option("-dynamiclib"));

        debugOption = "-g"; // was "-gdwarf-2";
    }

    private @NotNull String getCurrentSDKVersion()
    {
        return latestSDKVersion;
    }

    private @NotNull File getSDKForVersion(String version)
    {
        return new File(sdkRoot, "MacOSX" + latestSDKVersion + ".sdk");
    }

    private File findSDKRoot()
    {
        /*
           SDKs were previously located at /Developer/SDKs. Now they are bundled inside Xcode, as in
           /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs.
        */

        File app = new File("/Applications/Xcode.app");

        if (!app.isDirectory()) {
            buildFailed("Missing application: " + app);
        }

        File dir = new File(app, "Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs");
        if (!dir.isDirectory()) {
            buildFailed("Missing directory: " + app);
        }

        File[] fs = dir.listFiles();
        if (fs == null) {
            buildFailed("Unable to find available SDKs in " + dir);
        }

        return dir;
    }

    private @NotNull IList<String> discoverSDKVersions(@NotNull File sdkRoot)
    {
        /*
            An SDK has a name like:

            MacOSX10.5.sdk
            MacOSX10.6.sdk
            MacOSX11.0.sdk

            These days, it seems that Xcode only contains the most recent SDK.
        */

        File[] fs = sdkRoot.listFiles();
        if (fs == null) {
            buildFailed("Unable to find available SDKs in " + sdkRoot);
        }

        ListBuilder<String> b = IList.builder();
        int latestVersionNumber = 0;
        String latestVersion = null;

        assert fs != null;
        for (File f : fs) {
            String name = f.getName();
            if (name.endsWith(".sdk") && name.startsWith("MacOSX")) {
                String s = name.substring(6, name.length()-4);
                int pos = s.lastIndexOf('.');
                if (pos > 0) {
                    String version = s;
                    verbose("Found SDK: " + version);
                    b.add(version);
                    int versionNumber = parseVersion(version);
                    if (versionNumber > latestVersionNumber) {
                        latestVersionNumber = versionNumber;
                        latestVersion = version;
                    }
                }
            }
        }

        if (b.isEmpty()) {
            buildFailed("Unable to find available SDKs in " + sdkRoot);
        }

        IList<String> result = b.values();
        if (latestVersion != null) {
            // Ensure that the latest version is the last element
            return result.removing(latestVersion).appending(latestVersion);
        } else {
            return result;
        }
    }

    private int parseVersion(String s)
    {
        int pos = s.indexOf('.');
        if (pos > 0) {
            int v1 = parseInteger(s.substring(0, pos));
            int v2 = parseInteger(s.substring(pos+1));
            if (v1 >= 10 && v2 >= 0) {
                if (v1 == 10) {
                    // for backward compatibility
                    return v2;
                }
                return v1 * 100 + v2;
            }
        }

        buildFailed("Found SDK with invalid version: " + s);
        return 0;
    }

    private int parseInteger(String s)
    {
        try {
            int versionNumber = Integer.parseInt(s);
            if (versionNumber >= 0) {
                return versionNumber;
            }
        } catch (NumberFormatException ignore) {
        }
        return -1;
    }

    private @NotNull IMap<String,String> findArchitectureSDKVersions(@NotNull IList<String> availableSDKVersions)
    {
        MapBuilder<String,String> b = IMap.builder();
        if (availableSDKVersions.contains("10.6")) {
            b.put("ppc", "10.6");  // the last OS version to support the PowerPC architecture
        }
        b.put("x86_64", getCurrentSDKVersion());
        b.put("arm64", getCurrentSDKVersion());
        return b.value();
    }

    /**
      Create a set of options for a given SDK version.
    */

    protected String createSDKOptions(@NotNull String sdkVersion, @NotNull String minimumOSVersion)
    {
        // Is this useful?

        // sdkVersion might be something like "10.4u"
        // for the minimum OS version, we need "10.4"

        verbose("  SDK version: " + sdkVersion);

        String osv = minimumOSVersion;
        while (!osv.isEmpty() && !Character.isDigit(osv.charAt(osv.length()-1))) {
            osv = osv.substring(0, osv.length()-1);
        }

        File sdk = getSDKForVersion(osv);
        String pattern = "-isysroot " + sdk;

//        + " "
//                           //+ "-mmacosx-version-min=@V@ "
//                           + "-F" + sdk + "/System/Library/Frameworks/JavaVM.framework/Frameworks "
//                           + "-I" + sdk + "/System/Library/Frameworks/JavaVM.framework/Headers "
        /* + "-L" + sdk + "/usr/local/lib" */

        String s = Utils.replace(pattern, "@S@", sdkVersion);
        s = Utils.replace(s, "@V@", osv);
        return s;
    }

    /**
      The ID of a resource collection that identifies source files to be compiled.
      @ant.prop name="sourceRef"
      @ant.optional
    */

    public void setSourceRef(@NotNull Reference r)
    {
        Object o = r.getReferencedObject(getProject());
        if (o instanceof ResourceCollection) {
            ResourceCollection rc = (ResourceCollection) o;
            validateFileSystemOnly(rc, "source file collection");
            sourceFiles.add(rc);
        } else {
            buildFailed("Resource collection required for source files");
        }
    }

    private void validateFileSystemOnly(@NotNull ResourceCollection rc, @NotNull String description)
    {
        if (!rc.isFilesystemOnly()) {
            buildFailed("A " + description + " must contain only file system items");
            throw new AssertionError();
        }
    }

    /**
      Specify a search path for header files.
      @ant.prop name="includePath"
      @ant.optional If not defined, the value of the {@code jniIncludePath} property is used.
    */

    public void setIncludePath(@NotNull Path p)
    {
        includePath = p;
    }

    /**
      Specify a search path for required dynamic libraries.
      @ant.prop name="libraryPath"
      @ant.optional If not defined, a default path is used.
    */

    public void setLibraryPath(@NotNull Path p)
    {
        libraryPath = p;
    }

    /**
      Specify source files for the native library.
      @ant.type
    */

    public void addConfigured(@NotNull ResourceCollection fs)
    {
        validateFileSystemOnly(fs, "source files");
        sourceFiles.add(fs);
    }

    /**
      Specify a target architecture that the native library should support.
      @ant.type
    */

    public void addConfigured(@NotNull Target target)
    {
        NativeTarget t = target.asTarget();
        if (t != null) {
            targets.add(t);
        }
    }

    /**
      Specify a required native library. The value should be a library name or a Maven repo artifact key.
      <p>
      Example:
      {@snippet :
        <library>VAqua</library>
        <library value="org.jetbrains:annotations"/>
        }
      @ant.type name="library"
    */

    public Option createLibrary()
    {
        Option o = new Option();
        requiredLibraries.add(o);
        return o;
    }

    /**
      Specify a required Framework. The value should be the framework name.
      <p>
      Example:
      {@snippet :
        <framework>Cocoa</framework>
        <framework value="Cocoa"/>
        }
      @ant.type name="framework"
    */

    public Option createFramework()
    {
        Option o = new Option();
        requiredFrameworks.add(o);
        return o;
    }

    /**
      Specify an option to be passed to the native compiler.
      @ant.type name="compilerOption"
    */

    public Option createCompilerOption()
    {
        Option o = new Option();
        compilerOptions.add(o);
        return o;
    }

    /**
      Specify an option to be passed to the native compiler.
      @ant.type name="warningOption"
    */

    public Option createWarningOption()
    {
        Option o = new Option();
        warningOptions.add(o);
        return o;
    }

    /**
      Specify an option to be passed to the native linker.
      @ant.type name="linkerOption"
    */

    public Option createLinkerOption()
    {
        Option o = new Option();
        linkerOptions.add(o);
        return o;
    }

    /**
      The location where the native library is written.
      @ant.prop name="outputFile"
      @ant.required
    */

    public void setOutputfile(@NotNull String s)
    {
        outputFile = s;
    }

    /**
      An additional location where the native library should be installed.
      @ant.prop name="installDir"
      @ant.optional
    */

    public void setInstalldir(@NotNull String s)
    {
        installDir = s;
    }

    /**
      The linker visibility option. The only available option is {@code hidden}, which indicates that symbols not marked
      for export are unavailable to other libraries.
      @ant.prop name="visibility"
      @ant.optional If not specified, defaults to the value of the {@code jniVisibility} property. If that property
      is not defined, visibility of symbols is not restricted.
    */

    public void setVisibility(@NotNull String s)
    {
        visibilityOption = s;
    }

    /**
      Specifies the install name for the library. This is a path that will be attached to clients when they link
      against this library. The path may be used by the dynamic linker at runtime to locate the library.
      @ant.prop name="installName"
      @ant.optional If not specified, defaults to the path where the library is written.
    */

    public void setInstallname(@NotNull String s)
    {
        installName = s;
    }

    /**
      Specifies the library version number to install in the library.
      See the documentation of {@code ld} for details.
      @ant.prop name="version"
      @ant.optional
    */

    public void setVersion(String s)
    {
        libraryVersion = s;
    }

    /**
      Specifies the library compatibility version number to install in the library.
      See the documentation of {@code ld} for details.
      @ant.prop name="compatibilityVersion"
      @ant.optional
    */

    public void setCompatibilityversion(@NotNull String s)
    {
        libraryCompatibilityVersion = s;
    }

    /**
      The debug option passed to the native compiler.
      @ant.prop name="debugOption"
      @ant.optional
    */

    public void setDebugoption(@NotNull String s)
    {
        debugOption = s;
    }

    /**
      The location of the native compiler.
      @ant.prop name="compiler"
      @ant.optional If not specified, some standard locations will be searched.
    */

    public void setCompiler(@NotNull String s)
    {
        compiler = s;
    }

    /**
      The location of the {@code dsymutil} program. This program is used to create a debugging symbol bundle
      for the library.
      @ant.prop name="dSymUtil"
      @ant.optional If not specified, some standard locations will be searched.
    */
    public void setDsymutil(@NotNull String s)
    {
        dsymutil = s;
    }

    public void execute()
      throws BuildException
    {
        AntUtils.init();

        Project project = getProject();

        NativeLibraryBuilder.Delegate delegate = NativeLibraryBuilderAnt.create(getProject());

        delegate.verbose("JNILibrary execution: ");

        String cwd = System.getProperty("user.dir");
        if (cwd != null) {
            delegate.verbose("  Current directory: " + cwd);
        }

        availableSDKVersions = discoverSDKVersions(sdkRoot);
        latestSDKVersion = availableSDKVersions.last();
        latestSDK = getSDKForVersion(latestSDKVersion);

        architectureSDKVersions = findArchitectureSDKVersions(availableSDKVersions);

        includePath = determineIncludePath();
        delegate.info("Include path: " + includePath);

        if (libraryPath == null) {
            libraryPath = new Path(project, "/usr/local/lib");
            delegate.info("Using default library path: " + libraryPath);
        }

        if (outputFile == null) {
            buildFailed("Output file must be specified");
            throw new AssertionError();
        }

        if (installDir != null) {
            File dir = new File(installDir);
            try {
                Files.createDirectories(dir.toPath());
            } catch (IOException ex) {
                buildFailed(String.format("Unable to create installation directory [%s] : %s", ex.getMessage(), dir));
                throw new AssertionError();
            }
        }

        NativeLibraryBuilder.Configuration g = createConfiguration(delegate);
        NativeLibraryBuilder builder = NativeLibraryBuilder.create(g, delegate);
        builder.announce(g);
        try {
            builder.build();
        } catch (org.violetlib.vbuilder.BuildException e) {
            throw new BuildException(e);
        }
    }

    private @NotNull Path determineIncludePath()
      throws IllegalStateException
    {
        Project project = getProject();
        Path p = new Path(project);

        if (includePath != null) {
            p.add(includePath);
        } else {
            String path = project.getProperty(JNI_INCLUDE_PATH);
            if (path != null) {
                p.add(new Path(project, path));
            }
        }

        File javaHome = Utils.getConfiguredJavaHome();
        File includeDir = new File(javaHome, "include").getAbsoluteFile();
        if (Files.isDirectory(includeDir.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            p.add(new FileResource(project, includeDir));
        }
        File includeDir2 = new File(includeDir, "darwin").getAbsoluteFile();
        if (Files.isDirectory(includeDir2.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            p.add(new FileResource(project, includeDir2));
        }

        return p;
    }

    protected @NotNull NativeLibraryBuilder.Configuration createConfiguration(@NotNull Reporter r)
    {
        File compiler = getCompiler();
        File dsymutil = getDsymUtil();
        IList<File> sourceFiles = getSourceFiles();
        IList<File> includeDirs = getIncludeDirs();
        IList<File> libraryDirs = getLibraryDirs();
        File outputFile = getOutputFile();
        File installDirectory = getInstallDirectory();
        ISet<NativeTarget> targets = getTargets();
        ISet<Architecture> architectures = getArchitectures(targets);

        if (architectures.isEmpty()) {
            throw new BuildException("No valid architectures specified");
        }

        if (architectures.size() == 1) {
            verbose("Required architecture: " + architectures.choose());
        } else {
            verbose("Required architectures:");
            for (Architecture a : architectures) {
                verbose("  " + a);
            }
        }

        if (libraryDirs.isEmpty()) {
            verbose("Library search path is undefined");
        } else {
            verbose("Library search path:");
            for (File f : libraryDirs) {
                verbose("  " + f);
            }
        }

        IList<NativeLibrary> requiredLibraries = getNativeLibraries(libraryDirs, architectures, r);
        showLibraries(requiredLibraries);

        IList<NativeFramework> requiredFrameworks = getNativeFrameworks();
        showFrameworks(requiredFrameworks);

        IList<String> compilerOptions = getCompilerOptions();
        IList<String> warningOptions = getWarningOptions();
        IList<String> linkerOptions = getLinkerOptions();
        String debugOption = getDebugOption();
        String visibilityOption = getVisibilityOption();
        String libraryInstallName = getLibraryInstallName();
        String libraryVersion = getLibraryVersion();
        String libraryCompatibilityVersion = getLibraryCompatibilityVersion();

        NativeLibraryBuilder.Configuration g
          = NativeLibraryBuilder.createConfiguration(
          compiler,
          dsymutil,
          sourceFiles,
          includeDirs,
          outputFile,
          installDirectory,
          targets,
          requiredLibraries,
          requiredFrameworks,
          compilerOptions,
          warningOptions,
          linkerOptions,
          debugOption,
          visibilityOption,
          libraryInstallName,
          libraryVersion,
          libraryCompatibilityVersion
        );

        return g;
    }

    private void showLibraries(@NotNull IList<NativeLibrary> libs)
    {
        if (!libs.isEmpty()) {
            verbose("Native libraries:");
            for (NativeLibrary lib : libs) {
                verbose(lib.toString());
            }
        }
    }

    private void showFrameworks(@NotNull IList<NativeFramework> fs)
    {
        if (!fs.isEmpty()) {
            verbose("Native frameworks:");
            for (NativeFramework f : fs) {
                verbose(f.toString());
            }
        }
    }

    private @Nullable File getCompiler()
    {
        if (compiler != null) {
            File compiler = new File(this.compiler);
            if (Files.isRegularFile(compiler.toPath())) {
                return Utils.resolve(compiler).getAbsoluteFile();
            }
        }
        return null;
    }

    private @Nullable File getDsymUtil()
    {
        if (dsymutil != null) {
            File program = new File(dsymutil);
            if (Files.isRegularFile(program.toPath())) {
                return Utils.resolve(program).getAbsoluteFile();
            }
        }
        return null;
    }

    private @NotNull IList<File> getSourceFiles()
    {
        ListBuilder<File> b = IList.builder(IList.NO_DUPLICATES);
        for (ResourceCollection rc : sourceFiles) {
            ISet<File> sourceFiles
              = AntUtils.getResourceCollectionFiles(rc, f -> Files.isRegularFile(f.toPath()));
            if (sourceFiles != null) {
                b.addAll(sourceFiles);
            }
        }
        return b.values();
    }

    private @NotNull IList<File> getIncludeDirs()
    {
        if (includePath != null) {
            IList<File> files = AntUtils.getPathFiles(includePath, f -> Files.isDirectory(f.toPath()));
            if (files != null) {
                return files;
            }
        }
        return IList.empty();
    }

    private @NotNull IList<File> getLibraryDirs()
    {
        if (libraryPath != null) {
            IList<File> files = AntUtils.getPathFiles(libraryPath, f -> Files.isDirectory(f.toPath()));
            if (files != null) {
                return files;
            }
        }
        return IList.empty();
    }

    private @NotNull File getOutputFile()
    {
        if (outputFile == null) {
            buildFailed("An output file must be specified");
        }
        return new File(outputFile);
    }

    private @Nullable File getInstallDirectory()
    {
        if (installDir != null) {
            return new File(installDir);
        }
        return null;
    }

    private @NotNull ISet<NativeTarget> getTargets()
    {
        if (!targets.isEmpty()) {
            return ISet.create(targets);
        }
        TargetContainer tc = getProject().getReference(JNI_DEFAULT_TARGETS);
        if (tc != null) {
            return ISet.create(tc.getTargets());
        }
        return ISet.empty();
    }

    private @NotNull ISet<Architecture> getArchitectures(@NotNull ISet<NativeTarget> targets)
    {
        SetBuilder<Architecture> b = ISet.builder();
        for (NativeTarget target : targets) {
            Architecture a = target.getArch();
            if (a == null) {
                error("Target architecture is unsupported: " + target);
            } else {
                b.add(a);
            }
        }
        return b.values();
    }

    private @NotNull IList<NativeLibrary> getNativeLibraries(@NotNull IList<File> searchPath,
                                                             @NotNull ISet<Architecture> architectures,
                                                             @NotNull Reporter r)
    {
        boolean errors = false;

        SetBuilder<NativeLibrary> b = ISet.builder();
        for (Option o : requiredLibraries) {
            String name = o.getValue();
            try {
                NativeLibrary lib = NativeLibrarySupport.findLibrary(searchPath, name, architectures);
                if (lib != null) {
                    b.add(lib);
                } else {
                    errors = true;
                    error("Library not found: " + name);
                }
            } catch (IOException e) {
                throw new BuildException((e.getMessage()));
            }
        }

        if (errors) {
            throw new BuildException("Missing libraries");
        }

        return IList.create(b.values());
    }

    private @NotNull IList<NativeFramework> getNativeFrameworks()
    {
        SetBuilder<NativeFramework> b = ISet.builder();
        for (Option o : requiredFrameworks) {
            String value = o.getValue();
            NativeFramework framework = toNativeFramework(value);
            b.add(framework);
        }
        return IList.create(b.values());
    }

    private @NotNull NativeFramework toNativeFramework(@NotNull String s)
    {
        if (!s.contains(".")) {
            // It could be a system framework
            if (s.equals("JavaNativeFoundation")) {
                String loc = getProject().getProperty("javaNativeFoundationLocation");
                if (loc != null) {
                    File d = new File(loc);
                    File f = new File(d, "JavaNativeFoundation.framework");
                    if (Files.isDirectory(f.toPath())) {
                        info("Found framework: " + f);
                        return NativeFrameworkImpl.createFramework(f);
                    }
                    throw new BuildException("JavaNativeFoundation framework not found in " + d);
                }
            }
            return NativeFrameworkImpl.createSystemFramework(s);
        }
        File f = new File(s);
        String name = f.getName();
        if (Files.isDirectory(f.toPath()) && name.endsWith(".framework")) {
            return NativeFrameworkImpl.createFramework(Utils.resolve(f));
        } else {
            throw new BuildException("Native framework must be a directory with .framework suffix: " + f);
        }
    }

    private @NotNull IList<String> getCompilerOptions()
    {
        ListBuilder<String> b = IList.builder();
        for (Option o : compilerOptions) {
            String value = o.getValue();
            b.add(value);
        }
        IList<String> options = b.values();
        IList<String> standardOptions = getStandardCompilerOptions();
        return options.appendingAll(standardOptions);
    }

    private @NotNull IList<String> getStandardCompilerOptions()
    {
        return IList.empty();
    }

    private @NotNull IList<String> getWarningOptions()
    {
        ListBuilder<String> b = IList.builder();
        for (Option o : warningOptions) {
            String value = o.getValue();
            b.add(value);
        }
        IList<String> options = b.values();
        IList<String> standardOptions = getStandardWarningOptions();
        return options.appendingAll(standardOptions);
    }

    private @NotNull IList<String> getStandardWarningOptions()
    {
        return IList.empty();
    }

    private @NotNull IList<String> getLinkerOptions()
    {
        ListBuilder<String> b = IList.builder();
        for (Option o : linkerOptions) {
            String value = o.getValue();
            b.add(value);
        }
        IList<String> options = b.values();
        IList<String> standardOptions = getStandardLinkerOptions();
        return options.appendingAll(standardOptions);
    }

    private @NotNull IList<String> getStandardLinkerOptions()
    {
        return IList.empty();
    }

    private @Nullable String getDebugOption()
    {
        return debugOption;
    }

    private @Nullable String getVisibilityOption()
    {
        if (visibilityOption != null) {
            return visibilityOption;
        }
        Project p = getProject();
        String s = p.getProperty(JNI_VISIBILITY);
        if (s != null) {
            verbose("Using default visibility: " + s);
            return s;
        }
        return null;
    }

    private @Nullable String getLibraryInstallName()
    {
        return installName;
    }

    private @Nullable String getLibraryVersion()
    {
        return libraryVersion;
    }

    private @Nullable String getLibraryCompatibilityVersion()
    {
        return libraryCompatibilityVersion;
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
