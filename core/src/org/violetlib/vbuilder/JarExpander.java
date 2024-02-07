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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

/**
  Copy elements from JAR files and directories into target directories.
*/

public class JarExpander
{
    /**
      Copy elements as specified in the configuration.
      @return true if errors were detected.
      @throws IOException if the configuration is not valid.
    */

    public static @NotNull Result expand(@NotNull Configuration g, @NotNull Reporter reporter)
      throws IOException
    {
        return new JarExpander(g, reporter).getResult();
    }

    /**
      Create a configuration for a JAR expander operation.

      @param sourceFiles Sources to be copied or expanded (JAR files). Native libraries are frameworks are recognized
      only at top level.

      @param classTarget The destination directory for all sources that are not native libraries or frameworks. If null,
      those files are not copied.

      @param nativeLibraryTarget The destination directory for native libraries and associated symbols. If null, those
      files are not copied.

      @param nativeFrameworkTarget The destination directory for native frameworks and associated symbols. If null,
      those files are not copied.
    */

    public static @NotNull Configuration createConfiguration(@NotNull ISet<File> sourceFiles,
                                                             @Nullable File classTarget,
                                                             @Nullable File nativeLibraryTarget,
                                                             @Nullable File nativeFrameworkTarget)
    {
        return new Configuration(sourceFiles, classTarget, nativeLibraryTarget, nativeFrameworkTarget);
    }

    public static class Result
    {
        /** True if errors were detected and reported */
        public final boolean errorsFound;

        /** The native libraries that were copied into the native library target directory */
        public final @NotNull IList<NativeLibrary> nativeLibraries;

        /** The native frameworks that were copied into the native framework target directory */
        public final @NotNull IList<NativeFramework> nativeFrameworks;

        private Result(boolean errorsFound,
                       @NotNull IList<NativeLibrary> nativeLibraries,
                       @NotNull IList<NativeFramework> nativeFrameworks)
        {
            this.errorsFound = errorsFound;
            this.nativeLibraries = nativeLibraries;
            this.nativeFrameworks = nativeFrameworks;
        }
    }

    public static class Configuration
    {
        public final @NotNull ISet<File> sourceFiles;
        public final @Nullable File classTarget;
        public final @Nullable File nativeLibraryTarget;
        public final @Nullable File nativeFrameworkTarget;

        private Configuration(@NotNull ISet<File> sourceFiles,
                              @Nullable File classTarget,
                              @Nullable File nativeLibraryTarget,
                              @Nullable File nativeFrameworkTarget)
        {
            this.sourceFiles = sourceFiles;
            this.classTarget = classTarget;
            this.nativeLibraryTarget = nativeLibraryTarget;
            this.nativeFrameworkTarget = nativeFrameworkTarget;
        }
    }

    private final @NotNull Configuration g;
    private final @NotNull Reporter reporter;
    private boolean errorsFound;
    private final @NotNull Set<NativeLibrary> nativeLibraries = new HashSet<>();
    private final @NotNull Set<NativeFramework> nativeFrameworks = new HashSet<>();
    private final @NotNull Map<String,File> nativeLibrarySymbolsMap = new HashMap<>();
    private final @NotNull Map<String,File> nativeFrameworkSymbolsMap = new HashMap<>();

    private JarExpander(@NotNull Configuration g, @NotNull Reporter reporter)
      throws IOException
    {
        this.reporter = reporter;
        this.g = validate(g);

        execute();
    }

    public @NotNull Result getResult()
    {
        List<NativeLibrary> nls = new ArrayList<>(nativeLibraries);
        List<NativeFramework> nfs = new ArrayList<>(nativeFrameworks);

        for (Map.Entry<String,File> d : nativeLibrarySymbolsMap.entrySet()) {
            int index = getNativeLibrary(nls, d.getKey());
            reporter.info("  Found symbols for " + d.getKey() + ": " + d.getValue() + " " + index);
            nls.set(index, nls.get(index).withDebugSymbols(d.getValue()));
        }

        for (Map.Entry<String,File> d : nativeFrameworkSymbolsMap.entrySet()) {
            int index = getNativeFramework(nfs, d.getKey());
            reporter.info("  Found symbols for " + d.getKey() + ": " + d.getValue() + " " + index);
            nfs.set(index, nfs.get(index).withDebugSymbols(d.getValue()));
        }

        return new Result(errorsFound, IList.create(nls), IList.create(nfs));
    }

