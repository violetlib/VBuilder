package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.collections.IList;
import org.violetlib.collections.ISet;
import org.violetlib.collections.ListBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
  Build a native library.
  <p>
  This builder is Unix specific and supports features that are (probably) macOS specific:
  <ul>
  <li>
  The ability to link to frameworks.
  </li>
  <li>
  The ability to create a native library that supports multiple targets (architectures).
  </li>
  </ul>
*/

public class NativeLibraryBuilder
{
    public static @NotNull NativeLibraryBuilder create(@NotNull Configuration g, @NotNull Delegate delegate)
    {
        return new NativeLibraryBuilder(g, delegate);
    }

    public static @NotNull Configuration createConfiguration(
      @Nullable File compiler,
      @Nullable File dsymutil,
      @NotNull IList<File> sourceFiles,
      @NotNull IList<File> includeDirs,
      @NotNull File outputFile,
      @Nullable File installationDirectory,
      @NotNull ISet<NativeTarget> targets,
      @NotNull IList<NativeLibrary> requiredLibraries,
      @NotNull IList<NativeFramework> requiredFrameworks,
      @NotNull IList<String> compilerOptions,
      @NotNull IList<String> warningOptions,
      @NotNull IList<String> linkerOptions,
      @Nullable String debugOption,
      @Nullable String visibilityOption,
      @Nullable String libraryInstallName,
      @Nullable String libraryVersion,
      @Nullable String libraryCompatibilityVersion)
    {
        return new Configuration(compiler,
          dsymutil,
          sourceFiles,
          includeDirs,
          outputFile,
          installationDirectory,
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
          libraryCompatibilityVersion);
    }

    public static class Configuration
    {
        public final @Nullable File compiler; // the C compiler
        public final @Nullable File dsymutil; // the dsymutil tool
        public final @NotNull IList<File> sourceFiles;
        public final @NotNull IList<File> includeDirs;
        public final @NotNull File outputFile;
        public final @Nullable File installationDirectory;
        public final @NotNull ISet<NativeTarget> targets;
        public final @NotNull IList<NativeLibrary> requiredLibraries;
        public final @NotNull IList<NativeFramework> requiredFrameworks;
        public final @NotNull IList<String> compilerOptions;       // basic compiler options
        public final @NotNull IList<String> warningOptions;        // warning message options
        public final @NotNull IList<String> linkerOptions;         // basic linker options
        public final @Nullable String debugOption;
        public final @Nullable String visibilityOption;
        // Options for dynamic libraries
        public final @Nullable String libraryInstallName;
        public final @Nullable String libraryVersion;
        public final @Nullable String libraryCompatibilityVersion;

        public Configuration(@Nullable File compiler,
                             @Nullable File dsymutil,
                             @NotNull IList<File> sourceFiles,
                             @NotNull IList<File> includeDirs,
                             @NotNull File outputFile,
                             @Nullable File installationDirectory,
                             @NotNull ISet<NativeTarget> targets,
                             @NotNull IList<NativeLibrary> requiredLibraries,
                             @NotNull IList<NativeFramework> requiredFrameworks,
                             @NotNull IList<String> compilerOptions,
                             @NotNull IList<String> warningOptions,
                             @NotNull IList<String> linkerOptions,
                             @Nullable String debugOption,
                             @Nullable String visibilityOption,
                             @Nullable String libraryInstallName,
                             @Nullable String libraryVersion,
                             @Nullable String libraryCompatibilityVersion)
        {
            this.compiler = compiler;
            this.dsymutil = dsymutil;
            this.sourceFiles = sourceFiles;
            this.includeDirs = includeDirs;
            this.outputFile = outputFile;
            this.installationDirectory = installationDirectory;
            this.targets = targets;
            this.requiredLibraries = requiredLibraries;
            this.requiredFrameworks = requiredFrameworks;
            this.compilerOptions = compilerOptions;
            this.warningOptions = warningOptions;
            this.linkerOptions = linkerOptions;
            this.debugOption = debugOption;
            this.visibilityOption = visibilityOption;
            this.libraryInstallName = libraryInstallName;
            this.libraryVersion = libraryVersion;
            this.libraryCompatibilityVersion = libraryCompatibilityVersion;
        }
    }

