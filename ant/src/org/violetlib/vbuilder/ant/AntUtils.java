/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vbuilder.ant;

import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.ResourceCollection;
import org.apache.tools.ant.types.resources.FileProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.collections.*;
import org.violetlib.vbuilder.ExecutionService;
import org.violetlib.vbuilder.ExecutionServiceImpl;
import org.violetlib.vbuilder.RelativeFile;
import org.violetlib.vbuilder.Utils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.function.Function;
import java.util.function.Predicate;

/**

*/

public class AntUtils
{
    private static boolean isInitialized;

    public static synchronized void init()
    {
        if (!isInitialized) {
            isInitialized = true;
            ExecutionService executionService = ExecutionServiceAnt.create();
            ExecutionServiceImpl.installExecutionService(executionService);
        }
    }

    /**
      A convenience for using ant properties that might be undefined.
    */

    public static boolean isUndefined(@NotNull String s)
    {
        return s.startsWith("${") && s.endsWith("}");
    }

    public static boolean isTrue(@Nullable String s)
    {
        if (s == null) {
            return false;
        }
        return s.equals("true") || s.equals("yes") || s.equals("on");
    }

    /**
      Return the file system elements of a resource collection. The file system elements are not validated.
      Where possible, symlinks are resolved to paths to their current targets.

      @return the file system elements, or null if the resource collection contains non-file system elements.
    */

    public static @Nullable ISet<File> getResourceCollectionFiles(@NotNull ResourceCollection rc)
    {
        return getResourceCollectionFiles(rc, f -> true);
    }

    /**
      Return the file system elements of a resource collection that satisfy the specified predicate. The file system
      elements are not otherwise validated. Where possible, prior to validation, symlinks are resolved to paths to
      their current targets.

      @return the file system elements, or null if the resource collection contains non-file system elements.
    */

    public static @Nullable ISet<File> getResourceCollectionFiles(@NotNull ResourceCollection rc,
                                                                  @NotNull Predicate<File> p)
    {
        if (rc.isFilesystemOnly()) {
            SetBuilder<File> b = ISet.builder(ISet.ORDERED);
            for (Resource r : rc) {
                FileProvider fp = r.as(FileProvider.class);
                assert fp != null;
                File f = Utils.resolve(fp.getFile());
                if (p.test(f)) {
                    b.add(f);
                }
            }
            return b.values();
        } else {
            return null;
        }
    }

    /**
      Return a set of files obtained from a resource collection and identified using relative paths.
      <p>
      The resource collection must identify only file system objects.
      Directory objects and symbolic links are ignored.
      It must be possible to determine a relative path for each file.
      <p>

      @param rc The resource collection.

      @return the files, or null if the resource collection contains non-file system elements.
    */

    public static @Nullable ISet<RelativeFile> getResourceCollectionRelativeFiles(@NotNull ResourceCollection rc)
    {
        return getResourceCollectionRelativeFiles(rc, f -> true);
    }

    /**
      Return a set of files obtained from a resource collection and identified using relative paths.
      <p>
      The resource collection must identify only file system objects.
      Directory objects and symbolic links are ignored.
      It must be possible to determine a relative path for each file.
      <p>

      @param rc The resource collection.
      @param p A predicate applied to candidate files. Only files that satisfy the predicate are returned.

      @return the files, or null if the resource collection contains non-file system elements.
    */

