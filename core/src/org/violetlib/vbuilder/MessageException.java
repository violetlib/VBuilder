package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
  An exception with a simple text message designed for inclusion in a larger message
  or an IOException, and an optional File.
*/

public final class MessageException
  extends Exception
{
    public static @NotNull MessageException create(@NotNull String message, @Nullable File file)
    {
        return new MessageException(message, file, null);
    }

    public static @NotNull MessageException create(@NotNull IOException ex, @Nullable File file)
    {
        return new MessageException(null, file, ex);
    }

    private final @Nullable String message;
    private final @Nullable File file;
    private final @Nullable IOException ex;

    private MessageException(@Nullable String message, @Nullable File file, @Nullable IOException ex)
    {
        this.message = message;
        this.file = file;
        this.ex = ex;
    }

    public @Nullable String getMessage()
    {
        return message;
    }

    public @Nullable File getFile()
    {
        return file;
    }

    public @Nullable IOException getException()
    {
        return ex;
    }

    public @NotNull String createMessage(@NotNull String pattern)
    {
        String s = message != null ? pattern.replace("@@@", message) : pattern.replace(" [@@@]", "");
        if (ex != null) {
            s = s + ", " + ex;
        }
        if (file != null) {
            s = s + ": " + file;
        }
        return s;
    }
}
