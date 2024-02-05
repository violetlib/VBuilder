package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.Objects;

/**
  A file with an associated relative path, for use in creating a JAR.
*/

public class RelativeFile
  implements Comparable<RelativeFile>
{
    public static @NotNull RelativeFile create(@NotNull File baseDirectory, @NotNull File file)
      throws IllegalArgumentException
    {
        String dirPath = baseDirectory.getAbsolutePath() + "/";
        String filePath = file.getAbsolutePath();
        if (!filePath.startsWith(dirPath)) {
            throw new IllegalArgumentException("File path does not match base directory");
        }
        String relativePath = filePath.substring(dirPath.length());
        return new RelativeFile(relativePath, file);
    }

    public static @NotNull RelativeFile create(@NotNull String path, @NotNull File file)
      throws IllegalArgumentException
    {
        if (path.startsWith("/")) {
            throw new IllegalArgumentException("Path must be a relative path");
        }

        return new RelativeFile(path, file);
    }

    private final @NotNull String path;
    private final @NotNull File file;

    private RelativeFile(@NotNull String path, @NotNull File file)
    {
        this.path = path;
        this.file = file;
    }

    /**
      Return the relative path that identifies the file.
    */

    public @NotNull String getPath()
    {
        return path;
    }

    /**
      Return the actual file.
    */

    public @NotNull File getFile()
    {
        return file;
    }

    /**
      Return the base directory implied by the relative path.
      @return the base directory, or null if the relative path does not imply an existing base directory.
    */

    public @Nullable File getBaseDirectory()
    {
        String fullPath = file.getAbsolutePath();
        if (fullPath.endsWith(path)) {
            String basePath = fullPath.substring(0, fullPath.length() - path.length());
            File base = new File(basePath);
            if (Files.isDirectory(base.toPath())) {
                return base;
            }
        }

        return null;
    }

    @Override
    public int compareTo(@NotNull RelativeFile o)
    {
        String otherPath = o.getPath();
        return path.compareTo(otherPath);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof RelativeFile)) return false;
        RelativeFile that = (RelativeFile) o;
        return Objects.equals(path, that.path) && Objects.equals(file, that.file);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(path, file);
    }

    @Override
    public @NotNull String toString()
    {
        return path + ": " + file;
    }
}
