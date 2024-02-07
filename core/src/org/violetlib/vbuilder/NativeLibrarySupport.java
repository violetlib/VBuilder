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
import org.violetlib.collections.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.Set;

/**

*/

public class NativeLibrarySupport
{
    public static @NotNull NativeLibrary createNativeLibrary(@NotNull String basicName,
                                                             @NotNull IMap<Architecture,File> desc)
      throws IllegalArgumentException
    {
        if (desc.isEmpty()) {
            throw new IllegalArgumentException("At least one file must be specified");
        }
        if (desc.size() == 1) {
            Architecture a = desc.keySet().choose();
            File f = desc.get(a);
            assert f != null;
            return SingleArchitectureNativeLibrary.create(basicName, a, f, null);
        }

        ISet<Architecture> architectures = desc.keySet();
        ISet<File> files = desc.values();
        if (files.size() == 1) {
            return MultipleArchitectureNativeLibrary.create(basicName, architectures, files.choose(), null);
        }
        return MultipleNativeLibrary.create(basicName, desc, null);
    }

    public static @NotNull NativeLibrary createForFile(@NotNull File f, @NotNull Reporter reporter)
      throws BuildException
    {
        String name = toLibraryName(f);
        if (name == null) {
            throw new BuildException("Unrecognized library file name: " + f.getName());
        }

        IMap<String,ISet<String>> deps = LibraryDependencies.getRawDependencies(f, reporter);
        ISet<Architecture> as = getArchitectures(deps.keySet());
        if (as.isEmpty()) {
            throw new BuildException("File supports no known architectures");
        }
        if (as.size() == 1) {
            return SingleArchitectureNativeLibrary.create(name, as.choose(), f, null);
        }
        return MultipleArchitectureNativeLibrary.create(name, as, f, null);
    }

    /**
      Special case for the dynamic library in a framework, which has a plain file name.
    */

    public static @NotNull NativeLibrary createForFrameworkFile(@NotNull File f, @NotNull Reporter reporter)
      throws BuildException
    {
        IMap<String,ISet<String>> deps = LibraryDependencies.getRawDependencies(f, reporter);
        ISet<Architecture> as = getArchitectures(deps.keySet());
        if (as.isEmpty()) {
            throw new BuildException("File supports no known architectures");
        }
        String name = f.getName();
        if (as.size() == 1) {
            return SingleArchitectureNativeLibrary.create(name, as.choose(), f, null);
        }
        return MultipleArchitectureNativeLibrary.create(name, as, f, null);
    }

    private static @NotNull ISet<Architecture> getArchitectures(@NotNull ISet<String> names)
    {
        SetBuilder<Architecture> b = ISet.builder();
        for (String name : names) {
            Architecture a = ArchitectureUtils.parseArchitecture(name);
            b.addOptional(a);
        }
        return b.values();
    }

    public static @Nullable String toLibraryName(@NotNull File f)
    {
        return toLibraryName(f.getName());
    }

    public static @Nullable String toLibraryName(@NotNull String name)
    {
        if (name.startsWith("lib") && name.endsWith(".dylib")) {
            String basic = name.substring(3, name.length() - 6);
            return removeVersionNumbers(basic);
        }

        return null;
    }

    private static @NotNull String removeVersionNumbers(@NotNull String s)
    {
        int pos = s.lastIndexOf('.');
        if (pos < 0) {
            return s;
        }
        String v = s.substring(pos+1);
        if (!v.isEmpty()) {
            int n = -1;
            try {
                n = Integer.parseInt(v);
            } catch (NumberFormatException ignore) {
            }
            if (n >= 0) {
                return removeVersionNumbers(s.substring(0, pos));
            }
        }
        return s;
    }

    public static @NotNull String createLibraryFileName(@NotNull String name)
    {
        return "lib" + name + ".dylib";
    }

    public static @NotNull File createUniversalLibrary(@NotNull String name, @NotNull ICollection<File> libs)
      throws IOException
    {
        if (libs.isEmpty()) {
            throw new IllegalArgumentException("At least one library file must be specified");
        }

        File program = new File("/usr/bin/lipo");
        File outputFile = File.createTempFile("lib" + name, ".dylib");

        ListBuilder<String> b = IList.builder();
        for (File lib : libs) {
            b.add(lib.getAbsolutePath());
        }
        b.add("-create");
        b.add("-output");
        b.add(outputFile.getAbsolutePath());
        IList<String> args = b.values();

        ExecutionConfiguration g = ExecutionConfiguration.create(program, "lipo", args);
        ExecutionService es = ExecutionService.get();
        ExecutionResult result = es.execute(g);

        if (result.rc != 0) {
            throw new IOException("/usr/bin/lipo failed: " + result.rc);
        }

        return outputFile;
    }

    public static void setLinkerId(@NotNull File f, @NotNull String id)
      throws IOException
    {
        File program = new File("/usr/bin/install_name_tool");
        IList<String> args = IList.of("-id", id, f.getAbsolutePath());

        ExecutionConfiguration g = ExecutionConfiguration.create(program, "install_name_tool", args);
        ExecutionService es = ExecutionService.get();
        ExecutionResult result = es.execute(g);

        if (result.rc != 0) {
            throw new IOException("/usr/bin/install_name_tool failed: " + result.rc);
        }
    }

    public static @NotNull ISet<Architecture> getArchitectures(@NotNull File f)
      throws IOException
    {
        String s = getRawArchitectures(f);
        return parseRawArchitectures(s);
    }