    private final @NotNull Delegate delegate;
    private final @NotNull Configuration g;
    private final @NotNull File compiler;
    private final @NotNull File dsymutil;

    private NativeLibraryBuilder(@NotNull Configuration g, @NotNull Delegate delegate)
    {
        this.g = g;
        this.delegate = delegate;

        compiler = g.compiler != null ? g.compiler : findCompiler();
        dsymutil = g.dsymutil != null ? g.dsymutil : findDSYMUtil();
    }

    private @NotNull File findCompiler()
    {
        File f = new File("/Applications/Xcode.app/Contents/Developer/usr/bin/cc");
        if (f.isFile()) {
            return Utils.resolve(f);
        }

        f = new File("/usr/bin/cc");
        if (f.isFile()) {
            return Utils.resolve(f);
        }

        buildFailed("Unable to find C compiler");
        throw new AssertionError();
    }

    private @NotNull File findDSYMUtil()
    {
        File f = new File("/Applications/Xcode.app/Contents/Developer/usr/bin/dsymutil");
        if (f.isFile()) {
            return Utils.resolve(f);
        }

        f = new File("/usr/bin/dsymutil");
        if (f.isFile()) {
            return Utils.resolve(f);
        }

        buildFailed("Unable to find dsymutil");
        throw new AssertionError();
    }

    public void build()
      throws BuildException
    {
        validate(g);

        // Create a temporary directory where the object files and single-architecture libraries will go.

        File tmpDir;

        try {
            tmpDir = Utils.createTemporaryDirectory("antnl");
        } catch (IOException ex) {
            buildFailed(ex.getMessage());
            throw new AssertionError();
        }

        if (g.targets.size() > 1) {
            ListBuilder<File> b = IList.builder();
            for (NativeTarget target : g.targets) {
                File targetTempDir = new File(tmpDir, target.getValue());
                try {
                    Files.createDirectory(targetTempDir.toPath());
                } catch (IOException e) {
                    buildFailed("Unable to create directory: " + targetTempDir, e);
                    throw new AssertionError();
                }
                File lib = compileAndLink(target, false, targetTempDir);
                b.add(lib);
            }
            IList<File> libraries = b.values();
            createUniversalLibrary(libraries, g.outputFile);
        } else {
            NativeTarget t = g.targets.choose();
            compileAndLink(t, true, tmpDir);
        }

        delegate.info("Created: " + g.outputFile);

        createDSYM(dsymutil, g.outputFile);

        if (g.installationDirectory != null) {

            // install the dynamic library
            File dynamicLibrary = g.outputFile;
            String dynamicLibraryName = dynamicLibrary.getName();
            File installedDynamicLibrary = new File(g.installationDirectory, dynamicLibraryName);
            try {
                Files.copy(dynamicLibrary.toPath(), installedDynamicLibrary.toPath(), REPLACE_EXISTING, COPY_ATTRIBUTES);
            } catch (IOException ex) {
                buildFailed("Unable to install dynamic library: " + ex.getMessage());
                throw new AssertionError();
            }

            // install the dsym bundle
            File dSYM = new File(g.outputFile + ".dSYM");
            String bundleName = dSYM.getName();
            File installedBundle = new File(g.installationDirectory, bundleName);
            try {
                if (Files.exists(installedBundle.toPath(), NOFOLLOW_LINKS)) {
                    // delete the existing installed bundle
                    Utils.deleteDirectory(installedBundle.toPath());
                }
                if (dSYM.exists()) {
                    Utils.copyDirectory(dSYM, installedBundle);
                } else {
                    delegate.error("Failed to create " + dSYM);
                }
            } catch (Exception ex) {
                buildFailed("Unable to install dynamic library symbols: " + ex.getMessage());
            }
            delegate.info("Installed in " + g.installationDirectory);
        }

        try {
            Utils.deleteDirectory(tmpDir.toPath());
        } catch (IOException ex) {
            delegate.info("Unable to delete temporary files: " + ex.getMessage());
        }
    }