    private int getNativeLibrary(@NotNull List<NativeLibrary> nls, @NotNull String fn)
    {
        for (int i = 0; i < nls.size(); i++) {
            NativeLibrary nl = nls.get(i);
            File f = nl.getFile();
            if (f != null && f.getName().equals(fn)) {
                return i;
            }
        }
        return -1;
    }

    private int getNativeFramework(@NotNull List<NativeFramework> nfs, @NotNull String fn)
    {
        for (int i = 0; i < nfs.size(); i++) {
            NativeFramework nf = nfs.get(i);
            File f = nf.getRoot();
            if (f != null && f.getName().equals(fn)) {
                return i;
            }
        }
        return -1;
    }

    private @NotNull Configuration validate(@NotNull Configuration g)
      throws IOException
    {
        File classTarget = validateTarget(g.classTarget, "Class");
        File nativeLibraryTarget = validateTarget(g.nativeLibraryTarget, "Native library");
        File nativeFrameworkTarget = validateTarget(g.nativeFrameworkTarget, "Native framework");
        return new Configuration(g.sourceFiles, classTarget, nativeLibraryTarget, nativeFrameworkTarget);
    }

    private @Nullable File validateTarget(@Nullable File dir, @NotNull String kind)
      throws IOException
    {
        if (dir == null) {
            return null;
        }
        if (Files.isDirectory(dir.toPath())) {
            return Utils.resolve(dir).getAbsoluteFile();
        }
        throw new IOException(kind + " not found: " + dir);
    }

    private void execute()
    {
        if (g.classTarget != null || g.nativeLibraryTarget != null || g.nativeFrameworkTarget != null) {
            for (File f : g.sourceFiles) {
                processSource(f);
            }
        }
    }

    private void processSource(@NotNull File f)
    {
        Path p = f.toPath();
        if (Files.isRegularFile(p)) {
            File rf = Utils.resolve(f).getAbsoluteFile();
            if (Utils.isJarFile(f)) {
                String extra = "";
                try {
                    Utils.JarReleaseInfo info = Utils.getJarFileMinimumRelease(f);
                    if (info != null) {
                        if (info.lowestMinimumRelease == info.highestMinimumRelease) {
                            extra = String.format(" [%s]", info.lowestMinimumRelease);
                        } else {
                            extra = String.format(" [%s–%s]", info.lowestMinimumRelease, info.highestMinimumRelease);
                        }
                    }
                } catch (IOException ignore) {
                }
                if (!extra.isEmpty()) {
                    reporter.info(String.format("%-100s%s", "  Expanding: " + f, extra));
                } else {
                    reporter.info(String.format("  Expanding: %s", f));
                }
                expandJarFile(rf);
            } else if (Utils.isNativeLibrary(f)) {
                if (g.nativeLibraryTarget != null) {
                    reporter.info(String.format("  Copying: %s", f));
                    processNativeLibrary(f);
                }
            } else {
                error("Unsupported source file: " + f);
            }
        } else if (Files.isDirectory(p)) {
            File dir = Utils.resolve(f).getAbsoluteFile();
            if (Utils.isNativeLibrarySymbols(f)) {
                if (g.nativeLibraryTarget != null) {
                    reporter.info("  Copying:   " + f);
                    processNativeLibrarySymbols(dir);
                }
            } else if (Utils.isNativeFramework(f)) {
                if (g.nativeFrameworkTarget != null) {
                    reporter.info("  Copying:   " + f);
                    File t = copyNativeFramework(dir, g.nativeFrameworkTarget);
                    if (t != null) {
                        NativeFramework nl = NativeFrameworkImpl.createFramework(t);
                        nativeFrameworks.add(nl);
                    }
                }
            } else if (Utils.isNativeFrameworkSymbols(f)) {
                if (g.nativeFrameworkTarget != null) {
                    reporter.info("  Copying:   " + f);
                    processNativeFrameworkSymbols(f);
                }

            } else {
                reporter.info("  Copying:   " + f);
                copySourceTree(dir);
            }
        } else {
            error("Source not found: " + f);
        }
    }