    public static @Nullable ISet<RelativeFile> getResourceCollectionRelativeFiles(@NotNull ResourceCollection rc,
                                                                                  @NotNull Predicate<File> pred)
    {
        if (!rc.isFilesystemOnly()) {
            return null;
        }

        if (rc instanceof FileSet) {
            return getFileSetRelativeFiles((FileSet) rc, pred);
        }

        SetBuilder<RelativeFile> b = ISet.builder();
        for (Resource r : rc) {
            if (!r.isDirectory()) {
                String name = r.getName();
                if (name != null && !name.isEmpty() && !name.startsWith("/")) {
                    FileProvider fp = r.as(FileProvider.class);
                    assert fp != null;
                    File f = fp.getFile();
                    if (Files.isRegularFile(f.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                        if (pred.test(f)) {
                            RelativeFile rf = RelativeFile.create(name, f);
                            b.add(rf);
                        }
                    }
                }
            }
        }
        return b.values();
    }

    private static @NotNull ISet<RelativeFile> getFileSetRelativeFiles(@NotNull FileSet fs,
                                                                       @NotNull Predicate<File> pred)
    {
        SetBuilder<RelativeFile> b = ISet.builder();
        for (Resource r : fs) {
            if (!r.isDirectory()) {
                String name = r.getName();
                if (name != null && !name.isEmpty()) {
                    FileProvider fp = r.as(FileProvider.class);
                    assert fp != null;
                    File f = fp.getFile();
                    if (Files.isRegularFile(f.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                        if (pred.test(f)) {
                            RelativeFile rf = RelativeFile.create(name, f);
                            b.add(rf);
                        }
                    }
                }
            }
        }
        return b.values();
    }

    /**
      Return the file identified by a resource, if any.
      @param r The resource.
      @return the associated file, or null if none.
    */

    public static @Nullable File getResourceFile(@NotNull Resource r)
    {
        FileProvider fp = r.as(FileProvider.class);
        return fp != null ? fp.getFile() : null;
    }

    /**
      Return the file system elements of a collection of resources. The file system elements are not validated. Where
      possible, symlinks are resolved to paths to their current targets.

      @return the file system elements, or null if the resource collection contains non-file system elements.
    */

    public static @Nullable ISet<File> getResourceFiles(@NotNull ICollection<Resource> rc)
    {
        return getResourceFiles(rc, f -> true);
    }

    /**
      Return the file system elements of a collection of resources that satisfy the specified predicate. The file system
      elements are not otherwise validated. Where possible, prior to validation, symlinks are resolved to paths to their
      current targets.

      @return the file system elements, or null if the resource collection contains non-file system elements.
    */

    public static @Nullable ISet<File> getResourceFiles(@NotNull ICollection<Resource> rc,
                                                        @NotNull Predicate<File> p)
    {
        SetBuilder<File> b = ISet.builder();
        for (Resource r : rc) {
            if (r.isFilesystemOnly()) {
                FileProvider fp = r.as(FileProvider.class);
                assert fp != null;
                File f = Utils.resolve(fp.getFile());
                if (p.test(f)) {
                    b.add(f);
                }
            } else {
                return null;
            }
        }
        return b.values();
    }

    /**
      Return the file system elements of a Path. The file system elements are not otherwise validated. Where possible,
      prior to validation, symlinks are resolved to paths to their current targets.

      @return the file system elements, or null if the resource collection contains non-file system elements.
    */

    public static @Nullable IList<File> getPathFiles(@NotNull org.apache.tools.ant.types.Path path)
    {
        if (path.isFilesystemOnly()) {
            ListBuilder<File> b = IList.builder(IList.NO_DUPLICATES);
            for (Resource r : path) {
                FileProvider fp = r.as(FileProvider.class);
                assert fp != null;
                File f = Utils.resolve(fp.getFile());
                b.add(f);
            }
            return b.values();
        } else {
            return null;
        }
    }

    /**
      Return the file system elements of a Path that satisfy the specified predicate. The file system elements are not
      otherwise validated. Where possible, prior to validation, symlinks are resolved to paths to their current targets.

      @return the file system elements, or null if the resource collection contains non-file system elements.
    */

    public static @Nullable IList<File> getPathFiles(@NotNull org.apache.tools.ant.types.Path path,
                                                     @NotNull Predicate<File> p)
    {
        if (path.isFilesystemOnly()) {
            ListBuilder<File> b = IList.builder(IList.NO_DUPLICATES);
            for (Resource r : path) {
                FileProvider fp = r.as(FileProvider.class);
                assert fp != null;
                File f = Utils.resolve(fp.getFile());
                if (p.test(f)) {
                    b.add(f);
                }
            }
            return b.values();
        } else {
            return null;
        }
    }

    /**
      Obtain the file system elements of a resource collection and apply a map to those file system elements. The file
      system elements are not otherwise validated. Where possible, prior to validation, symlinks are resolved to paths
      to their current targets.

      @param rc The resource collection whose elements are examined.

      @param mapper A mapper used to transform the file system elements to the desired result type. The mapper may
      return null to exclude a file system element.

      @return the mapped file system elements, or null if the resource collection contains non-file system
      elements.
    */

    public static <T> @Nullable ISet<T> getResourceCollectionFilesMapped(@NotNull ResourceCollection rc,
                                                                         @NotNull Function<File,T> mapper)
    {
        return getResourceCollectionFilesMapped(rc, f -> true, mapper);
    }

    /**
      Obtain the file system elements of a resource collection that satisfy the specified predicate and apply a map to
      those file system elements. The file system elements are not otherwise validated. Where possible, prior to
      validation, symlinks are resolved to paths to their current targets.

      @param rc The resource collection whose elements are examined.

      @param p A predicate used to filter the file system elements. If the predicate returns false on a file system
      element, the file system element is not used.

      @param mapper A mapper used to transform the filtered file system elements to the desired result type. The mapper
      may return null to exclude a file system element.

      @return the mapped and filtered file system elements, or null if the resource collection contains non-file system
      elements.
    */

    public static <T> @Nullable ISet<T> getResourceCollectionFilesMapped(@NotNull ResourceCollection rc,
                                                                         @NotNull Predicate<File> p,
                                                                         @NotNull Function<File,T> mapper)
    {
        if (rc.isFilesystemOnly()) {
            SetBuilder<T> b = ISet.builder();
            for (Resource r : rc) {
                FileProvider fp = r.as(FileProvider.class);
                assert fp != null;
                File f = Utils.resolve(fp.getFile());
                if (p.test(f)) {
                    b.addOptional(mapper.apply(f));
                }
            }
            return b.values();
        } else {
            return null;
        }
    }
}