    public void validate(@NotNull Configuration g)
      throws BuildException
    {
        int errorCount = 0;

        if (g.sourceFiles.isEmpty()) {
            buildFailed("No source files specified");
        } else {
            for (File f : g.sourceFiles) {
                if (!Files.isRegularFile(f.toPath())) {
                    delegate.error("Source file not found: " + f);
                    ++errorCount;
                }
            }
        }

        for (File f : g.includeDirs) {
            if (!Files.isDirectory(f.toPath())) {
                delegate.error("Include directory not found: " + f);
                ++errorCount;
            }
        }

        File outputFile = g.outputFile.getAbsoluteFile();
        File outputDir = outputFile.getParentFile();

        if (outputDir == null) {
            delegate.error("Unsupported output file [no parent]: " + outputFile);
            errorCount++;
        } else {
            if (!Files.isDirectory(outputDir.toPath(), NOFOLLOW_LINKS)) {
                try {
                    Files.createDirectories(outputDir.toPath());
                } catch (IOException e) {
                    buildFailed("Unable to create output directory: " + outputDir, e);
                    throw new AssertionError();
                }
            }
        }

        if (errorCount > 0) {
            throw new BuildException("Errors detected");
        }
    }

    public void announce(@NotNull Configuration g)
    {
        show("Output file", g.outputFile);
        show("Installation directory", g.installationDirectory);
        show("Source files", g.sourceFiles);
        show("Include directories", g.includeDirs);
        show("Required libraries", g.requiredLibraries);
        show("Required frameworks", g.requiredFrameworks);
        show("Basic compiler options", g.compilerOptions);
        show("Warning options", g.warningOptions);
        show("Debug option", g.debugOption);
        show("Visibility option", g.visibilityOption);
        show("Basic linker options", g.linkerOptions);
        show("Library install name", g.libraryInstallName);
        show("Library version", g.libraryVersion);
        show("LibraryCompatibilityVersion", g.libraryCompatibilityVersion);
    }

    protected void show(@NotNull String category, @Nullable String value)
    {
        if (value != null) {
            delegate.verbose("  " + category + ": " + value);
        }
    }

    protected void show(@NotNull String category, @Nullable File value)
    {
        if (value != null) {
            delegate.verbose("  " + category + ": " + value);
        }
    }

    protected void show(@NotNull String category, @NotNull IList<?> values)
    {
        if (!values.isEmpty()) {
            delegate.verbose("  " + category + ":");
            for (Object value : values) {
                delegate.verbose("    " + value);
            }
        }
    }

    private @NotNull File compileAndLink(@NotNull NativeTarget target,
                                         boolean isSolo,
                                         @NotNull File outputDir)
    {
        IList<File> objectFiles = compile(target, g.sourceFiles, outputDir);
        IList<String> linkOptions = getLinkOptions(target);
        File outputDirOrFile = isSolo ? g.outputFile : outputDir;
        File library = link(compiler, target, outputDirOrFile, objectFiles, isSolo, linkOptions,
          g.libraryInstallName, g.libraryVersion, g.libraryCompatibilityVersion);
        return library;
    }

    private @NotNull IList<File> compile(@NotNull NativeTarget target,
                                         @NotNull IList<File> sourceFiles,
                                         @NotNull File outputDir)
    {
        IList<String> options = getCompileOptions(target);
        ListBuilder<File> b = IList.builder();
        for (File sourceFile : sourceFiles) {
            File obj = compile(compiler, target, outputDir, sourceFile, options);
            b.add(obj);
        }
        return b.values();
    }

    private @NotNull File compile(@NotNull File program,
                                  @NotNull NativeTarget target,
                                  @NotNull File outputDir,
                                  @NotNull File source,
                                  @NotNull IList<String> options)
    {
        ListBuilder<String> b = IList.builder();
        b.add("-c");
        b.add("-target");
        b.add(target.getValue());
        b.addAll(options);
        b.add(source.getAbsolutePath());

        String name = source.getName();
        int pos = name.lastIndexOf(".");
        if (pos >= 0) {
            name = name.substring(0, pos);
        }
        name = name + ".o";
        File objectFile = new File(outputDir, name);

        b.add("-o");
        b.add(objectFile.getPath());

        IList<String> args = b.values();

        ExecutionConfiguration g = ExecutionConfiguration.create(program, "compile", args);
        ExecutionService es = ExecutionService.get();
        try {
            ExecutionResult r = es.execute(g);
            if (r.rc != 0) {
                delegate.error("compile failed");
                if (!r.error.isEmpty()) {
                    delegate.error(r.error);
                }
                buildFailed("compile failed");
                throw new AssertionError();
            }
            return objectFile;
        } catch (IOException e) {
            buildFailed("Unable to run " + program, e);
            throw new AssertionError();
        }
    }

