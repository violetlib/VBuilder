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

import java.io.*;
import java.nio.file.*;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
  Utilities that are not dependent on Ant or any other build system.
*/

public class Utils
{
    static final int BUFFER_SIZE = 1024;

    public static void validateRuntime(@NotNull File jdk)
      throws IllegalArgumentException, FileNotFoundException
    {
        jdk = resolve(jdk);  // Allow links to the root directory

        if (!Files.exists(jdk.toPath(), NOFOLLOW_LINKS)) {
            throw new FileNotFoundException("not found: " + jdk);
        }

        if (!Files.isDirectory(jdk.toPath(), NOFOLLOW_LINKS)) {
            throw new FileNotFoundException("not a directory: " + jdk);
        }

        File home = new File(jdk, "Contents/Home");
        if (!Files.isDirectory(home.toPath(), NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Contents/Home not found: " + jdk);
        }
        File bin = new File(home, "bin");
        if (!Files.isDirectory(bin.toPath(), NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Contents/Home/bin not found: " + jdk);
        }
        File lib1 = new File(home, "lib/libjli.dylib");
        File lib2 = new File(home, "lib/jli/libjli.dylib");
        File lib3 = new File(home, "jre/lib/jli/libjli.dylib");
        if (!Files.isRegularFile(lib1.toPath(), NOFOLLOW_LINKS)
          && !Files.isRegularFile(lib2.toPath(), NOFOLLOW_LINKS)
          && !Files.isRegularFile(lib3.toPath(), NOFOLLOW_LINKS)
        ) {
            throw new IllegalArgumentException("launcher not found: " + jdk);
        }
    }

    /**
      Resolve a symlink to the actual path, if possible.
      @param f The file path to resolve.
      @return {@code f} if {@code f} is not a symlink or the symlink cannot be resolved, otherwise return a path to
      the current target of the symlink.
    */

    public static @NotNull File resolve(@NotNull File f)
    {
        if (Files.isSymbolicLink(f.toPath())) {
            try {
                return f.getCanonicalFile();
            } catch (IOException ignore) {
            }
        }
        return f;
    }

    public static @NotNull File getConfiguredJavaHome()
    {
        String s = System.getenv("JAVA_HOME");
        if (s != null) {
            File dir = new File(s);
            if (Files.isDirectory(dir.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                return dir;
            }
        }

        return getJavaHome();
    }

    public static @NotNull File getJavaHome()
    {
        String s = System.getProperty("java.home");
        if (s != null) {
            File f = new File(s);
            if (Files.isDirectory(f.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                return f;
            }
            throw new UnsupportedOperationException("Unexpected: java.home is not a directory: " + s);
        }
        throw new UnsupportedOperationException("Unexpected: java.home is undefined");
    }


    public static @NotNull ISet<Architecture> getJavaRuntimeArchitectures(@NotNull File runtime)
      throws IOException
    {
        runtime = resolve(runtime);  // Allow links to the root directory
        File home = new File(runtime, "Contents/Home");
        if (Files.isDirectory(home.toPath(), NOFOLLOW_LINKS)) {
            File lib = new File(home, "lib");
            if (!Files.isDirectory(lib.toPath(), NOFOLLOW_LINKS)) {
                lib = new File(home, "jre/lib");
                if (!Files.isDirectory(lib.toPath(), NOFOLLOW_LINKS)) {
                    throw new IOException("Not a valid Java runtime (unable to find libjli): " + runtime);
                }
            }
            File f = new File(lib, "libjli.dylib");
            return NativeLibrarySupport.getArchitectures(f);
        }
        throw new IOException("Not a valid Java runtime (unable to find Contents/Home): " + runtime);
    }

    /**
      Find an executable using the PATH.
    */

    public static @Nullable File findExecutable(@NotNull String name)
    {
        String s = System.getenv("PATH");
        if (s == null) {
            return null;
        }
        String[] paths = s.split(File.pathSeparator);
        for (String path : paths) {
            File f = new File(path, name);
            Path p = f.toPath();
            if (Files.isRegularFile(p) && Files.isExecutable(p)) {
                return f;
            }
        }
        return null;
    }

    /**
      Return the files in the specified directory tree.
      @param tree The directory whose contained files are to be returned. This parameter may be a symbolic link to a
      directory.
      @param convertToAbsolute If true, files are converted to absolute paths.
      @return The regular files contained in the tree.
      @throws IOException if {@code tree} does not identify a directory, or an error occurred traversing the tree.
    */

    public static @NotNull IList<File> getFilesInTree(@NotNull File tree, boolean convertToAbsolute)
      throws IOException
    {
        if (Files.isDirectory(tree.toPath())) {
            File dir = resolve(tree).getAbsoluteFile();
            TreeFileCollector c = new TreeFileCollector(dir, convertToAbsolute);
            return c.getFiles();
        }
        throw new IOException("Not a directory");
    }

    public static boolean isJarFile(@NotNull File f)
    {
        return isJarFile(f.getName());
    }

    public static boolean isJarFile(@NotNull String name)
    {
        return name.endsWith(".jar");
    }

    public static boolean isModularJarFile(@NotNull File f)
      throws IOException
    {
        JarFile jf = new JarFile(f, false);
        Enumeration<JarEntry> m = jf.entries();
        while (m.hasMoreElements()) {
            JarEntry e = m.nextElement();
            if (e.getName().equals("module-info.class")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSkippableEntry(@NotNull String name)
    {
        return name.endsWith(".so")
          || name.endsWith(".dll");
    }

    public static boolean isSupportedNativeLibraryRelatedEntry(@NotNull String name)
    {
        return !name.contains("/") && (name.endsWith(".dylib") || name.contains("dylib.dSYM/"));
    }

    public static boolean isNativeLibraryEntry(@NotNull String name)
    {
        return isNativeLibrary(name);
    }

    public static boolean isNativeLibrary(@NotNull File f)
    {
        return isNativeLibrary(f.getName())
          && !f.getPath().contains("dylib.dSYM/")
          && Files.isRegularFile(f.toPath(), NOFOLLOW_LINKS);
    }

    private static boolean isNativeLibrary(@NotNull String name)
    {
        return name.endsWith(".dylib");
    }

    public static boolean isNativeLibrarySymbols(@NotNull File f)
    {
        return isNativeLibrarySymbols(f.getName()) && Files.isDirectory(f.toPath(), NOFOLLOW_LINKS);
    }

    /**
      Determine if the specified file is the distinguished file of a debug symbols bundle.
      This method is used when iterating over regular files.

      @param f The file to test.
      @return the bundle directory, or null if the file is not the distinguished file of a debug symbols bundle.
    */

    public static @Nullable File isNativeLibrarySymbolsDistinguishedFile(@NotNull File f)
    {
        String path = f.getPath();
        if (path.endsWith(".dylib.dSYM/Contents/Info.plist")) {
            String bundlePath = path.substring(0, path.length() - 20);
            return new File(bundlePath);
        }
        return null;
    }

    public static boolean isNativeLibrarySymbols(@NotNull String name)
    {
        return name.endsWith(".dylib.dSYM");
    }

    /**
      Determine if the specified JAR entry name is the distinguished file of a debug symbols bundle.

      @param name The name to test.
      @return the name of the corresponding bundle directory, or null if the name is not the distinguished file of a
      debug symbols bundle.
    */

    public static @Nullable String isNativeLibrarySymbolsDistinguishedEntry(@NotNull String name)
    {
        // We need a singular JAR file entry to recognize the presence of a debug symbols directory.
        // There need not be JAR entries for directories.

        if (name.endsWith(".dylib.dSYM/Contents/Info.plist")) {
            return name.substring(0, name.length() - 20);
        }
        return null;
    }

    public static boolean isNativeFramework(@NotNull File f)
    {
        return isNativeFramework(f.getName()) && Files.isDirectory(f.toPath(), NOFOLLOW_LINKS);
    }

    private static boolean isNativeFramework(@NotNull String name)
    {
        return name.endsWith(".framework");
    }

    public static boolean isNativeFrameworkSymbols(@NotNull File f)
    {
        return isNativeFrameworkSymbols(f.getName()) && Files.isDirectory(f.toPath(), NOFOLLOW_LINKS);
    }

    public static boolean isNativeFrameworkSymbols(@NotNull String name)
    {
        return name.endsWith(".framework.dSYM");
    }

    public static boolean isNativeFrameworkEntry(@NotNull String name)
    {
        return name.contains(".framework/") || name.contains(".framework.dSYM/");
    }

    public static @Nullable String getSymbolsBase(@NotNull String name)
    {
        if (name.endsWith(".dSYM")) {
            return name.substring(0, name.length() - 5);
        }
        if (name.endsWith(".dSYM/Contents/Info.plist")) {
            // the distinguished entry
            return name.substring(0, name.length() - 25);
        }
        return null;
    }

    public static @Nullable File getLibraryFromSymbolsBundle(@NotNull File f)
    {
        String path = f.getPath();
        if (path.endsWith(".dSYM")) {
            String s = path.substring(0, path.length() - 5);
            return new File(s);
        }
        return null;
    }

    public static @Nullable String getLibraryNameFromSymbolsBundleName(@NotNull String name)
    {
        if (name.endsWith(".dSYM")) {
            return name.substring(0, name.length() - 5);
        }
        return null;
    }

    public static class JarReleaseInfo
    {
        public final int lowestMinimumRelease;
        public final int highestMinimumRelease;

        public JarReleaseInfo(int lowestMinimumRelease, int highestMinimumRelease)
        {
            this.lowestMinimumRelease = lowestMinimumRelease;
            this.highestMinimumRelease = highestMinimumRelease;
        }
    }

    /**
      Examine the minimum JDK releases of the class file entries of the specified JAR file. return the lowest and
      highest minimum JDK release, or null if the JAR file contains no class file entries.
      @throws IOException if an error occurs.
    */

    public static @Nullable JarReleaseInfo getJarFileMinimumRelease(@NotNull File f)
      throws IOException
    {
        int lowestMinimum = Integer.MAX_VALUE;
        int highestMinimum = 0;

        try (JarFile jf = new JarFile(f)) {
            Enumeration<JarEntry> m = jf.entries();
            while (m.hasMoreElements()) {
                JarEntry e = m.nextElement();
                String name = e.getName();  // getRealName if multi-release JAR
                if (name.endsWith(".class")) {
                    int r = getMinimumRelease(jf, e);
                    if (r > highestMinimum) {
                        highestMinimum = r;
                    }
                    if (r < lowestMinimum) {
                        lowestMinimum = r;
                    }
                }
            }
        }

        if (highestMinimum > 0) {
            return new JarReleaseInfo(lowestMinimum, highestMinimum);
        }
        return null;
    }

    private static int getMinimumRelease(@NotNull JarFile jf, @NotNull JarEntry e)
      throws IOException
    {
        try (InputStream s = jf.getInputStream(e)) {
            s.skip(6);
            int b1 = s.read();
            int b2 = s.read();
            if (b1 >= 0 && b2 >= 0) {
                int version = (b1 << 8) + b2;
                return getReleaseFromClassVersion(version);
            }
            throw new EOFException();
        }
    }

    private static int getReleaseFromClassVersion(int version)
    {
        // TBD: presumes each major release increments the major version number
        return Math.max(8, version - 44);
    }

    public static int getClassFileMinimumRelease(@NotNull File f)
      throws IOException
    {
        int version = getClassFileVersion(f);
        return getReleaseFromClassVersion(version);
    }

    public static int getClassFileVersion(@NotNull File f)
      throws IOException
    {
        try (InputStream s = Files.newInputStream(f.toPath())) {
            s.skip(6);
            int b1 = s.read();
            int b2 = s.read();
            if (b1 >= 0 && b2 >= 0) {
                return (b1 << 8) + b2;
            }
            throw new EOFException();
        }
    }

    public static void validateLibraryName(@NotNull String name)
      throws IllegalArgumentException
    {
        // Allowed characters: letters, digits (not leading), underscore
        int count = name.length();
        if (count == 0) {
            throw new IllegalArgumentException("Library name must not be empty");
        }
        for (int i = 0; i < count; i++) {
            char ch = name.charAt(i);
            if (Character.isWhitespace(ch)) {
                throw new IllegalArgumentException("Library name must not contain whitespace");
            }
            if (Character.isDigit(ch)) {
                if (i == 0) {
                    throw new IllegalArgumentException("Library name must not start with a digit");
                }
                continue;
            }
            if (Character.isAlphabetic(ch) || ch == '_') {
                continue;
            }
            throw new IllegalArgumentException("Invalid character in library name");
        }
    }

    public static @NotNull String replace(@NotNull String source,
                                          @NotNull String pattern,
                                          @NotNull String replacement)
    {
        int ps = source.indexOf(pattern);
        if (ps < 0) {
            return source;
        }
        int plen = pattern.length();
        StringBuilder sb = new StringBuilder();
        int next = 0;  // index of next character to be copied
        while (ps >= 0) {
            // found next pattern at offset PS
            sb.append(source, next, ps);
            sb.append(replacement);
            next = ps + plen;
            ps = source.indexOf(pattern, next);
        }
        sb.append(source.substring(next));
        return sb.toString();
    }

    private static class TreeFileCollector
    {
        private final boolean convertToAbsolute;
        private final @NotNull ListBuilder<File> b = IList.builder();
        private final @NotNull IList<File> result;

        public TreeFileCollector(@NotNull File dir, boolean convertToAbsolute)
          throws IOException
        {
            this.convertToAbsolute = convertToAbsolute;
            try (Stream<Path> s = Files.walk(dir.toPath())) {
                s.forEach(this::process);
            }
            result = b.values();
        }

        public @NotNull IList<File> getFiles()
        {
            return result;
        }

        private void process(@NotNull Path p)
        {
            if (Files.isRegularFile(p, NOFOLLOW_LINKS)) {
                File f = p.toFile();
                if (convertToAbsolute) {
                    f = f.getAbsoluteFile();
                }
                b.add(f);
            }
        }
    }

    /**
      Delete (recursively) the contents of a directory.
      @param dir The directory, which may be identified by a symlink. Symlinks in the directory contents are
      not followed.
      @return true if the directory exists and its contents were deleted, false if the directory does not exist.
      @throws IOException if some file or directory could not be deleted.
    */

    public static boolean deleteDirectoryContents(@NotNull File dir)
      throws IOException
    {
        if (!Files.isDirectory(dir.toPath())) {
            return false;
        }
        Path p = dir.toPath().toRealPath();
        deleteDirectoryContents(p);
        return true;
    }

    /**
      Delete the specified file, directory (recursively), or symbolic link.
      Symbolic links are not followed.
      @throws IOException if an error occurred.
    */

    public static void deleteFileItem(@NotNull Path p)
      throws IOException
    {
        if (Files.isDirectory(p, NOFOLLOW_LINKS)) {
            deleteDirectory(p);
        } else {
            Files.delete(p);
        }
    }

    /**
      Delete (recursively) a directory and its contents.
      Symbolic links are not followed.
      @param p The directory.
      @throws IOException if an error occurred.
    */

    public static void deleteDirectory(@NotNull Path p)
      throws IOException
    {
        for (int i = 0; i < 2; i++) {
            try {
                deleteDirectoryContents(p);
                Files.delete(p);
                return;
            } catch (DirectoryNotEmptyException e) {
                // Can happen when deletion provokes the creation of .DS_Store
            }
        }
        throw new DirectoryNotEmptyException(p.toString());
    }

    /**
      Delete (recursively) the contents of a directory.
      Symbolic links are not followed.
      @param p The directory.
      @throws IOException if an error occurred.
    */

    public static void deleteDirectoryContents(@NotNull Path p)
      throws IOException
    {
        try (DirectoryStream<Path> s = Files.newDirectoryStream(p)) {
            for (Path c : s) {
                deleteFileItem(c);
            }
        }
    }

    public static boolean sameContents(@NotNull File f1, @NotNull File f2)
      throws IOException
    {
        long fileLength = f1.length();
        if (f2.length() != fileLength) {
            return false;
        }
        byte[] buffer1 = new byte[BUFFER_SIZE];
        byte[] buffer2 = new byte[BUFFER_SIZE];
        try (InputStream s1 = Files.newInputStream(f1.toPath())) {
            try (InputStream s2 = Files.newInputStream(f2.toPath())) {
                int length;
                while ((length = s1.read(buffer1)) >= 0) {
                    int length2 = s2.read(buffer2);
                    if (length2 != length) {
                        return false;
                    }
                    for (int i = 0; i < length; i++) {
                        if (buffer1[i] != buffer2[i]) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    public static void copyFile(@NotNull File source, @NotNull File dest)
      throws IOException
    {
        Files.copy(source.toPath(), dest.toPath(), NOFOLLOW_LINKS, COPY_ATTRIBUTES, REPLACE_EXISTING);
    }

    /**
      Copy a directory (recursively).
      Symbolic links are not followed.
      @param source The source directory, which must exist.
      @param dest The destination directory, which must not exist and will be created.
      @param excludes Files whose name matches or starts with an element of this set are not copied.
      {@code .DS_Store} files are also not copied.
    */

    public static void copyDirectory(@NotNull File source, @NotNull File dest, @NotNull ISet<String> excludes)
      throws IOException
    {
        Path sourcePath = source.toPath();
        Path destPath = dest.toPath();
        IList<Path> rfs = getFilesToCopy(sourcePath, excludes);
        for (Path rf : rfs) {
            Path sourceFile = sourcePath.resolve(rf);
            Path destFile = destPath.resolve(rf);
            try {
                Files.copy(sourceFile, destFile, COPY_ATTRIBUTES, NOFOLLOW_LINKS, REPLACE_EXISTING);
            } catch (FileAlreadyExistsException | DirectoryNotEmptyException ignore) {
            }
        }
    }

    private static @NotNull IList<Path> getFilesToCopy(@NotNull Path sourcePath,
                                                       @NotNull ISet<String> excludes)
      throws IOException
    {
        try (Stream<Path> files = Files.walk(sourcePath)) {
            return files.map(sourcePath::relativize)
              .filter(x -> !x.endsWith(".DS_Store") && !isExcluded(x, excludes))
              .collect(IList.collector());
        }
    }

    private static boolean isExcluded(@NotNull Path p, @NotNull ISet<String> excludes)
    {
        // Not perfect code, but good enough
        String s = p.toString();
        for (String exclude : excludes) {
            if (s.startsWith(exclude)) {
                return true;
            }
        }
        return false;
    }

    /**
      Copy a directory (recursively).
      Symbolic links are not followed.
      @param source The source directory, which must exist.
      @param dest The destination directory, which must not exist and will be created.
    */

    public static void copyDirectory(@NotNull File source, @NotNull File dest)
      throws IOException
    {
        if (!Files.isDirectory(source.toPath(), NOFOLLOW_LINKS)) {
            throw new IOException("Not a directory: " + source);
        }
        if (Files.exists(dest.toPath(), NOFOLLOW_LINKS)) {
            throw new IOException("Destination exists: " + dest);
        }
        Files.createDirectories(dest.toPath());
        copyDirectoryContents(source, dest);
    }

    /**
      Copy a directory (recursively).
      Symbolic links are not followed.
      @param source The source directory, which must exist.
      @param dest The destination directory, which will be created. If the directory already exists, the old
      directory is first deleted.
    */

    public static void copyDirectoryReplacing(@NotNull File source, @NotNull File dest)
      throws IOException
    {
        if (!Files.isDirectory(source.toPath(), NOFOLLOW_LINKS)) {
            throw new IOException("Not a directory: " + source);
        }
        if (Files.exists(dest.toPath(), NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(dest.toPath(), NOFOLLOW_LINKS)) {
                throw new IOException("Destination exists [not a directory: " + dest);
            }
            deleteDirectory(dest.toPath());

        }
        Files.createDirectories(dest.toPath());
        copyDirectoryContents(source, dest);
    }

    /**
      Copy a directory (recursively).
      Symbolic links are not followed.
      @param source The source directory, which must exist.
      @param dest The destination directory, which will be created if needed.
    */

    public static void mergeDirectory(@NotNull File source, @NotNull File dest)
      throws IOException
    {
        if (!Files.isDirectory(source.toPath(), NOFOLLOW_LINKS)) {
            throw new IOException("Not a directory: " + source);
        }
        Files.createDirectories(dest.toPath());
        mergeDirectoryContents(source, dest);
    }

    /**
      Copy the contents (recursively) of a source directory to a target directory.
      Symbolic links are not followed.
      Both directories must exist.
    */

    public static void copyDirectoryContents(@NotNull File source, @NotNull File dest)
      throws IOException
    {
        Path sp = source.toPath();
        Path dp = dest.toPath();

        if (!Files.isDirectory(sp, NOFOLLOW_LINKS)) {
            throw new IOException("Not a directory: " + sp);
        }

        if (!Files.isDirectory(dp, NOFOLLOW_LINKS)) {
            throw new IOException("Not a directory: " + dp);
        }

        try (DirectoryStream<Path> s = Files.newDirectoryStream(sp)) {
            for (Path c : s) {
                copyFileOrDirectory(c, dp);
            }
        }
    }

    /**
      Copy the contents (recursively) of a source directory to a target directory.
      Symbolic links are not followed.
      Both directories must exist.
    */

    public static void mergeDirectoryContents(@NotNull File source, @NotNull File dest)
      throws IOException
    {
        Path sp = source.toPath();
        Path dp = dest.toPath();

        if (!Files.isDirectory(sp, NOFOLLOW_LINKS)) {
            throw new IOException("Not a directory: " + sp);
        }

        if (!Files.isDirectory(dp, NOFOLLOW_LINKS)) {
            throw new IOException("Not a directory: " + dp);
        }

        try (DirectoryStream<Path> s = Files.newDirectoryStream(sp)) {
            for (Path c : s) {
                mergeFileOrDirectory(c, dp);
            }
        }
    }

    private static void copyFileOrDirectory(@NotNull Path p, @NotNull Path dest)
      throws IOException
    {
        Path dp = dest.resolve(p.getFileName());

        if (Files.isRegularFile(p, NOFOLLOW_LINKS)) {
            copyFile(p.toFile(), dp.toFile());
            try {
                Files.setLastModifiedTime(dp, Files.getLastModifiedTime(p, NOFOLLOW_LINKS));
            } catch (IOException ignore) {
            }
        } else if (Files.isSymbolicLink(p)) {
            Files.createSymbolicLink(dp, Files.readSymbolicLink(p));
        } else if (Files.isDirectory(p, NOFOLLOW_LINKS)) {
            copyDirectory(p.toFile(), dp.toFile());
        }
    }

    public static void mergeFileOrDirectory(@NotNull Path p, @NotNull Path dest)
      throws IOException
    {
        Path dp = dest.resolve(p.getFileName());

        if (Files.isRegularFile(p, NOFOLLOW_LINKS)) {
            copyFile(p.toFile(), dp.toFile());
            try {
                Files.setLastModifiedTime(dp, Files.getLastModifiedTime(p, NOFOLLOW_LINKS));
            } catch (IOException ignore) {
            }
        } else if (Files.isSymbolicLink(p)) {
            Files.createSymbolicLink(dp, Files.readSymbolicLink(p));
        } else if (Files.isDirectory(p, NOFOLLOW_LINKS)) {
            mergeDirectory(p.toFile(), dp.toFile());
        }
    }

    public static void moveDirectoryToOutput(@NotNull File source, @NotNull File target)
      throws MessageException
    {
        try {
            Path sourcePath = source.toPath();
            Path targetPath = target.toPath();
            if (!Files.isDirectory(sourcePath, NOFOLLOW_LINKS)) {
                throw MessageException.create("not a directory", source);
            }
            if (Files.isDirectory(targetPath, NOFOLLOW_LINKS)) {
                deleteDirectory(targetPath);
            } else if (Files.exists(targetPath, NOFOLLOW_LINKS)) {
                throw MessageException.create("exists", target);
            }
            Files.move(sourcePath, targetPath);
        } catch (IOException e) {
            throw MessageException.create(e, null);
        }
    }

    public static void setLastModifiedTime(@NotNull File f, long time)
      throws IOException
    {
        if (!f.setLastModified(time)) {
            throw new IOException("Failed to change file modification time of " + f);
        }
    }

//    protected boolean fileIsSigned(@NotNull File f)
//    {
//        File program = new File("/usr/bin/codesign");
//        if (Files.isRegularFile(program.toPath())) {
//            String[] command = { program.getAbsolutePath(), "--display", f.getAbsolutePath() };
//            ByteArrayOutputStream os = new ByteArrayOutputStream();
//            PumpStreamHandler h = new PumpStreamHandler(os);
//            Project project = getProject();
//            try {
//                Execute exe = new Execute();
//                exe.setStreamHandler(h);
//                exe.setAntRun(project);
//                exe.setCommandline(command);
//                exe.execute();
//                String output = os.toString(StandardCharsets.UTF_8);
//                return !output.contains("code object is not signed at all");
//            } catch (IOException e) {
//                throw new BuildException("Error running codesign", e, getLocation());
//            }
//        } else {
//            throw new BuildException("Code sign program not found");
//        }
//    }

    public static File createTemporaryDirectory(@NotNull String prefix)
      throws IOException
    {
        Path p = Files.createTempDirectory(prefix);
        return p.toFile();
    }
}
