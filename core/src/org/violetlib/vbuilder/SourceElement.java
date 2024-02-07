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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**

*/

public abstract class SourceElement
{
    static final int BUFFER_SIZE = 1024;

    private final @NotNull String name;
    private final long date;
    private final boolean isDirectory;

    protected SourceElement(@NotNull String name, long date, boolean isDirectory)
    {
        this.name = name;
        this.date = date;
        this.isDirectory = isDirectory;
    }

    public @NotNull String getName()
    {
        return name;
    }

    public long getDate()
    {
        return date;
    }

    public boolean isDirectory()
    {
        return isDirectory;
    }

    public abstract @Nullable File getSourceFile();

    public abstract void copyTo(@NotNull File dest)
      throws IOException;

    public static @NotNull SourceElement createFile(@NotNull String entryName,
                                                    long entryDate,
                                                    boolean isDirectory,
                                                    @NotNull File f)
    {
        return new FileSourceElement(entryName, entryDate, isDirectory, f);
    }

    public static @NotNull SourceElement createFile(@NotNull File f)
    {
        String name = f.getName();
        long date = f.lastModified();
        boolean isDirectory = f.isDirectory();
        return new FileSourceElement(name, date, isDirectory, f);
    }

    private static class FileSourceElement
      extends SourceElement
    {
        public final @NotNull File f;

        public FileSourceElement(@NotNull String entryName,
                                 long entryDate,
                                 boolean isDirectory,
                                 @NotNull File f)
        {
            super(entryName, entryDate, isDirectory);

            this.f = f;
        }

        @Override
        public @NotNull File getSourceFile()
        {
            return f;
        }

        @Override
        public void copyTo(@NotNull File dest)
          throws IOException
        {
            Utils.copyFile(f, dest);
            Utils.setLastModifiedTime(dest, getDate());
        }
    }

    public static @NotNull SourceElement createZipEntry(@NotNull String entryName,
                                                        long entryDate,
                                                        boolean isDirectory,
                                                        @NotNull ZipFile zf,
                                                        @NotNull ZipEntry ze)
    {
        return new ZipEntrySourceElement(entryName, entryDate, isDirectory, zf, ze);
    }

    private static class ZipEntrySourceElement
      extends SourceElement
    {
        public final @NotNull ZipFile zf;
        public final @NotNull ZipEntry ze;

        private ZipEntrySourceElement(@NotNull String entryName,
                                      long entryDate,
                                      boolean isDirectory,
                                      @NotNull ZipFile zf,
                                      @NotNull ZipEntry ze)
        {
            super(entryName, entryDate, isDirectory);

            this.zf = zf;
            this.ze = ze;
        }

        @Override
        public @Nullable File getSourceFile()
        {
            return null;
        }

        @Override
        public void copyTo(@NotNull File dest)
          throws IOException
        {
            try (InputStream s = zf.getInputStream(ze); OutputStream fos = Files.newOutputStream(dest.toPath())) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int length;
                while ((length = s.read(buffer)) >= 0) {
                    fos.write(buffer, 0, length);
                }
            }
            Utils.setLastModifiedTime(dest, getDate());
        }
    }
}