    private static @NotNull String getRawArchitectures(@NotNull File f)
      throws IOException
    {
        File program = new File("/usr/bin/file");
        IList<String> args = IList.of(f.getAbsolutePath());

        ExecutionConfiguration g = ExecutionConfiguration.create(program, "get_raw_architectures", args);
        ExecutionService es = ExecutionService.get();
        ExecutionResult result = es.execute(g);

        if (result.rc != 0) {
            throw new IOException("/usr/bin/file failed: " + result.rc);
        }

        return result.output;
    }

    private static @NotNull ISet<Architecture> parseRawArchitectures(@NotNull String s)
      throws IOException
    {
        // <file>: Mach-O universal binary with 2 architectures: [x86_64:Mach-O 64-bit executable x86_64] [arm64:Mach-O 64-bit executable arm64]
        // <file>: Mach-O universal binary with 2 architectures: [x86_64:Mach-O 64-bit dynamically linked shared library x86_64] [arm64:Mach-O 64-bit dynamically linked shared library arm64]
        // <file>: Mach-O 64-bit executable x86_64

        Optional<String> o = s.lines().findFirst();
        if (o.isPresent()) {
            String line = o.get();
            int pos = line.indexOf("universal binary");
            if (pos > 0) {
                return parseRawArchitecturesUniversal(line, pos);
            }
            pos = line.lastIndexOf(' ');
            if (pos > 0) {
                String name = line.substring(pos+1);
                Architecture a = ArchitectureUtils.parseArchitecture(name);
                if (a != null) {
                    return ISet.of(a);
                }
                throw new IOException("Unable to parse file description: " + line);
            }
        }
        throw new IOException("No file description found");
    }

    private static @NotNull ISet<Architecture> parseRawArchitecturesUniversal(@NotNull String line, int p)
      throws IOException
    {
        SetBuilder<Architecture> b = ISet.builder();
        String s = line.substring(p);
        while (!s.isEmpty()) {
            int pos = s.indexOf('[');
            if (pos <= 0) {
                ISet<Architecture> architectures = b.values();
                if (!architectures.isEmpty()) {
                    return architectures;
                }
                break;
            }
            s = s.substring(pos + 1);
            pos = s.indexOf(':');
            if (pos <= 0) {
                break;
            }
            String name = s.substring(0, pos);
            s = s.substring(pos + 1);
            Architecture a = ArchitectureUtils.parseArchitecture(name);
            if (a == null) {
                break;
            }
            b.add(a);
        }
        throw new IOException("Unable to parse file description: " + line);
    }

    /**
      Search the specified directories for a library that supports the designated architectures or a collection of
      libraries that collectively support the designated architectures.

      @param searchPath The directories to search. Directories and libraries may be specified using symbolic links.
      Invalid library files may be skipped. In particular, a library file that cannot be analyzed for architectural
      support is skipped.

      @param name The basic library name, for example {@code "fft"}, not {@code "libfft.dylib"}.

      @param architectures The required architectures.

      @return a native library that supports the specified architectures based on library files with the
      specified name, or null if no such library is found.

      @throws IllegalArgumentException if {@code architectures} is empty.
      @throws IOException if the searchPath contains elements that do not identify a directory.
    */

    public static @Nullable NativeLibrary findLibrary(@NotNull IList<File> searchPath,
                                                      @NotNull String name,
                                                      @NotNull ISet<Architecture> architectures)
      throws IOException
    {
        if (architectures.isEmpty()) {
            throw new IllegalArgumentException("At least one architecture must be specified");
        }

        IList<File> candidates = searchForLibrary(searchPath, name);
        if (candidates.isEmpty()) {
            return null;
        }

        Set<Architecture> remaining = architectures.toJavaSet();
        MapBuilder<Architecture,File> b = IMap.builder();
        for (File candidate : candidates) {
            try {
                ISet<Architecture> archs = getArchitectures(candidate);
                for (Architecture a : archs) {
                    if (remaining.contains(a)) {
                        b.put(a, candidate);
                        remaining.remove(a);
                        if (remaining.isEmpty()) {
                            break;
                        }
                    }
                }
                if (remaining.isEmpty()) {
                    break;
                }
            } catch (IOException ignore) {
            }
        }

        if (remaining.isEmpty()) {
            IMap<Architecture,File> m = b.value();
            return createNativeLibrary(name, m);
        }
        return null;
    }

    /**
      Search the specified directories for libraries that match the specified library name.

      @param searchPath The directories to search. Directories and libraries may be specified using symbolic links.
      Library files are not validated.

      @param name The basic library name, for example {@code "fft"}, not {@code "libfft.dylib"}.

      @return the library files found, as absolute paths with no symlinks.

      @throws IOException if the searchPath contains elements that do not identify a directory.
    */

    public static @NotNull IList<File> searchForLibrary(@NotNull IList<File> searchPath, @NotNull String name)
      throws IOException
    {
        String fileName = createLibraryFileName(name);

        ListBuilder<File> b = IList.builder();
        for (File dir : searchPath) {
            if (Files.isDirectory(dir.toPath())) {
                File d = Utils.resolve(dir);
                File f = new File(d, fileName);
                if (Files.isRegularFile(f.toPath())) {
                    b.add(Utils.resolve(f).getAbsoluteFile());
                }
            } else {
                throw new IOException("Not a directory: " + dir);
            }
        }
        return b.values();
    }
}