    private void expandJarFile(@NotNull File f)
    {
        try (ZipFile zf = new ZipFile(f, ZipFile.OPEN_READ)) {
            boolean empty = true;
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                empty = false;
                InputStream is = null;
                reporter.verbose("extracting " + ze.getName());
                try {
                    // Directory entries are not very interesting.
                    // Entities that are directories in the file system (frameworks and .dSYM)
                    // will appear as individual content files in the JAR file.
                    boolean isDirectory = ze.isDirectory();
                    if (!isDirectory) {
                        String entryName = ze.getName();
                        long entryDate = ze.getTime();
                        is = zf.getInputStream(ze);
                        SourceElement e = SourceElement.createZipEntry(entryName, entryDate, isDirectory, zf, ze);
                        copyZipEntry(e);
                    }
                } finally {
                    if (is != null) {
                        is.close();
                    }
                }
            }
            if (empty) {
                reporter.info("JAR file is empty: " + f);
            }
            reporter.verbose("expand complete");
        } catch (IOException e) {
            error("Error while expanding " + f + ":" + e);
        }
    }

    private void copyZipEntry(@NotNull SourceElement e)
    {
        String name = e.getName();
        if (!shouldExclude(name)) {
            if (Utils.isSupportedNativeLibraryRelatedEntry(name)) {
                processNativeLibraryRelatedEntry(e);
            } else if (Utils.isNativeFrameworkEntry(name)) {
                // Native frameworks contain symlinks, which JAR does not support.
                reporter.info("  Skipping native framework in JAR: " + name + " [unsupported]");
            } else {
                if (g.classTarget != null) {
                    copyFileToDirectory(e, g.classTarget.toPath());
                }
            }
        }
    }

    private @Nullable File copyFileToDirectory(@NotNull SourceElement e, @NotNull Path destDir)
    {
        Path target = computeTarget(destDir, e.getName());
        if (target != null) {
            target = prepareTargetFile(e, target);
            if (target != null) {
                try {
                    ensureDirectory(target.getParent());
                } catch (IOException ex) {
                    error(ex.getMessage());
                    return null;
                }
                try {
                    e.copyTo(target.toFile());
                } catch (IOException ex) {
                    error("Unable to copy " + e.getName());
                    return null;
                }
                return target.toFile();
            }
        }
        return null;
    }

    private @Nullable File copyNativeLibrary(@NotNull File f, @NotNull File dir)
    {
        Path source = f.toPath();
        Path destDir = dir.toPath();
        Path dest = computeTarget(destDir, source.getFileName().toString());
        if (dest != null) {
            SourceElement e = SourceElement.createFile(source.toFile());
            try {
                e.copyTo(dest.toFile());
            } catch (IOException ex) {
                error("Unable to copy " + e.getName());
                return null;
            }
            return dest.toFile();
        }
        return null;
    }

    private @Nullable File copyNativeFramework(@NotNull File f, @NotNull File dir)
    {
        Path source = f.toPath();
        Path destDir = dir.toPath();
        Path dest = computeTarget(destDir, source.getFileName().toString());
        if (dest != null) {
            copyDirectory(source, dest);
            return dest.toFile();
        }
        return null;
    }

    private void copySourceTree(@NotNull File sourceDir)
    {
        // Target directories have been validated.

        Path sp = sourceDir.toPath();
        try {
            try (DirectoryStream<Path> s = Files.newDirectoryStream(sp)) {
                for (Path c : s) {
                    copyTopItem(c);
                }
            }
        } catch (IOException e) {
            error("Unable to scan directory: " + sourceDir);
        }
    }

    private void copyTopItem(@NotNull Path p)
    {
        File f = p.toFile();

        if (Utils.isNativeLibrary(f)) {
            processNativeLibrary(f);
        } else if (Utils.isNativeLibrarySymbols(f)) {
            processNativeLibrarySymbols(f);
        } else if (Utils.isNativeFramework(f)) {
            processNativeFramework(f);
        } else if (Utils.isNativeFrameworkSymbols(f)) {
            processNativeFrameworkSymbols(f);
        } else {
            if (g.classTarget != null) {
                copyFileOrDirectory(p, g.classTarget.toPath());
            }
        }
    }

    private void processNativeLibrary(@NotNull File f)
    {
        if (g.nativeLibraryTarget != null) {
            File t = copyNativeLibrary(f, g.nativeLibraryTarget);
            if (t != null) {
                try {
                    NativeLibrary nl = NativeLibrarySupport.createForFile(t, reporter);
                    nativeLibraries.add(nl);
                } catch (BuildException e) {
                    error("Invalid native library: " + f + " [" + e.getMessage() + "]");
                }
            }
        }
    }

    private void processNativeLibrarySymbols(@NotNull File f)
    {
        if (g.nativeLibraryTarget != null) {
            File target = new File(g.nativeLibraryTarget, f.getName());
            copyDirectory(f.toPath(), target.toPath());
            String libraryName = Utils.getSymbolsBase(f.getName());
            if (libraryName != null) {
                nativeLibrarySymbolsMap.put(libraryName, f);
            }
        }
    }

    private void processNativeLibraryRelatedEntry(@NotNull SourceElement e)
    {
        if (g.nativeLibraryTarget != null) {
            String name = e.getName();
            File t = copyFileToDirectory(e, g.nativeLibraryTarget.toPath());
            if (t != null) {
                if (Utils.isNativeLibraryEntry(name)) {
                    try {
                        NativeLibrary nl = NativeLibrarySupport.createForFile(t, reporter);
                        nativeLibraries.add(nl);
                    } catch (BuildException ex) {
                        error("Invalid native library: " + name + " [" + ex.getMessage() + "]");
                    }
                } else {
                    String bundleName = Utils.isNativeLibrarySymbolsDistinguishedEntry(name);
                    if (bundleName != null) {
                        String key = Utils.getLibraryNameFromSymbolsBundleName(bundleName);
                        if (key != null) {
                            File targetBundle = new File(g.nativeLibraryTarget, bundleName);
                            nativeLibrarySymbolsMap.put(key, targetBundle);
                        }
                    }
                }
            }
        }
    }

    private void processNativeFramework(@NotNull File f)
    {
        if (g.nativeFrameworkTarget != null) {
            copyDirectory(f.toPath(), g.nativeFrameworkTarget.toPath());
        }
    }

    private void processNativeFrameworkSymbols(@NotNull File f)
    {
        if (g.nativeFrameworkTarget != null) {
            copyDirectory(f.toPath(), g.nativeFrameworkTarget.toPath());
            String framework = Utils.getSymbolsBase(f.getName());
            if (framework != null) {
                nativeFrameworkSymbolsMap.put(framework, f);
            }
        }
    }

    private void copyDirectory(@NotNull Path source, @NotNull Path dest)
    {
        if (Files.exists(dest, NOFOLLOW_LINKS) && !Files.isDirectory(dest, NOFOLLOW_LINKS)) {
            error("Target is not a directory: " + dest);
        } else if (!Files.exists(dest, NOFOLLOW_LINKS)) {
            try {
                Files.createDirectories(dest);
            } catch (IOException e) {
                error("Unable to create directory: " + dest);
                return;
            }
        }
        try {
            try (DirectoryStream<Path> s = Files.newDirectoryStream(source)) {
                for (Path c : s) {
                    copyFileOrDirectory(c, dest);
                }
            }
        } catch (IOException e) {
            error("Unable to scan directory: " + source);
        }
    }

    private void copyFileOrDirectory(@NotNull Path source, @NotNull Path destDir)
    {
        try {
            if (Files.isRegularFile(source, NOFOLLOW_LINKS)) {
                SourceElement e = SourceElement.createFile(source.toFile());
                copyFileToDirectory(e, destDir);
            } else if (Files.isSymbolicLink(source)) {
                Path dest = computeTarget(destDir, source.getFileName().toString());
                if (dest != null) {
                    ensureDirectory(dest.getParent());
                    copySymlinkToDestination(source, dest);
                }
            } else if (Files.isDirectory(source, NOFOLLOW_LINKS)) {
                Path dest = computeTarget(destDir, source.getFileName().toString());
                if (dest != null) {
                    ensureDirectory(dest.getParent());
                    copyDirectory(source, dest);
                }
            }
        } catch (IOException e) {
            // unable to create parent directories
            error(e.getMessage());
        }
    }

    private void copySymlinkToDestination(@NotNull Path source, @NotNull Path dest)
    {
        if (Files.exists(dest, NOFOLLOW_LINKS)) {
            reporter.info("Target file exists: " + dest);
        } else {
            try {
                Files.createSymbolicLink(dest, Files.readSymbolicLink(source));
            } catch (IOException e) {
                error("Unable to copy symlink " + source + ": " + e);
            }
        }
    }

    private @Nullable Path computeTarget(@NotNull Path dir, @NotNull String targetPath)
    {
        Path target = dir.resolve(Path.of(targetPath));
        if (!target.startsWith(dir)) {
            error("Target " + target + " is outside directory " + dir);
            return null;
        }
        return target;
    }

    /**
      Prepare to write a file.
      @param e The source that provides the content of the file to be written.
      @param target The nominal file to be written.
      @return the actual file to be written, or null if no file should be written.
    */

    private @Nullable Path prepareTargetFile(@NotNull SourceElement e, @NotNull Path target)
    {
        if (Files.isRegularFile(target, NOFOLLOW_LINKS)) {
            if (!allowConflicts(target)) {
                // Conflicts are not allowed. Report an error and abort the operation.
                // TBD: not aborting because I need something to work (VBuilder and VAquaManager)
                reporter.error("Not expanding " + e.getName() + " [duplicate]");
                return null;
            }

            // If the existing file matches the content to be written, no action is needed.
            // If the contents differ, or comparison is not possible, write to a substitute file.
            try {
                File source = e.getSourceFile();
                if (source == null) {
                    Path temp = Files.createTempFile("jarx", null);
                    e.copyTo(temp.toFile());
                    source = temp.toFile();
                }
                if (Utils.sameContents(source, target.toFile())) {
                    reporter.verbose("Skipping entry [duplicate file exists]: " + e.getName());
                    return null;
                }
            } catch (IOException ex) {
                reporter.info("Unable to compare file contents: " + target + " [" + ex + "]");
            }
            reporter.verbose("Existing file has different content: " + e.getName());
            Path t = generateUniqueFile(target);
            reporter.info("Expanding " + e.getName() + " to " + t + " [file collision]");
            return t;
        }

        if (Files.exists(target, NOFOLLOW_LINKS)) {
            reporter.verbose("Collision with a target that is not a regular file: " + e.getName());
            Path t = generateUniqueFile(target);
            reporter.info("Expanding " + e.getName() + " to " + t + " [file collision]");
            return t;
        }

        return target;
    }

    private boolean allowConflicts(@NotNull Path target)
    {
        String name = target.getFileName().toString();
        if (name.endsWith(".class")) {
            // Class file conflicts must be reported
            return false;
        }
        return true;
    }

    /**
      Identify a non-existent file in the same directory as the specified file.
      The specified file must exist, so it must have a valid parent directory.
    */

    private @NotNull Path generateUniqueFile(@NotNull Path p)
    {
        Path parent = p.getParent();
        assert parent != null;
        String name = p.getFileName().toString();
        int counter = 1;
        Path target;
        do {
            target = createTarget(parent, name, counter);
            counter++;
        } while (Files.exists(target, NOFOLLOW_LINKS));
        return target;
    }

    private @NotNull Path createTarget(@NotNull Path parent, @NotNull String name, int index)
    {
        String start = name;
        String end = "";
        int pos = name.lastIndexOf('.');
        if (pos >= 0) {
            start = name.substring(0, pos);
            end = name.substring(pos);
        }
        return parent.resolve(start + "-" + index + end);
    }

    private void ensureDirectory(@NotNull Path dir)
      throws IOException
    {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IOException("Unable to create target directory");
        }
    }

    public boolean shouldExclude(@NotNull String entryName)
    {
        if (entryName.startsWith("META-INF/")) {
            String rest = entryName.substring(9);
            if (rest.equals("MANIFEST.MF") || rest.equals("INDEX.LIST") || rest.startsWith("maven/")) {
                return true;
            }
            // TBD: move top level license files into a subdirectory
            if (rest.endsWith(".SF") || rest.endsWith(".RSA")) {
                return true;
            }
            if (rest.startsWith("services/")) {
                // TBD: if needed, could combine these files
                return true;
            }
        }

        if (Utils.isSkippableEntry(entryName)) {
            return true;
        }

        return false;
    }

    private void error(@NotNull String message)
    {
        errorsFound = true;
        reporter.error(message);
    }
}
