package org.violetlib.vbuilder.ant;

import org.apache.maven.resolver.internal.ant.tasks.Resolve;
import org.apache.maven.resolver.internal.ant.types.Dependencies;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.ResourceCollection;
import org.apache.tools.ant.types.resources.FileResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.collections.IList;
import org.violetlib.collections.ISet;
import org.violetlib.collections.ListBuilder;
import org.violetlib.vbuilder.MavenVersionManagement;

import java.io.File;

/**
  Process dependencies to produce a compilation class path and a path for runtime dependencies.
*/

public class DependencySupport
{
    public static @NotNull Result compute(@NotNull Project project, @NotNull Configuration g)
    {
        return new DependencySupport(project, g).result;
    }

    public static @NotNull Configuration createConfiguration(@NotNull IList<File> compilePath,
                                                             @NotNull IList<File> runtimePath,
                                                             @NotNull Dependencies dependencies,
                                                             @Nullable MavenVersionManagement mm)
    {
        return new Configuration(compilePath, runtimePath, dependencies, mm);
    }

    public static class Configuration
    {
        public final @NotNull IList<File> compilePath;
        public final @NotNull IList<File> runtimePath;
        public final @NotNull Dependencies dependencies;
        public final @Nullable MavenVersionManagement mm;

        private Configuration(@NotNull IList<File> compilePath,
                              @NotNull IList<File> runtimePath,
                              @NotNull Dependencies dependencies,
                              @Nullable MavenVersionManagement mm)
        {
            this.compilePath = compilePath;
            this.runtimePath = runtimePath;
            this.dependencies = dependencies;
            this.mm = mm;
        }
    }

    public static class Result
    {
        public final @NotNull Path compilePath;
        public final @NotNull Path runtimePath;
        public final @NotNull IList<File> compileFiles;
        public final @NotNull IList<File> runtimeFiles;

        public Result(@NotNull Path compilePath,
                      @NotNull Path runtimePath,
                      @NotNull IList<File> compileFiles,
                      @NotNull IList<File> runtimeFiles)
        {
            this.compilePath = compilePath;
            this.runtimePath = runtimePath;
            this.compileFiles = compileFiles;
            this.runtimeFiles = runtimeFiles;
        }
    }

    private final @NotNull Project project;
    private final @NotNull Result result;

    public DependencySupport(@NotNull Project project, @NotNull Configuration g)
    {
        this.project = project;

        Resolve resolve = new Resolve();
        resolve.setProject(project);
        resolve.setTaskName("resolve");
        resolve.addDependencies(g.dependencies);

        Resolve.Path compilePath = resolve.createPath();
        compilePath.setProject(project);
        compilePath.setRefId("lib.path.maven.compile-only");
        compilePath.setClasspath("compile");

        Resolve.Path runtimePath = resolve.createPath();
        runtimePath.setProject(project);
        runtimePath.setRefId("lib.path.maven");
        runtimePath.setClasspath("runtime");

        Resolve.Files compileFiles = resolve.createFiles();
        compileFiles.setProject(project);
        compileFiles.setRefId("lib.files.maven.compile-only");
        compileFiles.setClasspath("compile");

        Resolve.Files runtimeFiles = resolve.createFiles();
        runtimeFiles.setProject(project);
        runtimeFiles.setRefId("lib.files.maven");
        runtimeFiles.setClasspath("runtime");

        resolve.execute();

        Path compile = project.getReference("lib.path.maven.compile-only");
        Path runtime = project.getReference("lib.path.maven");
        IList<File> cfs = getFiles(g.compilePath, project.getReference("lib.files.maven.compile-only"));
        IList<File> rfs = getFiles(g.runtimePath, project.getReference("lib.files.maven"));

        compile = createPath(g.compilePath, compile);
        runtime = createPath(g.runtimePath, runtime);

        result = new Result(compile, runtime, cfs, rfs);
    }

    private @NotNull IList<File> getFiles(@NotNull IList<File> fs, @Nullable ResourceCollection rc)
    {
        ListBuilder<File> b = IList.builder(IList.NO_DUPLICATES);
        b.addAll(fs);
        if (rc != null) {
            ISet<File> rfs = AntUtils.getResourceCollectionFiles(rc);
            if (rfs != null) {
                b.addAll(rfs);
            }
        }
        return b.values();
    }

    private @NotNull Path createPath(@NotNull IList<File> files, @Nullable Path p)
    {
        Path result = new Path(project);
        for (File f : files) {
            FileResource fr = new FileResource(project, f);
            result.add(fr);
        }
        if (p != null) {
            result.add(p);
        }
        return result;
    }
}