    private @NotNull IList<String> getCompileOptions(@NotNull NativeTarget target)
    {
        ListBuilder<String> b = IList.builder();
        b.addAll(getIncludeOptions());
        b.addAll(getLibraryOptions(target));
        b.addAll(g.compilerOptions);
        b.addAll(g.warningOptions);
        b.addOptional(g.debugOption);
        if (g.visibilityOption != null) {
            b.add("-fvisibility=" + g.visibilityOption);
        }
        return b.values();
    }

    private @NotNull IList<String> getIncludeOptions()
    {
        ListBuilder<String> b = IList.builder();
        for (File f : g.includeDirs) {
            b.add("-I" + Utils.resolve(f).getAbsolutePath());
        }
        for (NativeFramework f : g.requiredFrameworks) {
            File root = f.getRoot();
            if (root != null) {
                b.add("-F" + Utils.resolve(root.getParentFile()).getAbsolutePath());
            }
        }
        return b.values();
    }

    private @NotNull IList<String> getLinkOptions(@NotNull NativeTarget target)
    {
        ListBuilder<String> b = IList.builder();

        b.addAll(getFrameworkOptions());
        b.addAll(getLibraryOptions(target));
        b.addAll(g.linkerOptions);

        if (g.libraryInstallName != null) {
            b.add("-install_name");
            b.add(g.libraryInstallName);
        }

        if (g.libraryVersion != null) {
            b.add("-current_version");
            b.add(g.libraryVersion);
        }

        if (g.libraryCompatibilityVersion != null) {
            b.add("-compatibility_version");
            b.add(g.libraryCompatibilityVersion);
        }

        return b.values();
    }

    private @NotNull IList<String> getLibraryOptions(@NotNull NativeTarget target)
    {
        // There is a potential problem with libraries. The -l argument accepts a library name, not a path. The
        // containing directory must be in the library search path. If a library with a specified name is in multiple
        // such directories, the "wrong one" might be found. The order of the directories and library names as they are
        // encountered is preserved in hopes this will work properly. Note that command arguments that accept a library
        // path all have additional semantics which may not be wanted.

        ListBuilder<String> b = IList.builder();
        Set<String> directories = new HashSet<>();
        Set<String> libraries = new HashSet<>();

        for (NativeLibrary lib : g.requiredLibraries) {
            Architecture arch = target.getArch();
            if (arch != null) {
                File f = lib.getFile(arch);
                if (f != null) {
                    File ff = f.getAbsoluteFile();
                    File parent = ff.getParentFile();
                    if (parent != null) {
                        String dir = parent.toString();
                        if (!directories.contains(dir)) {
                            directories.add(dir);
                            b.add("-L" + dir);
                        }
                    }
                    String name = lib.getName();
                    if (!libraries.contains(name)) {
                        libraries.add(name);
                        b.add("-l" + name);
                    }
                } else {
                    delegate.error("Library does not support architecture: " + arch.getName());
                }
            } else {
                delegate.error("Unsupported target architecture: " + target);
            }
        }

        return b.values();
    }

    private @NotNull IList<String> getFrameworkOptions()
    {
        // There is a potential problem with frameworks. The -framework argument accepts a framework name, not a path.
        // The containing directory must be in the framework search path. If a framework with a specified name is in
        // multiple such directories, the "wrong one" might be found. The order of the directories and framework names
        // as they are encountered is preserved in hopes this will work properly.

        ListBuilder<String> b = IList.builder();
        Set<String> directories = new HashSet<>();
        Set<String> frameworks = new HashSet<>();

        for (NativeFramework fr : g.requiredFrameworks) {
            File f = fr.getRoot();
            if (f == null) {
                // A system framework
                String name = fr.getName();
                if (!frameworks.contains(name)) {
                    frameworks.add(name);
                    b.add("-framework");
                    b.add(name);
                }
            } else {
                File ff = f.getAbsoluteFile();
                File parent = ff.getParentFile();
                if (parent != null) {
                    String dir = parent.toString();
                    if (!directories.contains(dir)) {
                        directories.add(dir);
                        b.add("-F" + dir);
                    }
                }
                String name = fr.getName();
                if (!frameworks.contains(name)) {
                    frameworks.add(name);
                    b.add("-framework");
                    b.add(name);
                }
            }
        }

        return b.values();
    }

