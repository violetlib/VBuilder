/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vbuilder.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.collections.IList;
import org.violetlib.collections.ISet;
import org.violetlib.collections.ListBuilder;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
  Specify Java libraries as part of a class path.
  JAR files are specified using paths or lists of individual JAR files.

  @ant.type ignore="true"
*/

public class ClassPathElement
  extends DataType
{
    public static @NotNull ClassPathElement create(@NotNull Path path)
    {
        ClassPathElement e = new ClassPathElement();
        e.setProject(path.getProject());
        e.addConfiguredpath(path);
        return e;
    }

    public static @NotNull ClassPathElement create(@NotNull FileList files)
    {
        ClassPathElement e = new ClassPathElement();
        e.setProject(files.getProject());
        e.addConfiguredfiles(files);
        return e;
    }

    private final @NotNull List<FileList> fileLists = new ArrayList<>();
    private final @NotNull List<Path> paths = new ArrayList<>();
    private final @NotNull List<String> libraryNames = new ArrayList<>();

    private @Nullable File specifiedFile;


    @Override
    public void setRefid(Reference r) throws BuildException
    {
        if (!fileLists.isEmpty()) {
            throw tooManyAttributes();
        }
        super.setRefid(r);
    }

    private @NotNull IList<Object> getElements()
    {
        if (isReference()) {
            return getCheckedRef(ClassPathElement.class, getDataTypeName()).getElements();
        }
        ListBuilder<Object> b = IList.builder();
        b.addAll(fileLists);
        b.addAll(paths);
        return b.values();
    }

    /**
      Return the elements as files. The files are not validated.
    */

    public @NotNull IList<File> getFiles()
    {
        if (isReference()) {
            return getCheckedRef(ClassPathElement.class, getDataTypeName()).getFiles();
        }
        IList<Resource> resources = getResources();
        ListBuilder<File> b = IList.builder(IList.NO_DUPLICATES);
        for (Resource r : resources) {
            ISet<File> fs = AntUtils.getResourceCollectionFiles(r);
            if (fs != null) {
                b.addAll(fs);
            }
        }
        if (specifiedFile != null) {
            b.add(specifiedFile);
        }
        return b.values();
    }

    /**
      Return the specified library names or coordinates.
    */

    public @NotNull IList<String> getLibraryNames()
    {
        return IList.create(libraryNames);
    }

    /**
      Specify required libraries using library names or Maven repo coordinates (without the version).
      @ant.prop
    */

    public void addText(@NotNull String s)
    {
        installNames(s);
    }

    /**
      Add specified JAR files.
      @ant.prop
    */

    public void addConfiguredfiles(@NotNull FileList c) {
        if (isReference()) {
            throw noChildrenAllowed();
        }
        fileLists.add(c);
        setChecked(false);
    }

    /**
      Add a specified search path for JAR files.
      @ant.prop
    */

    public void addConfiguredpath(@NotNull Path path)
    {
        if (isReference()) {
            throw noChildrenAllowed();
        }
        paths.add(path);
        setChecked(false);
    }

    /**
      Specify a single JAR file.
      @ant.prop
      @ant.optional
    */

    public void setFile(@NotNull File f)
    {
        if (!Files.isRegularFile(f.toPath())) {
            throw new BuildException("Not a file: " + f);
        }
        this.specifiedFile = f;
    }

    /**
      Specify a search path for JAR files.
      @ant.prop
      @ant.optional
    */

    public void setPath(@NotNull Path p)
    {
        paths.add(p);
    }

    /**
      Specify library names or Maven repo coordinates (without versions).
      @ant.prop
      @ant.optional
    */

    public void setLibs(@NotNull String libraryNames)
    {
        installNames(libraryNames);
    }

    private void installNames(@NotNull String s)
    {
        StringTokenizer st = new StringTokenizer(s);
        while (st.hasMoreTokens()) {
            String name = st.nextToken();
            libraryNames.add(name);
        }
    }

    public @NotNull IList<Resource> getResources()
    {
        ListBuilder<Resource> b = IList.builder();
        for (Object o : getElements()) {
            if (o instanceof ResourceCollection) {
                ResourceCollection rc = (ResourceCollection) o;
                for (Resource r : rc) {
                    b.add(r);
                }
            }
        }
        return b.values();
    }
}
