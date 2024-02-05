package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;

/**

*/

public class LibraryDefinition
{
    public static @NotNull LibraryDefinition create(@NotNull String libraryName,
                                                    @NotNull MavenCoordinates artifactKey)
      throws IllegalArgumentException
    {
        return new LibraryDefinition(libraryName, artifactKey);
    }

    private final @NotNull String libraryName;
    private final @NotNull MavenCoordinates artifactKey;

    private LibraryDefinition(@NotNull String libraryName, @NotNull MavenCoordinates artifactKey)
      throws IllegalArgumentException
    {
        try {
            Utils.validateLibraryName(libraryName);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage());
        }

        if (artifactKey.version != null) {
            throw new IllegalArgumentException("Artifact version not allowed in key: " + artifactKey);
        }

        this.libraryName = libraryName;
        this.artifactKey = artifactKey;
    }

    public @NotNull String getLibraryName()
    {
        return libraryName;
    }

    public @NotNull MavenCoordinates getArtifactKey()
    {
        return artifactKey;
    }

    @Override
    public @NotNull String toString()
    {
        return libraryName + " -> " + artifactKey;
    }
}