    private @NotNull File link(@NotNull File program,
                               @NotNull NativeTarget target,
                               @NotNull File outputDirOrFile,
                               @NotNull IList<File> objectFiles,
                               boolean isSolo,
                               @NotNull IList<String> options,
                               @Nullable String libraryInstallName,
                               @Nullable String libraryVersion,
                               @Nullable String libraryCompatibilityVersion)
    {
        ListBuilder<String> ca = IList.builder();
        ca.add("-target");
        ca.add(target.getValue());
        ca.addAll(options);

        if (libraryInstallName != null) {
            ca.add("-install_name");
            ca.add(libraryInstallName);
        }

        if (libraryVersion != null) {
            ca.add("-current_version");
            ca.add(libraryVersion);
        }

        if (libraryCompatibilityVersion != null) {
            ca.add("-compatibility_version");
            ca.add(libraryCompatibilityVersion);
        }

        for (File f : objectFiles) {
            ca.add(f.getAbsolutePath());
        }

        File library = isSolo ? outputDirOrFile : new File(outputDirOrFile, "library");
        ca.add("-o");
        ca.add(library.getPath());
        IList<String> args = ca.values();

        ExecutionConfiguration g = ExecutionConfiguration.create(program, "link", args);
        ExecutionService es = ExecutionService.get();
        try {
            ExecutionResult r = es.execute(g);
            if (r.rc != 0) {
                delegate.error("link failed");
                if (!r.error.isEmpty()) {
                    delegate.error(r.error);
                }
                buildFailed("link failed");
                throw new AssertionError();
            }
            return library;
        } catch (IOException e) {
            buildFailed("Unable to run " + program, e);
            throw new AssertionError();
        }
    }

    private void createUniversalLibrary(@NotNull IList<File> libs, @NotNull File outputFile)
    {
        File program = new File("/usr/bin/lipo");
        ListBuilder<String> b = IList.builder();
        for (File f : libs) {
            b.add(f.getAbsolutePath());
        }
        b.add("-create");
        b.add("-output");
        b.add(outputFile.getAbsolutePath());
        IList<String> args = b.values();
        ExecutionConfiguration g = ExecutionConfiguration.create(program, "create_universal_library", args);
        ExecutionService es = ExecutionService.get();
        try {
            ExecutionResult r = es.execute(g);
            if (r.rc != 0) {
                delegate.error("lipo failed");
                if (!r.error.isEmpty()) {
                    delegate.error(r.error);
                }
                buildFailed("lipo failed");
                throw new AssertionError();
            }
        } catch (IOException e) {
            buildFailed("Unable to run " + program, e);
            throw new AssertionError();
        }
    }

    private @NotNull File createDSYM(@NotNull File program, @NotNull File library)
    {
        // We must create a dSYM bundle while we still have the object files. The library knows where the object files
        // are, but they are in a temporary directory and soon will be deleted.

        IList<String> args = IList.of(library.getAbsolutePath());
        ExecutionConfiguration g = ExecutionConfiguration.create(program, "create_dsym", args);
        ExecutionService es = ExecutionService.get();
        try {
            ExecutionResult r = es.execute(g);
            if (r.rc != 0) {
                delegate.error("dsymutil failed");
                if (!r.error.isEmpty()) {
                    delegate.error(r.error);
                }
                buildFailed("dsymutil failed");
                throw new AssertionError();
            }
            return new File(library.getPath() + ".dSYM");
        } catch (IOException e) {
            buildFailed("Unable to run " + program, e);
            throw new AssertionError();
        }
    }

    /**
      This method is expected to throw a RuntimeException.
    */

    protected final void buildFailed(@NotNull String message)
    {
        delegate.announceBuildFailure(message, null);
        throw new AssertionError("announceBuildFailure failed to throw an exception");
    }

    /**
      This method is expected to throw a RuntimeException.
    */

    protected final void buildFailed(@NotNull String message, @NotNull Exception ex)
    {
        delegate.announceBuildFailure(message, ex);
        throw new AssertionError("announceBuildFailure failed to throw an exception");
    }

    public interface Delegate
      extends BuildDelegate
    {
    }
}
