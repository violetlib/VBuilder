package org.violetlib.vbuilder.ant;

import org.violetlib.vbuilder.JARBuilder;
import org.violetlib.vbuilder.RelativeFile;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Manifest;
import org.apache.tools.ant.taskdefs.ManifestException;
import org.apache.tools.ant.types.ResourceCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.collections.ISet;
import org.violetlib.vbuilder.Utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;

/**
  A JAR file builder delegate for use in {@code ant}.
  <p>
  This delegate supports an {@code ant} {@link org.apache.tools.ant.types.ResourceCollection} as a source. The resource
  collection must identify only file system objects. The resource name of each file resource is used as the name of file
  in the JAR. For sources that specify a base directory, such as {@link org.apache.tools.ant.types.FileSet}, the
  resource name is the relative path from the base directory to the file.
  <p>
  This delegate supports an {@code ant} {@link org.apache.tools.ant.taskdefs.Manifest}.
*/

public class JARBuilderAnt
  extends AntBuildDelegate
  implements JARBuilder.Delegate
{
    /**
      Create an {@code ant}-specific delegate for building a Java library.
      @param project The {@code ant} project using the builder.
    */

    public static @NotNull JARBuilder.Delegate createDelegate(@NotNull Project project)
    {
        return new JARBuilderAnt(project);
    }

    protected JARBuilderAnt(@NotNull Project project)
    {
        super(project);
    }

    @Override
    public @Nullable File resolveManifest(@NotNull Object manifest, @Nullable String mainClassName)
    {
        if (manifest instanceof File) {
            File manifestFile = (File) manifest;
            if (Files.isRegularFile(manifestFile.toPath())) {
                return Utils.resolve(manifestFile).getAbsoluteFile();
            } else {
                throw new BuildException("Specified manifest file is unavailable: " + manifestFile.getPath());
            }
        } else if (manifest instanceof Manifest) {
            Manifest m = (Manifest) manifest;
            if (mainClassName != null) {
                try {
                    m.addConfiguredAttribute(new Manifest.Attribute("Main-Class", mainClassName));
                } catch (ManifestException ignore) {
                    // Main class already defined
                }
            }
            try {
                File temp = Files.createTempFile("Manifest", "mf").toFile();
                writeManifestFile(m, temp);
                return temp;
            } catch (IOException e) {
                throw new BuildException("Unable to create temporary manifest file", e);
            }
        }

        return null;
    }

    private void writeManifestFile(@NotNull Manifest m, @NotNull File f)
      throws IOException
    {
        try (PrintWriter writer = new PrintWriter(new FileWriter(f))) {
            m.write(writer);
            if (writer.checkError()) {
                throw new IOException("Unable to write manifest file");
            }
        }
    }

    @Override
    public @NotNull ISet<RelativeFile> resolveSource(@NotNull Object source)
    {
        if (source instanceof ResourceCollection) {
            ResourceCollection rc = (ResourceCollection) source;
            ISet<RelativeFile> files = AntUtils.getResourceCollectionRelativeFiles(rc);
            if (files == null) {
                throw new BuildException("Unsupported resource collection: " + source.getClass().getName());
            }
            return files;
        } else {
            throw new BuildException("Unsupported source type: " + source.getClass().getName());
        }
    }
}
