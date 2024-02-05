package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.collections.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
  An interface to the otool command to obtain library dependencies.
*/

public class LibraryDependencies
{
    /**
      Identify the dependencies of the specified native library.
      @param lib The native library.
      @param pathToBasicName A function that maps a library path to the basic library name, or null if the library should
      be excluded from the result.
      @return a map from basic library name to native library description
    */

    public static @NotNull IMap<String,NativeLibrary> getDependencies(@NotNull NativeLibrary lib,
                                                                      @NotNull Function<String,String> pathToBasicName,
                                                                      @NotNull Reporter reporter)
      throws BuildException
    {
        IMap<String,ISet<String>> raw = getRawDependencies(lib, reporter);

        if (raw.isEmpty()) {
            return IMap.empty();
        }

        ISet<String> basicNames = getBasicNames(raw, pathToBasicName);
        if (basicNames.isEmpty()) {
            return IMap.empty();
        }

        Map<String, NativeLibrary> map = new HashMap<>();
        for (String basicName : basicNames) {
            IMap<Architecture,File> desc = getLibraryDescription(basicName, raw, pathToBasicName);
            if (!desc.isEmpty()) {
                map.put(basicName, NativeLibrarySupport.createNativeLibrary(basicName, desc));
            }
        }

        return IMap.create(map);
    }

    private static @NotNull IMap<String,ISet<String>> getRawDependencies(@NotNull NativeLibrary lib,
                                                                         @NotNull Reporter reporter)
      throws BuildException
    {
        if (lib.isSingle()) {
            File f = lib.getFile();
            assert f != null;
            return getRawDependencies(f, reporter);
        }
        MapSetBuilder<String,String> b = IMap.mapSetBuilder();
        ISet<File> files = lib.getAllFiles();
        for (File f : files) {
            IMap<String,ISet<String>> deps = getRawDependencies(f, reporter);
            for (String archName : deps.keySet()) {
                ISet<String> paths = deps.get(archName);
                assert paths != null;
                for (String path : paths) {
                    b.add(archName, path);
                }
            }
        }
        return b.value();
    }

    private static @NotNull ISet<String> getBasicNames(@NotNull IMap<String,ISet<String>> raw,
                                                       @NotNull Function<String,String> pathToBasicName)
    {
        SetBuilder<String> b = ISet.builder();
        for (String archName : raw.keySet()) {
            ISet<String> paths = raw.get(archName);
            assert paths != null;
            for (String path : paths) {
                String basicName = pathToBasicName.apply(path);
                b.addOptional(basicName);
            }
        }
        return b.values();
    }

    private static @NotNull IMap<Architecture,File> getLibraryDescription(@NotNull String basicName,
                                                                          @NotNull IMap<String,ISet<String>> raw,
                                                                          @NotNull Function<String,String> pathToBasicName)
    {
        MapBuilder<Architecture,File> b = IMap.builder();
        for (String archName : raw.keySet()) {
            Architecture a = ArchitectureUtils.parseArchitecture(archName);
            if (a != null) {
                ISet<String> paths = raw.get(archName);
                assert paths != null;
                for (String path : paths) {
                    String name = pathToBasicName.apply(path);
                    if (basicName.equals(name)) {
                        b.put(a, new File(path));
                    }
                }
            }
        }
        return b.value();
    }

    /**
      Identify the dependencies of the specified library.
      @param f The library file.
      @return a map from architecture to a set of dependent library paths.
    */

