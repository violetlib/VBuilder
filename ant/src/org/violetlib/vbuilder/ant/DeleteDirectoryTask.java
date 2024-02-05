package org.violetlib.vbuilder.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
  Delete a single directory and its contents (recursively).
  Does not follow symlinks, even at top level.
  Will make multiple attempts, to avoid spontaneously created .DS_Store files on macOS.

  @ant.task name="deleteDirectory"
*/

public class DeleteDirectoryTask
  extends Task
{
    private @Nullable String directoryPath;

    private final @NotNull List<Path> itemsToDelete = new ArrayList<>();  // files and symlinks
    private final @NotNull List<Path> directoriesToDelete = new ArrayList<>();
    private int fileErrors;
    private int directoryErrors;

    /**
      Specify the directory to be deleted.
      @ant.required
    */

    public void setDirectory(@NotNull String path)
    {
        directoryPath = path;
    }

    @Override
    public void execute()
    {
        if (directoryPath == null) {
            throw new BuildException("A directory must be specified");
        }

        if (directoryPath.isEmpty()) {
            throw new BuildException("Directory path must not be empty");
        }

        Path dir = new File(directoryPath).toPath();
        if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
            log("Directory not found: " + directoryPath, Project.MSG_WARN);
            return;
        }
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            throw new BuildException("Not a directory: " + directoryPath);
        }

        try {
            collectItemsToDelete(dir);
        } catch (IOException e) {
            throw new BuildException("Unable to identify directory tree contents", e);
        }

        int fileCount = itemsToDelete.size();
        int subdirCount = directoriesToDelete.size() - 1;
        int totalFileCount = fileCount;
        int totalSubdirectoryCount = subdirCount;

        // Failure to delete a directory may be caused by the spontaneous creation of a .DS_Store file.
        // Such files can be created as a side effect of deleting files (!).
        // Retrying can work because it will be faster and more likely to complete before any
        // .DS_Store files are created.

        int iterationCount = 0;
        for (;;) {
            ++iterationCount;

            log("Found " + countMessage(fileCount, "file", "files") + " and "
              + countMessage(subdirCount, "subdirectory", "subdirectories") + " in " + dir, Project.MSG_VERBOSE);

            fileErrors = 0;
            directoryErrors = 0;

            deleteItems();

            if (fileErrors == 0) {
                if (directoryErrors == 0) {
                    log("Deleted " + countMessage(totalFileCount, "file", "files") + " and "
                      + countMessage(totalSubdirectoryCount, "subdirectory", "subdirectories") + " in " + dir);
                    return;
                }
                if (iterationCount < 3) {
                    try {
                        collectItemsToDelete(dir);
                        log("Retry...");
                        continue;
                    } catch (IOException ignore) {
                    }
                }
            }

            if (fileErrors > 0 || directoryErrors > 0) {
                throw new BuildException("Unable to delete " + countMessage(fileErrors, "file", "files")
                  + " and " + countMessage(directoryErrors, "directory", "directories"));
            }
        }
    }

    private @NotNull String countMessage(int count, @NotNull String singular, @NotNull String plural)
    {
        if (count == 1) {
            return count + " " + singular;
        }
        return count + " " + plural;
    }

    private void collectItemsToDelete(@NotNull Path dir)
      throws IOException
    {
        itemsToDelete.clear();
        directoriesToDelete.clear();
        try (Stream<Path> s = Files.walk(dir)) {
            s.forEach(this::inspect);
        }
    }

    private void inspect(@NotNull Path f)
    {
        if (Files.isDirectory(f, LinkOption.NOFOLLOW_LINKS)) {
            directoriesToDelete.add(f);
        } else {
            itemsToDelete.add(f);
        }
    }

    private void deleteItems()
    {
        for (Path p : itemsToDelete) {
            deleteItem(p);
        }
        Collections.reverse(directoriesToDelete);
        for (Path p : directoriesToDelete) {
            deleteDirectory(p);
        }
    }

    private void deleteItem(@NotNull Path p)
    {
        try {
            Files.delete(p);
        } catch (IOException e) {
            log("Unable to delete " + p, Project.MSG_ERR);
            ++fileErrors;
        }
    }

    private void deleteDirectory(@NotNull Path p)
    {
        try {
            Files.delete(p);
        } catch (IOException e) {
            log("Unable to delete directory " + p, Project.MSG_ERR);
            ++directoryErrors;
        }
    }
}
