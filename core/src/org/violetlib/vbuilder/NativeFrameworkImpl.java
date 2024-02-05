package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;

/**
  This class depends upon the ExecutionService, which must be configured by platform-specific code (such as the
  Ant-specific code).
*/

public class NativeFrameworkImpl
  implements NativeFramework
{
    public static @NotNull NativeFramework createSystemFramework(@NotNull String name)
    {
        return new NativeFrameworkImpl(name, null, null, null);
    }

    public static @NotNull NativeFramework createFramework(@NotNull File root)
      throws IllegalArgumentException
    {
        return new NativeFrameworkImpl(root);
    }

    private final @NotNull String name;
    private final @Nullable NativeLibrary library;
    private final @Nullable File root;
    private final @Nullable File debugSymbols;

    private NativeFrameworkImpl(@NotNull String name,
                                @Nullable NativeLibrary library,
                                @Nullable File root,
                                @Nullable File debugSymbols)
    {
        this.name = name;
        this.library = null;
        this.root = null;
        this.debugSymbols = debugSymbols;
    }

    private NativeFrameworkImpl(@NotNull File root)
      throws IllegalArgumentException
    {
        String fileName = root.getName();
        if (!fileName.endsWith(".framework")) {
            throw new IllegalArgumentException("Not a supported framework directory name: " + root);
        }
        if (!Files.isDirectory(root.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Framework directory not found: " + root);
        }
        this.name = fileName.substring(0, fileName.length() - 10);
        this.root = root;
        this.debugSymbols = null;
        File executable = new File(root, name);
        if (!Files.isSymbolicLink(executable.toPath())) {
            throw new IllegalArgumentException("Framework library link not found: " + executable);
        }
        String link;
        try {
            link = Files.readSymbolicLink(executable.toPath()).toString();
        } catch (IOException e) {
            throw new IllegalArgumentException("Framework library link could not be read: " + executable);
        }
        if (link.contains("..")) {
            throw new IllegalArgumentException("Framework library link is invalid: " + link);
        }
        File canonicalExecutable;
        try {
            canonicalExecutable = executable.getCanonicalFile();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Framework library link is invalid: " + link);
        }
        StringReporter sr = StringReporter.create();
        try {
            this.library = NativeLibrarySupport.createForFrameworkFile(canonicalExecutable, sr);
        } catch (BuildException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    @Override
    public @NotNull String getName()
    {
        return name;
    }

    @Override
    public @Nullable NativeLibrary getLibrary()
    {
        return library;
    }

    @Override
    public @Nullable File getRoot()
    {
        return root;
    }

    @Override
    public @Nullable File getDebugSymbols()
    {
        return debugSymbols;
    }

    @Override
    public @NotNull NativeFramework withDebugSymbols(@NotNull File f)
    {
        return new NativeFrameworkImpl(name, library, root, f);
    }

    @Override
    public @NotNull String toString()
    {
        return root != null ? root.getPath() : name;
    }
}