    public static @NotNull IMap<String,ISet<String>> getRawDependencies(@NotNull File f, @NotNull Reporter reporter)
      throws BuildException
    {
        if (!f.isFile()) {
            throw new BuildException("File not found: " + f.getPath());
        }

        File program = new File("/usr/bin/otool");
        IList<String> args = IList.of("-L", "-arch", "all", f.getAbsolutePath());

        ExecutionConfiguration g = ExecutionConfiguration.create(program, "get_raw_dependencies", args);
        ExecutionService es = ExecutionService.get();
        try {
            ExecutionResult result = es.execute(g);

            if (result.rc != 0) {
                throw new BuildException("/usr/bin/otool failed: " + result.rc);
            }

            IMap<String,ISet<String>> raw = parseOutput(result.output, f);

//        reporter.info("Raw dependencies of " + f);
//        for (String arch : raw.keySet()) {
//            ISet<String> paths = raw.get(arch);
//            assert paths != null;
//            for (String path: paths) {
//                reporter.info(" " + arch + " " + path);
//            }
//        }

            return raw;
        } catch (IOException e) {
            throw new BuildException("/usr/bin/otool failed: " + e.getMessage());
        }
    }

    private static @NotNull IMap<String,ISet<String>> parseOutput(@NotNull String output, @NotNull File f)
      throws BuildException
    {
        MapBuilder<String,ISet<String>> mb = IMap.builder();
        String currentArch = null;
        ListBuilder<String> lb = null;
        BufferedReader r = new BufferedReader(new StringReader(output));
        boolean isFirstLine = true;
        try {
            for (;;) {
                String line = r.readLine();
                if (line == null) {
                    break;
                }
                String arch = parsePossibleArchitectureHeader(line);
                if (arch != null) {
                    if (lb != null) {
                        IList<String> refs = lb.values();
                        mb.put(currentArch, ISet.create(refs));
                    }
                    currentArch = arch;
                    lb = IList.builder();
                } else {
                    String dep = parsePossibleDependency(line);
                    if (dep != null) {
                        if (lb != null) {
                            lb.add(dep);
                        } else {
                            throw new IOException("Dependency line before arch line in otool output");
                        }
                    } else if (isFirstLine) {
                        // A single architecture library does not have an architecture header
                        arch = getSingleArchitecture(f);
                        currentArch = arch;
                        lb = IList.builder();
                    } else {
                        throw new IOException("Unrecognized line in otool output: " + line);
                    }
                }
                isFirstLine = false;
            }
            if (lb != null) {
                IList<String> refs = lb.values();
                mb.put(currentArch, ISet.create(refs));
            }
            return mb.value();
        } catch (IOException e) {
            // should not happen
            return mb.value();
        }
    }

    private static @NotNull String getSingleArchitecture(@NotNull File f)
      throws BuildException
    {
        try {
            File program = new File("/usr/bin/file");
            IList<String> args = IList.of("-b", f.getAbsolutePath());

            ExecutionConfiguration g = ExecutionConfiguration.create(program, "get_single_architecture", args);
            ExecutionService es = ExecutionService.get();
            ExecutionResult result = es.execute(g);

            if (result.rc != 0) {
                throw new IOException("/usr/bin/file failed: " + result.rc);
            }

            String output = result.output;
            int pos = output.indexOf('\n');
            if (pos >= 0) {
                output = output.substring(0, pos);
            }
            String pattern = "dynamically linked shared library ";
            pos = output.indexOf(pattern);
            if (pos >= 0) {
                return output.substring(pos + pattern.length());
            }
        } catch (IOException ignore) {
        }
        throw new BuildException("Failed to identify architecture of " + f.getPath());
    }

    private static @Nullable String parsePossibleArchitectureHeader(@NotNull String line)
    {
        String pattern = " (architecture ";
        int pos = line.indexOf(pattern);
        if (pos < 0) {
            return null;
        }
        String s = line.substring(pos + pattern.length());
        pos = s.indexOf(')');
        if (pos < 0) {
            return null;
        }
        return s.substring(0, pos);
    }

    private static @Nullable String parsePossibleDependency(@NotNull String line)
    {
        if (line.isEmpty()) {
            return null;
        }
        if (line.charAt(0) != '\t') {
            return null;
        }
        int pos = line.indexOf(" (compatibility version ");
        if (pos < 0) {
            return null;
        }
        return line.substring(1, pos);
    }
}
