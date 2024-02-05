package org.violetlib.vbuilder.ant;

import org.jetbrains.annotations.NotNull;
import org.violetlib.collections.ISet;
import org.violetlib.vbuilder.RelativeFile;
import org.violetlib.vbuilder.Reporter;
import org.violetlib.vbuilder.SourceCollector;
import org.apache.tools.ant.types.ResourceCollection;
import org.violetlib.vbuilder.Utils;

import java.io.File;
import java.nio.file.Files;

/**

*/

public class SourceCollectorAnt
  extends SourceCollector
{
    public static @NotNull SourceCollectorAnt createAnt(@NotNull Reporter reporter)
    {
        return new SourceCollectorAnt(reporter);
    }

    private SourceCollectorAnt(@NotNull Reporter reporter)
    {
        super(reporter);
    }

    public void process(@NotNull ResourceCollection r)
    {
        ISet<RelativeFile> files = AntUtils.getResourceCollectionRelativeFiles(r);
        if (files == null) {
            reporter.error("Unsupported resource [contains non-file system resources]");
        } else {
            for (RelativeFile rf : files) {
                File f = rf.getFile();
                if (Files.isRegularFile(f.toPath())) {
                    if (Utils.isJarFile(f)) {
                        jarFiles.add(f.getAbsoluteFile());
                    } else if (Utils.isNativeLibrary(f)) {
                        nativeLibraries.add(f.getAbsoluteFile());
                    } else {
                        File bundle = Utils.isNativeLibrarySymbolsDistinguishedFile(f);
                        if (bundle != null) {
                            nativeLibrarySymbols.add(bundle.getAbsoluteFile());
                        } else {
                            resources.add(rf);
                        }
                    }
                } else if (Files.isDirectory(f.toPath())) {
                    resources.add(rf);
                } else {
                    reporter.error("Unsupported resource");
                }
            }
        }
    }
}
